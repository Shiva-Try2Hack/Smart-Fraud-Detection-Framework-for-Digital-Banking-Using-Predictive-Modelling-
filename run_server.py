"""
Start script for the Smart Fraud Detection Backend Server.
Prompts the user, loads models, seeds accounts, and launches Uvicorn with full A-Z logging.
"""
import sys
import os
import time

# Enable ANSI colors in Windows terminal
os.system("")

C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_CYAN = "\033[96m"
C_GREEN = "\033[92m"
C_YELLOW = "\033[93m"
C_WHITE = "\033[97m"

def display_startup_banner():
    print(f"\n{C_CYAN}{'='*75}{C_RESET}")
    print(f"{C_BOLD}{C_WHITE}    BANKING FRAUD DETECTION FRAMEWORK - BACKEND CONTROLLER{C_RESET}")
    print(f"{C_CYAN}{'='*75}{C_RESET}")
    print(f"{C_WHITE}Department of Computer Science & Application - Academic Year 2026-2027{C_RESET}")
    print(f"{C_CYAN}{'-'*75}{C_RESET}\n")

def main():
    display_startup_banner()

    # Step 3 requirement: Start prompt first
    prompt_answer = input(f"{C_BOLD}{C_YELLOW}[?] Press ENTER to initialize models and boot backend server (or type 'q' to quit): {C_RESET}")
    if prompt_answer.strip().lower() == 'q':
        print(f"{C_YELLOW}Startup aborted by user.{C_RESET}")
        sys.exit(0)

    print(f"\n{C_CYAN}[*] Step 1/3: Initializing database & user accounts...{C_RESET}")
    from src.db.database import seed_default_accounts
    seed_default_accounts()
    print(f"{C_GREEN}[+] Database ready (5 student accounts + 3 evil mule accounts registered).{C_RESET}")
    time.sleep(0.5)

    print(f"\n{C_CYAN}[*] Step 2/3: Loading trained AI / ML fraud models into memory...{C_RESET}")
    from src.ml_engine.evaluator import RiskScoringEngine
    engine = RiskScoringEngine()
    print(f"{C_GREEN}[+] Random Forest Classifier & Isolation Forest successfully loaded!{C_RESET}")
    print(f"{C_GREEN}[+] Model features: {', '.join(engine.feature_columns[:5])}... ({len(engine.feature_columns)} total features){C_RESET}")
    time.sleep(0.5)

    print(f"\n{C_CYAN}[*] Step 3/3: Booting FastAPI server on http://127.0.0.1:8000 with A-Z live logging...{C_RESET}")
    print(f"{C_GREEN}[+] Swagger API Docs available at: http://127.0.0.1:8000/docs{C_RESET}")
    print(f"{C_YELLOW}[+] Press CTRL+C at any time in this window to stop the server.{C_RESET}\n")
    print(f"{C_CYAN}{'='*75}{C_RESET}\n")

    # Launch uvicorn programmatically with detailed log level
    import uvicorn
    uvicorn.run("src.main:app", host="127.0.0.1", port=8000, reload=False, log_level="info")

if __name__ == "__main__":
    main()
