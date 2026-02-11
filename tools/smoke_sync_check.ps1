param(
    [string]$PackageName = "com.trimsytrack",
    [string]$AppId = "com.trimsytrack",
    [string]$AdbPath = "${env:LOCALAPPDATA}\Android\Sdk\platform-tools\adb.exe",
    [string]$GradleInstallCommand = ".\\gradlew.bat installDebug",
    [string]$ApkPath = "${PSScriptRoot}\..\app\build\outputs\apk\debug\app-debug.apk",
    [string]$OutRoot = "${PSScriptRoot}\..\tmp\sync_check",
    [string]$DeviceSerial = "",
    [switch]$DoRestart,
    [switch]$DoReinstall,
    [switch]$AssumeYesForReinstall,
    [int]$NonInteractiveWaitAfterActionSeconds = 25,
    [int]$NonInteractiveWaitAfterReloginSeconds = 25,
    [int]$PostLaunchSleepSeconds = 12,
    [int]$PostActionCaptureDelaySeconds = 20,
    [int]$PostReloginCaptureDelaySeconds = 45,
    [switch]$FailOnFrameworkResultErrors
)

$ErrorActionPreference = "Stop"

$didReinstall = $false
$script:hadCrash = $false
$script:hadFrameworkResultError = $false
$script:outDir = $null

# Always persist the last terminating error into the run folder when possible.
trap {
    try {
        if ($script:outDir) {
            $errText = ($_ | Out-String)
            $errText | Out-File -FilePath (Join-Path $script:outDir "00_fatal_error.txt") -Encoding utf8
        }
    } catch {
        # Ignore secondary failures while handling a failure
    }
    throw
}

function Write-Guidance {
    param(
        [string]$Text,
        [string]$Color = "Cyan"
    )
    # IMPORTANT: Write-Host is host-only; some VS Code task runners can hide/suppress it.
    # Write-Output guarantees the text appears in captured stdout.
    Write-Output $Text
    try {
        Write-Host $Text -ForegroundColor $Color
    } catch {
        # Ignore host rendering failures
    }
}

Write-Guidance "" "Cyan"
Write-Guidance "==================== SMOKE SYNC CHECK (GUIDED) ====================" "Cyan"
Write-Guidance "If you don't see guidance text in VS Code, open the Terminal for the task (not just Problems)." "Cyan"
Write-Guidance "===============================================================" "Cyan"

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

    # IMPORTANT: Keep this list grep-friendly (single-line, pipe-separated) so smoke scripts can assert.
    $pattern = "Handshake ok|handshakeGet|driverdata(Get|Put)|drivingTripCreate|writes disabled|CanonicalWriteOutboxWorker|CanonicalWriteOutbox:|Canonical outbox enqueued|CanonicalSync:|TrackEvents probe succeeded|TrackEvents endpoints returned 404|DriverDataSync: driverdataPut|EvidenceUpload:|SmokeSync:|ParkingTicketExport|region verify action|reconcile|restored|uploaded|no_cloud_backup|Cloud sync check failed|UNAUTHENTICATED|ACCOUNT_CONFLICT|UID_DELETED|PROTOCOL_MISMATCH|BackendBlocked|Exception|FATAL"
    $filtered = $all | Select-String -Pattern $pattern
    Save-Text -Path $OutFileFiltered -Lines ($filtered | ForEach-Object { $_.ToString() })

    # Crash / hard error detection (turn silent crashes into failing smoke runs)
    # NOTE: Do NOT match generic "AndroidRuntime" (monkey and other system components log it routinely).
    $crashPattern = "FATAL EXCEPTION|Fatal signal|ANR in\\s+${Pkg}|SIG(SEGV|ABRT)|Process:\\s+${Pkg}"
    $crashHits = $all | Select-String -Pattern $crashPattern
    if ($crashHits) {
        $script:hadCrash = $true
        $crashFile = $OutFileFiltered -replace "_filtered\\.txt$", "_crash_hits.txt"
        if ($crashFile -eq $OutFileFiltered) { $crashFile = "${OutFileFiltered}.crash_hits.txt" }
        Save-Text -Path $crashFile -Lines ($crashHits | ForEach-Object { $_.ToString() })
        Write-Guidance "CRASH DETECTED in logcat capture. See: $crashFile" "Red"
    }

    # Framework activity-result delivery error (previously observed regression)
    $deliverResultsPattern = "fail in deliverResultsIfNeeded"
    $deliverResultsHits = $all | Select-String -Pattern $deliverResultsPattern
    if ($deliverResultsHits) {
        $script:hadFrameworkResultError = $true
        $frameworkFile = $OutFileFiltered -replace "_filtered\\.txt$", "_framework_result_error_hits.txt"
        if ($frameworkFile -eq $OutFileFiltered) { $frameworkFile = "${OutFileFiltered}.framework_result_error_hits.txt" }
        Save-Text -Path $frameworkFile -Lines ($deliverResultsHits | ForEach-Object { $_.ToString() })
        Write-Guidance "Activity result delivery error detected (deliverResultsIfNeeded). See: $frameworkFile" "Yellow"
    }
}

