"""
UNIFIED ADMIN CONTROL CONSOLE & LIVE MONITOR.
Combines:
1. Interactive Command Input:
   - pay: execute transactions directly (with -s, -r, -a, -p, -t)
   - bal: check user balance
   - list: show all accounts and balances
   - add: credit money to account
   - deduct: debit money from account
   - pin: change account password / PIN
   - user: create new user or evil mule dummy
   - del: delete user
   - help / clear / exit
2. Background Live Surveillance Feed:
   - Streams newly intercepted transactions in real-time
   - Blinking yellow [ ! ] indicators
   - Bold GREEN for APPROVED, bold RED for BLOCKED
"""
import threading
import time
import requests
import os
import sys
import shlex
from datetime import datetime

# Enable ANSI colors for Windows terminal
os.system("")

# ANSI Color Codes
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_GREEN = "\033[92m"
C_RED = "\033[91m"
C_YELLOW = "\033[93m"
C_CYAN = "\033[96m"
C_WHITE = "\033[97m"
C_BLINK = "\033[5m"
C_DIM = "\033[90m"

API_BASE = "http://127.0.0.1:8000"
seen_ids = set()
running = True

def get_badge(status: str) -> str:
    yellow_excl = f"{C_BLINK}{C_YELLOW}[ ! ]{C_RESET}"
    if status == "APPROVED":
        return f"{yellow_excl}  {C_BOLD}{C_GREEN}APPROVED{C_RESET}  {yellow_excl}"
    elif status == "BLOCKED":
        return f"{yellow_excl}  {C_BOLD}{C_RED}BLOCKED{C_RESET}  {yellow_excl}"
    else:
        return f"{yellow_excl}  {C_BOLD}{C_YELLOW}FLAGGED{C_RESET}  {yellow_excl}"

def print_transaction_event(txn: dict):
    status = txn.get("status", "UNKNOWN")
    txn_id = txn.get("id", "N/A")
    sender = txn.get("sender_id", "N/A")
    receiver = txn.get("receiver_id", "N/A")
    amount = float(txn.get("amount", 0.0))
    sender_bal = float(txn.get("sender_balance", 0.0))
    receiver_bal = float(txn.get("receiver_balance", 0.0))
    risk_score = float(txn.get("risk_score", 0.0))
    risk_level = txn.get("risk_level", "N/A")
    timestamp = txn.get("created_at", "")
    reasons = txn.get("reasons", [])

    badge = get_badge(status)
    color = C_GREEN if status == "APPROVED" else (C_RED if status == "BLOCKED" else C_YELLOW)

    print(f"\n{C_CYAN}{'─'*85}{C_RESET}")
    print(f"{C_WHITE}[LIVE ALERT {timestamp}] {C_DIM}|{C_RESET} TXN ID: {C_BOLD}{txn_id}{C_RESET} ==> {badge}")
    print(f"{C_CYAN}{'─'*85}{C_RESET}")
    print(f"  {C_BOLD}FROM (Sender):{C_RESET}   {C_WHITE}{sender}{C_RESET}  (Sender Bal: INR {sender_bal:,.2f})")
    print(f"  {C_BOLD}TO   (Receiver):{C_RESET} {C_WHITE}{receiver}{C_RESET}  (Receiver Bal: INR {receiver_bal:,.2f})")
    print(f"  {C_BOLD}AMOUNT:{C_RESET}          {C_YELLOW}INR {amount:,.2f}{C_RESET}")
    print(f"  {C_BOLD}RISK:{C_RESET}            Score: {C_WHITE}{risk_score*100:.1f}%{C_RESET} | Level: {C_WHITE}{risk_level}{C_RESET}")
    print(f"  {color}AUDIT REASON(S):{C_RESET}")
    if reasons:
        for r in reasons:
            print(f"    {color}► {r}{C_RESET}")
    else:
        print(f"    {color}► Standard legitimate transaction within normal behavioral limits.{C_RESET}")
    print(f"{C_CYAN}{'─'*85}{C_RESET}")
    print(f"{C_YELLOW}admin>{C_RESET} ", end="", flush=True)

