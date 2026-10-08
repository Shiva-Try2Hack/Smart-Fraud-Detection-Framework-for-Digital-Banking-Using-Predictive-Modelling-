"""
Ultra-fast, zero-GPU, zero-cost ML model trainer for Fraud Detection.
Trains in ~5-10 seconds on any standard CPU.
Uses Random Forest + Isolation Forest on financial fraud patterns (PaySim distribution).
"""
import os
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier, IsolationForest
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
os.makedirs(MODELS_DIR, exist_ok=True)

def generate_synthetic_paysim_sample(n_samples=50000, fraud_ratio=0.03):
    """
    Synthesize realistic financial transaction distributions mimicking the PaySim benchmark:
    - Types: TRANSFER, CASH_OUT, PAYMENT, CASH_IN, DEBIT
    - Frauds concentrated in TRANSFER and CASH_OUT with balance depletion patterns.
    """
    np.random.seed(42)
    n_fraud = int(n_samples * fraud_ratio)
    n_legit = n_samples - n_fraud

    # 1. Legitimate transactions
    types_legit = np.random.choice(["PAYMENT", "TRANSFER", "CASH_OUT", "CASH_IN", "DEBIT"], size=n_legit, p=[0.35, 0.25, 0.25, 0.12, 0.03])
    amounts_legit = np.random.exponential(scale=2500, size=n_legit) + 50
    oldbalance_orig_legit = amounts_legit * np.random.uniform(1.2, 20.0, size=n_legit) + np.random.uniform(500, 50000, size=n_legit)
    newbalance_orig_legit = np.maximum(0, oldbalance_orig_legit - amounts_legit)
    
    oldbalance_dest_legit = np.random.exponential(scale=10000, size=n_legit)
    newbalance_dest_legit = oldbalance_dest_legit + amounts_legit
    is_fraud_legit = np.zeros(n_legit, dtype=int)

    # 2. Fraudulent transactions (High amount, complete balance drain, TRANSFER/CASH_OUT)
    types_fraud = np.random.choice(["TRANSFER", "CASH_OUT"], size=n_fraud, p=[0.55, 0.45])
    amounts_fraud = np.random.uniform(50000, 1500000, size=n_fraud)
    # Fraud pattern: account is emptied almost completely
    oldbalance_orig_fraud = amounts_fraud * np.random.uniform(1.0, 1.05, size=n_fraud)
    newbalance_orig_fraud = np.maximum(0, oldbalance_orig_fraud - amounts_fraud)
    
    # Destination often has 0 previous balance (mule accounts)
    oldbalance_dest_fraud = np.random.choice([0, 500, 2000], size=n_fraud, p=[0.7, 0.2, 0.1])
    newbalance_dest_fraud = oldbalance_dest_fraud + amounts_fraud
    is_fraud_fraud = np.ones(n_fraud, dtype=int)

    # Merge
    types = np.concatenate([types_legit, types_fraud])
    amounts = np.concatenate([amounts_legit, amounts_fraud])
    old_orig = np.concatenate([oldbalance_orig_legit, oldbalance_orig_fraud])
    new_orig = np.concatenate([newbalance_orig_legit, newbalance_orig_fraud])
    old_dest = np.concatenate([oldbalance_dest_legit, oldbalance_dest_fraud])
    new_dest = np.concatenate([newbalance_dest_legit, newbalance_dest_fraud])
    targets = np.concatenate([is_fraud_legit, is_fraud_fraud])

    df = pd.DataFrame({
        "type": types,
        "amount": amounts,
        "oldbalanceOrg": old_orig,
        "newbalanceOrig": new_orig,
        "oldbalanceDest": old_dest,
        "newbalanceDest": new_dest,
        "isFraud": targets
    })

    # Shuffle
    df = df.sample(frac=1.0, random_state=42).reset_index(drop=True)
    return df

def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """Feature engineering pipeline matching the college synopsis specification"""
    feat = pd.DataFrame()
    
    # One-hot / categorical map
    type_map = {"PAYMENT": 0, "TRANSFER": 1, "CASH_OUT": 2, "CASH_IN": 3, "DEBIT": 4}
    feat["type_code"] = df["type"].map(lambda x: type_map.get(str(x).upper(), 0))
    feat["amount"] = df["amount"]
    feat["oldbalanceOrg"] = df["oldbalanceOrg"]
    feat["newbalanceOrig"] = df["newbalanceOrig"]
    feat["oldbalanceDest"] = df["oldbalanceDest"]
    feat["newbalanceDest"] = df["newbalanceDest"]

    # Behavioral Contextual Ratios (from synopsis section 8 & 16)
    feat["amount_to_balance_ratio"] = feat["amount"] / (feat["oldbalanceOrg"] + 1.0)
    feat["balance_drain"] = (feat["oldbalanceOrg"] - feat["newbalanceOrig"]) / (feat["oldbalanceOrg"] + 1.0)
    feat["sudden_depletion_flag"] = ((feat["oldbalanceOrg"] > 1000) & (feat["newbalanceOrig"] <= 10)).astype(int)
    feat["orig_balance_err"] = (feat["oldbalanceOrg"] - feat["amount"]) - feat["newbalanceOrig"]
    feat["dest_balance_err"] = (feat["oldbalanceDest"] + feat["amount"]) - feat["newbalanceDest"]
    
    return feat

def train_and_save():
    print("[1/4] Generating synthetic benchmark transactions (PaySim distribution)...")
    df = generate_synthetic_paysim_sample(n_samples=40000, fraud_ratio=0.04)
    
    print("[2/4] Engineering contextual behavioral features...")
    X = engineer_features(df)
    y = df["isFraud"]

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

    print("[3/4] Training CPU-optimized Random Forest Classifier (fast, lightweight)...")
    # Low n_estimators and max_depth ensures it finishes in ~3-5 seconds with 0 GPU load
    rf = RandomForestClassifier(
        n_estimators=60,
        max_depth=12,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1
    )
    rf.fit(X_train, y_train)

    y_pred = rf.predict(X_test)
    y_prob = rf.predict_proba(X_test)[:, 1]
    auc = roc_auc_score(y_test, y_prob)

    print(f"Random Forest ROC-AUC Score: {auc:.4f}")
    print("Classification Report:")
    print(classification_report(y_test, y_pred, digits=3))

    # Also train an Unsupervised Isolation Forest for zero-day anomaly detection
    print("[4/4] Training Isolation Forest for behavioral anomaly detection...")
    iso = IsolationForest(
        n_estimators=40,
        contamination=0.04,
        random_state=42,
        n_jobs=-1
    )
    iso.fit(X_train[y_train == 0]) # Fit on clean data

    # Save models
    model_payload = {
        "classifier": rf,
        "isolation_forest": iso,
        "feature_columns": list(X.columns)
    }
    model_path = os.path.join(MODELS_DIR, "fraud_model.pkl")
    joblib.dump(model_payload, model_path)
    print(f"Successfully serialized model to: {model_path}")

if __name__ == "__main__":
    train_and_save()