function Read-FileTextOrEmpty {
    param([string]$Path)
    try {
        if (-not (Test-Path -LiteralPath $Path)) { return "" }
        return (Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue)
    } catch {
        return ""
    }
}

function Assert-RegexHit {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$FailureMessage
    )
    if ($Text -notmatch $Pattern) {
        throw $FailureMessage
    }
}

function Wait-ForSyncMarkers {
    param(
        [string]$PhaseName,
        [string]$Adb,
        [string]$Serial,
        [string]$Pkg,
        [string]$OutDir,
        [int]$MaxWaitSeconds = 140,
        [int]$PollEverySeconds = 20
    )

    $start = Get-Date
    $attempt = 0
    while ($true) {
        $attempt++
        $suffix = "{0:00}" -f $attempt
        $allPath = Join-Path $OutDir ("{0}_poll_{1}_all.txt" -f $PhaseName, $suffix)
        $filteredPath = Join-Path $OutDir ("{0}_poll_{1}_filtered.txt" -f $PhaseName, $suffix)

        Capture-Logcat -Adb $Adb -Serial $Serial -Pkg $Pkg -OutFileAll $allPath -OutFileFiltered $filteredPath
        $text = Read-FileTextOrEmpty -Path $filteredPath

        $hasHandshake = $text -match "Handshake ok"
        $hasDriverDataPut = ($text -match "DriverDataSync: driverdataPut START") -or ($text -match "SmokeSync: driverdataPut outcome=") -or ($text -match "driverdataPut")
        $hasEvidenceAttempt = ($text -match "EvidenceUpload: attempt") -or ($text -match "EvidenceUpload: put ok") -or ($text -match "SmokeSync: evidence uploaded=")
        $hasEvidenceUploaded = ($text -match "EvidenceUpload: put ok") -or ($text -match "EvidenceUpload: done uploaded=([1-9][0-9]*)") -or ($text -match "SmokeSync: evidence uploaded=([1-9][0-9]*)")
        $hasParkingTicketExport = $text -match "ParkingTicketExport ok"
        $hasCanonicalFlushed = $text -match "CanonicalSync: flushed ok=([1-9][0-9]*)"

        if ($hasHandshake -and $hasCanonicalFlushed -and $hasDriverDataPut -and $hasEvidenceAttempt -and $hasEvidenceUploaded -and $hasParkingTicketExport) {
            Write-Guidance "SYNC ASSERTIONS OK for ${PhaseName} (poll ${suffix})." "Green"
            return
        }

        $elapsed = (Get-Date) - $start
        if ($elapsed.TotalSeconds -ge $MaxWaitSeconds) {
            Write-Guidance "SYNC ASSERTIONS FAILED for ${PhaseName} after ${MaxWaitSeconds}s. See: ${filteredPath}" "Red"
            if (-not $hasHandshake) { Write-Guidance "  Missing: Handshake ok" "Yellow" }
            if (-not $hasCanonicalFlushed) { Write-Guidance "  Missing: CanonicalSync flushed ok>0" "Yellow" }
            if (-not $hasDriverDataPut) { Write-Guidance "  Missing: driverdataPut/DriverDataSync markers" "Yellow" }
            if (-not $hasEvidenceAttempt) { Write-Guidance "  Missing: EvidenceUpload attempt markers" "Yellow" }
            if (-not $hasEvidenceUploaded) { Write-Guidance "  Missing: EvidenceUpload success (uploaded>0) markers" "Yellow" }
            if (-not $hasParkingTicketExport) { Write-Guidance "  Missing: ParkingTicketExport ok markers" "Yellow" }
            throw "Smoke test sync assertions failed (see ${filteredPath})."
        }

        Write-Guidance "Waiting ${PollEverySeconds}s for sync markers... (elapsed ${([int]$elapsed.TotalSeconds)}s)" "DarkGray"
        Start-Sleep -Seconds $PollEverySeconds
    }
}

