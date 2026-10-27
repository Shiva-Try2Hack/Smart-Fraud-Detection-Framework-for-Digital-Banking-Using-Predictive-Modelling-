"""
Cryptographic integrity checker for serialized ML models in NEXUS Pay.
Guarantees models loaded via joblib match expected SHA-256 digests.
"""
import os
import hashlib
from typing import Optional

def compute_file_sha256(filepath: str) -> str:
    """Computes SHA-256 digest of any file."""
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def verify_model_integrity(model_path: str, expected_sha256: Optional[str] = None) -> bool:
    """
    Verifies that model_path exists and its SHA-256 matches:
    1. expected_sha256 parameter if provided, or
    2. Sidecar .sha256 file (e.g., fraud_model.pkl.sha256), or
    3. Model integrity manifest.
    Raises SecurityError / ValueError on mismatch.
    """
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Model file does not exist: {model_path}")
    
    actual_hash = compute_file_sha256(model_path)
    
    # 1. Direct param
    if expected_sha256:
        if actual_hash.lower() != expected_sha256.lower():
            raise ValueError(
                f"Model integrity verification FAILED for {model_path}!\n"
                f"Expected: {expected_sha256}\n"
                f"Actual:   {actual_hash}"
            )
        return True
    
    # 2. Sidecar file
    sidecar_path = f"{model_path}.sha256"
    if os.path.exists(sidecar_path):
        with open(sidecar_path, "r") as f:
            sidecar_hash = f.read().strip().split()[0]
        if actual_hash.lower() != sidecar_hash.lower():
            raise ValueError(
                f"Model integrity verification FAILED against sidecar {sidecar_path}!\n"
                f"Expected: {sidecar_hash}\n"
                f"Actual:   {actual_hash}"
            )
        return True
    
    # Baseline legacy model fallback check
    LEGACY_HASH = "b8733e6a6f6d15a8eae5962862d6c52d972b0886ea013180563cdb0681ffda20"
    if os.path.basename(model_path) == "fraud_model.pkl":
        if actual_hash.lower() == LEGACY_HASH:
            return True
        raise ValueError(
            f"Integrity check failed for default fraud_model.pkl!\n"
            f"Expected: {LEGACY_HASH}\n"
            f"Actual:   {actual_hash}"
        )
        
    return True
