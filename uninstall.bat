@echo off
setlocal
title Nexus FDS Server - Uninstaller
color 0F

echo ========================================================
echo         Nexus FDS Server - Uninstaller Assistant        
echo ========================================================
echo.
echo Product: Nexus FDS Server (win64)
echo Product Code: {DD691987-A25B-427F-9698-6B1C30BFF925}
echo.
echo This utility will safely remove Nexus FDS Server,
echo shortcuts, and its registered Windows components.
echo.

:: Check for administrative rights
net session >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [NOTICE] Requesting Administrator elevation...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

echo Are you sure you want to completely uninstall Nexus FDS Server?
set /p CONFIRM="Enter (Y)es to proceed or (N)o to cancel: "
if /i not "%CONFIRM%"=="Y" (
    echo.
    echo Uninstallation cancelled by user.
    pause
    exit /b
)

echo.
echo Stopping any running Nexus FDS background processes...
taskkill /F /IM "Nexus FDS Server.exe" >nul 2>&1
taskkill /F /IM "python.exe" /FI "WINDOWTITLE eq Nexus FDS*" >nul 2>&1

echo.
echo Invoking Windows Installer uninstallation...
msiexec.exe /x {DD691987-A25B-427F-9698-6B1C30BFF925} /qr

if %errorLevel% EQU 0 (
    echo.
    echo ========================================================
    echo      Nexus FDS Server was successfully uninstalled!    
    echo ========================================================
) else (
    echo.
    echo [WARNING] msiexec returned code %errorLevel%.
    echo If files remain in this folder, you may safely delete them.
)

echo.
pause
