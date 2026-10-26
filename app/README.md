<div align="center">

# 📱 NEXUS Pay — Android Mobile Application
### *Native Android Banking & Real-Time Fraud Interception Client*

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

---

## 📌 Overview
This directory contains the complete source code for the **NEXUS Pay** native Android application. It connects directly with the cloud-hosted NEXUS Fraud Detection framework to safeguard transactions against fraud, mule transfers, and account draining attacks in real time.

## 🛠️ Building & Running
1. Open this directory (`app/`) in **Android Studio**.
2. Sync Gradle files (`File ➔ Sync Project with Gradle Files`).
3. Connect an Android phone with USB Debugging enabled or start an Android Emulator (API 24+).
4. Click **Run 'app'** or execute:
   ```bash
   ./gradlew assembleDebug
   ```
5. The generated APK will be placed at:
   `app/build/outputs/apk/debug/app-debug.apk`
