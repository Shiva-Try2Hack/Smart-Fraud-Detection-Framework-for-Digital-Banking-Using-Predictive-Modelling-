"""
Nexus FDS Windows MSI Installer Builder
Features:
  1. Welcome Screen with official software logo image, Next button, and Cancel button.
  2. End User License Agreement (EULA) screen with interactive acceptance checkbox.
  3. Destination Path Selection with a working Browse button opening the Windows Folder/Drive tree browser.
  4. Elevated Administrator Installation button ("Install") with security notice.
  5. Installation Progress dialog with live Status indicator and dedicated item box displaying what is currently getting extracted/installed.
  6. Completion Screen with official logo, completion status, and Launch application option.
  7. High-resolution application icon (.ico) assigned to Desktop & Start Menu shortcuts, installer, and Add/Remove Programs.
"""

import sys
import os
import msilib
from cx_Freeze import setup, Executable
from cx_Freeze.command.bdist_msi import bdist_msi as _bdist_msi, PyDialog, add_data

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ASSETS_DIR = os.path.join(BASE_DIR, "assets")
LICENSE_PATH = os.path.join(BASE_DIR, "LICENSE.txt")
BANNER_BMP = os.path.join(ASSETS_DIR, "banner.bmp")
WELCOME_BMP = os.path.join(ASSETS_DIR, "welcome_screen.bmp")
ICON_ICO = os.path.join(ASSETS_DIR, "app_icon.ico")

