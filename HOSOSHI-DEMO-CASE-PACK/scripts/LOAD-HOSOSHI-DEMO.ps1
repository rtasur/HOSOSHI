$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081/api/v1'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Json($obj) { $obj | ConvertTo-Json -Depth 10 }
function PostJson($uri, $body, $headers) {
  $json = Json $body
  $utf8 = [System.Text.Encoding]::UTF8.GetBytes($json)
  try {
    Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $utf8
  } catch {
    $detail = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($detail)) { $detail = $_.Exception.Message }
    throw "POST $uri failed: $detail"
  }
}
function Write-Ok($m) { Write-Host "[PASS] $m" -ForegroundColor Green }
function Write-Info($m) { Write-Host "[INFO] $m" -ForegroundColor Cyan }

Write-Host "`n=== HOSOSHI DEMO LOAD: OPERATION SILENT HARBOR ===`n" -ForegroundColor Yellow

$health = Invoke-RestMethod "$base/health"
Write-Ok "Backend/PostgreSQL/Neo4j health is UP"

$login = PostJson "$base/auth/login" @{ username = 'supervisor'; password = 'supervisor123' } @{}
$token = $login.token
$headers = @{ Authorization = "Bearer $token" }
Write-Ok "Supervisor login/JWT"

$caseFile = Join-Path $PSScriptRoot '..\data\case.json'
$caseBody = Get-Content $caseFile -Raw | ConvertFrom-Json
$cases = Invoke-RestMethod -Uri "$base/cases" -Headers $headers
$case = $cases | Where-Object { $_.caseNumber -eq $caseBody.caseNumber } | Select-Object -First 1
if (-not $case) {
  $case = PostJson "$base/cases" $caseBody $headers
  Write-Ok "Created case $($case.caseNumber)"
} else {
  Write-Info "Case $($case.caseNumber) already exists; reusing it"
}

$assignable = Invoke-RestMethod -Uri "$base/users/assignable" -Headers $headers
$members = Invoke-RestMethod -Uri "$base/cases/$($case.id)/members" -Headers $headers
$assignments = @(
  @{ username = 'investigator'; role = 'INVESTIGATOR' },
  @{ username = 'analyst'; role = 'INTELLIGENCE_ANALYST' },
  @{ username = 'operator'; role = 'DATA_OPERATOR' }
)
foreach ($a in $assignments) {
  $u = $assignable | Where-Object { $_.username -eq $a.username } | Select-Object -First 1
  if (-not $u) { throw "Assignable user not found: $($a.username)" }
  $already = $members | Where-Object { $_.username -eq $u.username } | Select-Object -First 1
  if (-not $already) {
    PostJson "$base/cases/$($case.id)/members" @{ userId = $u.id; memberRole = $a.role } $headers | Out-Null
    Write-Ok "Assigned $($a.username) as $($a.role)"
  } else { Write-Info "$($a.username) already assigned" }
}

$entityData = Get-Content (Join-Path $PSScriptRoot '..\data\entities.json') -Raw | ConvertFrom-Json
$entityMap = @{}
$existingEntities = Invoke-RestMethod -Uri "$base/entities?caseId=$($case.id)" -Headers $headers
foreach ($e in $entityData) {
  $existing = $existingEntities | Where-Object { $_.primaryName -eq $e.primaryName } | Select-Object -First 1
  if ($existing) {
    $entityMap[$e.key] = $existing
    continue
  }
  $payload = @{
    caseId = $case.id; entityType = $e.entityType; primaryName = $e.primaryName; alias = $e.alias
    caseRole = $e.caseRole; nationality = $e.nationality; phone = $e.phone; email = $e.email
    description = $e.description; locationLabel = $e.locationLabel; locationLat = $e.locationLat
    locationLng = $e.locationLng; sourceReference = $e.sourceReference; confidence = $e.confidence
  }
  $created = PostJson "$base/entities" $payload $headers
  $entityMap[$e.key] = $created
  Write-Ok "Created entity $($e.key) / $($e.primaryName)"
}

