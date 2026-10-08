import requests
import json

BASE_URL = "http://127.0.0.1:8000"

def test_endpoints():
    print("--- 1. Testing Health Check ---")
    res = requests.get(f"{BASE_URL}/")
    print(res.json())

    print("\n--- 2. Testing Legitimate Payment (Grocery/Shopping) ---")
    legit_payload = {
        "sender_id": "USR_PRASAD_01",
        "receiver_id": "MERCHANT_GROCERY",
        "transaction_type": "PAYMENT",
        "amount": 450.0,
        "sender_current_balance": 15000.0,
        "receiver_current_balance": 50000.0,
        "pin": "1234"
    }
    r_legit = requests.post(f"{BASE_URL}/api/payment/process", json=legit_payload)
    print("Response:", json.dumps(r_legit.json(), indent=2))

    print("\n--- 3. Testing Fraudulent Payment (Complete Balance Drain / High Amount) ---")
    fraud_payload = {
        "sender_id": "USR_SAYALI_02",
        "receiver_id": "UNKNOWN_MULE_ACCT",
        "transaction_type": "TRANSFER",
        "amount": 298000.0,
        "sender_current_balance": 300000.0,
        "receiver_current_balance": 0.0,
        "pin": "9999"
    }
    r_fraud = requests.post(f"{BASE_URL}/api/payment/process", json=fraud_payload)
    print("Response:", json.dumps(r_fraud.json(), indent=2))

    print("\n--- 4. Testing Admin Feed ---")
    r_admin = requests.get(f"{BASE_URL}/api/admin/transactions?limit=5")
    print("Recent Logs Count:", len(r_admin.json()))

    print("\n--- 5. Testing Dashboard Stats ---")
    r_stats = requests.get(f"{BASE_URL}/api/admin/stats")
    print("KPI Metrics:", json.dumps(r_stats.json(), indent=2))

if __name__ == "__main__":
    test_endpoints()
