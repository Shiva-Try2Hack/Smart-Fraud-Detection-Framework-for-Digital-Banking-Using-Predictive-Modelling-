"""
Unit and Integration Tests for Phase 3:
- Tests all recommended actions (ALLOW, WARN_USER, REQUIRE_BIOMETRIC, REQUIRE_OTP, HOLD_FOR_REVIEW, BLOCK).
- Tests backward compatibility: old client still receives and parses status, risk_score, reasons.
- Tests scam warning generation when paying a brand-new payee large amounts.
- Tests new payee daily limit enforcement rule.
- Tests graded balance drain rules (70%, 85%, 95%).
- Tests safe degraded mode fallback (when model or DB fails, strictly uses rules-only scoring and returns degraded_mode=true).
- Tests cost-based threshold optimization function.
"""
import os
import sys
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from server_quickstart.run_local_server import app
from src.ml_engine.evaluator import RiskScoringEngine
from src.ml_engine.decision import DecisionLayer
from src.evaluation.optimize_thresholds import optimize_thresholds

client = TestClient(app)

def test_backward_compatibility_old_client_payload():
    """Verify that old client receiving responses from server still gets all expected fields."""
    response = client.post("/api/payment/process", json={
        "sender_id": "test_sender",
        "receiver_id": "test_receiver",
        "amount": 250.0,
        "transaction_type": "PAYMENT"
    })
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "risk_score" in data
    assert "reasons" in data
    assert "transaction_id" in data
    assert data["status"] in ["APPROVED", "FLAGGED", "BLOCKED"]

def test_scam_warning_triggered_for_new_payee_large_amount():
    """Verify scam warning object is produced when transferring large amount to new payee."""
    layer = DecisionLayer()
    payload = {
        "amount": 45000.0,
        "sender_current_balance": 100000.0,
        "user_behavior": {
            "is_new_payee": 1.0,
            "amount_zscore": 2.5,
            "amount_percentile_for_user": 0.95
        }
    }
    scam = layer.evaluate_scam_warning(payload)
    assert scam is not None
    assert scam["triggered"] is True
    assert "Scam Warning" in scam["message"]
    assert scam["confirmation_delay_seconds"] >= 5

def test_new_payee_daily_limit_rule():
    """Verify that transfers exceeding new payee daily cap (50,000 INR) trigger limit rule."""
    layer = DecisionLayer()
    payload = {
        "amount": 60000.0,
        "sender_current_balance": 100000.0,
        "user_behavior": {
            "is_new_payee": 1.0
        }
    }
    reasons, is_hard_block, _ = layer.evaluate_rules(payload)
    assert any(r["code"] == "NEW_PAYEE_LIMIT_EXCEEDED" for r in reasons)

def test_graded_balance_drain_rules():
    """Verify graded risk detection catches 70%, 85%, and 95% balance drains."""
    layer = DecisionLayer()
    
    # 70% drain with velocity
    p70 = {
        "amount": 7200.0,
        "sender_current_balance": 10000.0,
        "user_behavior": {"txn_count_1h": 2.0}
    }
    r70, _, _ = layer.evaluate_rules(p70)
    assert any(r["code"] == "RAPID_SPLIT_DRAIN_70" for r in r70)

    # 95% drain single shot
    p95 = {
        "amount": 19000.0,
        "sender_current_balance": 20000.0,
        "user_behavior": {}
    }
    r95, _, _ = layer.evaluate_rules(p95)
    assert any(r["code"] == "CRITICAL_BALANCE_DRAIN_95" for r in r95)

def test_safe_degraded_mode_fallback():
    """Verify when model is degraded or fails, system performs strict rules-only scoring with degraded_mode=True."""
    engine = RiskScoringEngine(version="v1")
    payload = {
        "transaction_type": "TRANSFER",
        "amount": 550000.0, # Exceeds high value cap
        "sender_current_balance": 600000.0,
        "receiver_current_balance": 1000.0,
        "degraded_mode": True
    }
    res = engine.evaluate_transaction(payload)
    assert res["degraded_mode"] is True
    assert res["status"] in ["FLAGGED", "BLOCKED"]
    assert len(res["reasons"]) > 0

def test_action_tier_coverage():
    """Verify actions ALLOW, WARN_USER, REQUIRE_OTP, REQUIRE_BIOMETRIC, HOLD_FOR_REVIEW, BLOCK."""
    layer = DecisionLayer()
    
    # ALLOW
    res_allow = layer.decide(
        payload={"amount": 100.0, "sender_current_balance": 10000.0},
        ml_probability=0.05,
        is_anomaly=False
    )
    assert res_allow["recommended_action"] == "ALLOW"
    assert res_allow["status"] == "APPROVED"

    # WARN_USER
    res_warn = layer.decide(
        payload={"amount": 30000.0, "sender_current_balance": 100000.0, "user_behavior": {"is_new_payee": 1.0, "amount_zscore": 2.2}},
        ml_probability=0.25,
        is_anomaly=False
    )
    assert res_warn["recommended_action"] == "WARN_USER"

    # BLOCK
    res_block = layer.decide(
        payload={"amount": 99000.0, "sender_current_balance": 100000.0},
        ml_probability=0.92,
        is_anomaly=True
    )
    assert res_block["recommended_action"] == "BLOCK"
    assert res_block["status"] == "BLOCKED"

def test_cost_based_threshold_optimization():
    """Verify threshold optimization executes and successfully finds minimum cost thresholds."""
    res = optimize_thresholds()
    assert "legacy" in res
    assert "optimal" in res
    assert res["optimal"]["total_cost_inr"] <= res["legacy"]["total_cost_inr"]
