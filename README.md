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

[📥 **Download Latest Android APK (.apk)**](https://github.com/Shiva-Try2Hack/Smart-Fraud-Detection-Framework-for-Digital-Banking-Using-Predictive-Modelling-/releases) | [📋 **View Changelog**](CHANGELOG.md)

</div>

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

## ✨ 2. Mobile Features

- **Liquid Glass Aesthetics**: Modern frosted glass surfaces, OLED Midnight and Crisp Light themes, and smooth micro-animations.
- **Interactive 3D Virtual EMV Card**: Tap the card for a smooth 3D flip animation displaying the 16-digit embossed number, cardholder name, chip, magnetic stripe, and hidden CVV.
- **Tactile Security Gate**: Balance is masked by default (`••••••••`). Checking balances or confirming payments triggers a tactile numeric keypad verified by the security engine.
- **Contactless NFC & QR Scanner**: Hardware NFC detection for tap-to-pay token preparation alongside ML Kit powered CameraX QR code scanning.
- **Live Fraud Alerts**: Instant feedback detailing why a transaction was approved or why it was intercepted by the AI model.

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

### Pre-Trained Model Bundle (`src/models/fraud_model.pkl`)
- `classifier`: Tuned Random Forest model scoring probability of deliberate transaction fraud.
- `isolation_forest`: Unsupervised detector flagging statistically anomalous behavioral deviations.
- `feature_columns`: Canonical feature schema ensuring zero drift and strict deterministic inference.

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

## ⚙️ 6. Automated GitHub CI/CD Pipeline

This repository includes a native GitHub Actions workflow in [`.github/workflows/build_android_apk.yml`](.github/workflows/build_android_apk.yml):
- **Continuous Integration**: Builds and tests the Android app on every push to `main` and on pull requests.
- **Automated Releases**: Automatically generates and attaches the compiled `app-debug.apk` whenever a new semantic version tag (e.g. `v2.4.0`) is pushed to the repository.

<br/>

---

## 📄 7. License

This project is licensed under the terms of the [MIT License](LICENSE.txt).
