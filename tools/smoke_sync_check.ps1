param(
    [string]$PackageName = "com.trimsytrack",
    [string]$AppId = "com.trimsytrack",
    [string]$AdbPath = "${env:LOCALAPPDATA}\Android\Sdk\platform-tools\adb.exe",
    [string]$GradleInstallCommand = ".\\gradlew.bat installDebug",
    [string]$OutRoot = "${PSScriptRoot}\..\tmp\sync_check",
    [string]$DeviceSerial = "",
    [switch]$DoRestart,
    [switch]$DoReinstall,
    [switch]$AssumeYesForReinstall,
    [int]$NonInteractiveWaitAfterActionSeconds = 25,
    [int]$NonInteractiveWaitAfterReloginSeconds = 25
)

$ErrorActionPreference = "Stop"

$didReinstall = $false

function Ensure-ToolPath {
    param([string]$Path, [string]$Name)
    if (-not $Path -or -not (Test-Path $Path)) {
        throw "$Name not found at: $Path"
    }
}

function Get-OnlineDeviceSerial {
    param([string]$Adb, [string]$Preferred)

    # Ensure adb server is running; avoids stale/stuck server issues.
    try {
        & $Adb start-server | Out-Null
    } catch {
        # Ignore; device detection below will still fail with a clear message.
    }

    $raw = & $Adb devices
    $lines = $raw | Select-String -Pattern "\tdevice$"
    if (-not $lines) {
        # One retry after restarting adb.
        try {
            & $Adb kill-server | Out-Null
            Start-Sleep -Milliseconds 250
            & $Adb start-server | Out-Null
        } catch {
            # Ignore
        }

        $raw = & $Adb devices
        $lines = $raw | Select-String -Pattern "\tdevice$"
    }

    if (-not $lines) {
        Write-Host "adb devices output:" -ForegroundColor Yellow
        $raw | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
        throw "No online adb device found. Ensure USB debugging is enabled and authorized."
    }

    if ($Preferred) {
        $match = $lines | Where-Object { $_.ToString().StartsWith($Preferred + "	") } | Select-Object -First 1
        if ($match) {
            return $Preferred
        }
        throw "Preferred DeviceSerial '$Preferred' not found as an online device."
    }

    return ($lines | Select-Object -First 1).ToString().Split("`t")[0].Trim()
}

function Ensure-OnlineDevice {
    param([string]$Adb)

    # If the previously selected device is still online, keep it.
    if ($script:serial) {
        $raw = & $Adb devices
        $stillOnline = $raw | Select-String -Pattern ([regex]::Escape($script:serial) + "\tdevice$")
        if ($stillOnline) {
            return $script:serial
        }
    }

    # Otherwise, re-select the first online device.
    $script:serial = Get-OnlineDeviceSerial -Adb $Adb -Preferred ""
    Write-Host "(adb) Device changed/reconnected; now using: $script:serial" -ForegroundColor Yellow
    return $script:serial
}

function New-OutDir {
    param([string]$Root)
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $dir = Join-Path $Root $ts
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return (Resolve-Path $dir).Path
}

function Save-Text {
    param([string]$Path, [string[]]$Lines)
    $Lines | Out-File -FilePath $Path -Encoding utf8
}

