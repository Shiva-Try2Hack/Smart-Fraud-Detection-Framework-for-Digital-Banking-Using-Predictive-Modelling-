"""
High-fidelity User Behavior & Multi-Scenario Fraud Simulation Generator.
Produces realistic temporal histories for ~2,000 users over 90 days.

Features:
- Profiles: Income bands, typical monthly spend, preferred active hours, regular payees.
- Legitimate patterns: Salaries, monthly rent, weekend shopping, festival-season spikes.
- Hard negatives: Legitimate large purchases, first-time payee gifts, festival spending spikes.
- 6 Labeled Fraud Scenarios:
  (a) Account Takeover (new device, sudden large drain to new payee)
  (b) Scam / Authorized Push Payment (large urgent payment to new payee)
  (c) Mule Ring (multiple compromised accounts sending to mule nodes followed by immediate exit)
  (d) Split-Drain (account emptied via 3-5 rapid successive transfers)
  (e) Slow-and-Low (repeated smaller transfers to new payee under radar)
  (f) Card-testing pattern (tiny nominal payments followed by a spike)
- Strictly chronological event emission (guarantees point-in-time calculation).
"""
import os
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))
import random
import yaml
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from typing import List, Dict, Any, Tuple

from src.features.schema import FEATURE_SCHEMA_VERSION, FEATURE_COLUMNS_V2, BASE_FEATURES_V1
from src.features.user_features import compute_user_behavior_from_history

