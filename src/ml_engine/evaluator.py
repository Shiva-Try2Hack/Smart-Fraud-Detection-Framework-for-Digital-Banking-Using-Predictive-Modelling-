import os
import joblib
import pandas as pd
import numpy as np
import sys
from typing import Optional, Dict, Any, List

from src.ml_engine.integrity import verify_model_integrity
from src.ml_engine.explainer import TransactionExplainer
from src.ml_engine.features import engineer_single_payload, ALL_11_FEATURES

MODEL_VERSION = os.environ.get("MODEL_VERSION", "v1").lower()

def _resolve_model_path(version: str = "v1"):
    model_filename = "fraud_model_v2.joblib" if version == "v2" else "fraud_model.pkl"
    # 1. Check relative to current evaluator file (__file__)
    candidates = [
        os.path.join(os.path.dirname(__file__), "..", "models", model_filename),
        os.path.join(os.path.dirname(__file__), "..", "..", "src", "models", model_filename),
    ]
    # 2. Check relative to sys.executable directory
    if getattr(sys, "frozen", False):
        exe_dir = os.path.dirname(sys.executable)
        candidates.extend([
            os.path.join(exe_dir, "src", "models", model_filename),
            os.path.join(exe_dir, "models", model_filename),
            os.path.join(exe_dir, "lib", "src", "models", model_filename),
        ])
    # 3. Check current working directory
    cwd = os.getcwd()
    candidates.extend([
        os.path.join(cwd, "src", "models", model_filename),
        os.path.join(cwd, "models", model_filename),
    ])
    for c in candidates:
        norm = os.path.abspath(c)
        if os.path.exists(norm):
            return norm
    return os.path.abspath(candidates[0])

MODEL_PATH = _resolve_model_path(MODEL_VERSION)

class RiskScoringEngine:
    def __init__(self, version: Optional[str] = None):
        self.version = (version or MODEL_VERSION).lower()
        resolved_path = _resolve_model_path(self.version)
        if not os.path.exists(resolved_path):
            # Fallback to v1 if v2 requested but not present
            if self.version == "v2":
                self.version = "v1"
                resolved_path = _resolve_model_path("v1")
            if not os.path.exists(resolved_path):
                raise FileNotFoundError(f"Model file not found at {resolved_path}. Run train.py first.")
        
        # Hard Rule 12: Cryptographic SHA-256 integrity check before loading
        verify_model_integrity(resolved_path)

        saved_bundle = joblib.load(resolved_path)
        
        # Phase 2 Requirement: Schema version check
        from src.features.schema import FEATURE_SCHEMA_VERSION, FEATURE_COLUMNS_V2
        bundle_schema = saved_bundle.get("schema_version")
        if bundle_schema and bundle_schema != FEATURE_SCHEMA_VERSION:
            raise ValueError(
                f"Model schema version mismatch! Bundle has '{bundle_schema}', "
                f"but runtime requires '{FEATURE_SCHEMA_VERSION}'"
            )

        self.classifier = saved_bundle["classifier"]
        self.iso_model = saved_bundle.get("isolation_forest")
        self.calibrator = saved_bundle.get("calibrator")
        self.feature_columns = saved_bundle.get("feature_columns", ALL_11_FEATURES)
        self.schema_version = bundle_schema or "1.0.0"
        self.thresholds = saved_bundle.get("thresholds", {
            "flagged": 0.40,
            "blocked": 0.75
        })
        self.explainer = TransactionExplainer(self.classifier, self.feature_columns)
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

        # Direct vector construction without pandas/pyarrow overhead or missing module errors
        features_dict = {
            "type_code": float(type_code),
            "amount": float(amount),
            "oldbalanceOrg": float(oldbalanceOrg),
            "newbalanceOrig": float(newbalanceOrig),
            "oldbalanceDest": float(oldbalanceDest),
            "newbalanceDest": float(newbalanceDest),
            "amount_to_balance_ratio": float(amount_to_balance_ratio),
            "balance_drain": float(balance_drain),
            "sudden_depletion_flag": float(sudden_depletion_flag),
            "orig_balance_err": float(orig_balance_err),
            "dest_balance_err": float(dest_balance_err)
        }
        vec = [features_dict.get(col, 0.0) for col in self.feature_columns]
        return np.array([vec], dtype=np.float64)

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

        # --- Layer 2 & 3: Model Inference & Feature Extraction ---
        from src.ml_engine.decision import DecisionLayer
        decision_layer = DecisionLayer()

        degraded_mode = bool(payload.get("degraded_mode", False))
        is_outlier = False
        ml_prob = 0.0
        shap_reasons = []

        if not degraded_mode:
            try:
                feature_df = engineer_single_payload(payload, self.feature_columns)
                raw_prob = float(self.classifier.predict_proba(feature_df)[0][1])
                
                # Apply isotonic calibration
                if self.calibrator is not None:
                    ml_prob = float(self.calibrator.predict(np.array([raw_prob]))[0])
                else:
                    ml_prob = raw_prob

                # Isolation Forest
                if self.iso_model is not None:
                    try:
                        iso_decision = int(self.iso_model.predict(feature_df)[0])
                        is_outlier = (iso_decision == -1)
                    except Exception:
                        is_outlier = False

                # SHAP Explainability if elevated risk
                flagged_th = self.thresholds.get("flagged", 0.35)
                if ml_prob >= flagged_th:
                    shap_reasons = self.explainer.explain_transaction(feature_df, top_k=3)
            except Exception as e:
                # Automatic safe fallback if model evaluation errors
                degraded_mode = True
                payload["degraded_mode"] = True

        # Phase 3 Decision Layer Evaluation
        decision_out = decision_layer.decide(
            payload=payload,
            ml_probability=ml_prob,
            is_anomaly=is_outlier,
            shap_reasons=shap_reasons
        )

        return {
            "status": decision_out["status"],
            "risk_score": decision_out["risk_score"],
            "risk_level": decision_out["risk_level"],
            "supervised_ml_prob": round(ml_prob, 4),
            "anomaly_detected": is_outlier,
            "reasons": decision_out["reasons"],
            "model_version": self.version,
            "recommended_action": decision_out["recommended_action"],
            "scam_warning": decision_out["scam_warning"],
            "hold_duration_minutes": decision_out["hold_duration_minutes"],
            "structured_reasons": decision_out["structured_reasons"],
            "degraded_mode": decision_out["degraded_mode"]
        }