function Capture-Logcat {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$Pkg,
        [string]$OutFileAll,
        [string]$OutFileFiltered
    )

    $Serial = Ensure-OnlineDevice -Adb $Adb

    $appPidRaw = & $Adb -s $Serial shell pidof $Pkg 2>$null
    $appPid = ($appPidRaw | Out-String).Trim()
    $pidNum = $null
    if ($appPid) {
        try { $pidNum = [int]$appPid } catch { $pidNum = $null }
    }

    $all = @()

    function Invoke-LogcatDump {
        param(
            [string]$Adb,
            [string]$Serial,
            [int]$ProcessId
        )
        if ($ProcessId -gt 0) {
            return & $Adb -s $Serial logcat -d -v threadtime --pid=$ProcessId 2>&1
        }
        return & $Adb -s $Serial logcat -d -v threadtime 2>&1
    }

    $pidArg = 0
    if ($pidNum -ne $null) { $pidArg = $pidNum }

    try {
        $all = Invoke-LogcatDump -Adb $Adb -Serial $Serial -ProcessId $pidArg
    } catch {
        # If the device dropped mid-call, refresh and retry once.
        $Serial = Ensure-OnlineDevice -Adb $Adb
        $all = Invoke-LogcatDump -Adb $Adb -Serial $Serial -ProcessId $pidArg
    }

    # On some devices/ROMs, pid-filtered logcat can come back empty during cold start.
    # If we got nothing, retry briefly and then fall back to non-pid capture.
    if (-not $all -or $all.Count -eq 0) {
        Start-Sleep -Seconds 2
        try {
            $all = Invoke-LogcatDump -Adb $Adb -Serial $Serial -ProcessId $pidArg
        } catch {
            $all = @()
        }
    }

    if (($pidNum -ne $null) -and (-not $all -or $all.Count -eq 0)) {
        try {
            $all = Invoke-LogcatDump -Adb $Adb -Serial $Serial -ProcessId 0
        } catch {
            $all = @()
        }
    }

    Save-Text -Path $OutFileAll -Lines $all

    $pattern = "Handshake ok|handshakeGet|driverdata(Get|Put)|drivingTripCreate|writes disabled|CanonicalWriteOutboxWorker|region verify action|reconcile|restored|uploaded|no_cloud_backup|Cloud sync check failed|UNAUTHENTICATED|ACCOUNT_CONFLICT|UID_DELETED|PROTOCOL_MISMATCH|BackendBlocked|Exception|FATAL"
    $filtered = $all | Select-String -Pattern $pattern
    Save-Text -Path $OutFileFiltered -Lines ($filtered | ForEach-Object { $_.ToString() })
}

function Wait-ForEnterOrSleep {
    param(
        [string]$Prompt = "Press Enter to continue",
        [int]$FallbackSeconds = 20
    )

    # Deterministic behavior in VS Code Tasks: Read-Host can auto-advance.
    # Instead: wait up to FallbackSeconds; if a real console exists, allow Enter to skip.
    Write-Host "${Prompt} (waiting up to ${FallbackSeconds}s; press Enter to continue sooner)" -ForegroundColor Green

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $FallbackSeconds) {
        try {
            if ([Console]::KeyAvailable) {
                $k = [Console]::ReadKey($true)
                if ($k.Key -eq [ConsoleKey]::Enter) {
                    return
                }
            }
        } catch {
            # Non-interactive host (or key APIs unsupported): just sleep the full duration.
            Start-Sleep -Seconds $FallbackSeconds
            return
        }

        Start-Sleep -Milliseconds 200
    }
}

Ensure-ToolPath -Path $AdbPath -Name "adb"

# Pick device
$serial = Get-OnlineDeviceSerial -Adb $AdbPath -Preferred $DeviceSerial
$script:serial = $serial
Write-Host "Using device serial: $serial" -ForegroundColor Cyan

# Prepare output
$outDir = New-OutDir -Root $OutRoot
Write-Host "Writing logs to: $outDir" -ForegroundColor Cyan

# Capture basic device state
Save-Text -Path (Join-Path $outDir "00_adb_devices.txt") -Lines (& $AdbPath devices -l)
Save-Text -Path (Join-Path $outDir "00_pm_path.txt") -Lines (& $AdbPath -s $serial shell pm path $PackageName 2>&1)
Save-Text -Path (Join-Path $outDir "00_dumpsys_package.txt") -Lines (& $AdbPath -s $serial shell dumpsys package $PackageName 2>&1)

# Phase 1: Fresh startup logs
Ensure-OnlineDevice -Adb $AdbPath | Out-Null
& $AdbPath -s $serial logcat -c | Out-Null
& $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 6
Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
    -OutFileAll (Join-Path $outDir "01_startup_all.txt") `
    -OutFileFiltered (Join-Path $outDir "01_startup_filtered.txt")

# Clear so phase 2 logs are isolated to user action/sync
Ensure-OnlineDevice -Adb $AdbPath | Out-Null
& $AdbPath -s $serial logcat -c | Out-Null

# Phase 2: User action
Write-Host "" 
Write-Host "ACTION STEP:" -ForegroundColor Green
Write-Host "On the phone: create/save something (trip, stop, receipt)." -ForegroundColor Green
Write-Host "Then come back here and press Enter." -ForegroundColor Green
Wait-ForEnterOrSleep -Prompt "Press Enter when done" -FallbackSeconds $NonInteractiveWaitAfterActionSeconds
Start-Sleep -Seconds 8
Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
    -OutFileAll (Join-Path $outDir "02_after_action_all.txt") `
    -OutFileFiltered (Join-Path $outDir "02_after_action_filtered.txt")

