# Phase 2: User Behavior & Velocity Model Report

- **Execution Time:** 2026-10-27T18:43:43.593596
- **Git Commit:** `5dd14dc572caaea0b5fb1cc15a6ff52b0a7330f2`
- **Schema Version:** `2.0.0`

## 1. Overall Model Comparison (Test Set)

| Model | Features | PR-AUC | ROC-AUC | Recall@Fraud | Fraud Money Caught |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 1 Baseline** | 5 (No Behavior) | 0.0543 | 0.9830 | 0.3235 | 53.8% |
| **Phase 2 Behavioral** | 25 (Behavior + Velocity) | **0.9857** | **1.0000** | **1.0000** | **100.0%** |

## 2. Per-Scenario Fraud Recall Breakdown

| Fraud Scenario | Test Instances | Phase 1 Recall | Phase 2 Recall | Delta |
| :--- | :--- | :--- | :--- | :--- |
| `SPLIT_DRAIN` | 28 | 25.0% | **100.0%** | +75.0% |
| `ACCOUNT_TAKEOVER` | 6 | 66.7% | **100.0%** | +33.3% |

## 3. False-Positive Rate on Hard Negatives

| Legitimate Scenario | Test Instances | Phase 1 FPR | Phase 2 FPR |
| :--- | :--- | :--- | :--- |
| `HARD_NEGATIVE_FIRST_TIME_LARGE_GIFT` | 13 | 84.62% | **7.69%** |
| `LEGIT_STANDARD` | 27,258 | 1.39% | **0.00%** |

## 4. Top Feature Importances

| Rank | Feature Name | Importance |
| :--- | :--- | :--- |
| 1 | `days_since_first_payment_to_payee` | 17.47% |
| 2 | `amount` | 16.62% |
| 3 | `receiver_account_age_days` | 15.98% |
| 4 | `amount_to_balance_ratio` | 12.57% |
| 5 | `amount_zscore` | 9.10% |
| 6 | `amount_percentile_for_user` | 5.58% |
| 7 | `receiver_distinct_senders_24h` | 4.80% |
| 8 | `oldbalanceDest` | 4.75% |
| 9 | `type_code` | 4.71% |
| 10 | `txn_count_30d` | 2.00% |

