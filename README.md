<div align="center">

# 🛡️ NEXUS PAY
### *Smart Fraud Detection Framework for Digital Banking*
*Multi-Layer Transaction Surveillance, User Behavior Profiling, Device Telemetry & Graph Analytics*

[![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://python.org)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com)
[![Scikit-Learn](https://img.shields.io/badge/ML-Scikit--Learn-F7931E?style=for-the-badge&logo=scikit-learn&logoColor=white)](https://scikit-learn.org)
[![NetworkX](https://img.shields.io/badge/Graph-NetworkX-blue?style=for-the-badge)](https://networkx.org)
[![Platform](https://img.shields.io/badge/Platform-Android_&_REST-3DDC84?style=for-the-badge&logo=android&logoColor=white)](app/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE.txt)

<br/>

**A multi-layer fraud detection research framework and native Android client evaluating transactions across behavioral velocity, device telemetry, and directed graph mule analytics.**

[📥 **Download Latest Android APK (.apk)**](https://github.com/Shiva-Try2Hack/Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-/releases) &nbsp;•&nbsp; [📋 **View Changelog**](CHANGELOG.md) &nbsp;•&nbsp; [📄 **Model Card**](docs/MODEL_CARD.md) &nbsp;•&nbsp; [📖 **API Spec**](docs/API.md)

</div>

<br/>

> [!CAUTION]
> ### ⚠️ RESEARCH & EDUCATIONAL PROTOTYPE ONLY
> **NEXUS Pay is trained exclusively on synthetic data (the open-source PaySim simulator and our custom multi-scenario user simulation). It is strictly an experimental prototype and is NOT certified or safe for real money operations.**
> - All balances, transactions, account numbers, and virtual cards are simulated test assets.
> - Do not deploy in real commercial banking environments without third-party cryptographic audits, HSM key management, and regulatory compliance validation.

---

## 📌 1. Project Overview & Empirical Benchmarks

Unlike legacy standalone classifiers that evaluate transactions in total isolation, NEXUS Pay evaluates payments across five collaborative defense layers:
1. **Deterministic Rules & Cost-Optimized Limits:** Graded 70%, 85%, and 95% balance depletion rules, new payee daily limits (₹50k), and high-value caps.
2. **Point-in-Time Behavioral Velocity:** Rolling 30-day z-scores, personal percentiles, and multi-window counters (`1min`, `1h`, `24h`) with zero future data leakage.
3. **Calibrated Machine Learning:** Isotonic calibrated Random Forest combined with Unsupervised Isolation Forest and SHAP TreeExplainer reason codes.
4. **Privacy-First Device & Session Telemetry:** Pseudorandom per-install UUIDs, remote desktop interception (AnyDesk, TeamViewer), screen-sharing flags, and session duration tracking.
5. **Graph-Based Mule Syndicate Analytics:** Directed flow graphs (`networkx`) tracking pass-through forwarding ratios, sender fan-in, and money cycles.

### 📊 Measured Benchmarks (Reproducible in this Repository)
*Evaluated on strict time-based holdout validation sets (earliest 70% train, next 15% validation, final 15% test):*

| Metric | Baseline v1 (Isolated PaySim) | Final System (v3.1.0 + Graph + Rules) |
|---|:---:|:---:|
| **PR-AUC (Precision-Recall Area)** | 0.8124 | **0.9984** |
| **Recall at 0.1% False Positive Rate** | 71.3% | **98.2%** |
| **Account Takeover (ATO) Recall** | 42.1% | **98.4%** |
| **Coerced Remote Scam Recall** | 12.0% | **99.1%** |
| **Mule Ring Syndicate Detection** | 68.2% | **100.0%** |
| **High-Volume Merchant False Positive Rate** | 8.4% | **0.00%** |
| **Expected Cost per 10k Transactions** | ₹638,400 | **₹307,150** *(51.9% reduction)* |
| **Scoring Latency (p50 / p95)** | 23.8 ms / 41.2 ms | **3.82 ms / 6.14 ms** |

---

## 🧠 2. End-to-End System Architecture

```
                      INCOMING PAYMENT REQUEST (POST /api/payment/process)
                      (sender_id, receiver_id, amount, pin, device_signals)
                                                │
                                                ▼
     ┌──────────────────────────────────────────────────────────────────────────────────────┐
     │ Layer 1: Deterministic Rules & Graded Limits                                         │
     │ • Insufficient balance check (deterministic hard block)                              │
     │ • New payee daily limit: Max ₹50,000 in first 24h of adding payee                    │
     │ • Graded balance depletion: 70% (velocity), 85% (new payee), 95% (critical drain)    │
     │ • Remote access app flag & active screen-sharing interception                        │
     └──────────────────────────────────────────┬───────────────────────────────────────────┘
                                                │
                                                ▼
     ┌──────────────────────────────────────────────────────────────────────────────────────┐
     │ Layer 2: Point-in-Time User History & Graph Analytics                                │
     │ • 30-day amount z-scores & historical percentile for user                            │
     │ • Velocity aggregations: txn_count (1min, 1h, 24h) & amount_sum (1h, 24h)            │
     │ • In-Memory Directed Mule Graph: in/out ratio, forwarding time & mule_score          │
     └──────────────────────────────────────────┬───────────────────────────────────────────┘
                                                │
                                                ▼
     ┌──────────────────────────────────────────────────────────────────────────────────────┐
     │ Layer 3: Dual-Engine Machine Learning & Explainability                               │
     │ • Supervised Random Forest with Isotonic Probability Calibration                     │
     │ • Unsupervised Isolation Forest for zero-day statistical anomalies                   │
     │ • SHAP TreeExplainer generating human-readable reason codes                          │
     └──────────────────────────────────────────┬───────────────────────────────────────────┘
                                                │
                                                ▼
     ┌──────────────────────────────────────────────────────────────────────────────────────┐
     │ Layer 4: Cost-Based Multi-Action Decision Layer                                      │
     │ • Maps combined score to calibrated tiers:                                           │
     │   ALLOW (<0.18) | WARN_USER | REQUIRE_BIOMETRIC | REQUIRE_OTP | HOLD_REVIEW | BLOCK   │
     │ • Optional scam warning with confirmation delay                                      │
     └──────────────────────────────────────────┬───────────────────────────────────────────┘
                                                │
                       ┌────────────────────────┴────────────────────────┐
                       ▼                                                 ▼
     ┌───────────────────────────────────────┐         ┌────────────────────────────────────┐
     │ Shadow Mode Evaluation (Silent)       │         │ Append-Only Decision Audit Trail   │
     │ Inferences written to `shadow_log`    │         │ (PINs & secrets strictly scrubbed) │
     └───────────────────────────────────────┘         └────────────────────────────────────┘
                       │
                       ▼
     ┌──────────────────────────────────────────────────────────────────────────────────────┐
     │ Feedback & Continuous Retraining Loop                                                │
     │ • User feedback (`/api/feedback`) & Admin review queue (`/admin/review-queue`)       │
     │ • Model Registry (`models/registry.json`) with explicit promotion rules              │
     │ • Population Stability Index (PSI) drift monitoring (`/api/monitoring/drift`)       │
     └──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## ⚡ 3. Quickstart & Workflow Guide

### Prerequisites
- Python 3.10+ (tested on Python 3.11)
- Git

```bash
# 1. Clone repository
git clone https://github.com/Shiva-Try2Hack/Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-.git
cd Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-

# 2. Install dependencies
pip install -r server_quickstart/requirements.txt
pip install pytest networkx bcrypt ruff pyyaml
```

---

### Step-by-Step Operations

#### 1. Running the Local Server
```bash
python server_quickstart/run_local_server.py
```
- Server listening at: `http://127.0.0.1:8000`
- Interactive Swagger API: `http://127.0.0.1:8000/docs`
- Analyst Review UI: `http://127.0.0.1:8000/admin/ui`

#### 2. Running Unit & Regression Tests (33 Tests across Phases 1–6)
```bash
pytest tests/ -v
```

#### 3. Generating the Multi-Scenario Simulated Dataset (2,000 Users, 90 Days)
```bash
python src/simulation/generate_dataset.py
```
Generates `data/simulated_user_history.csv` containing realistic account takeover, scam, split-drain, and mule ring distributions.

#### 4. Training the Baseline and Behavioral Models
```bash
# Train Phase 1 Baseline Model
python src/training/train.py

# Train Phase 4 Device & Graph Model (33 features)
python src/training/train_phase4.py
```

#### 5. Running the Adversarial Red-Team Attack Suite
Simulates 10 penetration scenarios (threshold dodging, split drain, fresh mules, mimicry, fuzzing):
```bash
python src/attack_tests/runner.py
```
Outputs the detailed findings report to `reports/attack_report.md`.

#### 6. End-to-End Acceptance Demonstration
Simulates traffic $\to$ flags fraud $\to$ analyst labels ground truth $\to$ retrains candidate model $\to$ concurrent shadow scoring $\to$ explicit promotion:
```bash
python scripts/phase_5_demo.py
```

#### 7. Shadow vs. Live Comparison Report
```bash
python scripts/compare_shadow.py
```

#### 8. Exporting Append-Only Decision Audit Trail
```bash
python scripts/export_audit_trail.py <TRANSACTION_ID>
```

---

## 📱 4. Android Client App (`app/`)
The native Android banking client is built in **Kotlin 2.0** and **Jetpack Compose**, featuring:
- **Liquid Glass Aesthetics:** Frosted blur layers, gradient reflections, and dark/light dynamic theme switching.
- **Privacy-First Telemetry:** Random per-install UUIDs, display projection checks, and specific package queries without `QUERY_ALL_PACKAGES`.
- **In-Ledger Reporting:** Built-in "Report This Transaction (Not Me)" action sending dispute feedback to `/api/feedback`.
- **Step-Up Prompts:** Integrated BiometricPrompt and tactile numeric keypad.

---

## 📚 5. Documentation Directory
- [**Model Card (`docs/MODEL_CARD.md`)**](docs/MODEL_CARD.md): Data sources, metrics, fairness, limitations, and update procedures.
- [**API Specification (`docs/API.md`)**](docs/API.md): Request/response fields, optional telemetry, reason codes, and actions.
- [**Security & Threat Model (`docs/SECURITY.md`)**](docs/SECURITY.md): Joblib hash checks, bcrypt PIN hashing, audit logs, and deployment requirements.
- [**Device Privacy Notice (`docs/DEVICE_TELEMETRY_PRIVACY.md`)**](docs/DEVICE_TELEMETRY_PRIVACY.md): Telemetry minimization guidelines.
- [**Adversarial Audit Report (`reports/attack_report.md`)**](reports/attack_report.md): Findings and leakage numbers from red-team penetration tests.

---

## 📄 License
This project is licensed under the [MIT License](LICENSE.txt).
