import os
import sys
import sqlite3
import argparse
from typing import Optional, Dict, Any, List

DB_PATH = os.path.join(os.path.dirname(__file__), "fresh_database.sqlite")

def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS accounts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE NOT NULL,
        full_name TEXT NOT NULL,
        email TEXT UNIQUE NOT NULL,
        upi_id TEXT UNIQUE NOT NULL,
        pin TEXT NOT NULL,
        balance REAL NOT NULL DEFAULT 0.0,
        is_evil INTEGER NOT NULL DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS transactions (
        id TEXT PRIMARY KEY,
        sender_id TEXT NOT NULL,
        receiver_id TEXT NOT NULL,
        transaction_type TEXT NOT NULL,
        amount REAL NOT NULL,
        sender_current_balance REAL NOT NULL,
        status TEXT NOT NULL,
        risk_score REAL NOT NULL,
        risk_level TEXT NOT NULL,
        supervised_ml_prob REAL NOT NULL,
        anomaly_detected INTEGER NOT NULL,
        reasons TEXT,
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS transaction_history (
        id TEXT PRIMARY KEY,
        sender_id TEXT NOT NULL,
        receiver_id TEXT NOT NULL,
        amount REAL NOT NULL,
        type TEXT NOT NULL,
        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        device_id TEXT,
        decision TEXT NOT NULL,
        risk_score REAL NOT NULL,
        model_version TEXT NOT NULL,
        was_confirmed_fraud INTEGER DEFAULT NULL
    )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_txn_hist_sender_time ON transaction_history (sender_id, timestamp)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_txn_hist_receiver_time ON transaction_history (receiver_id, timestamp)")

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS pin_attempts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        account_id TEXT NOT NULL,
        success INTEGER NOT NULL,
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_pin_attempts_acc_time ON pin_attempts (account_id, timestamp)")
    conn.commit()
    conn.close()

# Auto-initialize fresh database schema upon first import
init_db()

def create_account(username: str, full_name: str, upi_id: str, pin: str, balance: float = 10000.0, is_evil: int = 0, email: Optional[str] = None) -> bool:
    email = email or f"{username.lower()}@nexuspay.local"
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("""
        INSERT INTO accounts (username, full_name, email, upi_id, pin, balance, is_evil)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (username, full_name, email, upi_id, pin, balance, is_evil))
        conn.commit()
        return True
    except sqlite3.IntegrityError as e:
        print(f"Error: Account with username '{username}' or UPI '{upi_id}' already exists.")
        return False
    finally:
        conn.close()

def get_account(identifier: str) -> Optional[Dict[str, Any]]:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    SELECT * FROM accounts
    WHERE username = ? OR upi_id = ? OR email = ?
    """, (identifier, identifier, identifier))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None

def list_accounts() -> List[Dict[str, Any]]:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id, username, full_name, upi_id, balance, is_evil, created_at FROM accounts ORDER BY id ASC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

def update_balance(identifier: str, delta: float) -> Optional[float]:
    conn = get_connection()
    cursor = conn.cursor()
    acc = get_account(identifier)
    if not acc:
        conn.close()
        return None
    new_bal = max(0.0, acc["balance"] + delta)
    cursor.execute("UPDATE accounts SET balance = ? WHERE id = ?", (new_bal, acc["id"]))
    conn.commit()
    conn.close()
    return new_bal

def update_pin(identifier: str, new_pin: str) -> bool:
    conn = get_connection()
    cursor = conn.cursor()
    acc = get_account(identifier)
    if not acc:
        conn.close()
        return False
    cursor.execute("UPDATE accounts SET pin = ? WHERE id = ?", (new_pin, acc["id"]))
    conn.commit()
    conn.close()
    return True

def delete_account(identifier: str) -> bool:
    conn = get_connection()
    cursor = conn.cursor()
    acc = get_account(identifier)
    if not acc:
        conn.close()
        return False
    cursor.execute("DELETE FROM accounts WHERE id = ?", (acc["id"],))
    conn.commit()
    conn.close()
    return True

def record_pin_attempt(account_id: str, success: bool):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    INSERT INTO pin_attempts (account_id, success)
    VALUES (?, ?)
    """, (account_id, 1 if success else 0))
    conn.commit()
    conn.close()

def record_transaction(log_data: dict):
    conn = get_connection()
    cursor = conn.cursor()
    import json
    cursor.execute("""
    INSERT INTO transactions (id, sender_id, receiver_id, transaction_type, amount, sender_current_balance, status, risk_score, risk_level, supervised_ml_prob, anomaly_detected, reasons)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        log_data["id"],
        log_data["sender_id"],
        log_data["receiver_id"],
        log_data["transaction_type"],
        log_data["amount"],
        log_data["sender_current_balance"],
        log_data["status"],
        log_data["risk_score"],
        log_data["risk_level"],
        log_data["supervised_ml_prob"],
        1 if log_data["anomaly_detected"] else 0,
        json.dumps(log_data.get("reasons", []))
    ))
    # Also write to transaction_history
    cursor.execute("""
    INSERT INTO transaction_history (id, sender_id, receiver_id, amount, type, timestamp, device_id, decision, risk_score, model_version, was_confirmed_fraud)
    VALUES (?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?, ?, ?, ?, ?)
    """, (
        log_data["id"],
        log_data["sender_id"],
        log_data["receiver_id"],
        log_data["amount"],
        log_data["transaction_type"],
        log_data.get("timestamp"),
        log_data.get("device_id"),
        log_data["status"],
        log_data["risk_score"],
        log_data.get("model_version", "v1"),
        log_data.get("was_confirmed_fraud")
    ))
    conn.commit()
    conn.close()

