"""
Clean Dark/Graphite Server Controller & Admin Surveillance Desktop Application.
Unified single-window application with:
1. In-Window Admin Authentication Screen with configurable credentials.
2. Clean, elegant dark-neutral palette (slate/graphite, no harsh blue).
3. Server Start / Stop controls.
4. Log Display:
   - Base log text in crisp WHITE.
   - APPROVED highlighted in BOLD GREEN.
   - BLOCKED highlighted in BOLD RED.
   - PENDING highlighted in BOLD YELLOW / ORANGE.
5. Server Stopped Handling:
   - If the server is stopped when running 'pay', it immediately detects that the server is offline.
   - Returns an informative error in the admin console.
   - Logs the transaction as [PENDING - SERVER OFFLINE] in database and surveillance stream.
   - Does NOT hang or freeze.
6. Admin Command Console supporting pay, bal, list, add, deduct, pin, user, del, stats, clear, help.
"""
import sys
import os
import subprocess
import threading
import queue
import time
import shlex
import argparse
import requests
import uuid
import re
from datetime import datetime

import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext

API_BASE = "http://127.0.0.1:8000"

# Admin credentials loaded securely from environment or local defaults
ADMIN_USER = os.getenv("ADMIN_USER", "admin")
ADMIN_PASS = os.getenv("ADMIN_PASS", "thisismypassword")


