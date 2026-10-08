# Smart Fraud Detection Backend API Documentation

Backend server running at: `http://127.0.0.1:8000`  
Interactive Swagger UI: `http://127.0.0.1:8000/docs`

---

## 1. Architecture Overview
The backend provides real-time fraud scoring for the **Payment Client App** and telemetry/batch auditing for the **Admin Panel**.

### Fraud Detection Pipeline:
1. **Rule Engine**: Fast heuristic threshold checks (balance exhaustion, extreme single transfers, invalid math).
2. **Feature Extractor**: Dynamically derives contextual indicators (`amount_to_balance_ratio`, `balance_drain_velocity`, `sudden_depletion_flag`).
3. **Supervised Random Forest**: Trained on simulated PaySim distribution for high fraud recall (>90%).
4. **Unsupervised Isolation Forest**: Flags zero-day anomalies and unexpected behavioral shifts.
5. **Decision Tiering**:
   - `APPROVED` (Risk Score < 0.40)
   - `FLAGGED` (Risk Score 0.40 - 0.74, Step-up verification or review)
   - `BLOCKED` (Risk Score >= 0.75, Critical threat intercepted)

---

## 2. API Reference for the Payment App Developer

### Endpoint: Process & Score Payment
* **URL:** `POST http://127.0.0.1:8000/api/payment/process`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
```json
{
  "sender_id": "USR_PRASAD_01",
  "receiver_id": "MERCHANT_GROCERY_99",
  "transaction_type": "TRANSFER",
  "amount": 2500.0,
  "sender_current_balance": 18000.0,
  "receiver_current_balance": 25000.0,
  "pin": "1234"
}
```

* **Sample Response (Approved):**
```json
{
  "transaction_id": "TXN_D0246707BF",
  "status": "APPROVED",
  "risk_score": 0.0,
  "risk_level": "LOW",
  "supervised_ml_prob": 0.0,
  "anomaly_detected": false,
  "reasons": ["Transaction verified and safe"],
  "timestamp": "2026-10-05T02:19:24.982364"
}
```

* **Sample Response (Blocked - High Fraud):**
```json
{
  "transaction_id": "TXN_5361E8EFF0",
  "status": "BLOCKED",
  "risk_score": 1.0,
  "risk_level": "CRITICAL",
  "supervised_ml_prob": 1.0,
  "anomaly_detected": true,
  "reasons": [
    "Critical Account Depletion: Over 95% of total balance being transferred in single shot",
    "Zero-Day Anomaly: Transaction deviates significantly from normal spending baseline",
    "Supervised ML Fraud Risk Alert (Confidence: 100.0%)"
  ],
  "timestamp": "2026-10-05T02:19:25.066639"
}
```

---

## 3. API Reference for the Admin Panel Developer

### A. Real-time Transaction Stream
* **URL:** `GET http://127.0.0.1:8000/api/admin/transactions?limit=50&status=BLOCKED`
* **Query Params:**
  * `limit` (default: 50): Number of records.
  * `status` (optional): `APPROVED`, `FLAGGED`, or `BLOCKED`.
* **Sample Response:**
```json
[
  {
    "id": "TXN_5361E8EFF0",
    "sender_id": "USR_SAYALI_02",
    "receiver_id": "UNKNOWN_MULE_ACCT",
    "transaction_type": "TRANSFER",
    "amount": 298000.0,
    "sender_balance": 300000.0,
    "receiver_balance": 0.0,
    "status": "BLOCKED",
    "risk_score": 1.0,
    "risk_level": "CRITICAL",
    "reasons": [
      "Critical Account Depletion: Over 95% of total balance being transferred in single shot",
      "Supervised ML Fraud Risk Alert (Confidence: 100.0%)"
    ],
    "created_at": "2026-10-05T02:19:25.066639"
  }
]
```

### B. Summary KPIs and Fraud Prevention Stats
* **URL:** `GET http://127.0.0.1:8000/api/admin/stats`
* **Sample Response:**
```json
{
  "total_transactions": 2,
  "approved_count": 1,
  "flagged_count": 0,
  "blocked_count": 1,
  "fraud_prevention_rate_pct": 50.0,
  "total_volume_inr": 298450.0,
  "fraud_amount_saved_inr": 298000.0
}
```

### C. Bulk CSV Batch Auditing
* **URL:** `POST http://127.0.0.1:8000/api/admin/batch-audit`
* **Form-data:** `file`: `sample_transactions.csv`
* **Returns:** Summary count + per-row classification highlighting flagged rows in red for admin review.

---

## 4. How to Run Locally

1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
2. Run backend server:
   ```bash
   uvicorn src.main:app --host 127.0.0.1 --port 8000 --reload
   ```
3. Test all endpoints:
   ```bash
   python test_api.py
   ```
