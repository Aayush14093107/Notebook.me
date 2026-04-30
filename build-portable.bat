@echo off
setlocal
pushd "%~dp0"
echo =============================================
echo   notebook.me v6.1.2 - Portable Build
echo =============================================
echo.
PowerShell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-portable.ps1"
set "BUILD_EXIT=%ERRORLEVEL%"
popd
echo.
if not "%BUILD_EXIT%"=="0" (
    echo PORTABLE BUILD FAILED!
    pause
    exit /b %BUILD_EXIT%
)
echo   Portable app: dist\NotebookMe-portable\NotebookMe.bat
echo.
echo =============================================
pause
