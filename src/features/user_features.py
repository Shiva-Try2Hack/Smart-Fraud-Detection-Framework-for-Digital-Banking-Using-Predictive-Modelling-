"""
User Behavior and Velocity feature calculation engine for NEXUS Pay.

Computes point-in-time features strictly using historical transactions executed
prior to the transaction timestamp (no look-ahead leakage).
Supports single round-trip SQLite query for production serving and in-memory
point-in-time calculation for simulation/training.
"""
import os
import sqlite3
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from typing import Dict, Any, Optional, Union

from src.features.schema import FEATURE_SCHEMA_VERSION, USER_BEHAVIOR_FEATURES

COLD_START_THRESHOLD = 5

def parse_iso_or_datetime(ts: Union[str, datetime]) -> datetime:
    if isinstance(ts, datetime):
        return ts
    if isinstance(ts, (int, float)):
        # If timestamp is simulated step or unix timestamp
        return datetime.fromtimestamp(ts)
    # String format
    ts_str = str(ts).replace("Z", "").split(".")[0]
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(ts_str, fmt)
        except ValueError:
            continue
    return datetime.utcnow()

def compute_user_behavior_from_history(
    sender_id: str,
    receiver_id: str,
    amount: float,
    current_time: datetime,
    past_sender_txns: list,
    past_receiver_txns: list,
    failed_pin_count: int = 0,
    receiver_created_at: Optional[datetime] = None
) -> Dict[str, float]:
    """
    Computes all user behavior and velocity features purely from lists of historical records.
    Guarantee: All records in past_sender_txns and past_receiver_txns MUST have timestamp < current_time.
    """
    amount = float(amount)
    cur_hour = current_time.hour

    # Receiver account age in days
    if receiver_created_at:
        rx_age_days = max(0.0, (current_time - receiver_created_at).total_seconds() / 86400.0)
    else:
        # Default if unknown
        rx_age_days = 90.0

    # Filter sender history within 30 days
    t_30d = current_time - timedelta(days=30)
    t_24h = current_time - timedelta(hours=24)
    t_1h = current_time - timedelta(hours=1)
    t_1m = current_time - timedelta(minutes=1)

    txns_30d = [t for t in past_sender_txns if t["timestamp"] >= t_30d]
    txns_24h = [t for t in past_sender_txns if t["timestamp"] >= t_24h]
    txns_1h = [t for t in past_sender_txns if t["timestamp"] >= t_1h]
    txns_1m = [t for t in past_sender_txns if t["timestamp"] >= t_1m]

    amounts_30d = [t["amount"] for t in txns_30d]
    txn_count_30d = len(amounts_30d)

    # Cold start check
    cold_start = 1.0 if txn_count_30d < COLD_START_THRESHOLD else 0.0

    if txn_count_30d >= COLD_START_THRESHOLD:
        avg_amount_30d = float(np.mean(amounts_30d))
        max_amount_30d = float(np.max(amounts_30d))
        std_amount_30d = float(np.std(amounts_30d))

        if std_amount_30d > 1e-4:
            amount_zscore = float((amount - avg_amount_30d) / std_amount_30d)
        else:
            amount_zscore = float(amount / (avg_amount_30d + 1.0))
        
        # Percentile for user
        amount_percentile_for_user = float(np.mean([1.0 if a <= amount else 0.0 for a in amounts_30d]))

        # Usual hour of day (mode)
        hours_30d = [t["timestamp"].hour for t in txns_30d]
        if hours_30d:
            from collections import Counter
            usual_hour_of_day = float(Counter(hours_30d).most_common(1)[0][0])
            # Circular hour deviation (0 to 12)
            hour_diff = abs(cur_hour - usual_hour_of_day)
            hour_deviation = float(min(hour_diff, 24 - hour_diff))
        else:
            usual_hour_of_day = 12.0
            hour_deviation = 0.0
    else:
        # Sensible defaults for cold start / new users so they are not penalized
        avg_amount_30d = amount
        max_amount_30d = amount
        std_amount_30d = 0.0
        amount_zscore = 0.0
        amount_percentile_for_user = 0.5
        usual_hour_of_day = float(cur_hour)
        hour_deviation = 0.0

    # Velocity features
    txn_count_1min = float(len(txns_1m))
    txn_count_1h = float(len(txns_1h))
    txn_count_24h = float(len(txns_24h))

    amount_sum_1h = float(sum([t["amount"] for t in txns_1h]))
    amount_sum_24h = float(sum([t["amount"] for t in txns_24h]))

    # Payee-specific history
    payee_txns = [t for t in past_sender_txns if t.get("receiver_id") == receiver_id]
    payee_txn_count = float(len(payee_txns))
    is_new_payee = 1.0 if payee_txn_count == 0 else 0.0

    if payee_txn_count > 0:
        first_payee_time = min([t["timestamp"] for t in payee_txns])
        days_since_first_payment = max(0.0, (current_time - first_payee_time).total_seconds() / 86400.0)
    else:
        days_since_first_payment = 0.0

    # Receiver distinct senders in last 24h
    rx_txns_24h = [t for t in past_receiver_txns if t["timestamp"] >= t_24h]
    distinct_senders_24h = float(len(set([t.get("sender_id") for t in rx_txns_24h])))

    return {
        "avg_amount_30d": avg_amount_30d,
        "max_amount_30d": max_amount_30d,
        "std_amount_30d": std_amount_30d,
        "txn_count_30d": float(txn_count_30d),
        "amount_zscore": amount_zscore,
        "amount_percentile_for_user": amount_percentile_for_user,
        "txn_count_1min": txn_count_1min,
        "txn_count_1h": txn_count_1h,
        "txn_count_24h": txn_count_24h,
        "amount_sum_1h": amount_sum_1h,
        "amount_sum_24h": amount_sum_24h,
        "usual_hour_of_day": usual_hour_of_day,
        "hour_deviation": hour_deviation,
        "is_new_payee": is_new_payee,
        "payee_txn_count": payee_txn_count,
        "days_since_first_payment_to_payee": days_since_first_payment,
        "receiver_account_age_days": rx_age_days,
        "receiver_distinct_senders_24h": distinct_senders_24h,
        "failed_pin_attempts_last_15min": float(failed_pin_count),
        "cold_start": cold_start
    }

