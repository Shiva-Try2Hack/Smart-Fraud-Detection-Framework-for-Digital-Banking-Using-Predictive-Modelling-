"""
CLI Account, Balance & Transaction Control Tool.
Allows:
- Executing transactions with CLI arguments (sender, receiver, amount, pin, optional timestamp)
- Checking account balances
- Adding or deducting balance
- Creating accounts & deleting accounts
- Listing all accounts
"""
import argparse
import sys
import requests
from datetime import datetime
from src.db.database import (
    create_or_update_account,
    delete_account,
    change_password_pin,
    adjust_balance,
    get_account,
    list_all_accounts,
    seed_default_accounts
)

API_BASE = "http://127.0.0.1:8000"

def print_banner():
    print("=" * 85)
    print("      BANKING FRAUD DETECTION - COMMAND LINE CONTROLLER & CLIENT")
    print("=" * 85)

def list_users_cmd():
    print_banner()
    accounts = list_all_accounts()
    print(f"{'TYPE':<8} {'USERNAME':<15} {'FULL NAME':<20} {'UPI ID':<26} {'PIN':<6} {'BALANCE (INR)':<12}")
    print("-" * 92)
    for a in accounts:
        tag = "[EVIL]" if a["is_evil"] else "[USER]"
        print(f"{tag:<8} {a['username']:<15} {a['full_name']:<20} {a['upi_id']:<26} {a['pin']:<6} INR {a['balance']:,.2f}")
    print("-" * 92)
    print(f"Total Accounts: {len(accounts)}\n")

def check_balance_cmd(identifier: str):
    acc = get_account(identifier)
    if not acc:
        print(f"\n[-] Account '{identifier}' not found in database!\n")
        return
    tag = "[EVIL DUMMY]" if acc["is_evil"] else "[LEGITIMATE USER]"
    print("\n" + "=" * 50)
    print(f"  ACCOUNT BALANCE SUMMARY")
    print("=" * 50)
    print(f"  User:         {acc['full_name']} ({acc['username']})")
    print(f"  UPI ID:       {acc['upi_id']}")
    print(f"  Classification: {tag}")
    print(f"  Available:    INR {acc['balance']:,.2f}")
    print(f"  Default PIN:  {acc['pin']}")
    print("=" * 50 + "\n")

def transfer_cmd(sender_upi: str, receiver_upi: str, amount: float, pin: str, timestamp_str: str = None):
    """Executes a payment via the backend server, outputting full details & timestamp."""
    print("\n" + "=" * 70)
    print("  SUBMITTING TRANSACTION TO FRAUD INFERENCE BACKEND")
    print("=" * 70)

    t_time = timestamp_str or datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    payload = {
        "sender_id": sender_upi,
        "receiver_id": receiver_upi,
        "transaction_type": "TRANSFER",
        "amount": amount,
        "pin": pin,
        "timestamp": t_time
    }

    try:
        res = requests.post(f"{API_BASE}/api/payment/process", json=payload, timeout=3)
        if res.status_code == 200:
            data = res.json()
            status = data["status"]
            print(f"  Transaction ID:   {data['transaction_id']}")
            print(f"  Timestamp:        {data['timestamp']}")
            print(f"  Sender (From):    {data['sender_id']}")
            print(f"  Receiver (To):    {data['receiver_id']}")
            print(f"  Transfer Amount:  INR {data['amount']:,.2f}")
            print(f"  Evaluation Risk:  {data['risk_score']*100:.1f}% ({data['risk_level']})")
            print(f"  DECISION STATUS:  >>> {status} <<<")
            if status == "APPROVED":
                print(f"  Sender Balance:   INR {data.get('sender_new_balance', 0):,.2f}")
                print(f"  Receiver Balance: INR {data.get('receiver_new_balance', 0):,.2f}")
            print("\n  Decision Reasons:")
            for r in data.get("reasons", []):
                print(f"    * {r}")
            print("=" * 70 + "\n")
        else:
            print(f"[-] Server returned error code {res.status_code}: {res.text}\n")
    except requests.exceptions.ConnectionError:
        print("[-] Cannot connect to backend server. Make sure 'python run_server.py' is running!\n")

def delete_user_cmd(identifier: str):
    success = delete_account(identifier)
    if success:
        print(f"\n[+] Account '{identifier}' successfully deleted from database!\n")
    else:
        print(f"\n[-] Account '{identifier}' not found!\n")

def create_user_cmd(username, full_name, upi_id, pin, balance, is_evil):
    if not upi_id:
        clean_user = username.lower().replace(" ", "")
        upi_id = f"evil.{clean_user}@shadowbank" if is_evil else f"{clean_user}@okbank"
    create_or_update_account(
        username=username,
        full_name=full_name,
        upi_id=upi_id,
        pin=pin,
        initial_balance=balance,
        is_evil=1 if is_evil else 0
    )
    print(f"\n[+] Account successfully registered!")
    print(f"    Username: {username}")
    print(f"    Full Name: {full_name}")
    print(f"    UPI ID: {upi_id}")
    print(f"    PIN: {pin}")
    print(f"    Balance: INR {balance:,.2f}")
    print(f"    Type: {'EVIL DUMMY' if is_evil else 'LEGITIMATE USER'}\n")

