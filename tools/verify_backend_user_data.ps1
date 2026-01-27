param(
  [Parameter(Mandatory = $true)]
  [string]$Uid,

  [Parameter(Mandatory = $true)]
  [string]$ServiceAccountJsonPath,

  [string]$ProjectId = "trimsy-d12de"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ServiceAccountJsonPath)) {
  throw "Service account JSON not found: $ServiceAccountJsonPath"
}

$functionsDir = Join-Path $PSScriptRoot "..\BACKENDTRIMSY\functions"
$functionsDir = (Resolve-Path $functionsDir).Path

if (-not (Test-Path -LiteralPath $functionsDir)) {
  throw "Backend functions folder not found: $functionsDir"
}

Write-Host "[verify] uid=$Uid project=$ProjectId" -ForegroundColor Cyan
Write-Host "[verify] using firebase-admin from: $functionsDir" -ForegroundColor Cyan

Push-Location $functionsDir
try {
  $node = Get-Command node -ErrorAction Stop

  $env:GOOGLE_APPLICATION_CREDENTIALS = (Resolve-Path -LiteralPath $ServiceAccountJsonPath).Path

  $script = @'
const fs = require('fs');
const admin = require('firebase-admin');

const uid = process.env.TRIMSY_VERIFY_UID;
const projectId = process.env.TRIMSY_VERIFY_PROJECT;
const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;

if (!uid) throw new Error('Missing TRIMSY_VERIFY_UID');
if (!projectId) throw new Error('Missing TRIMSY_VERIFY_PROJECT');
if (!keyPath) throw new Error('Missing GOOGLE_APPLICATION_CREDENTIALS');

const keyJson = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
admin.initializeApp({
  credential: admin.credential.cert(keyJson),
  projectId,
});

const db = admin.firestore();

const collectionsToCheck = [
  'driverdata_snapshots',
  'appdata_heads',
  'appdata_chunks',
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
  'law_acceptances',
  'law_contracts',
  'law_quiz_sessions',
  'law_quiz_attempts',
  'law_quiz_passes',
  'law_quiz_state',
  'uid_roots',
  // legacy track app collections (best-effort)
  'uid_state',
  'track_users',
];

async function countQuery(q) {
  // Firestore count() is best, fallback to scanning if unavailable.
  if (typeof q.count === 'function') {
    const snap = await q.count().get();
    return snap.data().count || 0;
  }
  const snap = await q.get();
  return snap.size;
}

async function main() {
  const results = [];

  // driverdata_snapshots is doc-id keyed by uid
  {
    const snap = await db.collection('driverdata_snapshots').doc(uid).get();
    results.push({ name: 'driverdata_snapshots(doc)', count: snap.exists ? 1 : 0 });
  }

  // uid_roots is doc-id keyed by uid
  {
    const snap = await db.collection('uid_roots').doc(uid).get();
    results.push({ name: 'uid_roots(doc)', count: snap.exists ? 1 : 0 });
  }

  // deleted_uids tombstone is doc-id keyed by uid (only set by deleteMe)
  {
    const snap = await db.collection('deleted_uids').doc(uid).get();
    results.push({ name: 'deleted_uids(doc)', count: snap.exists ? 1 : 0 });
  }

  // collections with uid field
  const uidFieldCols = [
    'appdata_heads',
    'appdata_chunks',
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

  for (const col of uidFieldCols) {
    const count = await countQuery(db.collection(col).where('uid', '==', uid));
    results.push({ name: `${col}.where(uid==)`, count });
  }

  // law collections with profileId field = uid
  const profileFieldCols = [
    'law_acceptances',
    'law_contracts',
    'law_quiz_sessions',
    'law_quiz_attempts',
    'law_quiz_passes',
    'law_quiz_state',
  ];

  for (const col of profileFieldCols) {
    const count = await countQuery(db.collection(col).where('profileId', '==', uid));
    results.push({ name: `${col}.where(profileId==)`, count });
  }

  // legacy track app roots (doc-id keyed by uid)
  for (const col of ['uid_state', 'track_users']) {
    try {
      const snap = await db.collection(col).doc(uid).get();
      results.push({ name: `${col}(doc)`, count: snap.exists ? 1 : 0 });
    } catch (e) {
      results.push({ name: `${col}(doc)`, count: -1, error: String(e && e.message ? e.message : e) });
    }
  }

  // Print summary
  const nonZero = results.filter(r => r.count && r.count > 0);
  console.log(JSON.stringify({ ok: nonZero.length === 0, uid, projectId, results }, null, 2));

  if (nonZero.length > 0) {
    process.exitCode = 2;
  }
}

main().catch((e) => {
  console.error(String(e && e.stack ? e.stack : e));
  process.exitCode = 1;
});
'@

  $env:TRIMSY_VERIFY_UID = $Uid
  $env:TRIMSY_VERIFY_PROJECT = $ProjectId

  & node -e $script
  if ($LASTEXITCODE -eq 2) {
    Write-Host "[verify] NOT fully purged (some collections still have data)." -ForegroundColor Yellow
    exit 2
  }
  if ($LASTEXITCODE -ne 0) {
    throw "Verification failed with exit code $LASTEXITCODE"
  }

  Write-Host "[verify] OK: no remaining user-owned docs found in checked collections." -ForegroundColor Green
} finally {
  Pop-Location
}
