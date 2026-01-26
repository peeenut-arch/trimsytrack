# Authoritative Delivery Contract — Apps Author, Backend Is Truth

This contract defines what it means for data to be **real** and **authoritative**.

Short answer:
Yes — this contract should apply to all canonical writes, not just snapshots.

What we’re agreeing on:

Apps create data, but nothing is considered real until the backend acknowledges it.

Clients must persist and retry writes locally until they’re accepted.

The backend is the system of record and will reject conflicts rather than overwrite.

On rejection, clients refetch the latest state and retry by rebasing or re-deriving intent.

Scope clarification:

Snapshots (AppData / DriverData) use the full outbox + commit + rebase flow.

Other canonical writes follow the same rule, but with simpler mechanics (idempotency + refetch-on-conflict).

Only non-canonical data (logs, analytics, telemetry) is excluded.

One-line contract:

“For all canonical data, clients must retain pending writes until backend acknowledgement. Backend acceptance makes data authoritative; conflicts are resolved by refetch-and-retry, never silent overwrite.”
