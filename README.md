<div align="center">

# 🛡️ NEXUS PAY
### *Smart Fraud Detection Mobile Banking Application*
*Native Android Banking Client Powered by Predictive AI Fraud Interception*

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Scikit-Learn](https://img.shields.io/badge/ML-Scikit--Learn-F7931E?style=for-the-badge&logo=scikit-learn&logoColor=white)](https://scikit-learn.org)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE.txt)

<br/>

**Production-grade Android mobile banking client with pre-trained AI predictive models for testing real-time transaction surveillance.**

[📥 **Download Latest Android APK (.apk)**](https://github.com/Shiva-Try2Hack/Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-/releases) &nbsp;•&nbsp; [📋 **View Changelog**](CHANGELOG.md)

</div>

<br/>

> [!WARNING]
> ### ⚠️ EXPERIMENTAL MODEL TESTING & DEMONSTRATION SOFTWARE
> **This mobile application is built strictly for evaluation, testing, and benchmark demonstration of the Dual-Engine AI Fraud Detection Model.**
> - It is **NOT** a commercial banking application and does not process real fiat currency.
> - All balances, transactions, and virtual cards are simulated test assets designed to demonstrate **how accurately the predictive machine learning model catches fraudulent attempts**, flags statistical anomalies, and blocks syndicate mule transfers in real time.
> - Use this client exclusively in test/staging environments for research, security evaluation, and model validation purposes.

<br/>

---

## 📌 1. Project Overview

Modern digital banking requires high security against push payment scams, sudden balance-draining velocities, account takeovers, and organized mule accounts.

**NEXUS Pay** provides:
1. **The Mobile Application (`app/`)**: A fluid, modern Android banking app built with **Jetpack Compose**, featuring Apple Liquid Glass styling, 3D EMV virtual cards, biometric and PIN protection, NFC tap-to-pay, and QR code scanning.
2. **The Predictive ML Model (`src/models/fraud_model.pkl`)**: Pre-trained dual-engine artificial intelligence model capable of scoring financial transactions in **under 15 milliseconds**.
3. **Automated Interception**: Evaluates transactions in real time to approve, flag, or intercept malicious payments before funds leave an account.

> [!NOTE]
> **Zero Setup for Testers:** The mobile app connects automatically to the cloud fraud scoring gateway. You can install the APK directly on any Android device and test transactions immediately without configuring servers or network forwarding.

<br/>

---

## ✨ 2. Mobile App Features & Capabilities

### 🛡️ Fraud Interception & Security
- **Real-Time AI Risk Scoring**: Payments pass through our dual-engine predictive ML models in under 15 milliseconds, displaying immediate risk scores (`APPROVED`, `FLAGGED`, `BLOCKED`) with human-readable reason codes.
- **Sudden Velocity & Drain Detection**: Flags abnormal balance-depleting transfers (>95% total liquidity drain) and blocks known syndicate mule accounts before funds settle.
- **Privacy Balance Shield**: Account balance remains masked by default (`••••••••`) to protect against shoulder-surfing. Tapping reveals a tactile numeric keypad requiring secure PIN or biometric matching.
- **Biometric App Lock**: Fingerprint and face unlock integration (`BiometricPrompt`) to guard transaction approvals and settings.

### 💳 Digital Banking & Payment Methods
- **Interactive 3D Virtual EMV Card**: 3D animated debit card with realistic lighting physics (`graphicsLayer(rotationY)`). Displays 16-digit card number, holder name, chip, and a tap-to-reveal CVV code.
- **Contactless NFC Tap-to-Pay**: Automatic hardware NFC detection with Host Card Emulation (HCE) token preparation for contactless point-of-sale terminals.
- **Integrated CameraX QR Code Scanner**: ML Kit-powered high-speed QR scanner for instant UPI and merchant barcode parsing.
- **Direct UPI & Bank Transfers**: Multi-mode payment support for UPI IDs, direct account + IFSC transfers, contacts, and utility bill mock settlements.

### 🎨 Modern UI & UX Experience
- **Liquid Glass Aesthetics**: Apple-style frosted blur layers, subtle gradient reflections, and tactile haptic feedback on keypresses.
- **OLED Dark & Crisp Light Themes**: Dynamic theme switching tailored for low-light banking and battery saving on OLED displays.
- **Detailed Transaction Ledger**: Full ledger with real-time audit trails, timestamped receipts, transaction IDs, and status badges.
- **Zero-Config Cloud Connectivity**: Automatic connection to the cloud scoring gateway with auto-fallback to local gateway diagnostics for offline testing.

<br/>

---

## 🧠 3. Predictive Model Architecture

```
           Incoming Payment Request from Mobile App (NEXUS Pay)
                                   │
                                   ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ Layer 1: Deterministic Heuristic Engine                       │
   │ • Insufficient balance inspection                             │
   │ • Blacklisted recipient / syndicate mule verification         │
   │ • Critical one-shot balance depletion check (>95% balance)    │
   └───────────────────────────────┬───────────────────────────────┘
                                   │
                                   ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ Layer 2: Contextual Behavioral Feature Engineering            │
   │ • amount_to_balance_ratio: Transaction vs available liquidity│
   │ • balance_drain_velocity: Velocity of recent outgoing funds   │
   │ • sudden_depletion_flag: High-value account drain trigger     │
   └───────────────────────────────┬───────────────────────────────┘
                                   │
                                   ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ Layer 3: Dual-Engine Machine Learning Inference               │
   │ 1. Supervised Random Forest Classifier                        │
   │    Trained on PaySim multivariate financial transaction data  │
   │ 2. Unsupervised Isolation Forest                              │
   │    Zero-day anomaly detection across non-linear feature space │
   └───────────────────────────────┬───────────────────────────────┘
                                   │
                                   ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ Layer 4: Real-Time Risk Tiering & Explanation Codes           │
   │ • [ APPROVED ] ➔ Risk Score < 0.40  (Instant settlement)      │
   │ • [ FLAGGED  ] ➔ Risk Score 0.40 - 0.74 (Step-up challenge)   │
   │ • [ BLOCKED  ] ➔ Risk Score >= 0.75 (Critical Interception)   │
   └───────────────────────────────────────────────────────────────┘
```

### 📦 Model Bundle Architecture (`src/models/fraud_model.pkl`)

The model bundle is serialized via `joblib` and contains a complete dictionary artifact:
```python
{
    "classifier": RandomForestClassifier,   # Supervised fraud probability engine
    "isolation_forest": IsolationForest,    # Unsupervised zero-day outlier detector
    "feature_columns": [...]                # 11-dimensional canonical feature schema
}
```

#### 1. Supervised Random Forest Classifier
- **Objective**: Evaluates non-linear correlations and patterns indicative of fraudulent fund siphon maneuvers (derived from extensive PaySim financial transaction distributions).
- **Output**: Returns a calibrated probability score (`0.0` to `1.0`). Any transaction yielding `rf_prob >= 0.70` is classified as critical fraud risk.

#### 2. Unsupervised Isolation Forest
- **Objective**: Identifies novel, zero-day anomalous behaviors that do not fit regular spending baselines.
- **Output**: Decision function outputs `+1` (normal transaction profile) or `-1` (statistical outlier). Outlier status dynamically triggers step-up verification or block rules.

#### 3. 11-Dimensional Feature Vector Schema
The model evaluates transactions using direct numerical vectors, bypassing complex overheads:
| Feature Name | Type | Description |
| :--- | :---: | :--- |
| `type_code` | `float` | Transaction type: `PAYMENT (0)`, `TRANSFER (1)`, `CASH_OUT (2)`, `CASH_IN (3)`, `DEBIT (4)` |
| `amount` | `float` | Requested transaction amount in INR |
| `oldbalanceOrg` | `float` | Sender's current balance before transfer |
| `newbalanceOrig` | `float` | Sender's projected balance after transfer |
| `oldbalanceDest` | `float` | Recipient's initial balance before credit |
| `newbalanceDest` | `float` | Recipient's projected balance after credit |
| `amount_to_balance_ratio` | `float` | Relative impact: `amount / (oldbalanceOrg + 1.0)` |
| `balance_drain` | `float` | Velocity of balance loss: `(oldbalanceOrg - newbalanceOrig) / (oldbalanceOrg + 1.0)` |
| `sudden_depletion_flag` | `float` | Binary flag `1.0` if old balance > ₹ 1,000 and post-balance drops to ≤ ₹ 10 |
| `orig_balance_err` | `float` | Accounting integrity: `(oldbalanceOrg - amount) - newbalanceOrig` |
| `dest_balance_err` | `float` | Accounting integrity: `(oldbalanceDest + amount) - newbalanceDest` |

---

### 💻 How to Load & Use the Model in Python

You can load and query the model directly in your own scripts or backend services without needing any server:

```python
import joblib
import numpy as np

# 1. Load the pre-trained model bundle
bundle = joblib.load("src/models/fraud_model.pkl")
rf_model = bundle["classifier"]
iso_forest = bundle["isolation_forest"]
feature_cols = bundle["feature_columns"]

# 2. Define a test transaction (e.g. sudden drain attempt)
test_features = {
    "type_code": 1.0,                   # TRANSFER
    "amount": 49000.0,                  # Large transfer
    "oldbalanceOrg": 50000.0,           # Sender balance: 50,000
    "newbalanceOrig": 1000.0,           # Post balance: 1,000 (98% drain)
    "oldbalanceDest": 1200.0,
    "newbalanceDest": 50200.0,
    "amount_to_balance_ratio": 49000.0 / 50001.0, # 0.979
    "balance_drain": 49000.0 / 50001.0,           # 0.979
    "sudden_depletion_flag": 0.0,
    "orig_balance_err": 0.0,
    "dest_balance_err": 0.0
}

# 3. Format input vector according to canonical schema
vector = np.array([[test_features[col] for col in feature_cols]], dtype=np.float64)

# 4. Predict fraud probability and outlier status
fraud_probability = rf_model.predict_proba(vector)[0][1]
is_anomaly = iso_forest.predict(vector)[0] == -1

print(f"Fraud Probability : {fraud_probability * 100:.2f}%")
print(f"Zero-Day Anomaly   : {'YES (Outlier)' if is_anomaly else 'NO'}")
# Output:
# Fraud Probability : 89.40% -> [ BLOCKED ]
```

You can also run our standalone evaluator engine:
```python
from src.ml_engine.evaluator import RiskScoringEngine

engine = RiskScoringEngine()
verdict = engine.evaluate_transaction({
    "transaction_type": "TRANSFER",
    "amount": 44999.0,
    "sender_current_balance": 45000.0,
    "receiver_current_balance": 1500.0
})
print(verdict)
# Returns: {'status': 'BLOCKED', 'risk_score': 0.95, 'reasons': ['Critical Account Depletion: Over 95% of total balance...']}
```

<br/>

---

## 👥 4. Pre-Configured Test Accounts

The framework includes pre-configured accounts to experience both legitimate banking transactions and live fraud interceptions:

### 🎓 Legitimate User Accounts
| Full Name | Login Identifier | Personal UPI ID |
| :--- | :--- | :--- |
| **Shiva** | `shiva@nexuspay.com` | `shiva@okaxis` |
| **Prasad Kadu** | `prasad@nexuspay.com` | `prasad@oksbi` |
| **Sayali Dahake** | `sayali@nexuspay.com` | `sayali@okhdfc` |
| **Minal Thakare** | `minal@nexuspay.com` | `minal@okicici` |
| **Prathamesh Meshram** | `prathamesh@nexuspay.com` | `prathamesh@okpaytm` |

*Users can also tap **"Register Now"** inside the app to create and test their own account with custom credentials.*

### ⚠️ Simulated Blacklisted Syndicate & Mule Accounts
| Entity / Flagged Name | Flagged UPI ID | Threat Classification | Interception Outcome |
| :--- | :--- | :--- | :--- |
| **Dark Mule Node 01** | `evil.darkmule@darkpay` | Blacklisted Mule Node | **BLOCKED (Blacklist Rule)** |
| **Syndicate Phisher X** | `evil.phisher@shadowbank` | Phishing Syndicate Node | **BLOCKED (Blacklist Rule)** |
| **Launderer Ghost Hub** | `evil.launderer@ghostupi` | Money Laundering Target | **BLOCKED (Blacklist Rule)** |

### 🧪 Fraud Scenarios to Test:
1. **Sudden Account Depletion**: Log in as `sayali` (Balance: ₹ 45,000) and try to transfer `₹ 44,999` in a single payment. The contextual engine and Random Forest classifier flag the sudden 99.9% liquidity drain and block the attempt.
2. **Normal Retail Transfer**: Transfer ₹ 500 from `shiva` to `prasad`. The transaction is approved in under 15ms and balances update immediately.

<br/>

---

## 🚀 5. How to Get & Run the App

### Option A: Install Pre-Built APK (Fastest)
1. Head over to the [GitHub Releases](https://github.com/Shiva-Try2Hack/Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-/releases) page.
2. Download `app-debug.apk`.
3. Open and install on your Android device (Android 7.0+ / API 24+ supported).
4. Launch **NEXUS PAY**, log in with any test account above, and test payments!

### Option B: Build from Source in Android Studio
1. Clone this repository:
   ```bash
   git clone https://github.com/Shiva-Try2Hack/Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-.git
   ```
2. Launch **Android Studio** (Ladybug / Koala or newer recommended).
3. Select **Open** and choose the `app/` directory from the repository.
4. Allow Gradle to synchronize dependencies.
5. Click **Run** (`Shift + F10`) or navigate to **Build ➔ Build Bundle(s) / APK(s) ➔ Build APK(s)**.

<br/>

---

## 📄 6. License & Testing Disclaimer
- **License**: This project is licensed under the terms of the [MIT License](LICENSE.txt).
- **Disclaimer**: Provided strictly "as-is" for educational, academic, and artificial intelligence predictive testing purposes. No liability is assumed for unauthorized use or misinterpretation of financial risk scoring predictions.
