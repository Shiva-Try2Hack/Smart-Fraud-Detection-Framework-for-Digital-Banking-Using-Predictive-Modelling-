"""
Phase 1 Unit Tests for NEXUS Pay:
- Feature computation parity between training and inference (no skew)
- API contract stability (FastAPI /api/payment/process backwards-compatibility)
- SHA-256 cryptographic integrity verification (detects tampered/corrupt models)
- Reason codes guarantees (always present for FLAGGED and BLOCKED outcomes)
- Dual-engine fallback support (MODEL_VERSION v1 and v2)
"""
import os
import sys
import copy
import pytest
import numpy as np
import pandas as pd
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from server_quickstart.run_local_server import app
from src.ml_engine.evaluator import RiskScoringEngine
from src.ml_engine.features import engineer_features_df, engineer_single_payload, ALL_11_FEATURES
from src.ml_engine.integrity import verify_model_integrity, compute_file_sha256

client = TestClient(app)

def test_feature_computation_parity():
    """Verify that batch/training feature engineering and single-payload serving engineering match identically."""
    raw_sample = {
        "step": [10],
        "type": ["TRANSFER"],
        "amount": [45000.0],
        "nameOrig": ["C111"],
        "oldbalanceOrg": [50000.0],
        "newbalanceOrig": [5000.0],
        "nameDest": ["C222"],
        "oldbalanceDest": [1000.0],
        "newbalanceDest": [46000.0],
        "isFraud": [0],
        "isFlaggedFraud": [0]
    }
    df = pd.DataFrame(raw_sample)
    train_features = engineer_features_df(df, "all_11")

    serving_payload = {
        "transaction_type": "TRANSFER",
        "amount": 45000.0,
        "sender_current_balance": 50000.0,
        "receiver_current_balance": 1000.0
    }
    serving_features = engineer_single_payload(serving_payload, ALL_11_FEATURES)

    for col in ALL_11_FEATURES:
        train_val = train_features.iloc[0][col]
        serving_val = serving_features.iloc[0][col]
        assert np.isclose(train_val, serving_val), f"Feature skew detected on '{col}': train={train_val}, serving={serving_val}"

def test_api_contract_unchanged():
    """Verify POST /api/payment/process returns standard fields required by Android client."""
    response = client.post("/api/payment/process", json={
        "sender_id": "test_sender",
        "receiver_id": "test_receiver",
        "amount": 500.0,
        "transaction_type": "TRANSFER"
    })
    assert response.status_code == 200
    data = response.json()
    
    # Core fields required by Android GPayRepository / PaymentSheetScreen
    for required_key in ["transaction_id", "sender_id", "receiver_id", "amount", "status", "risk_score", "risk_level", "reasons"]:
        assert required_key in data, f"Missing required response field '{required_key}'"
    
    assert data["status"] in ["APPROVED", "FLAGGED", "BLOCKED"]
    assert isinstance(data["risk_score"], float)
    assert isinstance(data["reasons"], list)

def test_reason_codes_always_present_for_flagged_and_blocked():
    """Verify that any FLAGGED or BLOCKED decision provides human-readable explanations."""
    engine = RiskScoringEngine()
    
    # Trigger BLOCKED via insufficient balance
    blocked_res = engine.evaluate_transaction({
        "amount": 10000.0,
        "sender_current_balance": 500.0,
        "receiver_current_balance": 0.0
    })
    assert blocked_res["status"] == "BLOCKED"
    assert len(blocked_res["reasons"]) > 0
    assert any("insufficient" in r.lower() for r in blocked_res["reasons"])

    # Trigger critical balance drain
    drain_res = engine.evaluate_transaction({
        "amount": 99000.0,
        "sender_current_balance": 100000.0,
        "receiver_current_balance": 0.0
    })
    assert drain_res["status"] in ["FLAGGED", "BLOCKED"]
    assert len(drain_res["reasons"]) > 0

def test_sha256_integrity_rejects_tampered_model(tmp_path):
    """Verify cryptographic integrity checker raises ValueError if model bytes are altered."""
    fake_model = tmp_path / "tampered_model.joblib"
    fake_model.write_text("CORRUPTED_BYTES")
    
    expected_hash = "0000000000000000000000000000000000000000000000000000000000000000"
    with pytest.raises(ValueError) as excinfo:
        verify_model_integrity(str(fake_model), expected_sha256=expected_hash)
    assert "verification FAILED" in str(excinfo.value)

def test_model_v1_and_v2_loadable():
    """Verify both baseline v1 model and newly rebuilt v2 bundle are loadable and evaluate transactions."""
    engine_v1 = RiskScoringEngine(version="v1")
    assert engine_v1.classifier is not None
    
    engine_v2 = RiskScoringEngine(version="v2")
    assert engine_v2.classifier is not None
    assert engine_v2.calibrator is not None

    test_payload = {
        "transaction_type": "TRANSFER",
        "amount": 1200.0,
        "sender_current_balance": 5000.0,
        "receiver_current_balance": 200.0
    }
    res_v1 = engine_v1.evaluate_transaction(test_payload)
    res_v2 = engine_v2.evaluate_transaction(test_payload)
    assert res_v1["status"] in ["APPROVED", "FLAGGED", "BLOCKED"]
    assert res_v2["status"] in ["APPROVED", "FLAGGED", "BLOCKED"]
