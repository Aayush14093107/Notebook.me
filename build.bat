@echo off
setlocal
pushd "%~dp0"
echo =============================================
echo   notebook.me v6.1.1 - Jar Build
echo =============================================
echo.
PowerShell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-portable.ps1" -JarOnly
set "BUILD_EXIT=%ERRORLEVEL%"
popd
echo.
if not "%BUILD_EXIT%"=="0" (
    echo BUILD FAILED!
    pause
    exit /b %BUILD_EXIT%
)
echo   Output: NotebookMe.jar
echo   Run with: java -jar NotebookMe.jar
echo.
echo =============================================
pause
