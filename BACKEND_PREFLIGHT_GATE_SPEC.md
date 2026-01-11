# Backend Preflight + Law Gate (Cross-app spec)

This file is the shared **UI + behavior contract** to implement identically across Mobile/Web/PC/Electron.
The backend may not be live yet; this spec is for plumbing and consistent UX.

## UI (must-have)

When user attention is required, show a **blocking** gate:
- Top banner + modal (or full-screen gate)
- Title (exact): **Action required to protect your data**
- Must show:
  1) **What failed**
  2) **Why** (use backend `error.message` verbatim)
  3) **One primary fix action** button

Also provide:
- **Outbox / Sync Issues** list: blocked items with error code + primary fix action.
- Never silently drop or endlessly retry a rejected payload.

## Preflight checks (order + labels)

1) Auth token: confirm user is signed in and can mint a fresh Firebase ID token.
2) Backend reachable: call POST `${API_BASE}/lawGet` (Bearer token) and confirm `packSha256` returned.
3) Quiz status: if quiz pass missing → run quiz flow:
   - POST `${API_BASE}/lawQuizGet` → show questions
   - POST `${API_BASE}/lawQuizSubmit` → show score and pass/fail
   - If throttled (429), show countdown using `Retry-After` or `error.details.retryAfterSeconds` and disable submit until allowed.
4) Law acceptance: if acceptance missing for current `packSha256` → POST `${API_BASE}/lawAccept`.
5) Contract view: allow “View Contract” → POST `${API_BASE}/lawContractGet` and display returned markdown with **Copy**.

## Hard gating rules

- Any **Connect / Sync / Write** features must be disabled unless Preflight = PASS
  (quiz passed + acceptance recorded for current `packSha256`).

If any backend call returns:
- **412 failed-precondition** → immediately open Preflight and show the blocking gate.
- **429 resource-exhausted** → show blocking gate + countdown; do not retry-loop.
- **401 unauthenticated** → show “Sign in again”.
- **422/400 invalid data** → mark item Blocked and show “Edit & resubmit” (no auto-retry).

## Error mapping (single fix action)

Always display `error.message`.

If `error.details.machineCode` exists, map to exactly one fix action:
- `QUIZ_COOLDOWN` / `QUIZ_DAILY_LIMIT` → **Wait** (countdown)
- `LAW_NOT_ACCEPTED` / `QUIZ_NOT_PASSED` → **Open Preflight**
- `SAFETY_MODE_WRITE_BLOCKED` → **Read-only mode** (disable writes)
- `VALIDATION_FAILED` → **Edit draft** (no auto-retry)

## Retry policy

- Auto-retry only truly transient failures (network / 5xx) with capped attempts.
- Everything else requires explicit user action.
