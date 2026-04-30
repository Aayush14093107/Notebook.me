@echo off
setlocal
pushd "%~dp0"
echo =============================================
echo   notebook.me v6.1.2 - Jar Build
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
echo   Output: dist\NotebookMe.jar
echo   Run with: java -jar dist\NotebookMe.jar
echo.
echo =============================================
pause