def generate_simulated_user_history_dataset(
    n_users: int = 2000,
    days: int = 90,
    fraud_rate: float = 0.008,
    random_seed: int = 42,
    output_path: str = "data/simulated_user_history.csv"
) -> pd.DataFrame:
    print(f"\n[SIMULATION] Generating {n_users} user profiles across {days} days (target fraud rate: {fraud_rate*100:.1f}%)...")
    np.random.seed(random_seed)
    random.seed(random_seed)

    start_date = datetime(2026, 1, 1, 0, 0, 0)
    total_seconds = days * 86400

    # 1. Create User Profiles
    # Income bands: Lower (15k-30k), Mid (30k-80k), High (80k-250k)
    user_profiles = {}
    merchants = [f"M_{name}" for name in ["Amazon_IN", "Flipkart", "Zomato", "Swiggy", "DMart", "JioRecharge", "Uber_India", "Electricity_BESCOM", "Apollo_Pharmacy", "BookMyShow"]]
    mule_accounts = [f"MULE_SYNDICATE_{i}" for i in range(1, 15)]

    for uid in range(1, n_users + 1):
        user_id = f"USR_{uid:05d}"
        tier = np.random.choice(["LOWER", "MID", "HIGH"], p=[0.40, 0.45, 0.15])
        if tier == "LOWER":
            monthly_income = np.random.uniform(15000, 30000)
            avg_txn = np.random.uniform(150, 600)
        elif tier == "MID":
            monthly_income = np.random.uniform(30000, 80000)
            avg_txn = np.random.uniform(400, 2000)
        else:
            monthly_income = np.random.uniform(80000, 250000)
            avg_txn = np.random.uniform(1500, 8000)

        # 5 to 20 regular payees (friends, family, merchants)
        n_payees = np.random.randint(5, 21)
        friends = [f"USR_{np.random.randint(1, n_users + 1):05d}" for _ in range(n_payees)]
        regular_payees = list(set(friends + random.sample(merchants, 3)))
        active_hours = np.random.choice(range(8, 23), size=np.random.randint(6, 14), replace=False)

        user_profiles[user_id] = {
            "tier": tier,
            "monthly_income": monthly_income,
            "avg_txn": avg_txn,
            "regular_payees": regular_payees,
            "active_hours": list(active_hours),
            "created_at": start_date - timedelta(days=np.random.randint(60, 365)),
            "current_balance": monthly_income * np.random.uniform(0.5, 2.5),
            "device_id": f"DEV_{uid}_{np.random.randint(1000, 9999)}"
        }

    raw_events = []

    # 2. Generate Legitimate Behavioral Patterns
    # (a) Monthly Salary & Rent
    for uid, prof in user_profiles.items():
        for month_idx in range(3):
            # Salary credit around 1st-3rd of month
            salary_time = start_date + timedelta(days=month_idx * 30 + np.random.uniform(0, 3))
            prof["current_balance"] += prof["monthly_income"]

            # Rent payment around 5th of month (transfer)
            rent_amount = prof["monthly_income"] * np.random.uniform(0.25, 0.40)
            rent_time = start_date + timedelta(days=month_idx * 30 + 5, hours=np.random.randint(9, 18))
            landlord = prof["regular_payees"][0]
            raw_events.append({
                "timestamp": rent_time,
                "sender_id": uid,
                "receiver_id": landlord,
                "amount": round(rent_amount, 2),
                "type": "TRANSFER",
                "device_id": prof["device_id"],
                "is_fraud": 0,
                "fraud_scenario": "LEGIT_RENT"
            })

    # (b) Daily transactions & Weekend shopping
    for uid, prof in user_profiles.items():
        # User makes 15 to 45 transactions a month
        n_daily_txns = np.random.randint(45, 130)
        for _ in range(n_daily_txns):
            sec_offset = np.random.uniform(0, total_seconds)
            event_dt = start_date + timedelta(seconds=sec_offset)
            
            # Align with active hours
            hour = random.choice(prof["active_hours"])
            event_dt = event_dt.replace(hour=hour, minute=np.random.randint(0, 60))

            # Regular payee or occasional merchant
            if np.random.rand() < 0.85:
                receiver = random.choice(prof["regular_payees"])
            else:
                receiver = random.choice(merchants)

            # Amount
            txn_amount = max(20.0, np.random.exponential(scale=prof["avg_txn"]))
            txn_type = np.random.choice(["PAYMENT", "TRANSFER", "CASH_OUT", "DEBIT"], p=[0.60, 0.25, 0.10, 0.05])
            
            raw_events.append({
                "timestamp": event_dt,
                "sender_id": uid,
                "receiver_id": receiver,
                "amount": round(txn_amount, 2),
                "type": txn_type,
                "device_id": prof["device_id"],
                "is_fraud": 0,
                "fraud_scenario": "LEGIT_STANDARD"
            })

    # (c) Hard Negatives: Legitimate high-value purchases, festival spikes, new payee wedding gifts
    festival_day_45 = start_date + timedelta(days=45)
    for uid in list(user_profiles.keys())[:300]:
        prof = user_profiles[uid]
        # Festival splurge (Diwali/festive spike)
        fest_amount = prof["monthly_income"] * np.random.uniform(0.3, 0.7)
        fest_time = festival_day_45 + timedelta(hours=np.random.randint(10, 22), minutes=np.random.randint(0, 60))
        raw_events.append({
            "timestamp": fest_time,
            "sender_id": uid,
            "receiver_id": random.choice(merchants),
            "amount": round(fest_amount, 2),
            "type": "PAYMENT",
            "device_id": prof["device_id"],
            "is_fraud": 0,
            "fraud_scenario": "HARD_NEGATIVE_FESTIVAL_SPIKE"
        })

    # Hard Negative: Legitimate large wedding gift to a brand-new payee
    for uid in list(user_profiles.keys())[300:500]:
        prof = user_profiles[uid]
        gift_amount = np.random.uniform(15000, 60000)
        gift_time = start_date + timedelta(days=np.random.randint(20, 80), hours=np.random.randint(11, 20))
        brand_new_friend = f"USR_{np.random.randint(1, n_users+1):05d}"
        raw_events.append({
            "timestamp": gift_time,
            "sender_id": uid,
            "receiver_id": brand_new_friend,
            "amount": round(gift_amount, 2),
            "type": "TRANSFER",
            "device_id": prof["device_id"],
            "is_fraud": 0,
            "fraud_scenario": "HARD_NEGATIVE_FIRST_TIME_LARGE_GIFT"
        })

    # 3. Inject Labeled Fraud Scenarios
    victim_users = list(user_profiles.keys())[600:1100]
    random.shuffle(victim_users)

    # Scenario (a): Account Takeover (ATO)
    # Attacker from new device, unusual midnight hour, large drain to new mule
    for uid in victim_users[:40]:
        prof = user_profiles[uid]
        ato_time = start_date + timedelta(days=int(np.random.randint(25, 85)), hours=int(np.random.choice([1, 2, 3, 4])))
        ato_amount = prof["monthly_income"] * np.random.uniform(0.85, 1.4)
        mule = random.choice(mule_accounts)
        raw_events.append({
            "timestamp": ato_time,
            "sender_id": uid,
            "receiver_id": mule,
            "amount": round(ato_amount, 2),
            "type": "TRANSFER",
            "device_id": f"ATTACKER_DEVICE_{np.random.randint(100, 999)}",
            "is_fraud": 1,
            "fraud_scenario": "ACCOUNT_TAKEOVER"
        })

    # Scenario (b): Scam / APP (Authorized Push Payment) Fraud
    # Urgent high amount to fake lottery/tech support mule
    for uid in victim_users[40:80]:
        prof = user_profiles[uid]
        scam_time = start_date + timedelta(days=np.random.randint(15, 80), hours=np.random.randint(10, 19))
        scam_amount = np.random.uniform(45000, 180000)
        mule = f"SCAMMER_MULE_{np.random.randint(1, 20)}"
        raw_events.append({
            "timestamp": scam_time,
            "sender_id": uid,
            "receiver_id": mule,
            "amount": round(scam_amount, 2),
            "type": "TRANSFER",
            "device_id": prof["device_id"], # Legitimate device!
            "is_fraud": 1,
            "fraud_scenario": "SCAM_APP_FRAUD"
        })

    # Scenario (c): Mule Ring Syndicate (Multiple victims -> Single Mule -> Fast Out)
    mule_node = "SYNDICATE_COLLECTOR_01"
    mule_exit_node = "SYNDICATE_CRYPTO_EXIT"
    ring_time = start_date + timedelta(days=60, hours=14)
    for v_idx, uid in enumerate(victim_users[80:110]):
        in_time = ring_time + timedelta(minutes=v_idx * 2)
        raw_events.append({
            "timestamp": in_time,
            "sender_id": uid,
            "receiver_id": mule_node,
            "amount": round(np.random.uniform(25000, 60000), 2),
            "type": "TRANSFER",
            "device_id": user_profiles[uid]["device_id"],
            "is_fraud": 1,
            "fraud_scenario": "MULE_RING"
        })

    # Mule rapid exit transaction
    raw_events.append({
        "timestamp": ring_time + timedelta(minutes=70),
        "sender_id": mule_node,
        "receiver_id": mule_exit_node,
        "amount": 950000.0,
        "type": "CASH_OUT",
        "device_id": "MULE_DEVICE_EXIT",
        "is_fraud": 1,
        "fraud_scenario": "MULE_RING_EXIT"
    })

    # Scenario (d): Split-Drain (Account emptied across 4 rapid successive transfers within minutes)
    for uid in victim_users[110:140]:
        prof = user_profiles[uid]
        split_base_time = start_date + timedelta(days=np.random.randint(20, 85), hours=np.random.randint(1, 23))
        slice_amount = (prof["monthly_income"] * 0.9) / 4.0
        mule = random.choice(mule_accounts)
        for offset_min in [0, 2, 5, 8]:
            raw_events.append({
                "timestamp": split_base_time + timedelta(minutes=offset_min),
                "sender_id": uid,
                "receiver_id": mule,
                "amount": round(slice_amount, 2),
                "type": "TRANSFER",
                "device_id": f"ATTACKER_{uid}",
                "is_fraud": 1,
                "fraud_scenario": "SPLIT_DRAIN"
            })

    # Scenario (e): Slow-and-Low (Repeated smaller amounts to new payee)
    for uid in victim_users[140:170]:
        base_time = start_date + timedelta(days=np.random.randint(10, 60))
        mule = random.choice(mule_accounts)
        for day_offset in range(5):
            raw_events.append({
                "timestamp": base_time + timedelta(days=day_offset, hours=15),
                "sender_id": uid,
                "receiver_id": mule,
                "amount": round(np.random.uniform(1800, 4500), 2),
                "type": "TRANSFER",
                "device_id": f"SNEAKY_DEVICE_{uid}",
                "is_fraud": 1,
                "fraud_scenario": "SLOW_AND_LOW"
            })

    # Scenario (f): Card-testing pattern (Tiny nominal payments followed by a spike)
    for uid in victim_users[170:200]:
        base_time = start_date + timedelta(days=np.random.randint(15, 75))
        mule = random.choice(mule_accounts)
        # 3 tiny probe transactions (INR 1.00, 2.50, 5.00)
        for probe_min, p_amt in [(0, 1.0), (3, 2.5), (7, 5.0)]:
            raw_events.append({
                "timestamp": base_time + timedelta(minutes=probe_min),
                "sender_id": uid,
                "receiver_id": mule,
                "amount": p_amt,
                "type": "PAYMENT",
                "device_id": f"PROBE_BOT_{uid}",
                "is_fraud": 1,
                "fraud_scenario": "CARD_TESTING_PROBE"
            })
        # Massive hit immediately after
        raw_events.append({
            "timestamp": base_time + timedelta(minutes=10),
            "sender_id": uid,
            "receiver_id": mule,
            "amount": round(np.random.uniform(80000, 200000), 2),
            "type": "TRANSFER",
            "device_id": f"PROBE_BOT_{uid}",
            "is_fraud": 1,
            "fraud_scenario": "CARD_TESTING_SPIKE"
        })

    # Sort strictly by timestamp (guarantees point-in-time calculation)
    raw_df = pd.DataFrame(raw_events)
    raw_df = raw_df.sort_values(by="timestamp").reset_index(drop=True)
    raw_df["step"] = ((raw_df["timestamp"] - start_date).dt.total_seconds() // 3600).astype(int) + 1

    total_ev = len(raw_df)
    n_fraud_actual = int(raw_df["is_fraud"].sum())
    actual_fraud_pct = (n_fraud_actual / total_ev) * 100
    print(f"Generated {total_ev:,} transactions across {days} days.")
    print(f"Total Fraud Transactions: {n_fraud_actual:,} ({actual_fraud_pct:.2f}%)")

    # 4. Point-in-time Feature Extraction using identical production code path
    print("[SIMULATION] Extracting point-in-time user behavior features (strictly past data)...")
    history_by_sender = {}
    history_by_receiver = {}
    all_feature_rows = []

    type_map = {"PAYMENT": 0, "TRANSFER": 1, "CASH_OUT": 2, "CASH_IN": 3, "DEBIT": 4}

    # Running balances
    balances = {uid: prof["current_balance"] for uid, prof in user_profiles.items()}

    for idx, row in raw_df.iterrows():
        s_id = row["sender_id"]
        r_id = row["receiver_id"]
        amt = float(row["amount"])
        t_cur = row["timestamp"]
        t_type = row["type"]

        # Sender and Receiver running balance before transaction
        old_orig = balances.get(s_id, 10000.0)
        old_dest = balances.get(r_id, 5000.0)

        # Baseline features
        type_code = float(type_map.get(t_type, 0))
        amt_to_bal = amt / (old_orig + 1.0)

        # User behavior features strictly from historical transactions
        past_s = history_by_sender.get(s_id, [])
        past_r = history_by_receiver.get(r_id, [])
        rx_prof = user_profiles.get(r_id)
        rx_created = rx_prof["created_at"] if rx_prof else (start_date - timedelta(days=60))

        behav = compute_user_behavior_from_history(
            sender_id=s_id,
            receiver_id=r_id,
            amount=amt,
            current_time=t_cur,
            past_sender_txns=past_s,
            past_receiver_txns=past_r,
            failed_pin_count=1 if "ATTACKER" in str(row.get("device_id", "")) else 0,
            receiver_created_at=rx_created
        )

        row_feat = {
            "type_code": type_code,
            "amount": amt,
            "oldbalanceOrg": old_orig,
            "oldbalanceDest": old_dest,
            "amount_to_balance_ratio": amt_to_bal,
            **behav
        }
        all_feature_rows.append(row_feat)

        # Update historical state for future transactions ONLY AFTER current transaction has been evaluated!
        hist_entry = {
            "amount": amt,
            "receiver_id": r_id,
            "sender_id": s_id,
            "timestamp": t_cur
        }
        if s_id not in history_by_sender:
            history_by_sender[s_id] = []
        history_by_sender[s_id].append(hist_entry)

        if r_id not in history_by_receiver:
            history_by_receiver[r_id] = []
        history_by_receiver[r_id].append(hist_entry)

        # Update balance
        balances[s_id] = max(0.0, old_orig - amt)
        balances[r_id] = old_dest + amt

    feat_df = pd.DataFrame(all_feature_rows)
    merged_df = pd.concat([raw_df, feat_df], axis=1)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    merged_df.to_csv(output_path, index=False)
    print(f"[DONE] Saved simulated user history dataset to: {output_path}")
    return merged_df

if __name__ == "__main__":
    generate_simulated_user_history_dataset()
