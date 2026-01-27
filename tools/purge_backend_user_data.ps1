param(
  [Parameter(Mandatory = $true)]
  [string]$Uid,

  [Parameter(Mandatory = $true)]
  [string]$ServiceAccountJsonPath,

  [string]$ProjectId = "trimsy-d12de",

  [Parameter(Mandatory = $true)]
  [string]$Confirm
)

$ErrorActionPreference = "Stop"

if ($Confirm.Trim() -ne "PURGE_UID_FOREVER") {
  throw "Refusing to run. Pass -Confirm PURGE_UID_FOREVER"
}

if (-not (Test-Path -LiteralPath $ServiceAccountJsonPath)) {
  throw "Service account JSON not found: $ServiceAccountJsonPath"
}

$functionsDir = Join-Path $PSScriptRoot "..\BACKENDTRIMSY\functions"
$functionsDir = (Resolve-Path $functionsDir).Path

Write-Host "[purge] uid=$Uid project=$ProjectId" -ForegroundColor Cyan
Write-Host "[purge] using firebase-admin from: $functionsDir" -ForegroundColor Cyan

Push-Location $functionsDir
try {
  $node = Get-Command node -ErrorAction Stop

  $env:GOOGLE_APPLICATION_CREDENTIALS = (Resolve-Path -LiteralPath $ServiceAccountJsonPath).Path
  $env:TRIMSY_PURGE_UID = $Uid
  $env:TRIMSY_PURGE_PROJECT = $ProjectId

  $script = @'
const fs = require('fs');
const admin = require('firebase-admin');

const uid = process.env.TRIMSY_PURGE_UID;
const projectId = process.env.TRIMSY_PURGE_PROJECT;
const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;

if (!uid) throw new Error('Missing TRIMSY_PURGE_UID');
if (!projectId) throw new Error('Missing TRIMSY_PURGE_PROJECT');
if (!keyPath) throw new Error('Missing GOOGLE_APPLICATION_CREDENTIALS');

const keyJson = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
admin.initializeApp({
  credential: admin.credential.cert(keyJson),
  projectId,
});

const db = admin.firestore();

async function deleteQueryInBatches(query, pageSize = 200) {
  // Reliability-first: delete individually to avoid Firestore "Transaction too big" errors.
  pageSize = Math.max(1, Math.min(200, pageSize));
  let deleted = 0;
  while (true) {
    const snap = await query.limit(pageSize).get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      await doc.ref.delete();
      deleted++;
    }
  }
  return deleted;
}

async function main() {
  const out = {};

  // DriverData snapshot (doc id = uid)
  {
    const ref = db.collection('driverdata_snapshots').doc(uid);
    const snap = await ref.get();
    if (snap.exists) {
      await ref.delete();
      out.driverdata_snapshots = 1;
    } else {
      out.driverdata_snapshots = 0;
    }
  }

  // AppData chunked snapshots
  out.appdata_heads = await deleteQueryInBatches(db.collection('appdata_heads').where('uid', '==', uid));
  out.appdata_chunks = await deleteQueryInBatches(db.collection('appdata_chunks').where('uid', '==', uid));

  // Canonical/user-owned collections (field = uid)
  const uidCollections = [
    'canonical_events',
    'idempotency_keys',
    'notifications',
    'products',
    'receipts',
    'receipt_rows',
    'product_category_number',
    'product_cost_allocations',
    'storage_slots',
    'driving_trips',
    'driving_trip_client_refs',
  ];

  for (const col of uidCollections) {
    out[col] = await deleteQueryInBatches(db.collection(col).where('uid', '==', uid));
  }

  // Law-related profile-scoped collections (field = profileId)
  const profileCollections = [
    'law_acceptances',
    'law_contracts',
    'law_quiz_sessions',
    'law_quiz_attempts',
    'law_quiz_passes',
    'law_quiz_state',
  ];

  for (const col of profileCollections) {
    out[col] = await deleteQueryInBatches(db.collection(col).where('profileId', '==', uid));
  }

  // uid root marker
  {
    const ref = db.collection('uid_roots').doc(uid);
    const snap = await ref.get();
    if (snap.exists) {
      await ref.delete();
      out.uid_roots = 1;
    } else {
      out.uid_roots = 0;
    }
  }

  // Legacy track app roots (best-effort)
  for (const col of ['uid_state', 'track_users']) {
    try {
      const ref = db.collection(col).doc(uid);
      const snap = await ref.get();
      if (snap.exists) {
        await ref.delete();
        out[col] = 1;
      } else {
        out[col] = 0;
      }
    } catch (e) {
      out[col] = -1;
      out[`${col}_error`] = String(e && e.message ? e.message : e);
    }
  }

  console.log(JSON.stringify({ ok: true, uid, projectId, deleted: out }, null, 2));
}

main().catch((e) => {
  console.error(String(e && e.stack ? e.stack : e));
  process.exitCode = 1;
});
'@

  $safeUid = ($Uid -replace '[^A-Za-z0-9_-]', '_')
  $tmpJs = Join-Path $functionsDir ".tmp_purge_${safeUid}_$PID.cjs"
  try {
    Set-Content -LiteralPath $tmpJs -Value $script -Encoding UTF8
    & node $tmpJs
  } finally {
    Remove-Item -LiteralPath $tmpJs -Force -ErrorAction SilentlyContinue
  }
  if ($LASTEXITCODE -ne 0) {
    throw "Purge failed with exit code $LASTEXITCODE"
  }

  Write-Host "[purge] Done." -ForegroundColor Green
} finally {
  Pop-Location
}