class CustomBdistMSI(_bdist_msi):
    """
    Custom MSI builder configuring an enterprise-grade setup wizard with:
    - Welcome Screen with Nexus FDS Logo
    - Agreement Checkbox verification
    - Folder / Drive Browser dialog
    - Administrator Required Install button
    - Live Progress dialog with detailed item extraction sub-window
    - Branded Exit screen with Launch option
    """
    def add_ui(self) -> None:
        # Standard system dialogs
        self.add_text_styles()
        self.add_error_dialog()
        self.add_fatal_error_dialog()
        self.add_cancel_dialog()
        self.add_user_exit_dialog()
        self.add_files_in_use_dialog()
        self.add_wait_for_costing_dialog()
        self.add_prepare_dialog()
        self.add_maintenance_type_dialog()

        # Custom Branded Screens
        self.add_custom_welcome_dialog()
        self.add_custom_license_dialog()
        self.add_custom_browse_dialog()
        self.add_custom_select_directory_dialog()
        self.add_custom_progress_dialog()
        self.add_custom_exit_dialog()

        # Wizard Navigation Sequence:
        # In MSI wizard architecture, dialog transitions happen via NewDialog control events
        # (WelcomeDlg -> LicenseAgreementDlg -> SelectDirectoryDlg -> ProgressDlg -> ExecuteAction).
        # We ensure InstallUISequence contains ONLY the initial entry dialog (WelcomeDlg at sequence 1220).
        # We remove secondary dialogs from InstallUISequence so clicking 'Install' on SelectDirectoryDlg
        # triggers execution directly rather than falling through to LicenseAgreementDlg.
        try:
            for dlg_name in ["LicenseAgreementDlg", "SelectDirectoryDlg"]:
                v_del = self.db.OpenView(f"DELETE FROM `InstallUISequence` WHERE `Action` = '{dlg_name}'")
                v_del.Execute(None)
                v_del.Close()

            add_data(self.db, "InstallUISequence", [("WelcomeDlg", "not Installed", 1220)])
        except Exception as e:
            print("Notice: InstallUISequence configuration:", e)

        # Embed installer UI bitmaps into MSI Binary table
        add_data(self.db, "Binary", [
            ("NexusWelcomeBmp", msilib.Binary(WELCOME_BMP)),
            ("NexusBannerBmp", msilib.Binary(BANNER_BMP))
        ])

        # Enforce administrative privileges and rollback resilience
        add_data(self.db, "Property", [
            ("MSIUSEREALADMINDETECTION", "1"),
            ("DISABLEROLLBACK", "0"),
        ])

    def add_custom_welcome_dialog(self) -> None:
        """Welcome screen with full left sidebar logo banner, clear overview, Next and Cancel buttons."""
        dialog = PyDialog(
            self.db,
            "WelcomeDlg",
            x=self.x,
            y=self.y,
            w=self.width,
            h=self.height,
            attr=self.modal,
            title=self.title,
            first="Next",
            default="Next",
            cancel="Cancel",
        )
        dialog.bitmap("Bitmap", 0, 0, 374, 234, "NexusWelcomeBmp")
        dialog.text(
            "Title",
            135,
            20,
            225,
            40,
            0x30003,
            r"{\DlgFontBold8}Welcome to the Nexus FDS Setup Wizard"
        )
        dialog.text(
            "Description",
            135,
            65,
            225,
            90,
            0x30003,
            "The Setup Wizard will install Nexus FDS — Smart Fraud Detection Framework and Digital Banking Surveillance Suite on your computer.\n\nClick Next to continue, or Cancel to exit Setup Wizard."
        )
        
        # Valid closed cycle: Next -> Cancel -> Back -> Next
        dialog.backbutton("< &Back", "Next", active=False)
        btn_next = dialog.nextbutton("&Next >", "Cancel")
        btn_next.event("NewDialog", "LicenseAgreementDlg", ordering=1)

        btn_cancel = dialog.cancelbutton("&Cancel", "Back")
        btn_cancel.event("SpawnDialog", "CancelDlg", ordering=1)

    def add_custom_license_dialog(self) -> None:
        """License Agreement screen with interactive acceptance checkbox controlling Next button."""
        # Windows Installer ScrollableText control natively expects Rich Text Format (RTF)
        rtf_file = os.path.join(BASE_DIR, "LICENSE.rtf")
        if os.path.exists(rtf_file):
            with open(rtf_file, "r", encoding="utf-8") as f:
                lic_content = f.read()
        elif os.path.exists(LICENSE_PATH):
            with open(LICENSE_PATH, "r", encoding="utf-8") as f:
                raw_text = f.read()
            lic_content = (
                r"{\rtf1\ansi\ansicpg1252\deff0\deflang1033"
                r"{\fonttbl{\f0\fnil\fcharset0 Segoe UI;}}"
                r"\viewkind4\uc1\pard\f0\fs18 "
                + raw_text.replace("\n", r"\par ")
                + r"}"
            )
        else:
            lic_content = r"{\rtf1\ansi\ansicpg1252\deff0 Nexus FDS License Agreement\par}"

        dialog = PyDialog(
            self.db,
            "LicenseAgreementDlg",
            x=self.x,
            y=self.y,
            w=self.width,
            h=self.height,
            attr=self.modal,
            title=self.title,
            first="LicenseCheck",
            default="Next",
            cancel="Cancel",
        )
        dialog.bitmap("BannerBmp", 0, 0, 374, 44, "NexusBannerBmp")
        dialog.text("Title", 15, 10, 260, 18, 0x30003, r"{\DlgFontBold8}End-User License Agreement")
        dialog.text("SubTitle", 15, 26, 260, 15, 0x30003, "Please review the license terms before proceeding.")

        dialog.control(
            "LicenseText",
            "ScrollableText",
            15,
            50,
            344,
            156,
            3,
            None,
            lic_content,
            "LicenseCheck",
            None
        )

        chk = dialog.checkbox(
            "LicenseCheck",
            15,
            214,
            344,
            18,
            3,
            "AGREEMENT_ACCEPTED",
            "I &accept the terms and conditions in the License Agreement",
            "Next"
        )
        chk.event("[AGREEMENT_ACCEPTED]", "1")

        # Cycle: LicenseText -> LicenseCheck -> Next -> Cancel -> Back -> LicenseText
        btn_back = dialog.backbutton("< &Back", "LicenseText")
        btn_back.event("NewDialog", "WelcomeDlg", ordering=1)

        btn_next = dialog.nextbutton("&Next >", "Cancel", active=False)
        btn_next.event("NewDialog", "SelectDirectoryDlg", ordering=1)

        btn_cancel = dialog.cancelbutton("&Cancel", "Back")
        btn_cancel.event("SpawnDialog", "CancelDlg", ordering=1)

        add_data(
            self.db,
            "ControlCondition",
            [
                ("LicenseAgreementDlg", "Next", "Enable", "AGREEMENT_ACCEPTED=1"),
                ("LicenseAgreementDlg", "Next", "Disable", "NOT AGREEMENT_ACCEPTED=1")
            ]
        )

    def add_custom_browse_dialog(self) -> None:
        """Windows Folder & Drive Selection Browser dialog."""
        dialog = PyDialog(
            self.db,
            "BrowseDlg",
            x=self.x,
            y=self.y,
            w=self.width,
            h=self.height,
            attr=self.modal,
            title=self.title,
            first="OK",
            default="OK",
            cancel="Cancel",
        )
        dialog.title("Change Destination Folder or Drive")
        dialog.text("PathLabel", 20, 48, 80, 15, 3, "Folder name:")
        
        # Cycle: PathEdit -> DirectoryCombo -> DirectoryList -> Up -> NewDir -> OK -> Cancel -> PathEdit
        dialog.control("PathEdit", "PathEdit", 20, 64, 334, 18, 3, "_BrowseProperty", None, "DirectoryCombo", None)
        dialog.control("DirectoryCombo", "DirectoryCombo", 20, 90, 280, 80, 393219, "_BrowseProperty", None, "DirectoryList", None)
        dialog.control("DirectoryList", "DirectoryList", 20, 115, 334, 105, 3, "_BrowseProperty", None, "Up", None)

        btn_up = dialog.pushbutton("Up", 308, 90, 20, 18, 3, "Up", "NewDir")
        btn_up.event("DirectoryListUp", "0")

        btn_new = dialog.pushbutton("NewDir", 332, 90, 22, 18, 3, "New", "OK")
        btn_new.event("DirectoryListNew", "0")

        btn_ok = dialog.pushbutton("OK", 240, 235, 56, 17, 3, "OK", "Cancel")
        btn_ok.event("SetTargetPath", "[_BrowseProperty]", ordering=1)
        btn_ok.event("EndDialog", "Return", ordering=2)

        btn_cancel = dialog.cancelbutton("Cancel", "PathEdit")
        btn_cancel.event("Reset", "0", ordering=1)
        btn_cancel.event("EndDialog", "Return", ordering=2)

    def add_custom_select_directory_dialog(self) -> None:
        """Destination directory selection with working inline folder/drive navigator and elevation notice."""
        dialog = PyDialog(
            self.db,
            "SelectDirectoryDlg",
            x=self.x,
            y=self.y,
            w=self.width,
            h=self.height,
            attr=self.modal,
            title=self.title,
            first="Install",
            default="Install",
            cancel="Cancel",
        )
        dialog.bitmap("BannerBmp", 0, 0, 374, 44, "NexusBannerBmp")
        dialog.text("Title", 15, 8, 260, 16, 0x30003, r"{\DlgFontBold8}Destination Directory & Privileges")
        dialog.text("SubTitle", 15, 24, 260, 14, 0x30003, "Select installation folder and authorize elevated setup.")

        # Complete inline Windows directory & drive browser
        dialog.control(
            "DirectoryCombo",
            "DirectoryCombo",
            15,
            48,
            272,
            80,
            393219,
            "TARGETDIR",
            None,
            "DirectoryList",
            None,
        )
        dialog.control(
            "DirectoryList",
            "DirectoryList",
            15,
            70,
            308,
            110,
            3,
            "TARGETDIR",
            None,
            "PathEdit",
            None,
        )
        dialog.control(
            "PathEdit",
            "PathEdit",
            15,
            186,
            344,
            16,
            3,
            "TARGETDIR",
            None,
            "Install",
            None,
        )
        btn_up = dialog.pushbutton("Up", 295, 48, 26, 18, 3, "Up", None)
        btn_up.event("DirectoryListUp", "0")

        btn_new = dialog.pushbutton("NewDir", 325, 48, 34, 18, 3, "New", None)
        btn_new.event("DirectoryListNew", "0")

        # Elevation badge
        dialog.text(
            "AdminLabel",
            15,
            206,
            344,
            22,
            3,
            "Requires administrator elevation to bind port 8000 & init database."
        )

        # Tab cycle: DirectoryCombo -> DirectoryList -> PathEdit -> Install -> Cancel -> Back -> DirectoryCombo
        btn_back = dialog.backbutton("< &Back", "DirectoryCombo")
        btn_back.event("NewDialog", "LicenseAgreementDlg", ordering=1)

        btn_install = dialog.pushbutton("Install", 236, 241, 56, 17, 3, "&Install", "Cancel")
        btn_install.event("SetTargetPath", "TARGETDIR", ordering=1)
        btn_install.event("SpawnWaitDialog", "WaitForCostingDlg", ordering=2)
        btn_install.event("EndDialog", "Return", ordering=3)

        btn_cancel = dialog.cancelbutton("&Cancel", "Back")
        btn_cancel.event("SpawnDialog", "CancelDlg", ordering=1)

    def add_custom_progress_dialog(self) -> None:
        """Progress screen showing live action status and a sub-window displaying current files installed."""
        dialog = PyDialog(
            self.db,
            "ProgressDlg",
            x=self.x,
            y=self.y,
            w=self.width,
            h=self.height,
            attr=self.modeless,
            title=self.title,
            first="Cancel",
            default="Cancel",
            cancel="Cancel",
            bitmap=False,
        )
        dialog.bitmap("BannerBmp", 0, 0, 374, 44, "NexusBannerBmp")
        dialog.text("Title", 15, 10, 260, 18, 0x30003, r"{\DlgFontBold8}[Progress1] Nexus FDS Server")
        dialog.text("SubTitle", 15, 26, 260, 15, 0x30003, "Please wait while Setup installs Nexus FDS on your computer.")

        dialog.text("StatusLabel", 20, 56, 45, 15, 3, "Status:")
        act_text = dialog.text("ActionText", 65, 56, 290, 15, 3, "Installing files and components...")
        act_text.mapping("ActionText", "Text")

        prog = dialog.control("ProgressBar", "ProgressBar", 20, 75, 334, 12, 65537, None, "Progress done", None, None)
        prog.mapping("SetProgress", "Progress")

        dialog.text("DetailLabel", 20, 98, 334, 15, 3, "Files and components currently being configured:")
        data_text = dialog.text("ActionData", 20, 115, 334, 52, 3, "Processing...")
        data_text.mapping("ActionData", "Text")

        # Cycle: Cancel -> Back -> Next -> Cancel
        dialog.backbutton("< &Back", "Next", active=False)
        dialog.nextbutton("&Next >", "Cancel", active=False)
        btn_cancel = dialog.cancelbutton("&Cancel", "Back")
        btn_cancel.event("SpawnDialog", "CancelDlg", ordering=1)

    def add_custom_exit_dialog(self) -> None:
        """Branded completion screen with launch checkbox."""
        dialog = PyDialog(
            self.db,
            "ExitDialog",
            x=self.x,
            y=self.y,
            w=self.width,
            h=self.height,
            attr=self.modal,
            title=self.title,
            first="Finish",
            default="Finish",
            cancel="Finish",
        )
        dialog.bitmap("Bitmap", 0, 0, 374, 234, "NexusWelcomeBmp")
        dialog.text(
            "Title",
            135,
            20,
            225,
            40,
            0x30003,
            r"{\DlgFontBold8}Installation Completed Successfully"
        )
        dialog.text(
            "Desc",
            135,
            65,
            225,
            60,
            0x30003,
            "Nexus FDS Server is ready to use.\n\nDesktop and Start Menu shortcuts have been configured with official application logos."
        )

        dialog.checkbox("LaunchOnFinish", 135, 135, 225, 20, 3, "LAUNCHAPP", "Launch Nexus FDS Server now", "Finish")
        
        # Cycle: LaunchOnFinish -> Finish -> Cancel -> Back -> LaunchOnFinish
        dialog.backbutton("< &Back", "LaunchOnFinish", active=False)
        btn_cancel = dialog.cancelbutton("Cancel", "Back", active=False)

        btn_finish = dialog.nextbutton("&Finish", "Cancel", name="Finish")
        btn_finish.event("DoAction", "VSDCA_Launch", "LAUNCHAPP=1", 1)
        btn_finish.event("EndDialog", "Return", ordering=2)

        # Custom Action to launch app
        executables: list[Executable] = getattr(self.distribution, "executables", [])
        exe_target = executables[0].target_name if executables else "Nexus FDS Server.exe"
        add_data(self.db, "CustomAction", [
            ("VSDCA_Launch", 226, "TARGETDIR", f"[TARGETDIR]\\{exe_target}")
        ])
        add_data(self.db, "Property", [
            ("LAUNCHAPP", "1")
        ])


