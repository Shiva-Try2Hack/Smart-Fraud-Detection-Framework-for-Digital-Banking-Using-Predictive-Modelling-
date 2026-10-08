import uuid
from datetime import datetime
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional, List

from src.ml_engine.evaluator import RiskScoringEngine
from src.db.database import (
    log_transaction,
    get_recent_transactions,
    get_dashboard_metrics,
    get_account,
    list_all_accounts,
    adjust_balance
)

app = FastAPI(
    title="Smart Fraud Detection Framework API",
    description="Backend Scoring Engine & Surveillance API with Account State",
    version="2.1.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

engine = RiskScoringEngine()

class PaymentRequest(BaseModel):
    sender_id: str = Field(..., example="shiva@okaxis")
    receiver_id: str = Field(..., example="prasad@oksbi")
    transaction_type: str = Field("TRANSFER", example="TRANSFER")
    amount: float = Field(..., gt=0, example=5000.0)
    pin: Optional[str] = Field("1234", example="1234")
    timestamp: Optional[str] = Field(None, example="2026-10-05 03:30:00")

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
    receiver_new_balance: Optional[float] = None
    timestamp: str

@app.get("/")
def health_check():
    return {
        "service": "Smart Fraud Detection Framework API",
        "status": "online",
        "version": "2.1.0"
    }

@app.get("/api/accounts")
def get_accounts():
    return list_all_accounts()

@app.get("/api/accounts/{identifier}")
def get_single_account(identifier: str):
    acc = get_account(identifier)
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")
    return acc

@app.post("/api/payment/process", response_model=PaymentResponse)
def process_payment(payload: PaymentRequest):
    sender_acc = get_account(payload.sender_id)
    receiver_acc = get_account(payload.receiver_id)

    sender_bal = sender_acc["balance"] if sender_acc else 50000.0
    receiver_bal = receiver_acc["balance"] if receiver_acc else 0.0

    eval_dict = {
        "sender_id": payload.sender_id,
        "receiver_id": payload.receiver_id,
        "transaction_type": payload.transaction_type,
        "amount": payload.amount,
        "sender_current_balance": sender_bal,
        "receiver_current_balance": receiver_bal
    }

    eval_result = engine.evaluate_transaction(eval_dict)

    # 1. PIN verification
    if sender_acc and payload.pin and str(sender_acc["pin"]).strip() != str(payload.pin).strip():
        eval_result["status"] = "BLOCKED"
        eval_result["risk_level"] = "CRITICAL"
        eval_result["risk_score"] = 1.0
        eval_result["reasons"].append(f"Security Alert: Incorrect UPI PIN supplied for account {payload.sender_id}")

    # 2. Blacklist check (Evil / mule accounts)
    if receiver_acc and receiver_acc.get("is_evil") == 1:
        eval_result["status"] = "BLOCKED"
        eval_result["risk_level"] = "CRITICAL"
        eval_result["risk_score"] = 1.0
        eval_result["reasons"].append(f"Blacklist Trigger: Recipient {payload.receiver_id} is a known flagged syndicate/mule node")

    txn_id = f"TXN_{uuid.uuid4().hex[:10].upper()}"
    txn_time = payload.timestamp or datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    sender_new_bal = sender_bal
    receiver_new_bal = receiver_bal

    # If APPROVED, update balances in DB
    if eval_result["status"] == "APPROVED":
        if sender_acc:
            _, sender_new_bal = adjust_balance(payload.sender_id, -payload.amount)
        if receiver_acc:
            _, receiver_new_bal = adjust_balance(payload.receiver_id, payload.amount)

    log_record = {
        "id": txn_id,
        "sender_id": payload.sender_id,
        "receiver_id": payload.receiver_id,
        "transaction_type": payload.transaction_type,
        "amount": payload.amount,
        "sender_current_balance": sender_bal,
        "receiver_current_balance": receiver_bal,
        "status": eval_result["status"],
        "risk_score": eval_result["risk_score"],
        "risk_level": eval_result["risk_level"],
        "reasons": eval_result["reasons"],
        "timestamp": txn_time
    }
    log_transaction(log_record)

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
        sender_new_balance=sender_new_bal if eval_result["status"] == "APPROVED" else sender_bal,
        receiver_new_balance=receiver_new_bal if eval_result["status"] == "APPROVED" else receiver_bal,
        timestamp=txn_time
    )

@app.get("/api/admin/transactions")
def fetch_transactions(
    limit: int = Query(50, ge=1, le=500),
    status: Optional[str] = Query(None)
):
    return get_recent_transactions(limit=limit, status_filter=status)

@app.get("/api/admin/stats")
def fetch_stats():
    return get_dashboard_metrics()