def monitor_worker():
    """Background thread continuously polling for live transactions."""
    global seen_ids, running
    
    # Pre-mark past transactions as seen
    try:
        r = requests.get(f"{API_BASE}/api/admin/transactions?limit=500", timeout=2)
        if r.status_code == 200:
            for t in r.json():
                seen_ids.add(t["id"])
    except Exception:
        pass

    while running:
        try:
            time.sleep(1.0)
            r = requests.get(f"{API_BASE}/api/admin/transactions?limit=10", timeout=2)
            if r.status_code == 200:
                txns = r.json()
                for t in reversed(txns):
                    t_id = t["id"]
                    if t_id not in seen_ids:
                        seen_ids.add(t_id)
                        print_transaction_event(t)
        except Exception:
            pass

def print_banner():
    print(f"{C_CYAN}{'='*85}{C_RESET}")
    print(f"{C_BOLD}{C_WHITE}    ADMIN SURVEILLANCE & COMMAND CONTROLLER - INTERACTIVE SHELL{C_RESET}")
    print(f"{C_CYAN}{'='*85}{C_RESET}")
    print(f"Type {C_BOLD}'help'{C_RESET} for commands or execute payments/balance operations below.")
    print(f"{C_DIM}Live transaction intercept alerts will appear right in this window!{C_RESET}\n")

def print_help():
    print(f"\n{C_BOLD}AVAILABLE ADMIN COMMANDS:{C_RESET}")
    print(f"  {C_CYAN}pay -s <sender> -r <receiver> -a <amt> -p <pin> [-t <time>]{C_RESET}")
    print(f"      Execute a simulated transfer (e.g: pay -s shiva@okaxis -r prasad@oksbi -a 2000 -p 1234)")
    print(f"  {C_CYAN}bal <user/upi>{C_RESET}             Check current account balance")
    print(f"  {C_CYAN}list{C_RESET}                      List all user & dummy accounts")
    print(f"  {C_CYAN}add <user> <amount>{C_RESET}       Deposit / credit INR to account")
    print(f"  {C_CYAN}deduct <user> <amount>{C_RESET}    Withdraw / debit INR from account")
    print(f"  {C_CYAN}pin <user> <new_pin>{C_RESET}      Change UPI PIN for an account")
    print(f"  {C_CYAN}user <username> <name> <pin> <bal> [--evil]{C_RESET}  Create new user or mule account")
    print(f"  {C_CYAN}del <user>{C_RESET}                Delete an account")
    print(f"  {C_CYAN}stats{C_RESET}                     Show overall banking & fraud KPIs")
    print(f"  {C_CYAN}clear{C_RESET}                     Clear the terminal screen")
    print(f"  {C_CYAN}exit / quit{C_RESET}               Exit the admin console\n")

def execute_pay_cmd(args):
    # Parse short flags
    import argparse
    parser = argparse.ArgumentParser(prog="pay", add_help=False)
    parser.add_argument("-s", "--sender", required=True)
    parser.add_argument("-r", "--receiver", required=True)
    parser.add_argument("-a", "--amount", type=float, required=True)
    parser.add_argument("-p", "--pin", required=True)
    parser.add_argument("-t", "--time", default=None)
    try:
        p_args = parser.parse_args(args)
    except Exception as e:
        print(f"{C_RED}[!] Invalid arguments. Usage: pay -s <sender> -r <receiver> -a <amt> -p <pin> [-t <time>]{C_RESET}")
        return

    payload = {
        "sender_id": p_args.sender,
        "receiver_id": p_args.receiver,
        "transaction_type": "TRANSFER",
        "amount": p_args.amount,
        "pin": p_args.pin,
        "timestamp": p_args.time or datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }

    try:
        res = requests.post(f"{API_BASE}/api/payment/process", json=payload, timeout=3)
        if res.status_code == 200:
            d = res.json()
            st = d["status"]
            color = C_GREEN if st == "APPROVED" else C_RED
            print(f"{color}[>] Decision: {st} | TXN: {d['transaction_id']} | Risk: {d['risk_score']*100:.1f}%{C_RESET}")
            if st == "APPROVED":
                print(f"    New Balance ({p_args.sender}): INR {d.get('sender_new_balance', 0):,.2f}")
        else:
            print(f"{C_RED}[!] Server error: {res.text}{C_RESET}")
    except Exception as e:
        print(f"{C_RED}[!] Cannot connect to backend server ({API_BASE}).{C_RESET}")

