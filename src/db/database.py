import sqlite3
import os
import json
from datetime import datetime

DB_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "database.sqlite")

def get_connection():
    return sqlite3.connect(DB_PATH)

def init_db():
    conn = get_connection()
    cursor = conn.cursor()
    
    # 1. Accounts Table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS accounts (
            username TEXT PRIMARY KEY,
            full_name TEXT NOT NULL,
            upi_id TEXT UNIQUE NOT NULL,
            pin TEXT NOT NULL,
            balance REAL NOT NULL,
            is_evil INTEGER DEFAULT 0,
            created_at TIMESTAMP
        )
    """)

    # 2. Transactions Table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS transactions (
            id TEXT PRIMARY KEY,
            sender_id TEXT,
            receiver_id TEXT,
            transaction_type TEXT,
            amount REAL,
            sender_balance REAL,
            receiver_balance REAL,
            status TEXT,
            risk_score REAL,
            risk_level TEXT,
            reasons TEXT,
            created_at TIMESTAMP
        )
    """)
    conn.commit()
    conn.close()

def seed_default_accounts():
    """Populates 5 legitimate users + 3 evil mule accounts with realistic UPI IDs."""
    init_db()
    conn = get_connection()
    cursor = conn.cursor()

    default_users = [
        # 5 Legitimate Student Accounts
        ("shiva", "Shiva", "shiva@okaxis", "1234", 80000.0, 0),
        ("prasad", "Prasad Kadu", "prasad@oksbi", "1122", 60000.0, 0),
        ("sayali", "Sayali Dahake", "sayali@okhdfc", "2233", 45000.0, 0),
        ("minal", "Minal Thakare", "minal@okicici", "3344", 50000.0, 0),
        ("prathamesh", "Prathamesh Meshram", "prathamesh@okpaytm", "4455", 55000.0, 0),
        
        # 3 Dummy Evil Accounts (Mule accounts)
        ("evil_darkmule", "Dark Mule Node 01", "evil.darkmule@darkpay", "0000", 250.0, 1),
        ("evil_phisher", "Syndicate Phisher X", "evil.phisher@shadowbank", "0000", 100.0, 1),
        ("evil_launderer", "Launderer Ghost Hub", "evil.launderer@ghostupi", "0000", 0.0, 1)
    ]

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    for username, name, upi, pin, balance, is_evil in default_users:
        cursor.execute("""
            INSERT OR IGNORE INTO accounts (username, full_name, upi_id, pin, balance, is_evil, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (username, name, upi, pin, balance, is_evil, now))

    conn.commit()
    conn.close()

def create_or_update_account(username: str, full_name: str, upi_id: str, pin: str, initial_balance: float = 0.0, is_evil: int = 0):
    conn = get_connection()
    cursor = conn.cursor()
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    cursor.execute("""
        INSERT INTO accounts (username, full_name, upi_id, pin, balance, is_evil, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(username) DO UPDATE SET
            full_name = excluded.full_name,
            upi_id = excluded.upi_id,
            pin = excluded.pin,
            balance = excluded.balance,
            is_evil = excluded.is_evil
    """, (username.lower(), full_name, upi_id, pin, initial_balance, is_evil, now))
    conn.commit()
    conn.close()

def delete_account(identifier: str) -> bool:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM accounts WHERE username = ? OR upi_id = ?", (identifier.lower(), identifier.lower()))
    deleted = cursor.rowcount > 0
    conn.commit()
    conn.close()
    return deleted

def change_password_pin(identifier: str, new_pin: str) -> bool:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        UPDATE accounts SET pin = ? WHERE username = ? OR upi_id = ?
    """, (new_pin, identifier.lower(), identifier.lower()))
    updated = cursor.rowcount > 0
    conn.commit()
    conn.close()
    return updated

def adjust_balance(identifier: str, delta_amount: float) -> tuple[bool, float]:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT balance FROM accounts WHERE username = ? OR upi_id = ?", (identifier.lower(), identifier.lower()))
    row = cursor.fetchone()
    if not row:
        conn.close()
        return False, 0.0

    current = row[0]
    new_bal = current + delta_amount
    if new_bal < 0:
        new_bal = 0.0

    cursor.execute("UPDATE accounts SET balance = ? WHERE username = ? OR upi_id = ?", (new_bal, identifier.lower(), identifier.lower()))
    conn.commit()
    conn.close()
    return True, new_bal

def get_account(identifier: str) -> dict | None:
    conn = get_connection()
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM accounts WHERE username = ? OR upi_id = ?", (identifier.lower(), identifier.lower()))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None

def list_all_accounts(include_evil: bool = True) -> list[dict]:
    conn = get_connection()
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    if include_evil:
        cursor.execute("SELECT * FROM accounts ORDER BY is_evil ASC, username ASC")
    else:
        cursor.execute("SELECT * FROM accounts WHERE is_evil = 0 ORDER BY username ASC")
    rows = [dict(r) for r in cursor.fetchall()]
    conn.close()
    return rows

def log_transaction(record: dict):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO transactions (
            id, sender_id, receiver_id, transaction_type, amount,
            sender_balance, receiver_balance, status, risk_score,
            risk_level, reasons, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        record["id"],
        record["sender_id"],
        record["receiver_id"],
        record["transaction_type"],
        record["amount"],
        record["sender_current_balance"],
        record["receiver_current_balance"],
        record["status"],
        record["risk_score"],
        record["risk_level"],
        json.dumps(record["reasons"]),
        record.get("timestamp") or datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    ))
    conn.commit()
    conn.close()

def get_recent_transactions(limit: int = 50, status_filter: str = None) -> list[dict]:
    conn = get_connection()
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    if status_filter and status_filter.upper() != "ALL":
        cursor.execute("SELECT * FROM transactions WHERE status = ? ORDER BY created_at DESC LIMIT ?", (status_filter.upper(), limit))
    else:
        cursor.execute("SELECT * FROM transactions ORDER BY created_at DESC LIMIT ?", (limit,))
        
    rows = [dict(row) for row in cursor.fetchall()]
    for r in rows:
        try:
            r["reasons"] = json.loads(r["reasons"])
        except Exception:
            r["reasons"] = []
    conn.close()
    return rows

def get_dashboard_metrics() -> dict:
    conn = get_connection()
    cursor = conn.cursor()
    
    cursor.execute("SELECT COUNT(*) FROM transactions")
    total_txns = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM transactions WHERE status = 'BLOCKED'")
    blocked_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM transactions WHERE status = 'FLAGGED'")
    flagged_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM transactions WHERE status = 'APPROVED'")
    approved_count = cursor.fetchone()[0]

    cursor.execute("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE status = 'BLOCKED'")
    fraud_amount_prevented = cursor.fetchone()[0]

    cursor.execute("SELECT COALESCE(SUM(amount), 0) FROM transactions")
    total_volume = cursor.fetchone()[0]

    conn.close()
    return {
        "total_transactions": total_txns,
        "approved_count": approved_count,
        "flagged_count": flagged_count,
        "blocked_count": blocked_count,
        "fraud_prevention_rate_pct": round((blocked_count / total_txns * 100), 2) if total_txns > 0 else 0.0,
        "total_volume_inr": total_volume,
        "fraud_amount_saved_inr": fraud_amount_prevented
    }

init_db()
seed_default_accounts()
