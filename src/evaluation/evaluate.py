"""
Evaluation and Metrics module for Fraud Detection in NEXUS Pay.

Calculates:
- Precision, Recall, F1, PR-AUC, ROC-AUC
- Confusion matrix at configured thresholds
- Recall at fixed FPR (1%, 0.1%, 0.01%)
- Precision@top-k (k=100, 500, 1000)
- Monetary loss metrics: INR amount caught vs. missed
- Generates PR curve, ROC curve, and calibration plots
"""
import os
import json
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from typing import Dict, Any, List, Optional
from sklearn.metrics import (
    precision_score, recall_score, f1_score,
    precision_recall_curve, roc_curve, auc,
    roc_auc_score, confusion_matrix, brier_score_loss
)
from sklearn.calibration import calibration_curve

def evaluate_fraud_model(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    amounts: Optional[np.ndarray] = None,
    flagged_threshold: float = 0.40,
    blocked_threshold: float = 0.75,
    model_name: str = "Model"
) -> Dict[str, Any]:
    y_true = np.asarray(y_true, dtype=int)
    y_prob = np.asarray(y_prob, dtype=float)
    
    # ROC-AUC & PR-AUC
    try:
        roc_auc = float(roc_auc_score(y_true, y_prob))
    except Exception:
        roc_auc = 0.0
        
    precisions, recalls, pr_thresholds = precision_recall_curve(y_true, y_prob)
    pr_auc = float(auc(recalls, precisions))

    # Standard metrics at blocked threshold
    y_pred_blocked = (y_prob >= blocked_threshold).astype(int)
    y_pred_flagged = (y_prob >= flagged_threshold).astype(int)

    cm_blocked = confusion_matrix(y_true, y_pred_blocked, labels=[0, 1]).tolist()
    cm_flagged = confusion_matrix(y_true, y_pred_flagged, labels=[0, 1]).tolist()

    prec_blocked = float(precision_score(y_true, y_pred_blocked, zero_division=0))
    rec_blocked = float(recall_score(y_true, y_pred_blocked, zero_division=0))
    f1_blocked = float(f1_score(y_true, y_pred_blocked, zero_division=0))

    prec_flagged = float(precision_score(y_true, y_pred_flagged, zero_division=0))
    rec_flagged = float(recall_score(y_true, y_pred_flagged, zero_division=0))
    f1_flagged = float(f1_score(y_true, y_pred_flagged, zero_division=0))

    # Recall at fixed FPR (1%, 0.1%, 0.01%)
    fpr, tpr, roc_thresholds = roc_curve(y_true, y_prob)
    def get_recall_at_fpr(target_fpr: float) -> float:
        valid_indices = np.where(fpr <= target_fpr)[0]
        if len(valid_indices) == 0:
            return 0.0
        return float(tpr[valid_indices[-1]])

    rec_at_fpr_1 = get_recall_at_fpr(0.01)
    rec_at_fpr_01 = get_recall_at_fpr(0.001)
    rec_at_fpr_001 = get_recall_at_fpr(0.0001)

    # Precision@top-k
    sorted_indices = np.argsort(y_prob)[::-1]
    def get_prec_at_k(k: int) -> float:
        top_k_idx = sorted_indices[:k]
        if len(top_k_idx) == 0:
            return 0.0
        return float(np.mean(y_true[top_k_idx]))

    prec_at_100 = get_prec_at_k(100)
    prec_at_500 = get_prec_at_k(500)
    prec_at_1000 = get_prec_at_k(1000)

    # Monetary metrics (Amount caught vs. missed)
    money_metrics = {}
    if amounts is not None:
        amounts = np.asarray(amounts, dtype=float)
        fraud_mask = (y_true == 1)
        total_fraud_money = float(np.sum(amounts[fraud_mask]))
        
        caught_mask = fraud_mask & (y_pred_blocked == 1)
        money_caught = float(np.sum(amounts[caught_mask]))
        money_missed = total_fraud_money - money_caught
        
        # Intercepted under FLAGGED or BLOCKED
        flagged_or_blocked = fraud_mask & (y_pred_flagged == 1)
        money_intercepted = float(np.sum(amounts[flagged_or_blocked]))

        money_metrics = {
            "total_fraud_inr": total_fraud_money,
            "blocked_fraud_inr": money_caught,
            "missed_fraud_inr": money_missed,
            "intercepted_or_flagged_inr": money_intercepted,
            "money_catch_rate": round(money_caught / (total_fraud_money + 1e-9), 4)
        }

    brier = float(brier_score_loss(y_true, y_prob))

    return {
        "model_name": model_name,
        "roc_auc": round(roc_auc, 4),
        "pr_auc": round(pr_auc, 4),
        "brier_score": round(brier, 4),
        "blocked_metrics": {
            "threshold": blocked_threshold,
            "precision": round(prec_blocked, 4),
            "recall": round(rec_blocked, 4),
            "f1": round(f1_blocked, 4),
            "confusion_matrix": cm_blocked
        },
        "flagged_metrics": {
            "threshold": flagged_threshold,
            "precision": round(prec_flagged, 4),
            "recall": round(rec_flagged, 4),
            "f1": round(f1_flagged, 4),
            "confusion_matrix": cm_flagged
        },
        "recall_at_fixed_fpr": {
            "recall_at_fpr_1_pct": round(rec_at_fpr_1, 4),
            "recall_at_fpr_0.1_pct": round(rec_at_fpr_01, 4),
            "recall_at_fpr_0.01_pct": round(rec_at_fpr_001, 4)
        },
        "precision_at_top_k": {
            "precision_at_100": round(prec_at_100, 4),
            "precision_at_500": round(prec_at_500, 4),
            "precision_at_1000": round(prec_at_1000, 4)
        },
        "monetary": money_metrics
    }