def authenticate_admin() -> bool:
    print(f"\n{C_CYAN}{'='*85}{C_RESET}")
    print(f"{C_BOLD}{C_WHITE}          ADMIN SURVEILLANCE & COMMAND CONTROLLER - SECURITY GATE{C_RESET}")
    print(f"{C_CYAN}{'='*85}{C_RESET}")
    print(f"{C_YELLOW}[!] Access Restricted to Authorized Administrators Only.{C_RESET}\n")

    max_attempts = 3
    for attempt in range(1, max_attempts + 1):
        try:
            username = input(f"{C_BOLD}Enter Admin Username:{C_RESET} ").strip()
            import getpass
            try:
                # Masked input if supported by console
                password = getpass.getpass(prompt=f"{C_BOLD}Enter Admin Password:{C_RESET} ").strip()
            except Exception:
                password = input(f"{C_BOLD}Enter Admin Password:{C_RESET} ").strip()

            if username == "admin" and password == "thisismypassword":
                print(f"\n{C_GREEN}[+] Authentication Successful! Granting Admin Privileges...{C_RESET}\n")
                time.sleep(0.8)
                return True
            else:
                remaining = max_attempts - attempt
                print(f"{C_RED}[-] Invalid credentials! {remaining} attempt(s) remaining.{C_RESET}\n")
        except (KeyboardInterrupt, EOFError):
            print(f"\n{C_YELLOW}Login cancelled.{C_RESET}")
            sys.exit(0)

    print(f"{C_RED}[!] Maximum login attempts exceeded. Access Denied.{C_RESET}")
    sys.exit(1)

