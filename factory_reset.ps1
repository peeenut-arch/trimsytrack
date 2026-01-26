# Factory Reset - Clear ALL User Data (Local + Backend)
# This script will completely wipe all user data from device AND Firestore

param(
    [string]$Uid = ""
)

Write-Host "=====================================" -ForegroundColor Red
Write-Host " FACTORY RESET - COMPLETE DATA WIPE" -ForegroundColor Red
Write-Host "=====================================" -ForegroundColor Red
Write-Host ""
Write-Host "This will:" -ForegroundColor Yellow
Write-Host "  1. Clear ALL app data on device" -ForegroundColor Yellow
Write-Host "  2. Delete ALL user data from Firestore backend" -ForegroundColor Yellow
Write-Host "  3. Uninstall the app from device" -ForegroundColor Yellow
Write-Host "  4. Clear all build artifacts" -ForegroundColor Yellow
Write-Host ""
Write-Host "WARNING: THIS IS IRREVERSIBLE!" -ForegroundColor Red
Write-Host ""

# Get UID from logs if not provided
if ([string]::IsNullOrEmpty($Uid)) {
    Write-Host "Detecting UID from device logs..." -ForegroundColor Cyan
    $adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $adbPath) {
        $logOutput = & $adbPath logcat -d 2>$null | Select-String 'identity.*uid.*OPd5' | Select-Object -Last 1
        if ($logOutput -match '"uid":"([^"]+)"') {
            $Uid = $matches[1]
            Write-Host "  [OK] Detected UID: $Uid" -ForegroundColor Green
        }
    }
}

if ([string]::IsNullOrEmpty($Uid)) {
    Write-Host "[ERROR] Could not detect UID from device" -ForegroundColor Red
    Write-Host "Please provide UID manually: .\factory_reset.ps1 -Uid YOUR_UID" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "UID to delete: $Uid" -ForegroundColor Cyan
Write-Host ""
$confirm = Read-Host "Type 'DELETE' to confirm factory reset"
if ($confirm -ne "DELETE") {
    Write-Host "Operation cancelled." -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Starting factory reset..." -ForegroundColor Green

# 1. Delete backend data
Write-Host ""
Write-Host "[1/3] Deleting all backend data for UID..." -ForegroundColor Cyan
Push-Location "BACKENDTRIMSY\functions"
try {
    node tools\delete_user_data.js $Uid
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Backend data deleted" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] Backend deletion failed" -ForegroundColor Red
        Pop-Location
        exit 1
    }
} catch {
    Write-Host "  [ERROR] Error running deletion script: $_" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

# 2. Clear local device data
Write-Host ""
Write-Host "[2/3] Clearing all local device data..." -ForegroundColor Cyan
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $adbPath) {
    & $adbPath shell pm clear com.trimsytrack 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Device data cleared" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Could not clear device data (app may not be installed)" -ForegroundColor Yellow
    }
    
    # Uninstall app
    & $adbPath uninstall com.trimsytrack 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] App uninstalled" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] App not installed on device" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [WARN] ADB not found" -ForegroundColor Yellow
}

# 3. Clear build artifacts
Write-Host ""
Write-Host "[3/3] Clearing build artifacts..." -ForegroundColor Cyan
if (Test-Path "app\build") {
    Remove-Item -Path "app\build" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  [OK] Build directory cleared" -ForegroundColor Green
}
if (Test-Path ".gradle") {
    Remove-Item -Path ".gradle" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  [OK] Gradle cache cleared" -ForegroundColor Green
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Green
Write-Host " FACTORY RESET COMPLETE!" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Green
Write-Host ""
Write-Host "All data for UID $Uid has been permanently deleted." -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Build and install fresh: .\gradlew.bat installDebug" -ForegroundColor White
Write-Host "  2. Sign in with the same or different account" -ForegroundColor White
Write-Host "  3. App will auto-provision new user data" -ForegroundColor White
Write-Host ""
