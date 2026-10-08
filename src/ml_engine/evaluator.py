import os
import joblib
import pandas as pd
import numpy as np

MODEL_PATH = os.path.join(os.path.dirname(__file__), "..", "models", "fraud_model.pkl")

class RiskScoringEngine:
    def __init__(self):
        if not os.path.exists(MODEL_PATH):
            raise FileNotFoundError(f"Model file not found at {MODEL_PATH}. Run train.py first.")
        
        saved_bundle = joblib.load(MODEL_PATH)
        self.rf_model = saved_bundle["classifier"]
        self.iso_model = saved_bundle["isolation_forest"]
        self.feature_columns = saved_bundle["feature_columns"]
        self.type_map = {"PAYMENT": 0, "TRANSFER": 1, "CASH_OUT": 2, "CASH_IN": 3, "DEBIT": 4}

    def prepare_features(self, payload: dict) -> pd.DataFrame:
        txn_type = str(payload.get("transaction_type", "TRANSFER")).upper()
        amount = float(payload.get("amount", 0.0))
        oldbalanceOrg = float(payload.get("sender_current_balance", 0.0))
        newbalanceOrig = max(0.0, oldbalanceOrg - amount)
        oldbalanceDest = float(payload.get("receiver_current_balance", 0.0))
        newbalanceDest = oldbalanceDest + amount

        # Behavioral & contextual ratios
        type_code = self.type_map.get(txn_type, 0)
        amount_to_balance_ratio = amount / (oldbalanceOrg + 1.0)
        balance_drain = (oldbalanceOrg - newbalanceOrig) / (oldbalanceOrg + 1.0)
        sudden_depletion_flag = 1 if (oldbalanceOrg > 1000 and newbalanceOrig <= 10) else 0
        orig_balance_err = (oldbalanceOrg - amount) - newbalanceOrig
        dest_balance_err = (oldbalanceDest + amount) - newbalanceDest

        row = {
            "type_code": type_code,
            "amount": amount,
            "oldbalanceOrg": oldbalanceOrg,
            "newbalanceOrig": newbalanceOrig,
            "oldbalanceDest": oldbalanceDest,
            "newbalanceDest": newbalanceDest,
            "amount_to_balance_ratio": amount_to_balance_ratio,
            "balance_drain": balance_drain,
            "sudden_depletion_flag": sudden_depletion_flag,
            "orig_balance_err": orig_balance_err,
            "dest_balance_err": dest_balance_err
        }

        df = pd.DataFrame([row])
        # Reorder columns to match training schema
        return df[self.feature_columns]

    def evaluate_transaction(self, payload: dict) -> dict:
        """
        Multi-Layer Decision Engine:
        Layer 1: Rule-Based Checks (instant velocity/balance violation)
        Layer 2: Supervised Random Forest Risk Probability
        Layer 3: Unsupervised Isolation Forest Anomaly Detection
        Layer 4: Risk Tiering & Explanation Reason Codes
        """
        amount = float(payload.get("amount", 0.0))
        sender_balance = float(payload.get("sender_current_balance", 0.0))
        txn_type = str(payload.get("transaction_type", "TRANSFER")).upper()

        reasons = []
        is_hard_rule_violation = False

        # --- Layer 1: Rule Engine ---
        if amount > sender_balance:
            reasons.append("Insufficient funds: Amount exceeds account balance")
            is_hard_rule_violation = True
        
        if amount >= 500000:
            reasons.append(f"High-Value Transaction Threshold Exceeded (INR {amount:,.2f})")

        if sender_balance > 0 and (amount / sender_balance) >= 0.95 and amount >= 10000:
            reasons.append("Critical Account Depletion: Over 95% of total balance being transferred in single shot")

        # --- Layer 2 & 3: Model Inference ---
        features = self.prepare_features(payload)
        
        # Random Forest Probability [0.0 - 1.0]
        rf_prob = float(self.rf_model.predict_proba(features)[0][1])
        
        # Isolation Forest outlier detection: -1 is anomaly, 1 is normal
        iso_decision = int(self.iso_model.predict(features)[0])
        is_outlier = (iso_decision == -1)
        if is_outlier:
            reasons.append("Zero-Day Anomaly: Transaction deviates significantly from normal spending baseline")

        if rf_prob >= 0.70:
            reasons.append(f"Supervised ML Fraud Risk Alert (Confidence: {rf_prob*100:.1f}%)")

        # --- Layer 4: Final Risk Tiering ---
        # Overall risk score calculation
        combined_risk_score = round(max(rf_prob, 0.95 if is_hard_rule_violation else (0.75 if (is_outlier and rf_prob > 0.4) else rf_prob)), 4)

        if combined_risk_score >= 0.75 or is_hard_rule_violation:
            status = "BLOCKED"
            risk_level = "CRITICAL"
        elif combined_risk_score >= 0.40:
            status = "FLAGGED"
            risk_level = "MEDIUM"
            if not reasons:
                reasons.append("Moderate risk score requiring step-up verification")
        else:
            status = "APPROVED"
            risk_level = "LOW"
            if not reasons:
                reasons.append("Transaction verified and safe")

        return {
            "status": status,
            "risk_score": combined_risk_score,
            "risk_level": risk_level,
            "supervised_ml_prob": round(rf_prob, 4),
            "anomaly_detected": is_outlier,
            "reasons": reasons
        }
