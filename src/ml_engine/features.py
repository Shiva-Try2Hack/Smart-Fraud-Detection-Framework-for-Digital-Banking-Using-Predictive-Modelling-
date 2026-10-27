"""
Feature extraction module for training and serving.
Supports full 11-feature baseline and leak-free pre-transaction feature sets.
"""
import pandas as pd
import numpy as np
from typing import List, Dict, Any, Union

TYPE_MAP = {"PAYMENT": 0, "TRANSFER": 1, "CASH_OUT": 2, "CASH_IN": 3, "DEBIT": 4}

ALL_11_FEATURES = [
    "type_code",
    "amount",
    "oldbalanceOrg",
    "newbalanceOrig",
    "oldbalanceDest",
    "newbalanceDest",
    "amount_to_balance_ratio",
    "balance_drain",
    "sudden_depletion_flag",
    "orig_balance_err",
    "dest_balance_err"
]

NO_ERR_FEATURES = [
    "type_code",
    "amount",
    "oldbalanceOrg",
    "newbalanceOrig",
    "oldbalanceDest",
    "newbalanceDest",
    "amount_to_balance_ratio",
    "balance_drain",
    "sudden_depletion_flag"
]

LEAK_FREE_PRE_TXN_FEATURES = [
    "type_code",
    "amount",
    "oldbalanceOrg",
    "oldbalanceDest",
    "amount_to_balance_ratio"
]

def engineer_features_df(df: pd.DataFrame, feature_set: str = "all_11") -> pd.DataFrame:
    """
    Computes features from a transaction DataFrame (training or batch).
    Matches serving pipeline exactly.
    """
    feat = pd.DataFrame(index=df.index)
    
    # Type mapping
    feat["type_code"] = df["type"].astype(str).str.upper().map(lambda x: TYPE_MAP.get(x, 0)).astype(float)
    feat["amount"] = df["amount"].astype(float)
    feat["oldbalanceOrg"] = df["oldbalanceOrg"].astype(float)
    feat["newbalanceOrig"] = df["newbalanceOrig"].astype(float)
    feat["oldbalanceDest"] = df["oldbalanceDest"].astype(float)
    feat["newbalanceDest"] = df["newbalanceDest"].astype(float)

    # Ratios
    feat["amount_to_balance_ratio"] = feat["amount"] / (feat["oldbalanceOrg"] + 1.0)
    feat["balance_drain"] = (feat["oldbalanceOrg"] - feat["newbalanceOrig"]) / (feat["oldbalanceOrg"] + 1.0)
    feat["sudden_depletion_flag"] = ((feat["oldbalanceOrg"] > 1000) & (feat["newbalanceOrig"] <= 10)).astype(float)
    feat["orig_balance_err"] = (feat["oldbalanceOrg"] - feat["amount"]) - feat["newbalanceOrig"]
    feat["dest_balance_err"] = (feat["oldbalanceDest"] + feat["amount"]) - feat["newbalanceDest"]

    if feature_set == "no_err":
        return feat[NO_ERR_FEATURES]
    elif feature_set == "leak_free":
        return feat[LEAK_FREE_PRE_TXN_FEATURES]
    return feat[ALL_11_FEATURES]

def engineer_single_payload(payload: Dict[str, Any], feature_cols: List[str]) -> pd.DataFrame:
    """
    Engineers features for a single transaction payload at inference time.
    Returns a 1-row DataFrame with explicit feature column names to prevent Scikit-learn warnings.
    """
    txn_type = str(payload.get("transaction_type", "TRANSFER")).upper()
    amount = float(payload.get("amount", 0.0))
    oldbalanceOrg = float(payload.get("sender_current_balance", 0.0))
    newbalanceOrig = max(0.0, oldbalanceOrg - amount)
    oldbalanceDest = float(payload.get("receiver_current_balance", 0.0))
    newbalanceDest = oldbalanceDest + amount

    type_code = float(TYPE_MAP.get(txn_type, 0))
    amount_to_balance_ratio = amount / (oldbalanceOrg + 1.0)
    balance_drain = (oldbalanceOrg - newbalanceOrig) / (oldbalanceOrg + 1.0)
    sudden_depletion_flag = 1.0 if (oldbalanceOrg > 1000 and newbalanceOrig <= 10) else 0.0
    orig_balance_err = (oldbalanceOrg - amount) - newbalanceOrig
    dest_balance_err = (oldbalanceDest + amount) - newbalanceDest

    full_dict = {
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

    # If payload contains behavioral features (e.g. from user_features.py), incorporate them
    user_behavior = payload.get("user_behavior", {})
    if isinstance(user_behavior, dict):
        full_dict.update(user_behavior)

    # Sensible defaults for behavioral features if model v2 expects them but user is cold-start
    from src.features.schema import USER_BEHAVIOR_FEATURES
    for b_feat in USER_BEHAVIOR_FEATURES:
        if b_feat not in full_dict:
            if b_feat in ["avg_amount_30d", "max_amount_30d"]:
                full_dict[b_feat] = amount
            elif b_feat == "cold_start":
                full_dict[b_feat] = 1.0
            elif b_feat == "receiver_account_age_days":
                full_dict[b_feat] = 90.0
            elif b_feat == "amount_percentile_for_user":
                full_dict[b_feat] = 0.5
            elif b_feat == "usual_hour_of_day":
                full_dict[b_feat] = 12.0
            else:
                full_dict[b_feat] = 0.0
    
    row_data = {col: [full_dict.get(col, 0.0)] for col in feature_cols}
    return pd.DataFrame(row_data)
