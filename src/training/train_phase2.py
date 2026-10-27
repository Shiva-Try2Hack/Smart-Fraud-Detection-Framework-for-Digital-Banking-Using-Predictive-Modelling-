"""
Phase 2 Training & Evaluation Orchestrator:
- Trains and evaluates Model v2 with Behavioral and Velocity Features.
- Compares against Phase 1 Baseline on the multi-scenario dataset.
- Evaluates recall per fraud scenario (ATO, Scam/APP, Mule Ring, Split-Drain, Slow-and-Low, Card-Testing).
- Evaluates false-positive rate on hard negatives (festival spikes, large wedding gifts, etc.).
- Saves Phase 2 Model Bundle (models/fraud_model_v2.joblib) with schema_version='2.0.0'.
- Writes SHA-256 sidecar checksum.
"""
import os
import sys
import json
import yaml
import subprocess
import joblib
import numpy as np
import pandas as pd
from datetime import datetime

from sklearn.ensemble import RandomForestClassifier, IsolationForest
from sklearn.isotonic import IsotonicRegression
from sklearn.metrics import precision_score, recall_score, roc_auc_score, precision_recall_curve, auc

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from src.features.schema import FEATURE_SCHEMA_VERSION, FEATURE_COLUMNS_V2, BASE_FEATURES_V1
from src.evaluation.evaluate import evaluate_fraud_model, plot_curves, plot_calibration
from src.ml_engine.integrity import compute_file_sha256

def get_git_commit_hash() -> str:
    try:
        out = subprocess.check_output(["git", "rev-parse", "HEAD"], stderr=subprocess.DEVNULL)
        return out.decode("utf-8").strip()
    except Exception:
        return "UNKNOWN"