def main():
    global running
    # Authenticate admin before opening console
    authenticate_admin()

    print_banner()

    # Start live surveillance thread in background
    t = threading.Thread(target=monitor_worker, daemon=True)
    t.start()

    # Import database module for direct fast operations
    from src.db.database import (
        get_account,
        list_all_accounts,
        adjust_balance,
        change_password_pin,
        create_or_update_account,
        delete_account,
        get_dashboard_metrics
    )

    while running:
        try:
            cmd_line = input(f"{C_YELLOW}admin>{C_RESET} ").strip()
            if not cmd_line:
                continue

            parts = shlex.split(cmd_line)
            cmd = parts[0].lower()
            args = parts[1:]

            if cmd in ["exit", "quit", "q"]:
                running = False
                print(f"{C_YELLOW}Exiting Admin Console...{C_RESET}")
                sys.exit(0)

            elif cmd == "help":
                print_help()

            elif cmd == "clear":
                os.system("cls" if os.name == "nt" else "clear")
                print_banner()

            elif cmd == "pay":
                execute_pay_cmd(args)

            elif cmd in ["bal", "balance"]:
                if not args:
                    print(f"{C_RED}Usage: bal <username or upi_id>{C_RESET}")
                    continue
                acc = get_account(args[0])
                if acc:
                    tag = f"{C_RED}[EVIL]{C_RESET}" if acc["is_evil"] else f"{C_GREEN}[USER]{C_RESET}"
                    print(f"  {tag} {acc['full_name']} ({acc['upi_id']}) | PIN: {acc['pin']} | Balance: {C_BOLD}INR {acc['balance']:,.2f}{C_RESET}")
                else:
                    print(f"{C_RED}[!] Account '{args[0]}' not found!{C_RESET}")

            elif cmd == "list":
                accounts = list_all_accounts()
                print(f"\n{'TYPE':<8} {'USERNAME':<14} {'FULL NAME':<20} {'UPI ID':<26} {'PIN':<6} {'BALANCE':<12}")
                print("-" * 90)
                for a in accounts:
                    tag = f"{C_RED}[EVIL]{C_RESET}" if a["is_evil"] else f"{C_GREEN}[USER]{C_RESET}"
                    print(f"{tag:<8} {a['username']:<14} {a['full_name']:<20} {a['upi_id']:<26} {a['pin']:<6} INR {a['balance']:,.2f}")
                print("-" * 90)
                print(f"Total Accounts: {len(accounts)}\n")

            elif cmd == "add":
                if len(args) < 2:
                    print(f"{C_RED}Usage: add <user> <amount>{C_RESET}")
                    continue
                ok, nbal = adjust_balance(args[0], float(args[1]))
                if ok:
                    print(f"{C_GREEN}[+] Credited INR {float(args[1]):,.2f} to {args[0]}. New Balance: INR {nbal:,.2f}{C_RESET}")
                else:
                    print(f"{C_RED}[!] Account '{args[0]}' not found!{C_RESET}")

            elif cmd == "deduct":
                if len(args) < 2:
                    print(f"{C_RED}Usage: deduct <user> <amount>{C_RESET}")
                    continue
                ok, nbal = adjust_balance(args[0], -float(args[1]))
                if ok:
                    print(f"{C_YELLOW}[-] Debited INR {float(args[1]):,.2f} from {args[0]}. New Balance: INR {nbal:,.2f}{C_RESET}")
                else:
                    print(f"{C_RED}[!] Account '{args[0]}' not found!{C_RESET}")

            elif cmd == "pin":
                if len(args) < 2:
                    print(f"{C_RED}Usage: pin <user> <new_pin>{C_RESET}")
                    continue
                ok = change_password_pin(args[0], args[1])
                if ok:
                    print(f"{C_GREEN}[+] PIN successfully changed to '{args[1]}' for {args[0]}.{C_RESET}")
                else:
                    print(f"{C_RED}[!] Account '{args[0]}' not found!{C_RESET}")

            elif cmd == "user":
                if len(args) < 4:
                    print(f"{C_RED}Usage: user <username> <full_name> <pin> <balance> [--evil]{C_RESET}")
                    continue
                u_name = args[0]
                f_name = args[1]
                u_pin = args[2]
                u_bal = float(args[3])
                is_evil = 1 if len(args) >= 5 and args[4] == "--evil" else 0
                upi_id = f"evil.{u_name.lower()}@darkpay" if is_evil else f"{u_name.lower()}@okbank"
                create_or_update_account(u_name, f_name, upi_id, u_pin, u_bal, is_evil)
                print(f"{C_GREEN}[+] Account '{u_name}' created with UPI '{upi_id}' and balance INR {u_bal:,.2f}!{C_RESET}")

            elif cmd in ["del", "delete"]:
                if not args:
                    print(f"{C_RED}Usage: del <user>{C_RESET}")
                    continue
                ok = delete_account(args[0])
                if ok:
                    print(f"{C_GREEN}[+] Account '{args[0]}' deleted successfully.{C_RESET}")
                else:
                    print(f"{C_RED}[!] Account '{args[0]}' not found!{C_RESET}")

            elif cmd == "stats":
                s = get_dashboard_metrics()
                print(f"\n{C_BOLD}BANKING FRAUD STATISTICS:{C_RESET}")
                print(f"  Total Processed:  {s['total_transactions']}")
                print(f"  Approved Count:   {C_GREEN}{s['approved_count']}{C_RESET}")
                print(f"  Blocked Frauds:   {C_RED}{s['blocked_count']}{C_RESET} ({s['fraud_prevention_rate_pct']}%)")
                print(f"  Volume Processed: INR {s['total_volume_inr']:,.2f}")
                print(f"  Fraud Saved:      {C_GREEN}INR {s['fraud_amount_saved_inr']:,.2f}{C_RESET}\n")

            else:
                print(f"{C_RED}[!] Unknown command '{cmd}'. Type 'help' for instructions.{C_RESET}")

        except KeyboardInterrupt:
            running = False
            print(f"\n{C_YELLOW}Admin Console stopped.{C_RESET}")
            sys.exit(0)
        except Exception as e:
            print(f"{C_RED}[!] Error: {str(e)}{C_RESET}")

if __name__ == "__main__":
    main()