# Application data & directories
includefiles = [
    ("src", "src"),
    ("assets", "assets"),
    ("database.sqlite", "database.sqlite"),
    ("ACCOUNTS_DIRECTORY.txt", "ACCOUNTS_DIRECTORY.txt"),
    ("COMMANDS_REFERENCE.txt", "COMMANDS_REFERENCE.txt"),
    ("LICENSE.txt", "LICENSE.txt"),
    ("LICENSE.rtf", "LICENSE.rtf"),
    ("uninstall.bat", "uninstall.bat"),
    ("docs", "docs"),
    ("app_icon.ico", "app_icon.ico"),
]

packages = [
    "tkinter",
    "PIL",
    "uvicorn",
    "uvicorn.logging",
    "uvicorn.loops",
    "uvicorn.loops.auto",
    "uvicorn.protocols",
    "uvicorn.protocols.http",
    "uvicorn.protocols.http.auto",
    "uvicorn.lifespan",
    "uvicorn.lifespan.on",
    "fastapi",
    "pydantic",
    "sqlite3",
    "sklearn",
    "joblib",
    "numpy",
    "requests",
    "logging"
]

# MSI shortcut configuration with high-res icon
shortcut_table = [
    (
        "DesktopShortcut",
        "DesktopFolder",
        "Nexus FDS Server",
        "TARGETDIR",
        "[TARGETDIR]Nexus FDS Server.exe",
        None,
        "Nexus FDS — Fraud Detection & Surveillance Server",
        None,
        "InstallIcon",
        0,
        None,
        "TARGETDIR"
    ),
    (
        "StartMenuShortcut",
        "ProgramMenuFolder",
        "Nexus FDS Server",
        "TARGETDIR",
        "[TARGETDIR]Nexus FDS Server.exe",
        None,
        "Nexus FDS — Fraud Detection & Surveillance Server",
        None,
        "InstallIcon",
        0,
        None,
        "TARGETDIR"
    ),
    (
        "UninstallStartMenuShortcut",
        "ProgramMenuFolder",
        "Uninstall Nexus FDS Server",
        "TARGETDIR",
        "[SystemFolder]cmd.exe",
        r'/c ""[TARGETDIR]uninstall.bat""',
        "Uninstall Nexus FDS Server and components",
        None,
        "InstallIcon",
        0,
        None,
        "TARGETDIR"
    )
]