function Install-ApkViaAdb {
    param(
        [string]$Adb,
        [string]$Serial,
        [string]$Apk
    )

    if (-not (Test-Path $Apk)) {
        throw "APK not found at: $Apk (build with .\\gradlew.bat assembleDebug)"
    }

    $Serial = Ensure-OnlineDevice -Adb $Adb
    Write-Host "Running: adb install -r $Apk" -ForegroundColor Cyan

    # Capture full output for diagnostics.
    $out = & $Adb -s $Serial install -r $Apk 2>&1
    $text = ($out | Out-String)
    if ($text -match "Success") {
        return $true
    }

    Write-Host "adb install output:" -ForegroundColor Yellow
    $out | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }

    if ($text -match "INSTALL_FAILED_USER_RESTRICTED") {
        Write-Host "" 
        Write-Host "INSTALL_FAILED_USER_RESTRICTED: the device rejected USB install." -ForegroundColor Red
        Write-Host "Fix on device (common on Android 13/14):" -ForegroundColor Yellow
        Write-Host "  - Unlock phone" -ForegroundColor Yellow
        Write-Host "  - Developer options: enable 'USB debugging'" -ForegroundColor Yellow
        Write-Host "  - Developer options: enable 'Install via USB'" -ForegroundColor Yellow
        Write-Host "  - If prompted: accept/allow the install from this computer" -ForegroundColor Yellow
    }

    return $false
}