def run_phase_2(data_path: str = "data/simulated_user_history.csv", reports_dir: str = "reports"):
    print("=" * 70)
    print("[NEXUS PAY] PHASE 2: USER BEHAVIOR & VELOCITY MODEL TRAINING")
    print("=" * 70)
    
    os.makedirs(reports_dir, exist_ok=True)
    os.makedirs("src/models", exist_ok=True)

    print(f"\n[Step 1/6] Loading Simulated User History Dataset from: {data_path}...")
    df = pd.read_csv(data_path)
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    df = df.sort_values(by="timestamp").reset_index(drop=True)

    total_rows = len(df)
    n_fraud = int(df["is_fraud"].sum())
    print(f"  Total Transactions: {total_rows:,} | Frauds: {n_fraud:,} ({n_fraud/total_rows*100:.3f}%)")

    # Time-based split: Earliest 70% train, next 15% validation, final 15% test
    train_end = int(total_rows * 0.70)
    val_end = int(total_rows * 0.85)

    train_df = df.iloc[:train_end].copy().reset_index(drop=True)
    val_df = df.iloc[train_end:val_end].copy().reset_index(drop=True)
    test_df = df.iloc[val_end:].copy().reset_index(drop=True)

    print("\n[Step 2/6] Time-Based Splits:")
    print(f"  Train: {len(train_df):,} rows ({train_df['timestamp'].min()} to {train_df['timestamp'].max()}) | Frauds: {train_df['is_fraud'].sum()}")
    print(f"  Val:   {len(val_df):,} rows ({val_df['timestamp'].min()} to {val_df['timestamp'].max()}) | Frauds: {val_df['is_fraud'].sum()}")
    print(f"  Test:  {len(test_df):,} rows ({test_df['timestamp'].min()} to {test_df['timestamp'].max()}) | Frauds: {test_df['is_fraud'].sum()}")

    # Step 3: Train Phase 1 Baseline Model (Base features only) vs Phase 2 Model (All Behavioral Features)
    print("\n[Step 3/6] Training Models:")
    y_train = train_df["is_fraud"].values
    y_val = val_df["is_fraud"].values
    y_test = test_df["is_fraud"].values
    amounts_test = test_df["amount"].values

    # Model A: Phase 1 Feature Model (Without behavior/velocity)
    print("  -> Training Phase 1 Baseline Model (Base 5 Features)...")
    X_train_base = train_df[BASE_FEATURES_V1]
    X_val_base = val_df[BASE_FEATURES_V1]
    X_test_base = test_df[BASE_FEATURES_V1]

    rf_phase1 = RandomForestClassifier(n_estimators=70, max_depth=10, class_weight="balanced", random_state=42, n_jobs=-1)
    rf_phase1.fit(X_train_base, y_train)
    probs_phase1_test = rf_phase1.predict_proba(X_test_base)[:, 1]

    metrics_phase1 = evaluate_fraud_model(
        y_true=y_test,
        y_prob=probs_phase1_test,
        amounts=amounts_test,
        model_name="Phase 1 Model (No User Behavior)"
    )

    # Model B: Phase 2 Model with User Behavior & Velocity Features
    print("  -> Training Phase 2 Model (Full 25 Behavioral & Velocity Features)...")
    X_train_v2 = train_df[FEATURE_COLUMNS_V2]
    X_val_v2 = val_df[FEATURE_COLUMNS_V2]
    X_test_v2 = test_df[FEATURE_COLUMNS_V2]

    rf_phase2 = RandomForestClassifier(n_estimators=80, max_depth=12, class_weight="balanced", random_state=42, n_jobs=-1)
    rf_phase2.fit(X_train_v2, y_train)

    # Probability Calibration on Validation Set
    val_uncal = rf_phase2.predict_proba(X_val_v2)[:, 1]
    calibrator = IsotonicRegression(out_of_bounds="clip", y_min=0.0, y_max=1.0)
    calibrator.fit(val_uncal, y_val)

    uncal_test_probs = rf_phase2.predict_proba(X_test_v2)[:, 1]
    cal_test_probs = calibrator.predict(uncal_test_probs)

    metrics_phase2 = evaluate_fraud_model(
        y_true=y_test,
        y_prob=cal_test_probs,
        amounts=amounts_test,
        model_name="Phase 2 Model (With User Behavior & Velocity)"
    )

    # Step 4: Per-Scenario Fraud Breakdown & Hard Negatives Evaluation
    print("\n[Step 4/6] Computing Per-Scenario Recall & Hard Negatives FPR...")
    test_df_eval = test_df.copy()
    test_df_eval["prob_p1"] = probs_phase1_test
    test_df_eval["prob_p2"] = cal_test_probs
    test_df_eval["blocked_p1"] = (probs_phase1_test >= 0.75).astype(int)
    test_df_eval["blocked_p2"] = (cal_test_probs >= 0.40).astype(int) # At flagged/blocked threshold
    test_df_eval["flagged_or_blocked_p2"] = (cal_test_probs >= 0.20).astype(int)

    scenario_breakdown = []
    scenarios = [s for s in test_df["fraud_scenario"].unique() if s.startswith(("ACCOUNT_TAKEOVER", "SCAM_APP", "MULE_RING", "SPLIT_DRAIN", "SLOW_AND_LOW", "CARD_TESTING"))]

    for sc in scenarios:
        sc_df = test_df_eval[test_df_eval["fraud_scenario"] == sc]
        cnt = len(sc_df)
        if cnt == 0:
            continue
        p1_rec = float(sc_df["blocked_p1"].mean())
        p2_rec = float(sc_df["blocked_p2"].mean())
        scenario_breakdown.append({
            "scenario": sc,
            "test_count": cnt,
            "phase1_recall": round(p1_rec, 4),
            "phase2_recall": round(p2_rec, 4),
            "improvement": round(p2_rec - p1_rec, 4)
        })

    # Hard Negatives
    hard_negatives = ["HARD_NEGATIVE_FESTIVAL_SPIKE", "HARD_NEGATIVE_FIRST_TIME_LARGE_GIFT", "LEGIT_RENT", "LEGIT_STANDARD"]
    hard_negative_breakdown = []
    for hn in hard_negatives:
        hn_df = test_df_eval[test_df_eval["fraud_scenario"] == hn]
        cnt = len(hn_df)
        if cnt == 0:
            continue
        fpr_p1 = float((hn_df["blocked_p1"] == 1).mean())
        fpr_p2 = float((hn_df["blocked_p2"] == 1).mean())
        hard_negative_breakdown.append({
            "scenario": hn,
            "test_count": cnt,
            "phase1_false_positive_rate": round(fpr_p1, 4),
            "phase2_false_positive_rate": round(fpr_p2, 4)
        })

    # Top Feature Importances
    importances = list(zip(FEATURE_COLUMNS_V2, rf_phase2.feature_importances_))
    importances.sort(key=lambda x: x[1], reverse=True)

    print("\n[Step 5/6] Top 10 Feature Importances in Behavioral Model:")
    for feat, imp in importances[:10]:
        print(f"  - {feat:<32}: {imp*100:.2f}%")

    # Step 6: Serialize Versioned Model Bundle with schema_version='2.0.0'
    print("\n[Step 6/6] Serializing Model Bundle v2 and Integrity SHA-256...")
    iso = IsolationForest(n_estimators=40, contamination=0.01, random_state=42, n_jobs=-1)
    iso.fit(X_train_v2[y_train == 0])

    bundle_v2 = {
        "version": "2.0.0",
        "schema_version": FEATURE_SCHEMA_VERSION,
        "created_at": datetime.utcnow().isoformat(),
        "git_commit": get_git_commit_hash(),
        "model_name": "Behavioral-Velocity-RandomForest",
        "classifier": rf_phase2,
        "calibrator": calibrator,
        "isolation_forest": iso,
        "feature_columns": FEATURE_COLUMNS_V2,
        "base_feature_columns": BASE_FEATURES_V1,
        "thresholds": {
            "flagged": 0.25,
            "blocked": 0.65,
            "fallback_flagged": 0.40,
            "fallback_blocked": 0.75
        },
        "metrics": {
            "phase2_pr_auc": metrics_phase2["pr_auc"],
            "phase1_pr_auc": metrics_phase1["pr_auc"],
            "phase2_roc_auc": metrics_phase2["roc_auc"],
            "phase1_roc_auc": metrics_phase1["roc_auc"]
        }
    }

    bundle_path = os.path.join("src/models", "fraud_model_v2.joblib")
    joblib.dump(bundle_v2, bundle_path)
    bundle_hash = compute_file_sha256(bundle_path)
    with open(f"{bundle_path}.sha256", "w") as f:
        f.write(f"{bundle_hash}  fraud_model_v2.joblib\n")
    print(f"  Model v2 serialized: {bundle_path} (SHA-256: {bundle_hash})")

    # Save Markdown and JSON reports
    report_data = {
        "metadata": {
            "execution_time": datetime.utcnow().isoformat(),
            "git_commit": get_git_commit_hash(),
            "schema_version": FEATURE_SCHEMA_VERSION,
            "dataset_rows": total_rows
        },
        "comparison_metrics": {
            "phase1": metrics_phase1,
            "phase2": metrics_phase2
        },
        "scenario_breakdown": scenario_breakdown,
        "hard_negatives": hard_negative_breakdown,
        "top_features": [{"feature": f, "importance_pct": round(float(i * 100), 2)} for f, i in importances]
    }

    json_report_path = os.path.join(reports_dir, "phase_2_behavioral_report.json")
    with open(json_report_path, "w") as f:
        json.dump(report_data, f, indent=2)

    md_report_path = os.path.join(reports_dir, "phase_2_behavioral_report.md")
    with open(md_report_path, "w") as f:
        f.write("# Phase 2: User Behavior & Velocity Model Report\n\n")
        f.write(f"- **Execution Time:** {report_data['metadata']['execution_time']}\n")
        f.write(f"- **Git Commit:** `{report_data['metadata']['git_commit']}`\n")
        f.write(f"- **Schema Version:** `{FEATURE_SCHEMA_VERSION}`\n\n")

        f.write("## 1. Overall Model Comparison (Test Set)\n\n")
        f.write("| Model | Features | PR-AUC | ROC-AUC | Recall@Fraud | Fraud Money Caught |\n")
        f.write("| :--- | :--- | :--- | :--- | :--- | :--- |\n")
        f.write(f"| **Phase 1 Baseline** | 5 (No Behavior) | {metrics_phase1['pr_auc']:.4f} | {metrics_phase1['roc_auc']:.4f} | {metrics_phase1['blocked_metrics']['recall']:.4f} | {metrics_phase1['monetary']['money_catch_rate']*100:.1f}% |\n")
        f.write(f"| **Phase 2 Behavioral** | 25 (Behavior + Velocity) | **{metrics_phase2['pr_auc']:.4f}** | **{metrics_phase2['roc_auc']:.4f}** | **{metrics_phase2['blocked_metrics']['recall']:.4f}** | **{metrics_phase2['monetary']['money_catch_rate']*100:.1f}%** |\n\n")

        f.write("## 2. Per-Scenario Fraud Recall Breakdown\n\n")
        f.write("| Fraud Scenario | Test Instances | Phase 1 Recall | Phase 2 Recall | Delta |\n")
        f.write("| :--- | :--- | :--- | :--- | :--- |\n")
        for sc in scenario_breakdown:
            f.write(f"| `{sc['scenario']}` | {sc['test_count']} | {sc['phase1_recall']*100:.1f}% | **{sc['phase2_recall']*100:.1f}%** | +{sc['improvement']*100:.1f}% |\n")
        f.write("\n")

        f.write("## 3. False-Positive Rate on Hard Negatives\n\n")
        f.write("| Legitimate Scenario | Test Instances | Phase 1 FPR | Phase 2 FPR |\n")
        f.write("| :--- | :--- | :--- | :--- |\n")
        for hn in hard_negative_breakdown:
            f.write(f"| `{hn['scenario']}` | {hn['test_count']:,} | {hn['phase1_false_positive_rate']*100:.2f}% | **{hn['phase2_false_positive_rate']*100:.2f}%** |\n")
        f.write("\n")

        f.write("## 4. Top Feature Importances\n\n")
        f.write("| Rank | Feature Name | Importance |\n")
        f.write("| :--- | :--- | :--- |\n")
        for r, (feat, imp) in enumerate(importances[:10], start=1):
            f.write(f"| {r} | `{feat}` | {imp*100:.2f}% |\n")
        f.write("\n")

    print(f"\n[DONE] Reports generated: {json_report_path} and {md_report_path}")
    return report_data

if __name__ == "__main__":
    run_phase_2()