def plot_curves(
    results_list: List[Dict[str, Any]],
    output_dir: str = "reports"
):
    os.makedirs(output_dir, exist_ok=True)
    
    # PR Curve & ROC Curve
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    
    # 1. ROC
    ax_roc = axes[0]
    ax_roc.plot([0, 1], [0, 1], "k--", label="Chance (AUC = 0.50)")
    for res in results_list:
        y_true = res.get("y_true")
        y_prob = res.get("y_prob")
        name = res.get("name", "Model")
        if y_true is not None and y_prob is not None:
            fpr, tpr, _ = roc_curve(y_true, y_prob)
            score = roc_auc_score(y_true, y_prob)
            ax_roc.plot(fpr, tpr, label=f"{name} (AUC = {score:.3f})")
    ax_roc.set_title("ROC Curve (False Positive Rate vs. Recall)")
    ax_roc.set_xlabel("False Positive Rate")
    ax_roc.set_ylabel("True Positive Rate (Recall)")
    ax_roc.legend(loc="lower right")
    ax_roc.grid(True, alpha=0.3)

    # 2. PR
    ax_pr = axes[1]
    for res in results_list:
        y_true = res.get("y_true")
        y_prob = res.get("y_prob")
        name = res.get("name", "Model")
        if y_true is not None and y_prob is not None:
            prec, rec, _ = precision_recall_curve(y_true, y_prob)
            score = auc(rec, prec)
            ax_pr.plot(rec, prec, label=f"{name} (PR-AUC = {score:.3f})")
    ax_pr.set_title("Precision-Recall Curve (Critical for Imbalanced Data)")
    ax_pr.set_xlabel("Recall")
    ax_pr.set_ylabel("Precision")
    ax_pr.legend(loc="lower left")
    ax_pr.grid(True, alpha=0.3)

    plt.tight_layout()
    plot_path = os.path.join(output_dir, "model_curves.png")
    plt.savefig(plot_path, dpi=200)
    plt.close()
    print(f"Saved evaluation curves to: {plot_path}")

def plot_calibration(
    y_true: np.ndarray,
    uncalibrated_prob: np.ndarray,
    calibrated_prob: np.ndarray,
    output_dir: str = "reports"
):
    os.makedirs(output_dir, exist_ok=True)
    fig, ax = plt.subplots(figsize=(7, 6))
    
    # Perfectly calibrated
    ax.plot([0, 1], [0, 1], "k:", label="Perfectly calibrated")
    
    prob_true_uncal, prob_pred_uncal = calibration_curve(y_true, uncalibrated_prob, n_bins=10)
    ax.plot(prob_pred_uncal, prob_true_uncal, "s-", label="Uncalibrated Model", color="crimson")
    
    prob_true_cal, prob_pred_cal = calibration_curve(y_true, calibrated_prob, n_bins=10)
    ax.plot(prob_pred_cal, prob_true_cal, "o-", label="Isotonic Calibrated Model", color="dodgerblue")
    
    ax.set_title("Probability Calibration Curve")
    ax.set_xlabel("Mean predicted probability")
    ax.set_ylabel("Fraction of positives")
    ax.legend(loc="lower right")
    ax.grid(True, alpha=0.3)
    
    cal_path = os.path.join(output_dir, "calibration_curve.png")
    plt.savefig(cal_path, dpi=200)
    plt.close()
    print(f"Saved calibration curve to: {cal_path}")
