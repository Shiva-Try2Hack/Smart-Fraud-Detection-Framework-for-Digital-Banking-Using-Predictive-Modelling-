# Phase 1: Model Evaluation & Rebuild Report

- **Execution Date:** 2026-10-27T18:28:46.806915
- **Git Commit:** `d3d1d624bd16b319fb81faa10896003dfc7b17ff`
- **Dataset Source:** Synthetic PaySim Benchmark (80,000 samples, step 1-743)

## 1. Split Fraud Distribution (Time-Based Split)

| Split | Steps | Total Rows | Fraud Count | Fraud Rate |
| :--- | :--- | :--- | :--- | :--- |
| Train | 1-520 | 56,000 | 87 | 0.1554% |
| Val   | 520-630 | 12,000 | 18 | 0.15% |
| Test  | 630-743 | 12,000 | 15 | 0.125% |

## 2. Feature Leakage Ablation Study

| Model Feature Subset | PR-AUC | ROC-AUC | Precision@BLOCKED | Recall@BLOCKED | Fraud Money Catch Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Baseline (RF 11 Features) | **1.0000** | 1.0000 | 1.0000 | 1.0000 | 100.0% |
| Without Balance Errors (9 Feats) | **1.0000** | 1.0000 | 1.0000 | 1.0000 | 100.0% |
| Pre-Txn Leak-Free (5 Feats) | **1.0000** | 1.0000 | 1.0000 | 1.0000 | 100.0% |

## 3. Final Model v2 Performance on Test Set

- **Model:** Calibrated RandomForest (v2)
- **PR-AUC:** 1.0000
- **ROC-AUC:** 1.0000
- **Brier Score (Calibrated):** 0.0000
- **Total Fraud Amount in Test Set:** INR 11,555,622.45
- **Fraud Amount Blocked:** INR 11,555,622.45 (100.0%)
- **SHA-256:** `9cc52388d8675992370297b05beb3c4eaee29365815e2c490598847a9418240f`

