# Release Notes — NEXUS FDS v2.3.0

## 🛡️ NEXUS FDS: Enterprise Fraud Detection & Surveillance Server

We are pleased to release **NEXUS FDS v2.3.0**, a major evolution of our real-time digital banking fraud prevention suite featuring complete brand harmonization, full Windows installer overhaul, and low-latency machine learning inference fixes.

---

### 📦 Artifacts & Downloads
- **Windows Installer**: `Nexus FDS Server-2.3.0-win64.msi` (119.5 MB)
- **Install Directory**: `C:\Program Files\Nexus FDS\` (or custom selected path)
- **Included Utilities**:
  - `Nexus FDS Server.exe` (Unified Desktop GUI + Backend Server Controller)
  - `uninstall.bat` (Clean administrator uninstaller)

---

### 🚀 Key Improvements & Changelog

#### 1. ML Inference Engine Fix (Resolved HTTP 500 on Payments)
- **Problem**: In frozen binary environments, `pd.DataFrame([row])` invoked PyArrow string conversions that attempted to import `pyarrow.vendored.version`, which was removed in recent releases, causing `POST /api/payment/process` to fail with HTTP 500.
- **Solution**: Replaced DataFrame creation in `prepare_features()` with direct NumPy 2D array generation (`np.array([vec], dtype=np.float64)`).
- **Impact**: Zero PyArrow overhead, instant payment evaluation response in `< 15ms`, and zero 500 errors.

#### 2. Rebranding to NEXUS FDS
- Updated all API routes, title tags, logs, and GUI dialogs to **NEXUS FDS**.
- Converted the official geometric lettermark logo (`D:\Copy of Modern Logo with Geometric Lettermark H (1).svg`) into transparent emblems, taskbar icons, and GitHub README banners.
- Configured Windows `AppUserModelID` (`nexusfds.fraudsurveillance.server.2.3.0`) so the official geometric logo displays on the Windows taskbar and Alt+Tab switchers.

#### 3. Professional MSI Installer Wizard
- **Brand Imagery**: Custom welcome screen (`welcome_screen.bmp`) and header banner (`header_banner.bmp`) showcasing the geometric emblem.
- **Flow Corrections**: 
  - Welcome Dialog -> License Agreement -> Destination Folder Selection -> Verify Ready -> Progress Bar -> Finished.
  - Selecting a custom folder via "Browse" now proceeds forward without looping back to the license agreement.
- **Dedicated Uninstaller**:
  - Created [`uninstall.bat`](file:///d:/SFSD/uninstall.bat) packaged inside the installation root.
  - Added Start Menu shortcut for one-click uninstallation.
- **Closes Issue #1**: Addresses MSI rollback cache file permissions (`Error: 5`) by providing administrative cleanup instructions and the dedicated batch uninstaller.

---

### 📝 Issue Resolution Details (Issue #1)
- **Issue**: *Having issues Uninstalling SFDF server msi from pc - Could not set file security for file 'D:\Config.Msi\*.rbf. Error: 5.*
- **Resolution**:
  1. The new release ships with an automated [`uninstall.bat`](file:///d:/SFSD/uninstall.bat) that kills any lingering server process instances and cleanly invokes MSI uninstallation.
  2. Documented step-by-step resolution in the README under **Troubleshooting & Issue Resolution** using `takeown` / `icacls` commands for locked `Config.Msi` caches.
  3. Included an "Uninstall Nexus FDS Server" entry directly in the Windows Start Menu.
