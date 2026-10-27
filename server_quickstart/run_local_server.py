import os
import sys
import uuid
from typing import Optional, List
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from src.ml_engine.evaluator import RiskScoringEngine
from server_quickstart.local_manager import (
    get_account,
    update_balance,
    record_transaction,
    list_accounts
)

app = FastAPI(
    title="NEXUS Local Fraud Detection Server",
    description="Standalone local fraud evaluation API powered by pre-trained predictive models",
    version="1.0.0"
)

# Initialize Risk Engine
evaluator = RiskScoringEngine()

class PaymentRequest(BaseModel):
    sender_id: str = Field(..., description="Sender username or UPI ID")
    receiver_id: str = Field(..., description="Receiver username or UPI ID")
    transaction_type: str = Field("TRANSFER", description="TRANSFER, PAYMENT, CASH_OUT, CASH_IN, DEBIT")
    amount: float = Field(..., gt=0, description="Amount in INR")
    pin: Optional[str] = Field(None, description="UPI/Bank PIN for verification")

class PaymentResponse(BaseModel):
    transaction_id: str
    sender_id: str
    receiver_id: str
    amount: float
    status: str
    risk_score: float
    risk_level: str
    supervised_ml_prob: float
    anomaly_detected: bool
    reasons: List[str]
    sender_new_balance: Optional[float] = None
    model_version: Optional[str] = None

@app.get("/")
def health_check():
    return {
        "status": "ONLINE",
        "service": "NEXUS Local Fraud Detection Engine",
        "model_loaded": True
    }

@app.get("/api/accounts")
def fetch_all_accounts():
    return list_accounts()

@app.get("/api/accounts/{identifier}")
def fetch_account(identifier: str):
    acc = get_account(identifier)
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")
    acc_clean = dict(acc)
    acc_clean.pop("pin", None)
    return acc_clean

@app.post("/api/payment/process", response_model=PaymentResponse)
def process_payment(payload: PaymentRequest):
    txn_id = f"TXN_{uuid.uuid4().hex[:10].upper()}"
    sender_acc = get_account(payload.sender_id)
    receiver_acc = get_account(payload.receiver_id)

    sender_bal = sender_acc["balance"] if sender_acc else 0.0
    receiver_bal = receiver_acc["balance"] if receiver_acc else 0.0

    # 1. PIN Check
    if sender_acc and payload.pin is not None:
        if str(sender_acc["pin"]).strip() != str(payload.pin).strip():
            return PaymentResponse(
                transaction_id=txn_id,
                sender_id=payload.sender_id,
                receiver_id=payload.receiver_id,
                amount=payload.amount,
                status="BLOCKED",
                risk_score=1.0,
                risk_level="CRITICAL",
                supervised_ml_prob=1.0,
                anomaly_detected=True,
                reasons=["Security Check Failed: Incorrect bank PIN"],
                sender_new_balance=sender_bal
            )

    # 2. Blacklist / Syndicate Check
    if receiver_acc and receiver_acc.get("is_evil", 0) == 1:
        return PaymentResponse(
            transaction_id=txn_id,
            sender_id=payload.sender_id,
            receiver_id=payload.receiver_id,
            amount=payload.amount,
            status="BLOCKED",
            risk_score=1.0,
            risk_level="CRITICAL",
            supervised_ml_prob=1.0,
            anomaly_detected=True,
            reasons=["Blacklist Violation: Destination account is flagged as a malicious mule"],
            sender_new_balance=sender_bal
        )

    # 3. Model Evaluation
    eval_payload = {
        "transaction_type": payload.transaction_type,
        "amount": payload.amount,
        "sender_current_balance": sender_bal,
        "receiver_current_balance": receiver_bal
    }
    eval_result = evaluator.evaluate_transaction(eval_payload)

    sender_new_bal = sender_bal
    if eval_result["status"] == "APPROVED":
        if sender_acc:
            sender_new_bal = update_balance(sender_acc["id"], -payload.amount)
        if receiver_acc:
            update_balance(receiver_acc["id"], payload.amount)

    # Record in local database
    record_transaction({
        "id": txn_id,
        "sender_id": sender_acc["upi_id"] if sender_acc else payload.sender_id,
        "receiver_id": receiver_acc["upi_id"] if receiver_acc else payload.receiver_id,
        "transaction_type": payload.transaction_type,
        "amount": payload.amount,
        "sender_current_balance": sender_bal,
        "status": eval_result["status"],
        "risk_score": eval_result["risk_score"],
        "risk_level": eval_result["risk_level"],
        "supervised_ml_prob": eval_result["supervised_ml_prob"],
        "anomaly_detected": eval_result["anomaly_detected"],
        "reasons": eval_result["reasons"]
    })

    return PaymentResponse(
        transaction_id=txn_id,
        sender_id=payload.sender_id,
        receiver_id=payload.receiver_id,
        amount=payload.amount,
        status=eval_result["status"],
        risk_score=eval_result["risk_score"],
        risk_level=eval_result["risk_level"],
        supervised_ml_prob=eval_result["supervised_ml_prob"],
        anomaly_detected=eval_result["anomaly_detected"],
        reasons=eval_result["reasons"],
        sender_new_balance=sender_new_bal,
        model_version=eval_result.get("model_version")
    )

if __name__ == "__main__":
    import uvicorn
    print("\n" + "=" * 70)
    print(" 🚀 NEXUS Local Fraud Detection Server Starting...")
    print(" 📡 Listening at: http://127.0.0.1:8000")
    print(" 📖 Interactive Swagger API Docs: http://127.0.0.1:8000/docs")
    print("=" * 70 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
