param(
    [switch]$JarOnly,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Add-ExistingPath {
    param(
        [System.Collections.Generic.List[string]]$List,
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    if (Test-Path -LiteralPath $Path) {
        $Resolved = (Resolve-Path -LiteralPath $Path).Path
        if (-not $List.Contains($Resolved)) {
            [void]$List.Add($Resolved)
        }
    }
}

function Get-JdkHomes {
    $Homes = [System.Collections.Generic.List[string]]::new()
    Add-ExistingPath $Homes $env:JAVA_HOME

    $JavacCommand = Get-Command "javac.exe" -ErrorAction SilentlyContinue
    if ($JavacCommand -and $JavacCommand.Source) {
        $BinDir = Split-Path -Parent $JavacCommand.Source
        Add-ExistingPath $Homes (Split-Path -Parent $BinDir)
    }

    $JavaRoots = @()
    if ($env:ProgramFiles) {
        $JavaRoots += Join-Path $env:ProgramFiles "Java"
    }

    $ProgramFilesX86 = [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
    if ($ProgramFilesX86) {
        $JavaRoots += Join-Path $ProgramFilesX86 "Java"
    }

    foreach ($Root in ($JavaRoots | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $Root) {
            Get-ChildItem -LiteralPath $Root -Directory -Filter "jdk*" |
                Sort-Object LastWriteTime -Descending |
                ForEach-Object { Add-ExistingPath $Homes $_.FullName }
        }
    }

    return $Homes
}

function Resolve-JdkTool {
    param([Parameter(Mandatory=$true)][string]$Name)

    foreach ($JdkHome in (Get-JdkHomes)) {
        $Candidate = Join-Path $JdkHome "bin\$Name.exe"
        if (Test-Path -LiteralPath $Candidate) {
            return (Resolve-Path -LiteralPath $Candidate).Path
        }
    }

    $Command = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if ($Command -and $Command.Source) {
        return $Command.Source
    }

    throw "Could not find $Name.exe. Install a JDK with $Name, or set JAVA_HOME to a valid JDK."
}

function Remove-SafeDirectory {
    param([Parameter(Mandatory=$true)][string]$Path)

    $Target = [System.IO.Path]::GetFullPath($Path)
    $Root = [System.IO.Path]::GetFullPath($ProjectRoot)
    if (-not $Target.StartsWith($Root, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside the project: $Target"
    }

    if (Test-Path -LiteralPath $Target) {
        Remove-Item -LiteralPath $Target -Recurse -Force
    }
}

function Invoke-Tool {
    param(
        [Parameter(Mandatory=$true)][string]$Tool,
        [Parameter(Mandatory=$true)][string[]]$Arguments
    )

    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$([System.IO.Path]::GetFileName($Tool)) failed with exit code $LASTEXITCODE."
    }
}

$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$JarFile = Join-Path $BuildDir "NotebookMe.jar"
$RootJarFile = Join-Path $ProjectRoot "NotebookMe.jar"
$ManifestFile = Join-Path $ProjectRoot "MANIFEST.MF"

if ($Clean) {
    Remove-SafeDirectory $BuildDir
}

Remove-SafeDirectory $ClassesDir
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

$Sources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "src") -Filter "*.java" -Recurse -File | Sort-Object Name)
if ($Sources.Count -eq 0) {
    throw "No Java source files were found."
}

$Javac = Resolve-JdkTool "javac"
$Jar = Resolve-JdkTool "jar"

Write-Host "[1/4] Compiling Java sources..."
$JavacArgs = @("-encoding", "UTF-8", "--release", "11", "-d", $ClassesDir) + @($Sources.FullName)
Invoke-Tool $Javac $JavacArgs

Write-Host "Copying resources..."
Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "res") -File | Copy-Item -Destination $ClassesDir -Force

