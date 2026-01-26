param(
  [string]$PackageName = "com.trimsytrack",
  [string]$KeyName = "trackEventsBackendSupported",
  [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"

function Get-OnlineAdbSerial([string]$adb) {
  & $adb start-server | Out-Null
  for ($i = 0; $i -lt 10; $i++) {
    $serial = (& $adb devices | Select-String -Pattern '^\s*([^\s]+)\s+device\s*$' | ForEach-Object { $_.Matches[0].Groups[1].Value } | Select-Object -First 1)
    if ($serial) { return $serial }
    Start-Sleep -Seconds 1
  }
  return $null
}

function Get-FileBytesViaAdbExecOut([string]$adb, [string[]]$adbArgs) {
  $outFile = Join-Path $env:TEMP ("trimsy_adb_out_" + [Guid]::NewGuid().ToString("N") + ".bin")
  $errFile = Join-Path $env:TEMP ("trimsy_adb_err_" + [Guid]::NewGuid().ToString("N") + ".txt")
  $p = $null
  try {
    $safeArgs = @()
    foreach ($a in $adbArgs) {
      if ($null -eq $a) { throw "adb args contained a null element" }
      $s = [string]$a
      if ([string]::IsNullOrWhiteSpace($s)) { throw "adb args contained an empty/whitespace element" }
      $safeArgs += $s
    }
    $p = Start-Process -FilePath $adb -ArgumentList $safeArgs -NoNewWindow -Wait -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    $err = if (Test-Path $errFile) { Get-Content -LiteralPath $errFile -Raw -ErrorAction SilentlyContinue } else { "" }
    if ((Test-Path $outFile) -and (Get-Item -LiteralPath $outFile).Length -gt 0) {
      return [System.IO.File]::ReadAllBytes($outFile)
    }
    throw "adb exec-out produced no output. stderr: $err"
  }
  finally {
    if (Test-Path $outFile) { Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $errFile) { Remove-Item -LiteralPath $errFile -Force -ErrorAction SilentlyContinue }
    if ($p) { $p.Dispose() }
  }
}

function Read-Varint([byte[]]$bytes, [ref]$i) {
  [UInt64]$result = 0
  $shift = 0
  while ($true) {
    if ($i.Value -ge $bytes.Length) { throw "Unexpected EOF while reading varint" }
    $b = $bytes[$i.Value]
    $i.Value++
    $result = $result -bor (([UInt64]($b -band 0x7F)) -shl $shift)
    if (($b -band 0x80) -eq 0) { break }
    $shift += 7
    if ($shift -gt 63) { throw "Varint too long" }
  }
  return $result
}

function Read-LengthDelimited([byte[]]$bytes, [ref]$i) {
  $len = [int](Read-Varint $bytes $i)
  if ($len -lt 0) { throw "Negative length" }
  if (($i.Value + $len) -gt $bytes.Length) { throw "Unexpected EOF while reading length-delimited" }
  $slice = New-Object byte[] $len
  [Array]::Copy($bytes, $i.Value, $slice, 0, $len)
  $i.Value += $len
  return $slice
}

function Try-Get-PreferenceBool([byte[]]$bytes, [string]$keyName) {
  # Top-level: Preferences { repeated Preference preferences = 1; }
  $i = 0
  while ($i -lt $bytes.Length) {
    $tag = [UInt64](Read-Varint $bytes ([ref]$i))
    $field = [int]($tag -shr 3)
    $wire = [int]($tag -band 7)

    if ($field -eq 1 -and $wire -eq 2) {
      $prefBytes = Read-LengthDelimited $bytes ([ref]$i)

      # Preference { string name = 1; Value value = 2; }
      $j = 0
      $name = $null
      $valueBytes = $null
      while ($j -lt $prefBytes.Length) {
        $ptag = [UInt64](Read-Varint $prefBytes ([ref]$j))
        $pfield = [int]($ptag -shr 3)
        $pwire = [int]($ptag -band 7)
        if ($pfield -eq 1 -and $pwire -eq 2) {
          $nameBytes = Read-LengthDelimited $prefBytes ([ref]$j)
          $name = [System.Text.Encoding]::UTF8.GetString($nameBytes)
        }
        elseif ($pfield -eq 2 -and $pwire -eq 2) {
          $valueBytes = Read-LengthDelimited $prefBytes ([ref]$j)
        }
        else {
          # Skip unknown field
          switch ($pwire) {
            0 { [void](Read-Varint $prefBytes ([ref]$j)) }
            1 { $j += 8 }
            2 { [void](Read-LengthDelimited $prefBytes ([ref]$j)) }
            5 { $j += 4 }
            default { throw "Unsupported wire type in Preference: $pwire" }
          }
        }
      }

      if ($name -eq $keyName -and $valueBytes) {
        # Value oneof; bool is field 1 (varint)
        $k = 0
        while ($k -lt $valueBytes.Length) {
          $vtag = [UInt64](Read-Varint $valueBytes ([ref]$k))
          $vfield = [int]($vtag -shr 3)
          $vwire = [int]($vtag -band 7)
          if ($vfield -eq 1 -and $vwire -eq 0) {
            $raw = [UInt64](Read-Varint $valueBytes ([ref]$k))
            return [bool]($raw -ne 0)
          }
          else {
            # Skip other value types
            switch ($vwire) {
              0 { [void](Read-Varint $valueBytes ([ref]$k)) }
              1 { $k += 8 }
              2 { [void](Read-LengthDelimited $valueBytes ([ref]$k)) }
              5 { $k += 4 }
              default { throw "Unsupported wire type in Value: $vwire" }
            }
          }
        }
        throw "Found key '$keyName' but could not decode as bool"
      }
    }
    else {
      # Skip unknown top-level field
      switch ($wire) {
        0 { [void](Read-Varint $bytes ([ref]$i)) }
        1 { $i += 8 }
        2 { [void](Read-LengthDelimited $bytes ([ref]$i)) }
        5 { $i += 4 }
        default { throw "Unsupported wire type at top-level: $wire" }
      }
    }
  }
  return $null
}

if (-not (Test-Path $AdbPath)) {
  throw "adb not found at '$AdbPath' (set -AdbPath if needed)"
}

$serial = Get-OnlineAdbSerial -adb $AdbPath
if (-not $serial) {
  throw "No online adb device found (check USB debugging + authorization)"
}

$tmpDir = Join-Path $PSScriptRoot "..\tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

# Try the most common location first
$pathsToTry = @(
  "files/datastore/settings.preferences_pb",
  "datastore/settings.preferences_pb"
)

$bytes = $null
$chosenPath = $null
$lastErr = $null
foreach ($p in $pathsToTry) {
  try {
    $adbArgs = @('-s', $serial, 'exec-out', 'run-as', $PackageName, 'cat', $p)
    $bytes = Get-FileBytesViaAdbExecOut -adb $AdbPath -adbArgs $adbArgs
    if ($bytes -and $bytes.Length -gt 0) {
      $chosenPath = $p
      break
    }
  }
  catch {
    $lastErr = $_.Exception.Message
  }
}

if (-not $bytes -or $bytes.Length -eq 0) {
  if ($lastErr) {
    throw "Could not read settings.preferences_pb via run-as/exec-out. Last error: $lastErr"
  }
  throw "Could not read settings.preferences_pb via run-as/exec-out. Is this a debug build? Package=$PackageName"
}

$local = Join-Path $tmpDir "settings.preferences_pb"
[System.IO.File]::WriteAllBytes($local, $bytes)
$val = Try-Get-PreferenceBool -bytes $bytes -keyName $KeyName

if ($null -eq $val) {
  throw "Key '$KeyName' not found in $local"
}

Write-Host "$KeyName = $val"