def set_pin_cmd(identifier, new_pin):
    success = change_password_pin(identifier, new_pin)
    if success:
        print(f"\n[+] Password/PIN successfully changed to '{new_pin}' for account: {identifier}\n")
    else:
        print(f"\n[-] Account '{identifier}' not found!\n")

def add_balance_cmd(identifier, amount):
    success, new_bal = adjust_balance(identifier, amount)
    if success:
        print(f"\n[+] Credited INR {amount:,.2f} to {identifier}. New Balance: INR {new_bal:,.2f}\n")
    else:
        print(f"\n[-] Account '{identifier}' not found!\n")

def deduct_balance_cmd(identifier, amount):
    success, new_bal = adjust_balance(identifier, -amount)
    if success:
        print(f"\n[-] Debited INR {amount:,.2f} from {identifier}. New Balance: INR {new_bal:,.2f}\n")
    else:
        print(f"\n[-] Account '{identifier}' not found!\n")

def main():
    seed_default_accounts()
    parser = argparse.ArgumentParser(description="Bank Account, Balance & Transaction CLI Controller")
    subparsers = parser.add_subparsers(dest="command", help="Available subcommands")

    # 1. pay / transfer
    pay_p = subparsers.add_parser("pay", help="Perform a simulated transfer transaction")
    pay_p.add_argument("-s", "--sender", required=True, help="Sender UPI ID (e.g. shiva@okaxis)")
    pay_p.add_argument("-r", "--receiver", required=True, help="Receiver UPI ID (e.g. prasad@oksbi)")
    pay_p.add_argument("-a", "--amount", type=float, required=True, help="Amount in INR")
    pay_p.add_argument("-p", "--pin", required=True, help="Sender UPI PIN")
    pay_p.add_argument("-t", "--time", default=None, help="Custom timestamp (e.g. '2026-10-05 03:30:00')")

    # 2. balance
    bal_p = subparsers.add_parser("balance", help="Check bank balance of any account")
    bal_p.add_argument("-u", "--user", required=True, help="Username or UPI ID (e.g. shiva or shiva@okaxis)")

    # 3. list
    subparsers.add_parser("list", help="List all accounts with balances & UPI IDs")

    # 4. add-balance
    add_p = subparsers.add_parser("add-balance", help="Deposit money into an account")
    add_p.add_argument("-u", "--user", required=True, help="Username or UPI ID")
    add_p.add_argument("-a", "--amount", type=float, required=True, help="Amount in INR")

    # 5. deduct-balance
    deduct_p = subparsers.add_parser("deduct-balance", help="Withdraw money from an account")
    deduct_p.add_argument("-u", "--user", required=True, help="Username or UPI ID")
    deduct_p.add_argument("-a", "--amount", type=float, required=True, help="Amount in INR")

    # 6. create
    create_p = subparsers.add_parser("create", help="Create a new user or evil dummy account")
    create_p.add_argument("-u", "--username", required=True, help="Username (e.g. aniket)")
    create_p.add_argument("-n", "--name", required=True, help="Full Name")
    create_p.add_argument("--upi", default="", help="Custom UPI ID (optional)")
    create_p.add_argument("-p", "--pin", default="1234", help="Account UPI PIN")
    create_p.add_argument("-b", "--balance", type=float, default=50000.0, help="Initial balance in INR")
    create_p.add_argument("-e", "--evil", action="store_true", help="Flag as dummy evil account")

    # 7. delete
    del_p = subparsers.add_parser("delete", help="Delete an account from the database")
    del_p.add_argument("-u", "--user", required=True, help="Username or UPI ID to delete")

    # 8. set-pin
    pin_p = subparsers.add_parser("set-pin", help="Change account password / UPI PIN")
    pin_p.add_argument("-u", "--user", required=True, help="Username or UPI ID")
    pin_p.add_argument("-p", "--pin", required=True, help="New PIN")

    args = parser.parse_args()

    if args.command == "pay":
        transfer_cmd(args.sender, args.receiver, args.amount, args.pin, args.time)
    elif args.command == "balance":
        check_balance_cmd(args.user)
    elif args.command == "list" or not args.command:
        list_users_cmd()
    elif args.command == "add-balance":
        add_balance_cmd(args.user, args.amount)
    elif args.command == "deduct-balance":
        deduct_balance_cmd(args.user, args.amount)
    elif args.command == "create":
        create_user_cmd(args.username, args.name, args.upi, args.pin, args.balance, args.evil)
    elif args.command == "delete":
        delete_user_cmd(args.user)
    elif args.command == "set-pin":
        set_pin_cmd(args.user, args.pin)

if __name__ == "__main__":
    main()