Write-Host "[2/4] Packaging NotebookMe.jar..."
$JarArgs = @("cfm", $JarFile, $ManifestFile, "-C", $ClassesDir, ".")
Invoke-Tool $Jar $JarArgs
Copy-Item -LiteralPath $JarFile -Destination $RootJarFile -Force

if ($JarOnly) {
    Write-Host "Done. Wrote $RootJarFile"
    exit 0
}

$Jdeps = Resolve-JdkTool "jdeps"
$Jlink = Resolve-JdkTool "jlink"
$PortableDir = Join-Path $ProjectRoot "dist\NotebookMe-portable"
$AppDir = Join-Path $PortableDir "app"
$DataDir = Join-Path $PortableDir "data"
$RuntimeDir = Join-Path $PortableDir "runtime"

Write-Host "[3/4] Creating portable folder..."
Remove-SafeDirectory $PortableDir
New-Item -ItemType Directory -Force -Path $AppDir, $DataDir | Out-Null
Copy-Item -LiteralPath $JarFile -Destination (Join-Path $AppDir "NotebookMe.jar") -Force

$Modules = "java.base,java.desktop"
try {
    $DetectedModules = & $Jdeps --ignore-missing-deps --print-module-deps $JarFile 2>$null |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Last 1

    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($DetectedModules)) {
        $Modules = $DetectedModules.Trim()
    }
} catch {
    $Modules = "java.base,java.desktop"
}

if (($Modules -split ",") -notcontains "java.desktop") {
    $Modules = "$Modules,java.desktop"
}

Write-Host "[4/4] Bundling a minimal Java runtime ($Modules)..."
$JlinkArgs = @(
    "--add-modules", $Modules,
    "--strip-debug",
    "--no-header-files",
    "--no-man-pages",
    "--output", $RuntimeDir
)
Invoke-Tool $Jlink $JlinkArgs

$Launcher = @'
@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA_EXE=%APP_HOME%runtime\bin\javaw.exe"

if not exist "%JAVA_EXE%" (
    echo Portable runtime is missing: %JAVA_EXE%
    pause
    exit /b 1
)

if not exist "%APP_HOME%data" mkdir "%APP_HOME%data"

start "" "%JAVA_EXE%" "-Dnotebookme.portable=true" "-Dnotebookme.dataDir=%APP_HOME%data" -jar "%APP_HOME%app\NotebookMe.jar" %*
'@

$ConsoleLauncher = @'
@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA_EXE=%APP_HOME%runtime\bin\java.exe"

if not exist "%JAVA_EXE%" (
    echo Portable runtime is missing: %JAVA_EXE%
    pause
    exit /b 1
)

if not exist "%APP_HOME%data" mkdir "%APP_HOME%data"

"%JAVA_EXE%" "-Dnotebookme.portable=true" "-Dnotebookme.dataDir=%APP_HOME%data" -jar "%APP_HOME%app\NotebookMe.jar" %*
'@

$PortableReadme = @'
NotebookMe Portable
===================

Run:
  NotebookMe.bat

This folder is self-contained for Windows:
  app\NotebookMe.jar     The application
  runtime\               Bundled Java runtime
  data\                  Notes, diary entries, drawings, and app data

No Java install is required on the computer that runs this folder.
Copy the whole NotebookMe-portable folder to a USB drive or another Windows PC.

Use NotebookMe-console.bat only when you want to see console errors while testing.

Note: Java runtimes are operating-system specific. To make a portable build for
macOS or Linux, run the portable build on that operating system.
'@

Set-Content -LiteralPath (Join-Path $PortableDir "NotebookMe.bat") -Value $Launcher -Encoding ASCII
Set-Content -LiteralPath (Join-Path $PortableDir "NotebookMe-console.bat") -Value $ConsoleLauncher -Encoding ASCII
Set-Content -LiteralPath (Join-Path $PortableDir "README-PORTABLE.txt") -Value $PortableReadme -Encoding ASCII

Write-Host "Done. Portable app written to $PortableDir"
