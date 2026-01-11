# TrimsyTRACK - Clear All App Data Script
# This script will completely wipe all user data, trips, images, and reset the app to a clean state

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " TrimsyTRACK Data Wipe Utility" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This will:" -ForegroundColor Yellow
Write-Host "  1. Uninstall the app from connected device" -ForegroundColor Yellow
Write-Host "  2. Delete all local build artifacts" -ForegroundColor Yellow
Write-Host "  3. Clear all cached data" -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Are you sure you want to continue? (yes/no)"
if ($confirm -ne "yes") {
    Write-Host "Operation cancelled." -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Starting cleanup..." -ForegroundColor Green

# 1. Uninstall app from device (clears all app data on device)
Write-Host ""
Write-Host "[1/5] Uninstalling app from device..." -ForegroundColor Cyan
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $adbPath) {
    & $adbPath uninstall com.trimsytrack 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ App uninstalled successfully" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ App not found on device (might already be uninstalled)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ⚠ ADB not found, skipping device cleanup" -ForegroundColor Yellow
}

# 2. Delete build directory
Write-Host ""
Write-Host "[2/5] Clearing build directory..." -ForegroundColor Cyan
$buildPath = "app\build"
if (Test-Path $buildPath) {
    Remove-Item -Path $buildPath -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  ✓ Build directory cleared" -ForegroundColor Green
} else {
    Write-Host "  ✓ Build directory already clean" -ForegroundColor Green
}

# 3. Delete .gradle cache
Write-Host ""
Write-Host "[3/5] Clearing Gradle cache..." -ForegroundColor Cyan
$gradleCachePath = ".gradle"
if (Test-Path $gradleCachePath) {
    Remove-Item -Path $gradleCachePath -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  ✓ Gradle cache cleared" -ForegroundColor Green
} else {
    Write-Host "  ✓ Gradle cache already clean" -ForegroundColor Green
}

# 4. Clean Gradle
Write-Host ""
Write-Host "[4/5] Running Gradle clean..." -ForegroundColor Cyan
& .\gradlew.bat clean | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Gradle clean completed" -ForegroundColor Green
} else {
    Write-Host "  ⚠ Gradle clean had issues (non-critical)" -ForegroundColor Yellow
}

# 5. Clear evidence files from source (if any test files exist)
Write-Host ""
Write-Host "[5/5] Checking for local test evidence files..." -ForegroundColor Cyan
$evidencePaths = @(
    "app\src\main\assets\evidence",
    "app\evidence"
)
$foundEvidence = $false
foreach ($path in $evidencePaths) {
    if (Test-Path $path) {
        Remove-Item -Path $path -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "  ✓ Removed: $path" -ForegroundColor Green
        $foundEvidence = $true
    }
}
if (-not $foundEvidence) {
    Write-Host "  ✓ No local evidence files found" -ForegroundColor Green
}

Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host " Cleanup Complete!" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Build and install the app: .\gradlew.bat installDebug" -ForegroundColor White
Write-Host "  2. The app will start fresh with no data" -ForegroundColor White
Write-Host ""
