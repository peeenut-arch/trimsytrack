param(
  [Parameter(Mandatory=$true)][string]$ClientRequestId,
  [string]$ProjectId = "trimsy-d12de",
  [string]$ServiceName = "apiv1"
)

# Opens Google Cloud Logging with a query that matches the given clientRequestId.
# Useful when the client only has `req=<uuid>` but the backend returned an opaque 500.

$rid = $ClientRequestId.Trim()
if ([string]::IsNullOrWhiteSpace($rid)) {
  throw "ClientRequestId cannot be empty."
}

# Broad query that works even if logs are stored in jsonPayload or textPayload.
# Also includes a best-effort service hint for Gen2 (Cloud Run) logs.
$queryLines = @(
  ('("{0}" OR jsonPayload.clientRequestId="{0}")' -f $rid),
  ('(resource.labels.service_name="{0}" OR resource.labels.function_name="apiV1" OR resource.labels.function_name="{0}")' -f $ServiceName)
)

$query = ($queryLines -join "\n")
$queryEscaped = [System.Uri]::EscapeDataString($query)

$url = "https://console.cloud.google.com/logs/query;query=${queryEscaped}?project=$ProjectId"

Write-Host "Opening Cloud Logs for clientRequestId=$rid"
Write-Host $url
Start-Process $url
