param(
    [string]$PackageName = "com.trimsytrack",
    [string]$AppId = "com.trimsytrack",
    [string]$AdbPath = "${env:LOCALAPPDATA}\Android\Sdk\platform-tools\adb.exe",
    [string]$GradleInstallCommand = ".\\gradlew.bat installDebug",
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"

function Ensure-ToolPath {
    param([string]$Path, [string]$Name)
    if (-not $Path -or -not (Test-Path $Path)) {
        throw "$Name not found at: $Path"
    }
}

function Get-OnlineDeviceSerial {
    param([string]$Adb, [string]$Preferred)

    $raw = & $Adb devices
    $lines = $raw | Select-String -Pattern "\tdevice$"
    if (-not $lines) {
        Write-Host "adb devices output:" -ForegroundColor Yellow
        $raw | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
        throw "No online adb device found. Ensure USB debugging is enabled and authorized."
    }

    if ($Preferred) {
        $match = $lines | Where-Object { $_.ToString().StartsWith($Preferred + "\t") } | Select-Object -First 1
        if ($match) {
            return $Preferred
        }
        throw "Preferred DeviceSerial '$Preferred' not found as an online device."
    }

    return ($lines | Select-Object -First 1).ToString().Split("`t")[0].Trim()
}

Ensure-ToolPath -Path $AdbPath -Name "adb"

Write-Host "Installing debug APK via Gradle…" -ForegroundColor Cyan
Push-Location (Join-Path $PSScriptRoot "..")
try {
    Invoke-Expression $GradleInstallCommand
} finally {
    Pop-Location
}

& $AdbPath start-server | Out-Null

$serial = Get-OnlineDeviceSerial -Adb $AdbPath -Preferred $DeviceSerial
Write-Host "Using device serial: $serial" -ForegroundColor Cyan

Write-Host "Launching app via monkey…" -ForegroundColor Cyan
& $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null

Write-Host "Done." -ForegroundColor Green
