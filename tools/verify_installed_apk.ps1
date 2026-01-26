param(
    [string]$PackageName = "com.trimsytrack",
    # The app's Application class (TrimsyTRACK's class name happens to be TrimsyApp historically).
    [string]$ApplicationClassName = "com.trimsytrack.TrimsyApp",
    # Guardrails: fail if we detect markers for the other app (TrimsyApp) in the installed APK.
    [string[]]$ForbiddenApplicationIds = @(
        "com.trimsyapp",
        "se.pierre.skuphoto"
    ),
    # NOTE: Keep this narrowly scoped to package namespaces to avoid false positives
    # (TrimsyTRACK historically has an Application class named TrimsyApp).
    [string[]]$ForbiddenDexPackageSubstrings = @(
        "se.pierre.skuphoto",
        "com.trimsyapp"
    ),
    [string]$LocalApkPath = "${PSScriptRoot}\..\app\build\outputs\apk\debug\app-debug.apk",
    # Optional adb serial. If not provided, the script selects the first online device.
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"

function Resolve-AndroidSdkTool {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][string]$FriendlyName
    )

    $sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
    if (-not $sdkRoot -or -not (Test-Path $sdkRoot)) {
        throw "Android SDK not found at $sdkRoot. Set ANDROID_SDK_ROOT or install the Android SDK."
    }

    $toolPath = Join-Path $sdkRoot $RelativePath
    if (-not (Test-Path $toolPath)) {
        throw "$FriendlyName not found at $toolPath. Install/update Android SDK tools."
    }

    return $toolPath
}

$adb = Resolve-AndroidSdkTool -RelativePath "platform-tools\adb.exe" -FriendlyName "adb"
$apkanalyzer = Resolve-AndroidSdkTool -RelativePath "cmdline-tools\latest\bin\apkanalyzer.bat" -FriendlyName "apkanalyzer"

$devicesRaw = & $adb devices
$deviceLines = $devicesRaw | Select-String -Pattern "\tdevice$"
if (-not $deviceLines) {
    Write-Host "No connected devices found via adb." -ForegroundColor Yellow
    Write-Host "Connect a device (USB debugging enabled) or start an emulator, then rerun." -ForegroundColor Yellow
    exit 2
}

Write-Host "Device(s) detected:" -ForegroundColor Cyan
$deviceLines | ForEach-Object { Write-Host "  $($_.Line)" }

if (-not $DeviceSerial) {
    # Pick the first online device. This avoids failures when an emulator is offline or multiple devices are connected.
    $DeviceSerial = ($deviceLines | Select-Object -First 1).ToString().Split("`t")[0].Trim()
}

if (-not $DeviceSerial) {
    throw "Failed to resolve an adb device serial."
}

Write-Host "Using device: $DeviceSerial" -ForegroundColor Cyan

$pmPathsRaw = & $adb -s $DeviceSerial shell pm path $PackageName
if (-not $pmPathsRaw) {
    throw "Package '$PackageName' not found on device."
}

$apkPaths = @()
foreach ($line in $pmPathsRaw) {
    if ($line -match "^package:(.+)$") {
        $apkPaths += $Matches[1]
    }
}

if (-not $apkPaths) {
    throw "No APK paths returned by 'pm path' for '$PackageName'. Output was: $pmPathsRaw"
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $PSScriptRoot "..\tmp\apk_inspect\$timestamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "Pulling installed APK splits:" -ForegroundColor Cyan
$pulledApks = @()
foreach ($remotePath in $apkPaths) {
    $fileName = Split-Path -Leaf $remotePath
    if (-not $fileName) {
        $fileName = "base.apk"
    }
    $localPath = Join-Path $outDir $fileName

    Write-Host "  $remotePath -> $localPath"
    & $adb -s $DeviceSerial pull $remotePath $localPath | Out-Null
    $pulledApks += $localPath
}

function Inspect-Apk {
    param(
        [Parameter(Mandatory = $true)][string]$ApkPath,
        [Parameter(Mandatory = $true)][string]$Label
    )

    Write-Host "" 
    Write-Host "== $Label ==" -ForegroundColor Green
    Write-Host "APK: $ApkPath"

    $appId = (& $apkanalyzer manifest application-id $ApkPath) -join ""
    $versionName = (& $apkanalyzer manifest version-name $ApkPath) -join ""
    $versionCode = (& $apkanalyzer manifest version-code $ApkPath) -join ""
    $debuggable = (& $apkanalyzer manifest debuggable $ApkPath) -join ""

    Write-Host "applicationId: $appId"
    Write-Host "versionName:   $versionName"
    Write-Host "versionCode:   $versionCode"
    Write-Host "debuggable:    $debuggable"

    if (-not $appId) {
        throw "Failed to read applicationId from APK via apkanalyzer."
    }
    if ($appId -ne $PackageName) {
        throw "Unexpected applicationId '$appId' (expected '$PackageName')."
    }
    if ($ForbiddenApplicationIds -contains $appId) {
        throw "Forbidden applicationId detected: '$appId'."
    }

    $dexPackages = & $apkanalyzer dex packages $ApkPath
    foreach ($needle in $ForbiddenDexPackageSubstrings) {
        if (-not $needle) { continue }
        $hit = $dexPackages | Select-String -Pattern $needle -SimpleMatch -CaseSensitive -Quiet
        if ($hit) {
            throw "Forbidden DEX package marker detected ('$needle') in APK: $ApkPath"
        }
    }

    $classHit = $dexPackages | Select-String -Pattern $ApplicationClassName -SimpleMatch -Quiet
    if ($classHit) {
        Write-Host "Application class present in DEX: $ApplicationClassName" -ForegroundColor Cyan
    } else {
        throw "Missing expected Application class from DEX: $ApplicationClassName"
    }
}

# Inspect local built APK (for comparison)
$localApkFullPath = Resolve-Path -Path $LocalApkPath -ErrorAction SilentlyContinue
if ($localApkFullPath) {
    Inspect-Apk -ApkPath $localApkFullPath.Path -Label "Local build artifact"
} else {
    Write-Host "Local APK not found at $LocalApkPath (skipping local inspection)." -ForegroundColor Yellow
}

# Inspect pulled device APK(s)
foreach ($apk in $pulledApks) {
    Inspect-Apk -ApkPath $apk -Label "Device-installed split"
}

Write-Host "" 
Write-Host "Done. Pulled APKs saved under: $outDir" -ForegroundColor Cyan