bdist_msi_options = {
    "data": {
        "Shortcut": shortcut_table
    },
    "add_to_path": True,
    "all_users": True,
    "initial_target_dir": r"[ProgramFilesFolder]\Nexus FDS",
    "install_icon": ICON_ICO,
    "license_file": LICENSE_PATH,
    "product_name": "Nexus FDS Server",
    "summary_data": {
        "author": "Nexus FDS Security",
        "comments": "Nexus FDS — Fraud Detection & Surveillance Server",
        "keywords": "nexus-fds;fraud-detection;digital-banking;surveillance;security"
    }
}

build_exe_options = {
    "packages": packages,
    "include_files": includefiles,
    "excludes": ["matplotlib", "torch", "scipy.spatial.cKDTree"],
    "include_msvcr": True
}

base = "Win32GUI" if sys.platform == "win32" else None

executables = [
    Executable(
        script="server_ui.py",
        target_name="Nexus FDS Server.exe",
        base=base,
        icon=ICON_ICO,
        shortcut_name="Nexus FDS Server",
        shortcut_dir="DesktopFolder",
    )
]

setup(
    name="Nexus FDS Server",
    version="2.3.0",
    description="Nexus FDS — Fraud Detection & Surveillance Server",
    author="Nexus FDS Security",
    cmdclass={"bdist_msi": CustomBdistMSI},
    options={
        "build_exe": build_exe_options,
        "bdist_msi": bdist_msi_options
    },
    executables=executables
)
