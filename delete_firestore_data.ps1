# Delete Firestore user data using Firebase CLI
# Usage: .\delete_firestore_data.ps1 -Uid "YOUR_UID"

param(
    [Parameter(Mandatory=$true)]
    [string]$Uid
)

Write-Host "Deleting Firestore data for UID: $Uid" -ForegroundColor Cyan
Write-Host ""

$projectId = "trimsy-d12de"

# Collections to delete
$paths = @(
    "uid_state/$Uid",
    "track_users/$Uid"
)

foreach ($path in $paths) {
    Write-Host "Deleting: $path" -ForegroundColor Yellow
    & firebase firestore:delete "$path" --project $projectId --recursive --force 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Deleted $path" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Could not delete $path (may not exist)" -ForegroundColor Yellow
    }
}

# Delete driving trips (requires query - manual for now)
Write-Host ""
Write-Host "Note: driving_trips where uid='$Uid' must be deleted manually from Firebase Console" -ForegroundColor Yellow
Write-Host "Or run: Get-ChildItem driving_trips | Where-Object uid -eq '$Uid' | Remove-Item" -ForegroundColor Yellow

Write-Host ""
Write-Host "Firestore deletion complete!" -ForegroundColor Green
