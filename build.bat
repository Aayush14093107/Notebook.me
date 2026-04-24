@echo off
echo =============================================
echo   notebook.me v5.0.0 — Build Script
echo =============================================
echo.
echo [1/3] Compiling Java sources...
javac -encoding UTF-8 *.java
if %ERRORLEVEL% NEQ 0 (
    echo COMPILATION FAILED!
    pause
    exit /b 1
)
echo       Done.
echo.
echo [2/3] Packaging into NotebookMe.jar...
"C:\Program Files\Java\jdk-25\bin\jar.exe" cfm NotebookMe.jar MANIFEST.MF *.class
if %ERRORLEVEL% NEQ 0 (
    echo JAR PACKAGING FAILED!
    pause
    exit /b 1
)
echo       Done.
echo.
echo [3/3] Build complete!
echo.
echo   Output: NotebookMe.jar
echo   Run with: java -jar NotebookMe.jar
echo   Or double-click NotebookMe.jar
echo.
echo =============================================
pause
