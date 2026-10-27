"""
Latency benchmarking harness for NEXUS Pay RiskScoringEngine.
Measures 10,000 single transaction scoring invocations one-by-one.
Calculates and reports p50, p95, and p99 latency in milliseconds.
"""
import os
import sys
import time
import argparse
import numpy as np

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
from src.ml_engine.evaluator import RiskScoringEngine

def benchmark_engine_latency(n_iterations: int = 10000, model_version: str = "v1") -> dict:
    print(f"\n[BENCHMARK] Warming up and benchmarking RiskScoringEngine ({model_version.upper()}) over {n_iterations:,} transactions...")
    engine = RiskScoringEngine(version=model_version)

    sample_txns = [
        {"transaction_type": "TRANSFER", "amount": 2500.0, "sender_current_balance": 15000.0, "receiver_current_balance": 5000.0},
        {"transaction_type": "PAYMENT", "amount": 150.0, "sender_current_balance": 8000.0, "receiver_current_balance": 20000.0},
        {"transaction_type": "TRANSFER", "amount": 480000.0, "sender_current_balance": 500000.0, "receiver_current_balance": 0.0},
        {"transaction_type": "CASH_OUT", "amount": 95000.0, "sender_current_balance": 98000.0, "receiver_current_balance": 200.0},
    ]

    # Warmup 50 iterations
    for _ in range(50):
        engine.evaluate_transaction(sample_txns[0])

    latencies_ms = []
    
    t0_total = time.perf_counter()
    for i in range(n_iterations):
        payload = sample_txns[i % len(sample_txns)]
        t0 = time.perf_counter()
        _ = engine.evaluate_transaction(payload)
        t1 = time.perf_counter()
        latencies_ms.append((t1 - t0) * 1000.0)
    total_time_s = time.perf_counter() - t0_total

    latencies_ms = np.array(latencies_ms)
    p50 = float(np.percentile(latencies_ms, 50))
    p95 = float(np.percentile(latencies_ms, 95))
    p99 = float(np.percentile(latencies_ms, 99))
    mean_lat = float(np.mean(latencies_ms))
    throughput = n_iterations / total_time_s

    print("-" * 55)
    print(f" Model Version:     {model_version.upper()}")
    print(f" Total Invocations: {n_iterations:,}")
    print(f" Total Wall Time:   {total_time_s:.2f} s")
    print(f" Mean Latency:      {mean_lat:.3f} ms")
    print(f" p50 Latency:       {p50:.3f} ms")
    print(f" p95 Latency:       {p95:.3f} ms")
    print(f" p99 Latency:       {p99:.3f} ms")
    print(f" Throughput:        {throughput:.1f} req/s")
    print("-" * 55)

    return {
        "model_version": model_version,
        "n_iterations": n_iterations,
        "mean_ms": round(mean_lat, 3),
        "p50_ms": round(p50, 3),
        "p95_ms": round(p95, 3),
        "p99_ms": round(p99, 3),
        "throughput_req_per_s": round(throughput, 1)
    }

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Latency benchmark for RiskScoringEngine")
    parser.add_argument("-n", "--count", type=int, default=10000, help="Number of transaction evaluations")
    parser.add_argument("-v", "--version", type=str, default="v1", choices=["v1", "v2"], help="Model version (v1 or v2)")
    args = parser.parse_args()

    benchmark_engine_latency(n_iterations=args.count, model_version=args.version)
