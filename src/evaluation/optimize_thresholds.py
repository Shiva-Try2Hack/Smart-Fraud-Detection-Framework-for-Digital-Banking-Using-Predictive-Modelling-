"""
Cost-based threshold optimization script for NEXUS Pay.

Calculates the expected financial cost across candidate thresholds on validation data:
- Cost of Missed Fraud = Amount lost to fraud
- Cost of False Block = Friction & support overhead (INR 500)
- Cost of Step-up Action = OTP / Biometric friction (INR 25)
- Cost of Review Hold = Operational delay cost (INR 100)

Minimizes total expected cost and compares against legacy fixed thresholds.
"""
import os
import sys
import yaml
import numpy as np
import pandas as pd

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from src.ml_engine.decision import load_decision_config

def calculate_expected_cost(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    amounts: np.ndarray,
    blocked_th: float,
    step_up_th: float,
    cost_missed_mult: float = 1.0,
    cost_false_block: float = 500.0,
    cost_step_up: float = 25.0
) -> dict:
    y_true = np.asarray(y_true, dtype=int)
    y_prob = np.asarray(y_prob, dtype=float)
    amounts = np.asarray(amounts, dtype=float)

    is_blocked = (y_prob >= blocked_th)
    is_step_up = (y_prob >= step_up_th) & (~is_blocked)
    is_allowed = (~is_blocked) & (~is_step_up)

    # 1. Missed Fraud Cost (Fraud allowed or delayed)
    missed_fraud_mask = (y_true == 1) & is_allowed
    cost_missed_fraud = float(np.sum(amounts[missed_fraud_mask] * cost_missed_mult))

    # 2. False Block Cost (Legit user blocked)
    false_block_mask = (y_true == 0) & is_blocked
    cost_fb = float(np.sum(false_block_mask) * cost_false_block)

    # 3. Step-up Cost (Friction on legitimate transactions)
    step_up_legit_mask = (y_true == 0) & is_step_up
    cost_su = float(np.sum(step_up_legit_mask) * cost_step_up)

    total_cost = cost_missed_fraud + cost_fb + cost_su

    # Counts
    blocked_frauds = int(np.sum((y_true == 1) & is_blocked))
    total_frauds = int(np.sum(y_true == 1))

    return {
        "blocked_threshold": round(blocked_th, 3),
        "step_up_threshold": round(step_up_th, 3),
        "total_cost_inr": round(total_cost, 2),
        "cost_missed_fraud_inr": round(cost_missed_fraud, 2),
        "cost_false_block_inr": round(cost_fb, 2),
        "cost_step_up_inr": round(cost_su, 2),
        "frauds_caught": blocked_frauds,
        "total_frauds": total_frauds,
        "false_blocks": int(np.sum(false_block_mask)),
        "step_ups": int(np.sum(step_up_legit_mask))
    }

def optimize_thresholds(val_dataset_path: str = "data/simulated_user_history.csv") -> dict:
    print("\n[OPTIMIZE] Optimizing Cost-Based Decision Thresholds on Validation Data...")
    df = pd.read_csv(val_dataset_path)
    total_rows = len(df)
    val_start = int(total_rows * 0.70)
    val_end = int(total_rows * 0.85)
    val_df = df.iloc[val_start:val_end].copy().reset_index(drop=True)

    y_val = val_df["is_fraud"].values
    amounts_val = val_df["amount"].values

    # Load model v2 to get validation probabilities
    import joblib
    bundle = joblib.load("src/models/fraud_model_v2.joblib")
    clf = bundle["classifier"]
    cal = bundle["calibrator"]
    feats = bundle["feature_columns"]

    X_val = val_df[feats]
    uncal = clf.predict_proba(X_val)[:, 1]
    cal_probs = cal.predict(uncal) if cal else uncal

    cfg = load_decision_config()
    cm = cfg.get("cost_model", {})
    cost_missed = float(cm.get("cost_of_missed_fraud_multiplier", 1.0))
    cost_fb = float(cm.get("cost_of_false_block", 500.0))
    cost_su = float(cm.get("cost_of_step_up", 25.0))

    # Evaluate legacy thresholds (BLOCKED=0.75, STEP-UP/FLAGGED=0.40)
    legacy_cost = calculate_expected_cost(
        y_val, cal_probs, amounts_val,
        blocked_th=0.75, step_up_th=0.40,
        cost_missed_mult=cost_missed, cost_false_block=cost_fb, cost_step_up=cost_su
    )

    # Grid search candidate thresholds
    candidates = []
    for b_th in np.linspace(0.40, 0.90, 11):
        for s_th in np.linspace(0.15, b_th - 0.05, 7):
            res = calculate_expected_cost(
                y_val, cal_probs, amounts_val,
                blocked_th=b_th, step_up_th=s_th,
                cost_missed_mult=cost_missed, cost_false_block=cost_fb, cost_step_up=cost_su
            )
            candidates.append(res)

    best_candidate = min(candidates, key=lambda c: c["total_cost_inr"])

    print("=" * 65)
    print(f" Legacy Thresholds (0.40 / 0.75) Expected Cost: INR {legacy_cost['total_cost_inr']:,.2f}")
    print(f" Optimal Thresholds ({best_candidate['step_up_threshold']} / {best_candidate['blocked_threshold']}) Expected Cost: INR {best_candidate['total_cost_inr']:,.2f}")
    print(f" Cost Reduction: INR {legacy_cost['total_cost_inr'] - best_candidate['total_cost_inr']:,.2f}")
    print("=" * 65)

    return {
        "legacy": legacy_cost,
        "optimal": best_candidate,
        "top_candidates": sorted(candidates, key=lambda c: c["total_cost_inr"])[:5]
    }

if __name__ == "__main__":
    optimize_thresholds()