$relationshipData = Get-Content (Join-Path $PSScriptRoot '..\data\relationships.json') -Raw | ConvertFrom-Json
$existingRelationships = Invoke-RestMethod -Uri "$base/relationships?caseId=$($case.id)" -Headers $headers
foreach ($r in $relationshipData) {
  $src = $entityMap[$r.from]
  $tgt = $entityMap[$r.to]
  $exists = $existingRelationships | Where-Object {
    $_.source -eq $src.id -and $_.target -eq $tgt.id -and $_.type -eq $r.type
  } | Select-Object -First 1
  if (-not $exists) {
    PostJson "$base/relationships" @{ caseId=$case.id; sourceEntityId=$src.id; targetEntityId=$tgt.id; relationshipType=$r.type; confidence=$r.confidence; sourceReference=$r.sourceReference; observedAt=$r.observedAt } $headers | Out-Null
    Write-Ok "Created relationship $($r.from) -> $($r.to) [$($r.type)]"
  }
}

$docs = Get-ChildItem (Join-Path $PSScriptRoot '..\documents') -File
$existingIngestions = Invoke-RestMethod -Uri "$base/ingestion?caseId=$($case.id)" -Headers $headers
$existingEvidence = Invoke-RestMethod -Uri "$base/evidence?caseId=$($case.id)" -Headers $headers
foreach ($doc in $docs) {
  $ingestionExists = @($existingIngestions | Where-Object { $_.fileName -eq $doc.Name }) | Select-Object -First 1
  if (-not $ingestionExists) {
    $tmp = Join-Path $env:TEMP ("hososhi-ingestion-" + [guid]::NewGuid() + ".json")
    try {
      $status = & curl.exe -sS -o $tmp -w "%{http_code}" -X POST "$base/ingestion" -H "Authorization: Bearer $token" -F "caseId=$($case.id)" -F "sourceType=SYNTHETIC_DOCUMENT" -F "notes=Synthetic demo source for local testing." -F "file=@$($doc.FullName)"
      if ([int]$status -lt 200 -or [int]$status -ge 300) { throw "HTTP $status: $(Get-Content $tmp -Raw)" }
      Write-Ok "Ingestion uploaded: $($doc.Name)"
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
  } else { Write-Info "Ingestion already exists: $($doc.Name)" }

  $evidenceExists = @($existingEvidence | Where-Object { $_.originalName -eq $doc.Name }) | Select-Object -First 1
  if (-not $evidenceExists) {
    $tmp = Join-Path $env:TEMP ("hososhi-evidence-" + [guid]::NewGuid() + ".json")
    try {
      $status = & curl.exe -sS -o $tmp -w "%{http_code}" -X POST "$base/evidence" -H "Authorization: Bearer $token" -F "caseId=$($case.id)" -F "file=@$($doc.FullName)"
      if ([int]$status -lt 200 -or [int]$status -ge 300) { throw "HTTP $status: $(Get-Content $tmp -Raw)" }
      Write-Ok "Evidence uploaded: $($doc.Name)"
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
  } else { Write-Info "Evidence already exists: $($doc.Name)" }
}

$report = Get-Content (Join-Path $PSScriptRoot '..\data\report.json') -Raw | ConvertFrom-Json
PostJson "$base/reports" @{ caseId=$case.id; title=$report.title; content=$report.content } $headers | Out-Null
Write-Ok "Created draft investigation report"

$graph = Invoke-RestMethod -Uri "$base/graph/cases/$($case.id)" -Headers $headers
Write-Ok "Neo4j graph projection rebuilt: $($graph.nodes.Count) nodes / $($graph.edges.Count) relationships"

$timeline = Invoke-RestMethod -Uri "$base/timeline/cases/$($case.id)" -Headers $headers
Write-Ok "Timeline generated: $($timeline.Count) events"

$summary = Invoke-RestMethod -Uri "$base/dashboard/summary" -Headers $headers
Write-Ok "Dashboard: entities=$($summary.entitiesIdentified), connections=$($summary.activeConnections), sourceRecords=$($summary.sourceRecords)"

Write-Host "`nCase ID: $($case.id)" -ForegroundColor Magenta
Write-Host "Case Number: $($case.caseNumber)"
Write-Host "Open: http://localhost:5173"
Write-Host "Neo4j Browser: http://localhost:7474"
Write-Host "`nDemo load complete.`n" -ForegroundColor Green
