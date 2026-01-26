param(
    [string]$PackageName = "com.trimsytrack",
    [string]$LocalApkPath = "${PSScriptRoot}\..\app\build\outputs\apk\debug\app-debug.apk",
    [string]$DeviceSerial = "",
    [int]$PostLaunchSeconds = 6,
    [int]$AfterActionSeconds = 4,
    [switch]$SkipReinstall = $false
)

$ErrorActionPreference = "Stop"

function Resolve-AndroidSdkTool {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$FriendlyName
    )

    $sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
    if (-not $sdkRoot -or -not (Test-Path $sdkRoot)) {
        throw "Android SDK not found at $sdkRoot. Install Android SDK or fix LOCALAPPDATA."
    }

    $toolPath = Join-Path $sdkRoot $RelativePath
    if (-not (Test-Path $toolPath)) {
        throw "$FriendlyName not found at $toolPath. Install/update Android SDK tools."
    }

    return $toolPath
}

$adb = Resolve-AndroidSdkTool -RelativePath "platform-tools\adb.exe" -FriendlyName "adb"

function Get-OnlineDeviceSerial {
    $devicesRaw = & $adb devices
    $deviceLines = $devicesRaw | Select-String -Pattern "\tdevice$"
    if (-not $deviceLines) {
        throw "No online adb devices found. Enable USB debugging and accept the authorization prompt."
    }

    return ($deviceLines | Select-Object -First 1).ToString().Split("`t")[0].Trim()
}

if (-not $DeviceSerial) {
    $DeviceSerial = Get-OnlineDeviceSerial
}

Write-Host "Using device: $DeviceSerial" -ForegroundColor Cyan

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $PSScriptRoot "..\tmp\smoke_sync\$timestamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$pattern = "Handshake ok|handshakeGet|driverdataPut|driverdataGet|drivingTripCreate|region verify action|Worker result|BackendBlocked|UID_DATA_MISSING|UNAUTHENTICATED|ACCOUNT_CONFLICT|UID_DELETED|PROTOCOL_MISMATCH|Cloud sync check failed|Exception|FATAL| 401 | 403 | 500 "

function Clear-Logcat {
    & $adb -s $DeviceSerial logcat -c | Out-Null
}

function Launch-App {
    & $adb -s $DeviceSerial shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 | Out-Null
}

function ForceStop-App {
    & $adb -s $DeviceSerial shell am force-stop $PackageName | Out-Null
}

function Get-AppPid {
    return ((& $adb -s $DeviceSerial shell pidof $PackageName) -join "").Trim()
}

function Dump-Logs {
    param(
        [Parameter(Mandatory = $true)][string]$PhaseName
    )

    $appPid = Get-AppPid
    $fullPath = Join-Path $outDir ("{0}_full.logcat.txt" -f $PhaseName)
    $filteredPath = Join-Path $outDir ("{0}_filtered.txt" -f $PhaseName)

    if ($appPid) {
        & $adb -s $DeviceSerial logcat -d --pid=$appPid | Out-File -FilePath $fullPath -Encoding utf8
        & $adb -s $DeviceSerial logcat -d --pid=$appPid | Select-String -Pattern $pattern | Out-File -FilePath $filteredPath -Encoding utf8
    } else {
        & $adb -s $DeviceSerial logcat -d | Out-File -FilePath $fullPath -Encoding utf8
        & $adb -s $DeviceSerial logcat -d | Select-String -Pattern $pattern | Out-File -FilePath $filteredPath -Encoding utf8
    }
}

function Phase {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [switch]$Launch,
        [switch]$PromptForAction
    )

    Write-Host "" 
    Write-Host "=== $Name ===" -ForegroundColor Green

    Clear-Logcat

    if ($Launch) {
        Launch-App
        Start-Sleep -Seconds $PostLaunchSeconds
    }

    if ($PromptForAction) {
        Write-Host "Do your action now (create/save), then press ENTER to capture logs..." -ForegroundColor Yellow
        [void][System.Console]::ReadLine()
        Start-Sleep -Seconds $AfterActionSeconds
    }

    Dump-Logs -PhaseName $Name
    Write-Host "Saved: $outDir\${Name}_filtered.txt" -ForegroundColor Cyan
}

# Phase 1: cold launch
Phase -Name "01_cold_launch" -Launch

# Phase 2: create/save action window (manual)
Phase -Name "02_create_or_edit_then_save" -PromptForAction

# Phase 3: restart check
Write-Host "" 
Write-Host "=== 03_restart_check ===" -ForegroundColor Green
Clear-Logcat
ForceStop-App
Start-Sleep -Seconds 1
Launch-App
Start-Sleep -Seconds $PostLaunchSeconds
Dump-Logs -PhaseName "03_restart_check"
Write-Host "Saved: $outDir\03_restart_check_filtered.txt" -ForegroundColor Cyan

# Phase 4: reinstall check
if (-not $SkipReinstall) {
    Write-Host "" 
    Write-Host "=== 04_reinstall_check ===" -ForegroundColor Green

    $apk = Resolve-Path -Path $LocalApkPath -ErrorAction SilentlyContinue
    if (-not $apk) {
        throw "Local APK not found at $LocalApkPath. Build first (assembleDebug)."
    }

    Clear-Logcat
    Write-Host "Uninstalling $PackageName..." -ForegroundColor Yellow
    & $adb -s $DeviceSerial uninstall $PackageName | Out-Null

    Write-Host "Installing APK: $($apk.Path)" -ForegroundColor Yellow
    & $adb -s $DeviceSerial install -r $apk.Path | Out-Null

    Launch-App
    Start-Sleep -Seconds $PostLaunchSeconds
    Dump-Logs -PhaseName "04_reinstall_check"
    Write-Host "Saved: $outDir\04_reinstall_check_filtered.txt" -ForegroundColor Cyan
}

Write-Host "" 
Write-Host "DONE. All logs are in: $outDir" -ForegroundColor Cyan
Write-Host "Open the *_filtered.txt files first (they contain the important lines)." -ForegroundColor Cyan