def get_user_behavior_features_db(
    conn: sqlite3.Connection,
    sender_id: str,
    receiver_id: str,
    amount: float,
    current_time: Optional[Union[datetime, str]] = None
) -> Dict[str, float]:
    """
    High-performance, single round-trip fetch from SQLite transaction_history & pin_attempts tables.
    Uses indexed queries strictly before current_time.
    """
    cur_dt = parse_iso_or_datetime(current_time or datetime.utcnow())
    cur_iso = cur_dt.strftime("%Y-%m-%d %H:%M:%S")
    t_30d_iso = (cur_dt - timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")
    t_15m_iso = (cur_dt - timedelta(minutes=15)).strftime("%Y-%m-%d %H:%M:%S")

    cursor = conn.cursor()

    # 1. Fetch sender history (last 30 days strictly < cur_iso)
    cursor.execute("""
    SELECT amount, receiver_id, timestamp
    FROM transaction_history
    WHERE sender_id = ? AND timestamp >= ? AND timestamp < ?
    ORDER BY timestamp ASC
    """, (sender_id, t_30d_iso, cur_iso))
    sender_rows = cursor.fetchall()

    past_sender_txns = []
    for r in sender_rows:
        past_sender_txns.append({
            "amount": float(r["amount"] if isinstance(r, sqlite3.Row) else r[0]),
            "receiver_id": str(r["receiver_id"] if isinstance(r, sqlite3.Row) else r[1]),
            "timestamp": parse_iso_or_datetime(r["timestamp"] if isinstance(r, sqlite3.Row) else r[2])
        })

    # 2. Fetch receiver history (last 24h strictly < cur_iso)
    t_24h_iso = (cur_dt - timedelta(hours=24)).strftime("%Y-%m-%d %H:%M:%S")
    cursor.execute("""
    SELECT sender_id, timestamp
    FROM transaction_history
    WHERE receiver_id = ? AND timestamp >= ? AND timestamp < ?
    """, (receiver_id, t_24h_iso, cur_iso))
    rx_rows = cursor.fetchall()
    past_rx_txns = []
    for r in rx_rows:
        past_rx_txns.append({
            "sender_id": str(r["sender_id"] if isinstance(r, sqlite3.Row) else r[0]),
            "timestamp": parse_iso_or_datetime(r["timestamp"] if isinstance(r, sqlite3.Row) else r[1])
        })

    # 3. Receiver account creation timestamp
    cursor.execute("SELECT created_at FROM accounts WHERE username = ? OR upi_id = ?", (receiver_id, receiver_id))
    acc_row = cursor.fetchone()
    rx_created_at = None
    if acc_row:
        raw_val = acc_row["created_at"] if isinstance(acc_row, sqlite3.Row) else acc_row[0]
        rx_created_at = parse_iso_or_datetime(raw_val)

    # 4. Failed PIN attempts in last 15 min
    cursor.execute("""
    SELECT COUNT(*) FROM pin_attempts
    WHERE account_id = ? AND success = 0 AND timestamp >= ? AND timestamp <= ?
    """, (sender_id, t_15m_iso, cur_iso))
    pin_row = cursor.fetchone()
    failed_pins = int(pin_row[0] if pin_row else 0)

    return compute_user_behavior_from_history(
        sender_id=sender_id,
        receiver_id=receiver_id,
        amount=amount,
        current_time=cur_dt,
        past_sender_txns=past_sender_txns,
        past_receiver_txns=past_rx_txns,
        failed_pin_count=failed_pins,
        receiver_created_at=rx_created_at
    )