# CLI Entrypoint for account commands
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fresh Server Account & CLI Manager")
    subparsers = parser.add_subparsers(dest="command", help="Sub-commands")

    # Create account
    p_create = subparsers.add_parser("create", help="Create a brand new account")
    p_create.add_argument("-u", "--username", required=True, help="Username")
    p_create.add_argument("-n", "--name", required=True, help="Full name")
    p_create.add_argument("--upi", default=None, help="UPI ID (defaults to <username>@testbank)")
    p_create.add_argument("-p", "--pin", default="1234", help="Bank/UPI PIN")
    p_create.add_argument("-b", "--balance", type=float, default=10000.0, help="Initial balance in INR")
    p_create.add_argument("-e", "--evil", action="store_true", help="Flag account as evil / blacklisted mule")

    # Check Balance
    p_bal = subparsers.add_parser("balance", help="Check account balance")
    p_bal.add_argument("-u", "--user", required=True, help="Username or UPI ID")

    # Add Balance
    p_add = subparsers.add_parser("add-balance", help="Deposit funds into account")
    p_add.add_argument("-u", "--user", required=True, help="Username or UPI ID")
    p_add.add_argument("-a", "--amount", type=float, required=True, help="Amount to add")

    # Deduct Balance
    p_ded = subparsers.add_parser("deduct-balance", help="Deduct funds from account")
    p_ded.add_argument("-u", "--user", required=True, help="Username or UPI ID")
    p_ded.add_argument("-a", "--amount", type=float, required=True, help="Amount to deduct")

    # Set PIN
    p_pin = subparsers.add_parser("set-pin", help="Change account UPI PIN")
    p_pin.add_argument("-u", "--user", required=True, help="Username or UPI ID")
    p_pin.add_argument("-p", "--pin", required=True, help="New PIN")

    # List Accounts
    p_list = subparsers.add_parser("list", help="List all accounts in this local database")

    # Delete Account
    p_del = subparsers.add_parser("delete", help="Delete an account")
    p_del.add_argument("-u", "--user", required=True, help="Username or UPI ID")

    args = parser.parse_args()

    if args.command == "create":
        upi = args.upi or f"{args.username.lower()}@testbank"
        is_evil = 1 if args.evil else 0
        success = create_account(args.username, args.name, upi, args.pin, args.balance, is_evil)
        if success:
            tag = "[BLACKLISTED MULE]" if is_evil else "[LEGITIMATE]"
            print(f"[OK] Created {tag} account '{args.username}' (UPI: {upi}, Balance: INR {args.balance:,.2f})")

    elif args.command == "balance":
        acc = get_account(args.user)
        if acc:
            print(f"Account: {acc['full_name']} ({acc['upi_id']})")
            print(f"Current Balance: INR {acc['balance']:,.2f}")
            print(f"Status: {'[BLACKLISTED MULE]' if acc['is_evil'] else '[ACTIVE]'}")
        else:
            print(f"Error: Account '{args.user}' not found.")

    elif args.command == "add-balance":
        new_b = update_balance(args.user, args.amount)
        if new_b is not None:
            print(f"[OK] Credited INR {args.amount:,.2f}. New Balance: INR {new_b:,.2f}")
        else:
            print(f"Error: Account '{args.user}' not found.")

    elif args.command == "deduct-balance":
        new_b = update_balance(args.user, -args.amount)
        if new_b is not None:
            print(f"[OK] Deducted INR {args.amount:,.2f}. New Balance: INR {new_b:,.2f}")
        else:
            print(f"Error: Account '{args.user}' not found.")

    elif args.command == "set-pin":
        success = update_pin(args.user, args.pin)
        if success:
            print(f"[OK] PIN updated successfully for '{args.user}'.")
        else:
            print(f"Error: Account '{args.user}' not found.")

    elif args.command == "list":
        accs = list_accounts()
        if not accs:
            print("No accounts found. Create one with: python server_quickstart/local_manager.py create -u testuser -n 'Test User'")
        else:
            print(f"{'Username':<15} {'Full Name':<20} {'UPI ID':<25} {'Balance (INR)':<15} {'Status'}")
            print("-" * 85)
            for a in accs:
                st = "MULE" if a["is_evil"] else "ACTIVE"
                print(f"{a['username']:<15} {a['full_name']:<20} {a['upi_id']:<25} {a['balance']:<15,.2f} {st}")

    elif args.command == "delete":
        success = delete_account(args.user)
        if success:
            print(f"[OK] Account '{args.user}' deleted.")
        else:
            print(f"Error: Account '{args.user}' not found.")
    else:
        parser.print_help()
