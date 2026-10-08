"""
Setup script to build Windows MSI installer for SFDF Server using cx_Freeze.
Generates:
  - SFDF Server.exe (Desktop GUI application)
  - SFDF Server-2.2.0-win64.msi (Windows Installer)
  - Creates Desktop Shortcut & Start Menu Shortcut
"""
import sys
import os
from cx_Freeze import setup, Executable

# Include application dependencies and data
includefiles = [
    ("src", "src"),
    ("database.sqlite", "database.sqlite"),
    ("ACCOUNTS_DIRECTORY.txt", "ACCOUNTS_DIRECTORY.txt"),
    ("COMMANDS_REFERENCE.txt", "COMMANDS_REFERENCE.txt"),
    ("docs", "docs")
]

packages = [
    "tkinter",
    "uvicorn",
    "fastapi",
    "pydantic",
    "sqlite3",
    "sklearn",
    "joblib",
    "numpy",
    "requests"
]

# MSI shortcut configuration table
shortcut_table = [
    (
        "DesktopShortcut",        # Shortcut
        "DesktopFolder",          # Directory_
        "SFDF Server",            # Name
        "TARGETDIR",              # Component_
        "[TARGETDIR]SFDF Server.exe", # Target
        None,                     # Arguments
        "Smart Fraud Detection Framework Server", # Description
        None,                     # Hotkey
        None,                     # Icon
        None,                     # IconIndex
        None,                     # ShowCmd
        'TARGETDIR'               # WkDir
    ),
    (
        "StartMenuShortcut",      # Shortcut
        "ProgramMenuFolder",      # Directory_
        "SFDF Server",            # Name
        "TARGETDIR",              # Component_
        "[TARGETDIR]SFDF Server.exe", # Target
        None,                     # Arguments
        "Smart Fraud Detection Framework Server", # Description
        None,                     # Hotkey
        None,                     # Icon
        None,                     # IconIndex
        None,                     # ShowCmd
        'TARGETDIR'               # WkDir
    )
]

msi_data = {"Shortcut": shortcut_table}

bdist_msi_options = {
    "data": msi_data,
    "add_to_path": True,
    "initial_target_dir": r"[ProgramFilesFolder]\SFDF Server",
}

build_exe_options = {
    "packages": packages,
    "include_files": includefiles,
    "excludes": ["matplotlib", "torch", "scipy.spatial.cKDTree"],
}

base = "Win32GUI" if sys.platform == "win32" else None

executables = [
    Executable(
        script="server_ui.py",
        target_name="SFDF Server.exe",
        base=base,
        shortcut_name="SFDF Server",
        shortcut_dir="DesktopFolder",
    )
]

setup(
    name="SFDF Server",
    version="2.2.0",
    description="Smart Fraud Detection Framework - Digital Banking Server & Surveillance",
    author="Shiva",
    options={
        "build_exe": build_exe_options,
        "bdist_msi": bdist_msi_options
    },
    executables=executables
)