# Optional: Restart
if ($DoRestart) {
    Write-Host "" 
    Write-Host "RESTART STEP:" -ForegroundColor Green
    Write-Host "Restarting app (force-stop + relaunch)…" -ForegroundColor Green

    Ensure-OnlineDevice -Adb $AdbPath | Out-Null
    & $AdbPath -s $serial shell am force-stop $PackageName | Out-Null
    Start-Sleep -Seconds 1
    Ensure-OnlineDevice -Adb $AdbPath | Out-Null
    & $AdbPath -s $serial logcat -c | Out-Null
    Ensure-OnlineDevice -Adb $AdbPath | Out-Null
    & $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 6

    Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
        -OutFileAll (Join-Path $outDir "03_after_restart_all.txt") `
        -OutFileFiltered (Join-Path $outDir "03_after_restart_filtered.txt")
}

# Optional: Reinstall (uninstall + install + launch)
if ($DoReinstall) {
    Write-Host "" 
    Write-Host "REINSTALL STEP:" -ForegroundColor Yellow
    Write-Host "This will UNINSTALL the app (clears local data)." -ForegroundColor Yellow
    $confirm = ""
    if ($AssumeYesForReinstall) {
        $confirm = "YES"
    } else {
        try {
            $confirm = Read-Host "Type YES to continue"
        } catch {
            $confirm = ""
        }
    }

    if ($confirm -ne "YES") {
        Write-Host "Skipping reinstall." -ForegroundColor Yellow
    } else {
        Ensure-OnlineDevice -Adb $AdbPath | Out-Null
        & $AdbPath -s $serial uninstall $PackageName | Out-Null

        Push-Location (Join-Path $PSScriptRoot "..")
        try {
            Write-Host "Running: $GradleInstallCommand" -ForegroundColor Cyan
            try {
                Invoke-Expression $GradleInstallCommand
                $didReinstall = $true
            } catch {
                Write-Host "" 
                Write-Host "INSTALL STEP FAILED." -ForegroundColor Red
                Write-Host "If you saw an on-device prompt, unlock the phone and tap Accept/Install." -ForegroundColor Yellow
                Write-Host "Common fix on Android 13/14: enable 'Install via USB' (Developer options) and allow installs." -ForegroundColor Yellow
                Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Yellow
                Write-Host "Continuing without reinstall logs." -ForegroundColor Yellow
                $didReinstall = $false
            }
        } finally {
            Pop-Location
        }

        if (-not $didReinstall) {
            $DoReinstall = $false
        }

        Ensure-OnlineDevice -Adb $AdbPath | Out-Null
        & $AdbPath -s $serial logcat -c | Out-Null
        Ensure-OnlineDevice -Adb $AdbPath | Out-Null
        & $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
        Start-Sleep -Seconds 8

        Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
            -OutFileAll (Join-Path $outDir "04_after_reinstall_all.txt") `
            -OutFileFiltered (Join-Path $outDir "04_after_reinstall_filtered.txt")

        # Optional: Re-login after reinstall (prove handshake/restore works after auth is re-established)
        Ensure-OnlineDevice -Adb $AdbPath | Out-Null
        & $AdbPath -s $serial logcat -c | Out-Null

        Write-Host "" 
        Write-Host "RE-LOGIN STEP:" -ForegroundColor Green
        Write-Host "On the phone: sign in again (Google sign-in)." -ForegroundColor Green
        Write-Host "Wait until you see the app's main screen, then come back here and press Enter." -ForegroundColor Green
        Wait-ForEnterOrSleep -Prompt "Press Enter when you're back in the app" -FallbackSeconds $NonInteractiveWaitAfterReloginSeconds

        Start-Sleep -Seconds 10
        Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
            -OutFileAll (Join-Path $outDir "05_after_relogin_all.txt") `
            -OutFileFiltered (Join-Path $outDir "05_after_relogin_filtered.txt")
    }
}

Write-Host "" 
Write-Host "Done. Review filtered logs first:" -ForegroundColor Cyan
Write-Host "  $outDir\01_startup_filtered.txt" -ForegroundColor Cyan
Write-Host "  $outDir\02_after_action_filtered.txt" -ForegroundColor Cyan
if ($DoRestart) { Write-Host "  $outDir\03_after_restart_filtered.txt" -ForegroundColor Cyan }
if ($DoReinstall) {
    Write-Host "  $outDir\04_after_reinstall_filtered.txt" -ForegroundColor Cyan
    Write-Host "  $outDir\05_after_relogin_filtered.txt" -ForegroundColor Cyan
}
