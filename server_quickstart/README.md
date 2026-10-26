# 🚀 Ready-to-Run Standalone Fraud Detection Server & Account CLI

This directory provides a **100% clean, ready-made standalone server** and **CLI account manager** for anyone who wants to host the NEXUS Fraud Detection framework on their own computer, local network, or cloud instance.

- **Zero Credentials / Empty Fresh State**: Contains no hardcoded user accounts, personal passwords, or private database files.
- **Model-Powered**: Connects directly to the pre-trained predictive AI model in `src/models/fraud_model.pkl`.
- **Full CLI Suite**: Allows you to create accounts, check balances, add money, withdraw funds, change PINs, and test fraud detection in seconds.

---

## ⚡ 1. Quickstart: Run the Server Locally

### Step 1: Install Dependencies
Open your terminal (PowerShell, Command Prompt, or bash) and install the lightweight requirements:
```bash
pip install -r server_quickstart/requirements.txt
```

### Step 2: Start the Server
```bash
python server_quickstart/run_local_server.py
```

Once started:
- **API Base URL**: `http://127.0.0.1:8000`
- **Interactive Swagger Documentation**: `http://127.0.0.1:8000/docs`
- **Health Check**: `http://127.0.0.1:8000/`

---

## 🛠️ 2. Account & Balance Management CLI Commands

Use `server_quickstart/local_manager.py` to manage your local accounts directly through the command line:

### 👤 1. Create a New User Account
```bash
# Create a standard user (Initial balance: ₹ 25,000 | PIN: 1234)
python server_quickstart/local_manager.py create -u rahul -n "Rahul Sharma" --upi rahul@testbank -p 1234 -b 25000

# Create a blacklisted syndicate mule account to test fraud blocking
python server_quickstart/local_manager.py create -u evil_bot -n "Evil Mule Node" --upi evil.mule@darkbank -p 0000 -b 0 --evil
```

### 💰 2. Check Account Balance
```bash
# Check by username or UPI ID
python server_quickstart/local_manager.py balance -u rahul
python server_quickstart/local_manager.py balance -u rahul@testbank
```

### 💵 3. Add Money / Deposit Funds
```bash
# Add ₹ 10,000 to an account
python server_quickstart/local_manager.py add-balance -u rahul -a 10000
```

### 💸 4. Deduct Money / Withdraw Funds
```bash
# Deduct ₹ 3,000 from an account
python server_quickstart/local_manager.py deduct-balance -u rahul -a 3000
```

### 🔑 5. Change UPI / Bank PIN
```bash
# Update PIN for an account
python server_quickstart/local_manager.py set-pin -u rahul -p 9988
```

### 📋 6. List All Accounts
```bash
python server_quickstart/local_manager.py list
```

### ❌ 7. Delete an Account
```bash
python server_quickstart/local_manager.py delete -u rahul
```

---

## 💳 3. Test Payments & Fraud Interception via Terminal

While the server is running (`python server_quickstart/run_local_server.py`), you can trigger payments using `curl`:

### A. Safe Normal Payment (Approved by AI Model)
```bash
curl -X POST http://127.0.0.1:8000/api/payment/process \
  -H "Content-Type: application/json" \
  -d '{
    "sender_id": "rahul",
    "receiver_id": "priya@testbank",
    "transaction_type": "TRANSFER",
    "amount": 500.0,
    "pin": "9988"
  }'
```
**Response:**
```json
{
  "status": "APPROVED",
  "risk_score": 0.02,
  "risk_level": "LOW",
  "reasons": ["Transaction verified and safe"]
}
```

### B. Critical Balance Drain Attempt (Blocked by AI Model)
```bash
curl -X POST http://127.0.0.1:8000/api/payment/process \
  -H "Content-Type: application/json" \
  -d '{
    "sender_id": "rahul",
    "receiver_id": "priya@testbank",
    "transaction_type": "TRANSFER",
    "amount": 31900.0,
    "pin": "9988"
  }'
```
**Response:**
```json
{
  "status": "BLOCKED",
  "risk_score": 0.95,
  "risk_level": "CRITICAL",
  "reasons": [
    "Critical Account Depletion: Over 95% of total balance being transferred in single shot"
  ]
}
```

### C. Transfer to Blacklisted Mule (Blocked by Heuristic Filter)
```bash
curl -X POST http://127.0.0.1:8000/api/payment/process \
  -H "Content-Type: application/json" \
  -d '{
    "sender_id": "rahul",
    "receiver_id": "evil.mule@darkbank",
    "transaction_type": "TRANSFER",
    "amount": 1000.0,
    "pin": "9988"
  }'
```
**Response:**
```json
{
  "status": "BLOCKED",
  "risk_score": 1.0,
  "risk_level": "CRITICAL",
  "reasons": ["Blacklist Violation: Destination account is flagged as a malicious mule"]
}
```

---

## 📱 4. Connect with the Android App
If you are running the **NEXUS Pay** Android application on an Android emulator or physical device on the same local network:
1. Open the Android App.
2. Open Settings (tap the gear / profile icon).
3. Set Server URL to:
   - For Android Emulator: `http://10.0.2.2:8000`
   - For Physical Phone: `http://<your-computer-local-ip>:8000` (e.g. `http://192.168.1.15:8000`)
4. Test live banking and real-time fraud scoring directly on your device!