function Wait-ForEnterOrSleep {
    param(
        [string]$Prompt = "Press OK/Enter to continue",
        [int]$FallbackSeconds = 20,
        [int]$TickSeconds = 5
    )

    # Deterministic behavior in VS Code Tasks: Read-Host can auto-advance.
    # Instead: wait up to FallbackSeconds; if a real console exists, allow Enter to skip.
    Write-Guidance "${Prompt} (waiting up to ${FallbackSeconds}s; press OK/Enter to finish earlier)" "Green"

    $interactive = $false
    try {
        # In VS Code Tasks / redirected input, touching Console.KeyAvailable can be flaky.
        # Only enable "press Enter to skip" when input is a real console.
        $interactive = [Environment]::UserInteractive -and -not [Console]::IsInputRedirected
    } catch {
        $interactive = $false
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $lastShown = -1
    while ($sw.Elapsed.TotalSeconds -lt $FallbackSeconds) {
        $remaining = [math]::Ceiling($FallbackSeconds - $sw.Elapsed.TotalSeconds)
        if ($remaining -lt 0) { $remaining = 0 }

        # Show a heartbeat/countdown so the operator knows the script is waiting.
        if ($TickSeconds -gt 0) {
            if ($lastShown -lt 0 -or ($lastShown - $remaining) -ge $TickSeconds) {
                Write-Guidance ("  (waiting) {0}s remaining" -f $remaining) "DarkGray"
                $lastShown = $remaining
            }
        }

        if ($interactive) {
            try {
                if ([Console]::KeyAvailable) {
                    $k = [Console]::ReadKey($true)
                    if ($k.Key -eq [ConsoleKey]::Enter) {
                        return
                    }
                }
                Start-Sleep -Milliseconds 200
                continue
            } catch {
                # Fall through to non-interactive sleep
            }
        }

        # Non-interactive host: sleep in ticks so we still print countdown.
        $sleepFor = if ($TickSeconds -gt 0) { [math]::Min($TickSeconds, $remaining) } else { $remaining }
        if ($sleepFor -le 0) { return }
        Start-Sleep -Seconds $sleepFor
    }
}

Ensure-ToolPath -Path $AdbPath -Name "adb"

# Pick device
$serial = Get-OnlineDeviceSerial -Adb $AdbPath -Preferred $DeviceSerial
$script:serial = $serial
Write-Host "Using device serial: $serial" -ForegroundColor Cyan

# Prepare output
$outDir = New-OutDir -Root $OutRoot
$script:outDir = $outDir
Write-Host "Writing logs to: $outDir" -ForegroundColor Cyan

# Best-effort transcript for debugging task/host issues.
try {
    Start-Transcript -Path (Join-Path $outDir "00_script_transcript.txt") -Force | Out-Null
} catch {
    # Ignore transcript failures (common in some hosts)
}

# Capture basic device state
Save-Text -Path (Join-Path $outDir "00_adb_devices.txt") -Lines (& $AdbPath devices -l)
$pmPathLines = & $AdbPath -s $serial shell pm path $PackageName 2>&1
Save-Text -Path (Join-Path $outDir "00_pm_path.txt") -Lines $pmPathLines
Save-Text -Path (Join-Path $outDir "00_dumpsys_package.txt") -Lines (& $AdbPath -s $serial shell dumpsys package $PackageName 2>&1)

$pmText = ($pmPathLines | Out-String)
$isInstalled = $pmText -match "package:"
if (-not $isInstalled) {
    Write-Host "" 
    Write-Host "Package not installed: $PackageName" -ForegroundColor Red
    if (-not $DoReinstall) {
        Write-Host "Install it first (or rerun this script with -DoReinstall)." -ForegroundColor Yellow
        throw "Package $PackageName not installed on device (pm path returned empty)."
    }

    Write-Host "DoReinstall is set; attempting install before smoke steps…" -ForegroundColor Yellow
    $ok = Install-ApkViaAdb -Adb $AdbPath -Serial $serial -Apk $ApkPath
    if (-not $ok) {
        throw "Install failed; cannot continue smoke test without a runnable app."
    }

    # Refresh install state after installing.
    $pmPathLines = & $AdbPath -s $serial shell pm path $PackageName 2>&1
    Save-Text -Path (Join-Path $outDir "00_pm_path_after_install.txt") -Lines $pmPathLines
    $pmText = ($pmPathLines | Out-String)
    $isInstalled = $pmText -match "package:"
    if (-not $isInstalled) {
        throw "Package still not installed after adb install attempt."
    }
}

$resolve = & $AdbPath -s $serial shell cmd package resolve-activity --brief $AppId 2>&1
Save-Text -Path (Join-Path $outDir "00_resolve_activity.txt") -Lines $resolve
$resolveText = ($resolve | Out-String)
if ($resolveText -match "No activity found") {
    Write-Host "" 
    Write-Host "No launchable activity found for: $AppId" -ForegroundColor Red
    Write-Host "If the app is installed but has no launcher activity, monkey cannot start it." -ForegroundColor Yellow
    throw "No launchable activity found for $AppId"
}

Write-Guidance "" "Cyan"
Write-Guidance "==================== SMOKE SYNC CHECK (GUIDED) ====================" "Cyan"
Write-Guidance "This script will write logs to:" "Cyan"
Write-Guidance "  $outDir" "Cyan"
Write-Guidance "Steps:" "Cyan"
Write-Guidance "  1) Launch app + capture startup logs" "Cyan"
Write-Guidance "  2) ACTION: create a trip + add evidence media + add parking fee + add receipt media" "Cyan"
Write-Guidance "  3) Capture logs after ACTION + poll until sync assertions pass (or fail)" "Cyan"
if ($DoRestart) { Write-Guidance "  4) Restart app + capture restart logs" "Cyan" }
if ($DoReinstall) { Write-Guidance "  5) Reinstall app + relogin + capture logs" "Cyan" }
Write-Guidance "When you see 'ACTION STEP', do the phone action." "Cyan"
Write-Guidance "===============================================================" "Cyan"

# Phase 1: Fresh startup logs
$serial = Ensure-OnlineDevice -Adb $AdbPath
& $AdbPath -s $serial logcat -c | Out-Null
& $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds $PostLaunchSleepSeconds
Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
    -OutFileAll (Join-Path $outDir "01_startup_all.txt") `
    -OutFileFiltered (Join-Path $outDir "01_startup_filtered.txt")

# Clear so phase 2 logs are isolated to user action/sync
$serial = Ensure-OnlineDevice -Adb $AdbPath
& $AdbPath -s $serial logcat -c | Out-Null

# Phase 2: User action
Write-Guidance "" "Green"
Write-Guidance "ACTION STEP:" "Green"
Write-Guidance "On the phone:" "Green"
Write-Guidance "  A) Create/save a NEW trip (Driving Journal)" "Green"
Write-Guidance "  B) Add normal evidence media to that trip (photo/PDF)" "Green"
Write-Guidance "  C) Add a Parking/Traffic fee AND attach its receipt media" "Green"
Write-Guidance "  D) Wait until the app has a moment to sync (stay online)" "Green"
Write-Guidance "Then come back here and press Enter." "Green"
Write-Guidance "Tip: you can press OK/Enter anytime to finish earlier." "Green"
Wait-ForEnterOrSleep -Prompt "Press OK/Enter when done" -FallbackSeconds $NonInteractiveWaitAfterActionSeconds
Start-Sleep -Seconds $PostActionCaptureDelaySeconds
Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
    -OutFileAll (Join-Path $outDir "02_after_action_all.txt") `
    -OutFileFiltered (Join-Path $outDir "02_after_action_filtered.txt")

# Assert sync happened (polling). This makes the smoke run fail if media + parking fee never synced.
Wait-ForSyncMarkers -PhaseName "02_after_action" -Adb $AdbPath -Serial $serial -Pkg $PackageName -OutDir $outDir

# Optional: Restart
if ($DoRestart) {
    Write-Guidance "" "Green"
    Write-Guidance "RESTART STEP:" "Green"
    Write-Guidance "Restarting app (force-stop + relaunch)..." "Green"

    $serial = Ensure-OnlineDevice -Adb $AdbPath
    & $AdbPath -s $serial shell am force-stop $PackageName | Out-Null
    Start-Sleep -Seconds 1
    $serial = Ensure-OnlineDevice -Adb $AdbPath
    & $AdbPath -s $serial logcat -c | Out-Null
    $serial = Ensure-OnlineDevice -Adb $AdbPath
    & $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds $PostLaunchSleepSeconds

    Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
        -OutFileAll (Join-Path $outDir "03_after_restart_all.txt") `
        -OutFileFiltered (Join-Path $outDir "03_after_restart_filtered.txt")

    Wait-ForSyncMarkers -PhaseName "03_after_restart" -Adb $AdbPath -Serial $serial -Pkg $PackageName -OutDir $outDir
}

# Optional: Reinstall (uninstall + install + launch)
if ($DoReinstall) {
    Write-Host "" 
    Write-Host "REINSTALL STEP:" -ForegroundColor Yellow
    Write-Host "This will perform a TRUE reinstall (uninstall + install) for a fresh start." -ForegroundColor Yellow
    Write-Host "If reinstall is blocked on your device, it will fall back to a debug reset." -ForegroundColor Yellow
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
        $serial = Ensure-OnlineDevice -Adb $AdbPath

        # Record package metadata before reinstall (proof)
        $pkgBefore = & $AdbPath -s $serial shell dumpsys package $PackageName 2>&1
        Save-Text -Path (Join-Path $outDir "04_dumpsys_package_before_reinstall.txt") -Lines $pkgBefore
        $pkgBeforeMeta = $pkgBefore | Select-String -Pattern "firstInstallTime|lastUpdateTime|versionName|versionCode" -CaseSensitive:$false
        Save-Text -Path (Join-Path $outDir "04_dumpsys_package_before_reinstall_meta.txt") -Lines ($pkgBeforeMeta | ForEach-Object { $_.ToString() })

        $pmBefore = & $AdbPath -s $serial shell pm path $PackageName 2>&1
        Save-Text -Path (Join-Path $outDir "04_pm_path_before_reset.txt") -Lines $pmBefore
        $pmBeforeText = ($pmBefore | Out-String)
        $wasInstalledBefore = $pmBeforeText -match "package:"

        $didInstall = $true
        if (-not $wasInstalledBefore) {
            Push-Location (Join-Path $PSScriptRoot "..")
            try {
                $didInstall = $false
                for ($attempt = 1; $attempt -le 3 -and -not $didInstall; $attempt++) {
                    Write-Host "Install attempt $attempt/3" -ForegroundColor Cyan
                    Write-Host "Running: $GradleInstallCommand" -ForegroundColor Cyan
                    try {
                        Invoke-Expression $GradleInstallCommand
                        if ($LASTEXITCODE -ne 0) {
                            throw "Gradle install failed with exit code $LASTEXITCODE"
                        }
                        $didInstall = $true
                        break
                    } catch {
                        Write-Host "" 
                        Write-Host "INSTALL STEP FAILED." -ForegroundColor Red
                        Write-Host "If you saw an on-device prompt, unlock the phone and tap Accept/Install." -ForegroundColor Yellow
                        Write-Host "Common fix on Android 13/14: enable 'Install via USB' (Developer options) and allow installs." -ForegroundColor Yellow
                        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Yellow

                        Write-Host "Trying fallback install via adb using: $ApkPath" -ForegroundColor Yellow
                        try {
                            $didInstall = Install-ApkViaAdb -Adb $AdbPath -Serial $serial -Apk $ApkPath
                        } catch {
                            Write-Host "Fallback adb install also failed: $($_.Exception.Message)" -ForegroundColor Yellow
                            $didInstall = $false
                        }

                        if (-not $didInstall -and $attempt -lt 3) {
                            Write-Host "" 
                            Write-Host "If you got a device prompt: accept it, then press Enter to retry." -ForegroundColor Yellow
                            try {
                                Read-Host "Press Enter to retry" | Out-Null
                            } catch {
                                Start-Sleep -Seconds 5
                            }
                        }
                    }
                }
            } finally {
                Pop-Location
            }

            if (-not $didInstall) {
                throw "Install failed; cannot continue the reinstall/reset step."
            }
        }

        # TRUE reinstall (uninstall + install). If this fails, fall back to debug reset.
        $serial = Ensure-OnlineDevice -Adb $AdbPath
        Write-Host "Running: adb uninstall $PackageName" -ForegroundColor Cyan
        $uninstallOut = & $AdbPath -s $serial uninstall $PackageName 2>&1
        Save-Text -Path (Join-Path $outDir "04_uninstall_output.txt") -Lines $uninstallOut

        # Confirm uninstall (pm path should be empty).
        $pmAfterUninstall = & $AdbPath -s $serial shell pm path $PackageName 2>&1
        Save-Text -Path (Join-Path $outDir "04_pm_path_after_uninstall.txt") -Lines $pmAfterUninstall
        $pmAfterUninstallText = ($pmAfterUninstall | Out-String)
        $isStillInstalled = $pmAfterUninstallText -match "package:"

        $didTrueReinstall = $false
        if ($isStillInstalled) {
            Write-Host "Warning: package still appears installed after uninstall; falling back to debug reset." -ForegroundColor Yellow
        } else {
            try {
                $didTrueReinstall = Install-ApkViaAdb -Adb $AdbPath -Serial $serial -Apk $ApkPath
            } catch {
                Write-Host "Install via adb failed: $($_.Exception.Message)" -ForegroundColor Yellow
                $didTrueReinstall = $false
            }
        }

        if (-not $didTrueReinstall) {
            # Fallback: Reset local state to simulate a fresh install without requiring uninstall/reinstall.
            # NOTE: Some devices block `pm clear` for shell. We use a debug-only broadcast receiver in the app.
            $serial = Ensure-OnlineDevice -Adb $AdbPath
            $resetAction = "com.trimsytrack.DEBUG_RESET_APP"
            $resetComponent = "$PackageName/com.trimsytrack.debug.DebugResetReceiver"
            Write-Host "FALLBACK: Running debug reset broadcast: adb shell am broadcast -n $resetComponent -a $resetAction" -ForegroundColor Cyan
            $resetOut = & $AdbPath -s $serial shell am broadcast -n $resetComponent -a $resetAction 2>&1
            Save-Text -Path (Join-Path $outDir "04_debug_reset_broadcast.txt") -Lines $resetOut

            $resetText = ($resetOut | Out-String)
            if ($resetText -notmatch "Broadcast completed") {
                Write-Host "Warning: reset broadcast did not report completion (continuing)." -ForegroundColor Yellow
            }
            # NOTE: 'result=0' is common even when a broadcast is delivered; do not treat it as a failure.
        } else {
            $didReinstall = $true
            Write-Host "TRUE reinstall completed (uninstall + install)." -ForegroundColor Green
        }

        # Confirm the app is present before continuing.
        $pmAfter = & $AdbPath -s $serial shell pm path $PackageName 2>&1
        Save-Text -Path (Join-Path $outDir "04_pm_path_after_reset.txt") -Lines $pmAfter
        $pmAfterText = ($pmAfter | Out-String)
        $isInstalledAfter = $pmAfterText -match "package:"
        if (-not $isInstalledAfter) {
            Write-Host "" 
            Write-Host "Reset step failed; app is not installed ($PackageName)." -ForegroundColor Red
            Write-Host "Fix on device: unlock phone; accept the install prompt; enable Developer options -> Install via USB." -ForegroundColor Yellow
            throw "Reset failed: package not installed"
        }

        # Record package metadata after reinstall/reset (proof)
        $pkgAfter = & $AdbPath -s $serial shell dumpsys package $PackageName 2>&1
        Save-Text -Path (Join-Path $outDir "04_dumpsys_package_after_reinstall.txt") -Lines $pkgAfter
        $pkgAfterMeta = $pkgAfter | Select-String -Pattern "firstInstallTime|lastUpdateTime|versionName|versionCode" -CaseSensitive:$false
        Save-Text -Path (Join-Path $outDir "04_dumpsys_package_after_reinstall_meta.txt") -Lines ($pkgAfterMeta | ForEach-Object { $_.ToString() })

        # Prevent the script from exiting with a prior non-zero exit code.
        $global:LASTEXITCODE = 0

        $serial = Ensure-OnlineDevice -Adb $AdbPath
        & $AdbPath -s $serial logcat -c | Out-Null
        $serial = Ensure-OnlineDevice -Adb $AdbPath
        & $AdbPath -s $serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
        Start-Sleep -Seconds 8

        Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
            -OutFileAll (Join-Path $outDir "04_after_reinstall_all.txt") `
            -OutFileFiltered (Join-Path $outDir "04_after_reinstall_filtered.txt")

        # After reinstall (even before relogin), we want to ensure canonical+snapshot+evidence workers can run once auth is present.
        # If the device is still logged-in (some devices restore auth quickly), this will pass; otherwise it will pass on 05_after_relogin.
        try {
            Wait-ForSyncMarkers -PhaseName "04_after_reinstall" -Adb $AdbPath -Serial $serial -Pkg $PackageName -OutDir $outDir -MaxWaitSeconds 60 -PollEverySeconds 15
        } catch {
            Write-Guidance "Note: sync assertions did not pass immediately after reinstall (likely not logged in yet). Will re-check after relogin." "Yellow"
        }

        # Optional: Re-login after reinstall (prove handshake/restore works after auth is re-established)
        $serial = Ensure-OnlineDevice -Adb $AdbPath
        & $AdbPath -s $serial logcat -c | Out-Null

        Write-Host "" 
        Write-Host "RE-LOGIN STEP:" -ForegroundColor Green
        Write-Host "On the phone: sign in again (Google sign-in)." -ForegroundColor Green
        Write-Guidance "Wait until you see the app's main screen, then come back here and press OK/Enter." "Green"
        Write-Guidance "Tip: you can press OK/Enter anytime to finish earlier." "Green"
        Wait-ForEnterOrSleep -Prompt "Press OK/Enter when you're back in the app" -FallbackSeconds $NonInteractiveWaitAfterReloginSeconds

        Write-Guidance "Waiting ${PostReloginCaptureDelaySeconds}s to capture post-login sync…" "DarkGray"
        Start-Sleep -Seconds $PostReloginCaptureDelaySeconds
        Capture-Logcat -Adb $AdbPath -Serial $serial -Pkg $PackageName `
            -OutFileAll (Join-Path $outDir "05_after_relogin_all.txt") `
            -OutFileFiltered (Join-Path $outDir "05_after_relogin_filtered.txt")

        # Assert sync also works after a reinstall+relogin (restore path + background workers).
        Wait-ForSyncMarkers -PhaseName "05_after_relogin" -Adb $AdbPath -Serial $serial -Pkg $PackageName -OutDir $outDir
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

if ($script:hadCrash) {
    throw "Smoke test detected a crash in logcat. See *_crash_hits.txt in $outDir"
}
if ($script:hadFrameworkResultError) {
    $msg = "Smoke test detected ActivityThread deliverResultsIfNeeded error. See *_framework_result_error_hits.txt in $outDir"
    if ($FailOnFrameworkResultErrors) {
        throw $msg
    }
    Write-Guidance $msg "Yellow"
}

try {
    Stop-Transcript | Out-Null
} catch {
    # Ignore
}