class ServerAdminGUI(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Banking Fraud Detection - Server Controller & Admin Console")
        self.geometry("1180x760")
        self.minsize(980, 620)

        # Clean Dark Graphite Palette (Zinc / Neutral)
        self.c_bg = "#121214"         # Base background
        self.c_surface = "#18181b"    # Panels and header bars
        self.c_border = "#27272a"     # Borders and dividers
        self.c_input = "#1c1c1f"      # Console text area background
        self.c_text = "#ffffff"       # Pure white for main logs & text
        self.c_muted = "#a1a1aa"      # Secondary / muted labels
        self.c_green = "#22c55e"      # Success / Approved
        self.c_red = "#ef4444"        # Blocked / Danger
        self.c_yellow = "#eab308"     # Warning / Pending / Prompt
        self.c_silver = "#f4f4f5"

        self.configure(bg=self.c_bg)

        # Process and surveillance state
        self.server_process = None
        self.log_queue = queue.Queue()
        self.is_monitoring = True
        self.seen_txn_ids = set()

        # Build Login Frame first
        self._build_login_screen()

        # Window closing protocol
        self.protocol("WM_DELETE_WINDOW", self._on_close)

    # =========================================================================
    # 1. IN-WINDOW LOGIN SCREEN
    # =========================================================================
    def _build_login_screen(self):
        self.login_frame = tk.Frame(self, bg=self.c_bg)
        self.login_frame.pack(fill=tk.BOTH, expand=True)

        card = tk.Frame(
            self.login_frame,
            bg=self.c_surface,
            padx=32,
            pady=30,
            highlightthickness=1,
            highlightbackground=self.c_border
        )
        card.place(relx=0.5, rely=0.5, anchor=tk.CENTER, width=380, height=360)

        lbl_shield = tk.Label(
            card,
            text="🔒",
            font=("Segoe UI", 32),
            bg=self.c_surface,
            fg=self.c_silver
        )
        lbl_shield.pack(pady=(0, 4))

        lbl_title = tk.Label(
            card,
            text="Admin Security Gate",
            font=("Segoe UI", 14, "bold"),
            bg=self.c_surface,
            fg=self.c_silver
        )
        lbl_title.pack(pady=(0, 4))

        lbl_sub = tk.Label(
            card,
            text="Sign in to access server controller & surveillance",
            font=("Segoe UI", 9),
            bg=self.c_surface,
            fg=self.c_muted
        )
        lbl_sub.pack(pady=(0, 18))

        # Username Field
        lbl_u = tk.Label(card, text="Username", font=("Segoe UI", 9), bg=self.c_surface, fg=self.c_silver, anchor="w")
        lbl_u.pack(fill=tk.X)
        self.ent_user = tk.Entry(
            card,
            font=("Segoe UI", 10),
            bg=self.c_input,
            fg="#ffffff",
            insertbackground="white",
            relief=tk.FLAT,
            highlightthickness=1,
            highlightbackground=self.c_border,
            highlightcolor=self.c_muted
        )
        self.ent_user.pack(fill=tk.X, pady=(2, 10), ipady=4)
        self.ent_user.insert(0, "admin")

        # Password Field
        lbl_p = tk.Label(card, text="Password", font=("Segoe UI", 9), bg=self.c_surface, fg=self.c_silver, anchor="w")
        lbl_p.pack(fill=tk.X)
        self.ent_pass = tk.Entry(
            card,
            font=("Segoe UI", 10),
            bg=self.c_input,
            fg="#ffffff",
            show="●",
            insertbackground="white",
            relief=tk.FLAT,
            highlightthickness=1,
            highlightbackground=self.c_border,
            highlightcolor=self.c_muted
        )
        self.ent_pass.pack(fill=tk.X, pady=(2, 16), ipady=4)
        self.ent_pass.bind("<Return>", lambda e: self._attempt_login())
        self.ent_pass.focus_set()

        # Sign In Button
        btn_login = tk.Button(
            card,
            text="Sign In to Admin Panel",
            font=("Segoe UI", 10, "bold"),
            bg="#22c55e",
            fg="#ffffff",
            activebackground="#16a34a",
            activeforeground="#ffffff",
            relief=tk.FLAT,
            cursor="hand2",
            pady=6,
            command=self._attempt_login
        )
        btn_login.pack(fill=tk.X)

    def _attempt_login(self):
        u = self.ent_user.get().strip()
        p = self.ent_pass.get().strip()
        if u == ADMIN_USER and p == ADMIN_PASS:
            self.login_frame.destroy()
            self._init_main_dashboard()
        else:
            messagebox.showerror("Authentication Failed", "Invalid Admin credentials!\nPlease check your admin username and password.")
            self.ent_pass.delete(0, tk.END)
            self.ent_pass.focus_set()

    # =========================================================================
    # 2. MAIN DASHBOARD INITIALIZATION
    # =========================================================================
    def _init_main_dashboard(self):
        self._build_header()
        self._build_main_panels()
        self._build_status_bar()

        # Start periodic log checks
        self.after(100, self._process_log_queue)

        # Start background transaction watcher thread
        self.watcher_thread = threading.Thread(target=self._surveillance_loop, daemon=True)
        self.watcher_thread.start()

    def _build_header(self):
        header_frame = tk.Frame(self, bg=self.c_surface, pady=10, padx=16, highlightthickness=1, highlightbackground=self.c_border)
        header_frame.pack(fill=tk.X, side=tk.TOP)

        title_label = tk.Label(
            header_frame,
            text="Fraud Detection Framework — Server & Surveillance Console",
            font=("Segoe UI", 12, "bold"),
            bg=self.c_surface,
            fg=self.c_silver
        )
        title_label.pack(side=tk.LEFT)

        # Status badge indicator
        self.lbl_server_badge = tk.Label(
            header_frame,
            text="● SERVER STOPPED",
            font=("Segoe UI", 9, "bold"),
            bg="#27272a",
            fg=self.c_muted,
            padx=10,
            pady=4,
            relief=tk.FLAT
        )
        self.lbl_server_badge.pack(side=tk.RIGHT, padx=8)

        # Stop Button
        self.btn_stop = tk.Button(
            header_frame,
            text="Stop Server",
            command=self.stop_server,
            bg="#7f1d1d",
            fg="#fecaca",
            activebackground="#991b1b",
            activeforeground="white",
            font=("Segoe UI", 9, "bold"),
            padx=12,
            pady=3,
            relief=tk.FLAT,
            cursor="hand2",
            state=tk.DISABLED
        )
        self.btn_stop.pack(side=tk.RIGHT, padx=4)

        # Start Button
        self.btn_start = tk.Button(
            header_frame,
            text="Start Server",
            command=self.start_server,
            bg="#166534",
            fg="#bbf7d0",
            activebackground="#15803d",
            activeforeground="white",
            font=("Segoe UI", 9, "bold"),
            padx=12,
            pady=3,
            relief=tk.FLAT,
            cursor="hand2"
        )
        self.btn_start.pack(side=tk.RIGHT, padx=4)

    def _build_main_panels(self):
        main_paned = ttk.PanedWindow(self, orient=tk.HORIZONTAL)
        main_paned.pack(fill=tk.BOTH, expand=True, padx=10, pady=8)

        # LEFT PANEL: Server Logs & Surveillance
        left_frame = tk.Frame(main_paned, bg=self.c_bg)
        main_paned.add(left_frame, weight=3)

        lbl_logs = tk.Label(
            left_frame,
            text="Server Logs & Live Intercept Surveillance",
            font=("Segoe UI", 10, "bold"),
            bg=self.c_bg,
            fg=self.c_silver,
            anchor="w"
        )
        lbl_logs.pack(fill=tk.X, pady=(2, 4))

        self.log_text = scrolledtext.ScrolledText(
            left_frame,
            wrap=tk.WORD,
            bg=self.c_input,
            fg="#ffffff",             # White color text for logs
            insertbackground="white",
            font=("Consolas", 10),
            padx=10,
            pady=10,
            relief=tk.FLAT,
            highlightthickness=1,
            highlightbackground=self.c_border
        )
        self.log_text.pack(fill=tk.BOTH, expand=True)

        # Log color tags
        self.log_text.tag_config("LOG_WHITE", foreground="#ffffff")
        self.log_text.tag_config("LOG_APPROVED", foreground="#22c55e", font=("Consolas", 10, "bold"))
        self.log_text.tag_config("LOG_BLOCKED", foreground="#ef4444", font=("Consolas", 10, "bold"))
        self.log_text.tag_config("LOG_PENDING", foreground="#facc15", font=("Consolas", 10, "bold"))
        self.log_text.tag_config("LOG_DIVIDER", foreground="#52525b")

        # RIGHT PANEL: Admin Command Console
        right_frame = tk.Frame(main_paned, bg=self.c_bg)
        main_paned.add(right_frame, weight=2)

        lbl_cmd = tk.Label(
            right_frame,
            text="Admin Command Terminal (admin_monitor)",
            font=("Segoe UI", 10, "bold"),
            bg=self.c_bg,
            fg=self.c_silver,
            anchor="w"
        )
        lbl_cmd.pack(fill=tk.X, pady=(2, 4))

        self.cmd_output = scrolledtext.ScrolledText(
            right_frame,
            wrap=tk.WORD,
            bg=self.c_input,
            fg="#ffffff",
            insertbackground="white",
            font=("Consolas", 10),
            padx=8,
            pady=8,
            relief=tk.FLAT,
            highlightthickness=1,
            highlightbackground=self.c_border
        )
        self.cmd_output.pack(fill=tk.BOTH, expand=True)

        self.cmd_output.tag_config("CMD_IN", foreground="#facc15", font=("Consolas", 10, "bold"))
        self.cmd_output.tag_config("CMD_OUT", foreground="#ffffff")
        self.cmd_output.tag_config("CMD_SUCCESS", foreground="#22c55e", font=("Consolas", 10, "bold"))
        self.cmd_output.tag_config("CMD_ERR", foreground="#ef4444", font=("Consolas", 10, "bold"))
        self.cmd_output.tag_config("CMD_WARN", foreground="#facc15", font=("Consolas", 10, "bold"))
        self.cmd_output.tag_config("CMD_INFO", foreground="#a1a1aa")

        # Quick action buttons bar
        quick_bar = tk.Frame(right_frame, bg=self.c_surface, pady=4, padx=4, highlightthickness=1, highlightbackground=self.c_border)
        quick_bar.pack(fill=tk.X, pady=(4, 4))

        quick_btns = [
            ("List Accounts", "list"),
            ("Statistics", "stats"),
            ("Test Safe Pay", "pay -s shiva -r prasad -a 2000 -p 1234"),
            ("Test Evil Pay", "pay -s shiva -r evil.darkmule@darkpay -a 5000 -p 1234"),
            ("Clear Screen", "clear"),
            ("Help", "help"),
        ]
        for label, cmd_str in quick_btns:
            b = tk.Button(
                quick_bar,
                text=label,
                bg="#27272a",
                fg="#f4f4f5",
                activebackground="#3f3f46",
                activeforeground="white",
                relief=tk.FLAT,
                font=("Segoe UI", 8),
                padx=6,
                pady=2,
                cursor="hand2",
                command=lambda c=cmd_str: self._run_quick_command(c)
            )
            b.pack(side=tk.LEFT, padx=2)

        # Command input bar
        input_frame = tk.Frame(right_frame, bg=self.c_surface, pady=6, padx=6, highlightthickness=1, highlightbackground=self.c_border)
        input_frame.pack(fill=tk.X, side=tk.BOTTOM)

        lbl_prompt = tk.Label(
            input_frame,
            text="admin>",
            font=("Consolas", 10, "bold"),
            bg=self.c_surface,
            fg="#facc15"
        )
        lbl_prompt.pack(side=tk.LEFT, padx=(4, 6))

        self.cmd_entry = tk.Entry(
            input_frame,
            bg=self.c_input,
            fg="#ffffff",
            insertbackground="white",
            font=("Consolas", 10),
            relief=tk.FLAT
        )
        self.cmd_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=4)
        self.cmd_entry.bind("<Return>", self._handle_cmd_enter)

        self.btn_send_cmd = tk.Button(
            input_frame,
            text="Run",
            bg="#3f3f46",
            fg="white",
            activebackground="#52525b",
            activeforeground="white",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=12,
            pady=2,
            cursor="hand2",
            command=self._handle_cmd_enter
        )
        self.btn_send_cmd.pack(side=tk.RIGHT, padx=4)

        # Initial console message
        self._append_cmd_output("Admin Console Authenticated.\nReady for commands:\n", "CMD_INFO")
        self._append_cmd_output("  pay -s shiva -r prasad -a 2000 -p 1234\n", "CMD_IN")
        self._append_cmd_output("  list\n  bal shiva\n  stats\n--------------------------------------------\n", "CMD_INFO")

    def _build_status_bar(self):
        status_frame = tk.Frame(self, bg=self.c_surface, pady=4, padx=12, highlightthickness=1, highlightbackground=self.c_border)
        status_frame.pack(fill=tk.X, side=tk.BOTTOM)

        self.lbl_status = tk.Label(
            status_frame,
            text="Backend Status: Idle. Click 'Start Server' to boot backend API.",
            font=("Segoe UI", 9),
            bg=self.c_surface,
            fg=self.c_muted
        )
        self.lbl_status.pack(side=tk.LEFT)

        lbl_url = tk.Label(
            status_frame,
            text="Backend API: http://127.0.0.1:8000 | /docs",
            font=("Segoe UI", 9),
            bg=self.c_surface,
            fg=self.c_muted
        )
        lbl_url.pack(side=tk.RIGHT)

    # =========================================================================
    # 3. SERVER PROCESS CONTROL (START / STOP)
    # =========================================================================
    def start_server(self):
        if self.server_process is not None:
            messagebox.showinfo("Server", "Server is already running.")
            return

        self.btn_start.config(state=tk.DISABLED)
        self.btn_stop.config(state=tk.NORMAL)
        self.lbl_server_badge.config(text="● SERVER RUNNING", bg="#14532d", fg="#86efac")
        self.lbl_status.config(text="Starting server subprocess (uvicorn src.main:app on 127.0.0.1:8000)...")

        self._append_formatted_log("[*] Initializing server subprocess...\n")

        workspace_dir = os.path.dirname(os.path.abspath(__file__)) if not getattr(sys, 'frozen', False) else os.path.dirname(sys.executable)

        # In frozen executable mode, run uvicorn in an embedded thread/process or using sys.executable
        if getattr(sys, 'frozen', False):
            # Frozen exe: run in-process background thread to serve uvicorn
            import uvicorn
            def run_uvicorn_in_thread():
                try:
                    config = uvicorn.Config("src.main:app", host="127.0.0.1", port=8000, log_level="info")
                    self.server_instance = uvicorn.Server(config)
                    self.log_queue.put(("LOG", "[+] Uvicorn server running in embedded mode on http://127.0.0.1:8000!\n"))
                    self.server_instance.run()
                except Exception as ex:
                    self.log_queue.put(("LOG", f"[-] Server error: {str(ex)}\n"))
                self.log_queue.put(("STATUS", "Server terminated."))

            self.server_thread = threading.Thread(target=run_uvicorn_in_thread, daemon=True)
            self.server_thread.start()
            self._append_formatted_log("[+] Uvicorn server successfully launched on http://127.0.0.1:8000!\n")
            return

        cmd = [sys.executable, "-m", "uvicorn", "src.main:app", "--host", "127.0.0.1", "--port", "8000", "--log-level", "info"]

        try:
            self.server_process = subprocess.Popen(
                cmd,
                cwd=workspace_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                universal_newlines=True
            )

            t = threading.Thread(target=self._stream_server_stdout, daemon=True)
            t.start()
            self._append_formatted_log("[+] Uvicorn server successfully launched on http://127.0.0.1:8000!\n")
        except Exception as e:
            self._append_formatted_log(f"[-] Failed to launch server: {str(e)}\n")
            self.stop_server()

    def _stream_server_stdout(self):
        if not self.server_process or not self.server_process.stdout:
            return
        for line in iter(self.server_process.stdout.readline, ""):
            if line:
                self.log_queue.put(("LOG", line))
        self.log_queue.put(("STATUS", "Server process terminated."))

    def stop_server(self):
        if hasattr(self, 'server_instance') and self.server_instance:
            self.server_instance.should_exit = True
            self.server_instance = None

        if self.server_process:
            try:
                self.server_process.terminate()
                self.server_process.wait(timeout=2)
            except Exception:
                try:
                    self.server_process.kill()
                except Exception:
                    pass
            self.server_process = None

        if hasattr(self, 'btn_start') and self.btn_start:
            self.btn_start.config(state=tk.NORMAL)
        if hasattr(self, 'btn_stop') and self.btn_stop:
            self.btn_stop.config(state=tk.DISABLED)
        if hasattr(self, 'lbl_server_badge') and self.lbl_server_badge:
            self.lbl_server_badge.config(text="● SERVER STOPPED", bg="#27272a", fg=self.c_muted)
        if hasattr(self, 'lbl_status') and self.lbl_status:
            self.lbl_status.config(text="Backend server stopped.")
        self._append_formatted_log("[*] Server was stopped.\n")

    def _process_log_queue(self):
        try:
            while True:
                item = self.log_queue.get_nowait()
                msg_type, content = item
                if msg_type == "LOG":
                    self._append_formatted_log(content)
                elif msg_type == "STATUS":
                    self.stop_server()
        except queue.Empty:
            pass

        self.after(100, self._process_log_queue)

    def _append_formatted_log(self, text: str):
        """
        Renders log text in white, with APPROVED in bold green,
        BLOCKED in bold red, and PENDING in bold yellow.
        """
        if not hasattr(self, 'log_text') or not self.log_text:
            return

        # Split tokens to colorize status words
        pattern = re.compile(r'(APPROVED|BLOCKED|PENDING|--+)')
        pos = 0
        for match in pattern.finditer(text):
            start, end = match.span()
            # Text before match
            if start > pos:
                self.log_text.insert(tk.END, text[pos:start], "LOG_WHITE")
            matched_word = match.group(0)
            if matched_word == "APPROVED":
                self.log_text.insert(tk.END, matched_word, "LOG_APPROVED")
            elif matched_word == "BLOCKED":
                self.log_text.insert(tk.END, matched_word, "LOG_BLOCKED")
            elif matched_word == "PENDING":
                self.log_text.insert(tk.END, matched_word, "LOG_PENDING")
            elif matched_word.startswith("-"):
                self.log_text.insert(tk.END, matched_word, "LOG_DIVIDER")
            pos = end

        # Remainder of string
        if pos < len(text):
            self.log_text.insert(tk.END, text[pos:], "LOG_WHITE")

        self.log_text.see(tk.END)

    # =========================================================================
    # 4. SURVEILLANCE FEED LOOP
    # =========================================================================
    def _surveillance_loop(self):
        from src.db.database import get_recent_transactions

        while self.is_monitoring:
            try:
                txns = None
                try:
                    r = requests.get(f"{API_BASE}/api/admin/transactions?limit=20", timeout=0.8)
                    if r.status_code == 200:
                        txns = r.json()
                except Exception:
                    pass

                # If server is not responding over HTTP, read directly from DB
                if txns is None:
                    txns = get_recent_transactions(limit=20)

                if txns:
                    if not self.seen_txn_ids:
                        for t in txns:
                            self.seen_txn_ids.add(t["id"])
                    else:
                        new_txns = [t for t in txns if t["id"] not in self.seen_txn_ids]
                        for txn in reversed(new_txns):
                            self.seen_txn_ids.add(txn["id"])
                            self._display_surveillance_alert(txn)
            except Exception:
                pass
            time.sleep(1.0)

    def _display_surveillance_alert(self, txn: dict):
        status = txn.get("status", "UNKNOWN")
        txn_id = txn.get("id", "N/A")
        sender = txn.get("sender_id", "N/A")
        receiver = txn.get("receiver_id", "N/A")
        amount = float(txn.get("amount", 0.0))
        risk_score = float(txn.get("risk_score", 0.0))
        reasons = txn.get("reasons", [])
        if isinstance(reasons, str):
            import json
            try:
                reasons = json.loads(reasons)
            except Exception:
                reasons = [reasons]

        ts = datetime.now().strftime("%H:%M:%S")

        log_lines = [
            f"\n---------------- LIVE INTERCEPT [{ts}] ----------------\n",
            f"TXN #{txn_id} ==> [{status}]  |  Amount: INR {amount:,.2f}  |  Risk: {risk_score*100:.1f}%\n",
            f"From: {sender}  -->  To: {receiver}\n"
        ]
        if reasons:
            for r in reasons:
                log_lines.append(f"  * {r}\n")
        else:
            log_lines.append("  * Normal legitimate behavioral pattern.\n")
        log_lines.append("----------------------------------------------------------\n")

        full_msg = "".join(log_lines)
        self.log_queue.put(("LOG", full_msg))

    # =========================================================================
    # 5. ADMIN COMMAND EXECUTION
    # =========================================================================
    def _run_quick_command(self, cmd_text: str):
        self.cmd_entry.delete(0, tk.END)
        self.cmd_entry.insert(0, cmd_text)
        self._handle_cmd_enter()

    def _handle_cmd_enter(self, event=None):
        raw_cmd = self.cmd_entry.get().strip()
        if not raw_cmd:
            return
        self.cmd_entry.delete(0, tk.END)

        self._append_cmd_output(f"\nadmin> {raw_cmd}\n", "CMD_IN")
        threading.Thread(target=self._execute_admin_command, args=(raw_cmd,), daemon=True).start()

    def _append_cmd_output(self, text: str, tag: str = "CMD_OUT"):
        if hasattr(self, 'cmd_output') and self.cmd_output:
            self.cmd_output.insert(tk.END, text, tag)
            self.cmd_output.see(tk.END)

    def _execute_admin_command(self, cmd_line: str):
        from src.db.database import (
            get_account,
            list_all_accounts,
            adjust_balance,
            change_password_pin,
            create_or_update_account,
            delete_account,
            get_dashboard_metrics
        )

        try:
            parts = shlex.split(cmd_line)
            cmd = parts[0].lower()
            args = parts[1:]

            if cmd == "help":
                help_text = (
                    "Available Commands:\n"
                    "  pay -s <sender> -r <receiver> -a <amt> -p <pin> [-t <time>]\n"
                    "  bal <username or upi_id>\n"
                    "  list\n"
                    "  add <user> <amount>\n"
                    "  deduct <user> <amount>\n"
                    "  pin <user> <new_pin>\n"
                    "  user <username> <full_name> <pin> <balance> [--evil]\n"
                    "  del <user>\n"
                    "  stats\n"
                    "  clear\n"
                )
                self._append_cmd_output(help_text, "CMD_INFO")

            elif cmd == "clear":
                self.cmd_output.delete("1.0", tk.END)

            elif cmd == "pay":
                self._cmd_pay(args)

            elif cmd in ["bal", "balance"]:
                if not args:
                    self._append_cmd_output("Usage: bal <username or upi_id>\n", "CMD_ERR")
                    return
                acc = get_account(args[0])
                if acc:
                    tag_str = "[EVIL]" if acc["is_evil"] else "[USER]"
                    out = f"  {tag_str} {acc['full_name']} ({acc['upi_id']}) | PIN: {acc['pin']} | Balance: INR {acc['balance']:,.2f}\n"
                    self._append_cmd_output(out, "CMD_SUCCESS" if not acc["is_evil"] else "CMD_ERR")
                else:
                    self._append_cmd_output(f"Account '{args[0]}' not found.\n", "CMD_ERR")

            elif cmd == "list":
                accounts = list_all_accounts()
                header = f"{'TYPE':<7} {'USERNAME':<12} {'FULL NAME':<18} {'UPI ID':<22} {'PIN':<6} {'BALANCE'}\n"
                div = "-" * 75 + "\n"
                self._append_cmd_output("\n" + header + div, "CMD_INFO")
                for a in accounts:
                    type_str = "[EVIL]" if a["is_evil"] else "[USER]"
                    line = f"{type_str:<7} {a['username']:<12} {a['full_name']:<18} {a['upi_id']:<22} {a['pin']:<6} INR {a['balance']:,.2f}\n"
                    tag = "CMD_ERR" if a["is_evil"] else "CMD_OUT"
                    self._append_cmd_output(line, tag)
                self._append_cmd_output(div + f"Total: {len(accounts)} accounts\n", "CMD_INFO")

            elif cmd == "add":
                if len(args) < 2:
                    self._append_cmd_output("Usage: add <user> <amount>\n", "CMD_ERR")
                    return
                ok, nbal = adjust_balance(args[0], float(args[1]))
                if ok:
                    self._append_cmd_output(f"[+] Credited INR {float(args[1]):,.2f} to {args[0]}. New Balance: INR {nbal:,.2f}\n", "CMD_SUCCESS")
                else:
                    self._append_cmd_output(f"Account '{args[0]}' not found.\n", "CMD_ERR")

            elif cmd == "deduct":
                if len(args) < 2:
                    self._append_cmd_output("Usage: deduct <user> <amount>\n", "CMD_ERR")
                    return
                ok, nbal = adjust_balance(args[0], -float(args[1]))
                if ok:
                    self._append_cmd_output(f"[-] Debited INR {float(args[1]):,.2f} from {args[0]}. New Balance: INR {nbal:,.2f}\n", "CMD_SUCCESS")
                else:
                    self._append_cmd_output(f"Account '{args[0]}' not found.\n", "CMD_ERR")

            elif cmd == "pin":
                if len(args) < 2:
                    self._append_cmd_output("Usage: pin <user> <new_pin>\n", "CMD_ERR")
                    return
                ok = change_password_pin(args[0], args[1])
                if ok:
                    self._append_cmd_output(f"[+] PIN successfully changed to '{args[1]}' for {args[0]}.\n", "CMD_SUCCESS")
                else:
                    self._append_cmd_output(f"Account '{args[0]}' not found.\n", "CMD_ERR")

            elif cmd == "user":
                if len(args) < 4:
                    self._append_cmd_output("Usage: user <username> <full_name> <pin> <balance> [--evil]\n", "CMD_ERR")
                    return
                u_name = args[0]
                f_name = args[1]
                u_pin = args[2]
                u_bal = float(args[3])
                is_evil = 1 if len(args) >= 5 and args[4] == "--evil" else 0
                upi_id = f"evil.{u_name.lower()}@darkpay" if is_evil else f"{u_name.lower()}@okbank"
                create_or_update_account(u_name, f_name, upi_id, u_pin, u_bal, is_evil)
                self._append_cmd_output(f"[+] Created account '{u_name}' ({upi_id}) with INR {u_bal:,.2f}\n", "CMD_SUCCESS")

            elif cmd in ["del", "delete"]:
                if not args:
                    self._append_cmd_output("Usage: del <user>\n", "CMD_ERR")
                    return
                ok = delete_account(args[0])
                if ok:
                    self._append_cmd_output(f"[+] Account '{args[0]}' deleted.\n", "CMD_SUCCESS")
                else:
                    self._append_cmd_output(f"Account '{args[0]}' not found.\n", "CMD_ERR")

            elif cmd == "stats":
                s = get_dashboard_metrics()
                txt = (
                    f"STATISTICS OVERVIEW:\n"
                    f"  Total Processed: {s['total_transactions']}\n"
                    f"  Approved Count:  {s['approved_count']}\n"
                    f"  Blocked Frauds:  {s['blocked_count']} ({s['fraud_prevention_rate_pct']}%)\n"
                    f"  Total Volume:    INR {s['total_volume_inr']:,.2f}\n"
                    f"  Fraud Prevented: INR {s['fraud_amount_saved_inr']:,.2f}\n"
                )
                self._append_cmd_output(txt, "CMD_INFO")

            else:
                self._append_cmd_output(f"Unknown command '{cmd}'. Type 'help' for instructions.\n", "CMD_ERR")

        except Exception as e:
            self._append_cmd_output(f"Command execution error: {str(e)}\n", "CMD_ERR")

    def _cmd_pay(self, args):
        """
        Executes payment:
        1. If server is running -> routes to HTTP backend, reports APPROVED or BLOCKED.
        2. If server is stopped -> returns immediate error in console, logs transaction
           as PENDING (Server Offline), without hanging or blocking the UI.
        """
        from src.db.database import get_account, log_transaction

        parser = argparse.ArgumentParser(prog="pay", add_help=False)
        parser.add_argument("-s", "--sender", required=True)
        parser.add_argument("-r", "--receiver", required=True)
        parser.add_argument("-a", "--amount", type=float, required=True)
        parser.add_argument("-p", "--pin", required=True)
        parser.add_argument("-t", "--time", default=None)

        try:
            p_args = parser.parse_args(args)
        except Exception:
            self._append_cmd_output("Usage: pay -s <sender> -r <receiver> -a <amt> -p <pin> [-t <time>]\n", "CMD_ERR")
            return

        # Resolve sender and receiver accounts
        sender_acc = get_account(p_args.sender)
        receiver_acc = get_account(p_args.receiver)

        sender_id = sender_acc["upi_id"] if sender_acc else p_args.sender
        receiver_id = receiver_acc["upi_id"] if receiver_acc else p_args.receiver

        timestamp_str = p_args.time or datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        payload = {
            "sender_id": sender_id,
            "receiver_id": receiver_id,
            "transaction_type": "TRANSFER",
            "amount": p_args.amount,
            "pin": p_args.pin,
            "timestamp": timestamp_str
        }

        # Check if server process is running
        is_server_up = (self.server_process is not None and self.server_process.poll() is None)

        if is_server_up:
            try:
                # Fast timeout (1.5s) to avoid any UI freeze
                res = requests.post(f"{API_BASE}/api/payment/process", json=payload, timeout=1.5)
                if res.status_code == 200:
                    d = res.json()
                    st = d["status"]
                    tag = "CMD_SUCCESS" if st == "APPROVED" else "CMD_ERR"
                    msg = f"[>] Status: {st} | TXN ID: {d['transaction_id']} | Risk: {d['risk_score']*100:.1f}%\n"
                    if st == "APPROVED":
                        msg += f"    Sender New Balance ({sender_id}): INR {d.get('sender_new_balance', 0):,.2f}\n"
                    else:
                        reasons = d.get('reasons', [])
                        msg += f"    Reasons: {', '.join(reasons)}\n"
                    self._append_cmd_output(msg, tag)
                    return
                else:
                    self._append_cmd_output(f"Server error ({res.status_code}): {res.text}\n", "CMD_ERR")
                    return
            except requests.exceptions.RequestException:
                # Server process exists but not responding to HTTP yet
                pass

        # === SERVER IS STOPPED / OFFLINE ===
        # Return error and register PENDING transaction
        txn_id = f"TXN_{uuid.uuid4().hex[:10].upper()}"
        sender_bal = sender_acc["balance"] if sender_acc else 0.0
        receiver_bal = receiver_acc["balance"] if receiver_acc else 0.0

        pending_record = {
            "id": txn_id,
            "sender_id": sender_id,
            "receiver_id": receiver_id,
            "transaction_type": "TRANSFER",
            "amount": p_args.amount,
            "sender_current_balance": sender_bal,
            "receiver_current_balance": receiver_bal,
            "status": "PENDING",
            "risk_score": 0.0,
            "risk_level": "PENDING",
            "reasons": ["Server Stopped / Offline - Transaction queued in PENDING state"],
            "timestamp": timestamp_str
        }
        try:
            log_transaction(pending_record)
        except Exception:
            pass

        # Immediate Console Output: Error & PENDING status
        err_msg = (
            f"[!] ERROR: Cannot process payment — Backend Server is STOPPED!\n"
            f"[>] TXN ID: {txn_id} recorded with status: PENDING\n"
            f"    From: {sender_id}  -->  To: {receiver_id}  (INR {p_args.amount:,.2f})\n"
            f"    Action Required: Click 'Start Server' at the top to resume transaction processing.\n"
        )
        self._append_cmd_output(err_msg, "CMD_WARN")

        # Live log intercept showing PENDING
        alert_log = (
            f"\n---------------- LIVE INTERCEPT [{datetime.now().strftime('%H:%M:%S')}] ----------------\n"
            f"TXN #{txn_id} ==> [PENDING]  |  Amount: INR {p_args.amount:,.2f}  |  Server: OFFLINE\n"
            f"From: {sender_id}  -->  To: {receiver_id}\n"
            f"  * Transaction held in PENDING state: Backend Server is not running.\n"
            f"----------------------------------------------------------\n"
        )
        self.log_queue.put(("LOG", alert_log))

    def _on_close(self):
        self.is_monitoring = False
        if self.server_process:
            if messagebox.askyesno("Exit", "The backend server is still running. Stop server and quit?"):
                self.stop_server()
                self.destroy()
        else:
            self.destroy()


if __name__ == "__main__":
    app = ServerAdminGUI()
    app.mainloop()
