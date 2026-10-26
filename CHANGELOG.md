# 📋 Changelog — NEXUS Pay & Fraud Detection Framework

All notable changes to the **NEXUS Pay Client App & Fraud Detection Framework** are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v2.4.0] — 2026-10-26 (Nexus Pay Mobile Client & Cloud Guard)

### 🌟 Added
- **Native Android Client (`app/`)**: Full-featured Jetpack Compose mobile banking application with Material 3 styling, Apple-style liquid glass aesthetics, and dark/light dynamic theme switching.
- **Client-Side Testing Environment**: Anyone can install the APK or clone the Android project in Android Studio to test live fraud interception against our high-accuracy machine learning model.
- **Dual-Engine Predictive ML Model Artifact (`src/models/fraud_model.pkl`)**:
  - Supervised Random Forest Classifier trained on PaySim transaction distribution with tuned decision boundaries.
  - Unsupervised Isolation Forest for zero-day behavioral and statistical anomaly detection.
  - Sub-15ms inference latency.
- **Interactive 3D Virtual EMV Card**:
  - 3D card flip animation (`graphicsLayer(rotationY)`).
  - Front: 16-digit embossed number, cardholder name, expiry date, golden EMV chip.
  - Back: Magnetic stripe, signature strip, tap-to-reveal 3-digit CVV.
- **Tactile Security Gate**:
  - Masked bank balance by default (`••••••••`).
  - Tactile frosted numeric PIN keypad with server verification.
  - Biometric lock and vibration haptic feedback.
- **NFC Tap-to-Pay**:
  - Hardware NFC presence detection with host card emulation (HCE) token preparation.
  - QR Scanner with ML Kit and CameraX integration.
- **Automated GitHub Actions CI/CD (`.github/workflows/build_android_apk.yml`)**:
  - Automates Gradle assembleDebug builds on Ubuntu runners.
  - Generates downloadable APK artifacts and auto-publishes releases on version tags.

### 🔒 Security & Architecture Refinements
- **Protected Server Runtime Separation**: Completely decoupled private host-side server runners (`server_ui.py`, `run_server.py`, `admin_monitor.py`, `database.sqlite`) from public distribution.
- **Zero Sensitive Data Leaks**: Sanitized credentials, tokens, local development `.env`, keystore passwords, and personal machine paths.
- **Decoupled Gateway Protocol**: The mobile app seamlessly connects to the managed fraud scoring server gateway without requiring local port forwarding or static IP exposures.

---

## [v2.3.0] — 2026-10-18 (NEXUS FDS Core Release)

### 🌟 Added
- Multi-tier detection pipeline: Deterministic Heuristic Engine + Contextual Behavioral Feature Engine + Dual ML Models.
- NumPy-optimized inference vectors eliminating PyArrow runtime overhead.
- Pre-configured legitimate user accounts and blacklisted syndicate mule nodes for live scenario testing.
- Windows desktop administrative management GUI with real-time surveillance feed.

---

## [v2.2.0] — 2026-10-08 (Initial Model & Engine Framework)

### 🌟 Added
- Core transaction fraud scoring pipeline.
- Supervised ML model trained on PaySim synthetic financial dataset.
- FastAPI REST endpoints for real-time transaction processing.
