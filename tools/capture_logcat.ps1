param(
    [string]$Serial,
    [int]$DurationSeconds = 45,
    [switch]$ClearFirst,
    [string]$OutputDir = "$PSScriptRoot\..\tmp\logcat",

    # Include generic crash markers in the filtered output. Off by default to
    # avoid pulling in unrelated crashes from other apps.
    [switch]$IncludeCrashes,

    # Regex pattern passed to Select-String for a filtered output file.
    [string]$FilterPattern = 'fail in deliverResultsIfNeeded|ParkingFeePhoto|onActivityResult rc=|TrimsyTrack|Process: com\.trimsytrack|Killing\s+\d+\:com\.trimsytrack|ANR in com\.trimsytrack|PhotoPicker(GetContent|PickImages)Activity|PhotopickerGetContentActivity|android\.intent\.action\.(GET_CONTENT|OPEN_DOCUMENT)|GmsDocument'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-AdbPath {
    $candidates = @()

    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
    }
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    }

    # Common fallback locations.
    $candidates += @(
        'C:\Android\sdk\platform-tools\adb.exe'
    )

    $found = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($found) { return $found }

    try {
        return (Get-Command adb -ErrorAction Stop).Source
    } catch {
        throw "adb.exe not found. Install Android Platform Tools or set ANDROID_SDK_ROOT/ANDROID_HOME."
    }
}

function Get-FirstDeviceSerial([string]$adb) {
    $lines = & $adb devices
    $serials = @()
    foreach ($line in $lines) {
        if ($line -match '^([0-9a-zA-Z\-_.:]+)\s+device\s*$') {
            $serials += $Matches[1]
        }
    }

    if ($serials.Count -eq 0) {
        throw 'No adb devices found. Ensure USB debugging is enabled and the device is authorized.'
    }

    return $serials[0]
}

$adb = Resolve-AdbPath

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $Serial = Get-FirstDeviceSerial -adb $adb
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$ts = (Get-Date).ToString('yyyyMMdd_HHmmss')
$rawPath = Join-Path $OutputDir "logcat_$ts.raw.txt"
$filteredPath = Join-Path $OutputDir "logcat_$ts.filtered.txt"

if ($IncludeCrashes) {
    $FilterPattern = "($FilterPattern)|FATAL EXCEPTION|AndroidRuntime"
}

Write-Host "adb=$adb"
Write-Host "serial=$Serial"
Write-Host "raw=$rawPath"
Write-Host "filtered=$filteredPath"

if ($ClearFirst) {
    & $adb -s $Serial logcat -c | Out-Null
}

# Capture for DurationSeconds by running adb logcat and killing it.
$p = Start-Process -FilePath $adb -ArgumentList @('-s', $Serial, 'logcat', '-v', 'time') -NoNewWindow -PassThru -RedirectStandardOutput $rawPath
Write-Host "Capturing logcat for $DurationSeconds seconds..."
Start-Sleep -Seconds $DurationSeconds
if (-not $p.HasExited) {
    Stop-Process -Id $p.Id -Force
}

$hits = Select-String -LiteralPath $rawPath -Pattern $FilterPattern
$hits | ForEach-Object { $_.Line } | Set-Content -Encoding UTF8 -LiteralPath $filteredPath

Write-Host "hits=$($hits.Count)"
Write-Host "---- tail (filtered) ----"
Get-Content $filteredPath -Tail 80 | Out-String
