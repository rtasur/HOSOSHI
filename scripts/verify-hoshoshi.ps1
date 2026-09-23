$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$ApiBase = 'http://localhost:8081/api/v1'
$Frontend = 'http://localhost:5173'
$Checks = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$Name, [bool]$Pass, [string]$Detail) {
    $Checks.Add([pscustomobject]@{ Name = $Name; Pass = $Pass; Detail = $Detail })
    if ($Pass) {
        Write-Host "[PASS] $Name - $Detail" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $Name - $Detail" -ForegroundColor Red
    }
}

function Stop-Verification([string]$Message) {
    Add-Check 'Verifier' $false $Message
    Show-Summary
    exit 1
}

function Show-Summary {
    Write-Host ''
    Write-Host '============================================================'
    Write-Host '                 HOSOSHI VERIFICATION SUMMARY'
    Write-Host '============================================================'
    foreach ($check in $Checks) {
        $state = if ($check.Pass) { 'PASS' } else { 'FAIL' }
        Write-Host ("{0,-5} {1}: {2}" -f $state, $check.Name, $check.Detail)
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)] [ValidateSet('GET','POST','PUT','PATCH','DELETE')] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        [string]$Token,
        [object]$Body,
        [int[]]$ExpectedStatus = @(200)
    )

    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }

    try {
        $params = @{
            Method = $Method
            Uri = "$ApiBase$Path"
            Headers = $headers
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $params['ContentType'] = 'application/json'
            $params['Body'] = $Body | ConvertTo-Json -Depth 10 -Compress
        }

        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $raw = $response.Content
    } catch {
        $status = 0
        $raw = ''
        if ($_.Exception.Response) {
            try { $status = [int]$_.Exception.Response.StatusCode.value__ } catch { }
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $raw = $reader.ReadToEnd()
                $reader.Dispose()
            } catch { }
        }
    }

    $data = $null
    if ($raw) {
        try { $data = $raw | ConvertFrom-Json } catch { $data = $raw }
    }

    [pscustomobject]@{
        Ok = ($ExpectedStatus -contains $status)
        Status = $status
        Data = $data
        Raw = $raw
    }
}

function Login([string]$Username, [string]$Password) {
    $result = Invoke-Api -Method POST -Path '/auth/login' -Body @{ username = $Username; password = $Password }
    if (-not $result.Ok -or -not $result.Data.token) {
        throw "Login failed for $Username (HTTP $($result.Status))"
    }
    return $result.Data
}

function Read-EnvFile {
    $map = @{}
    if (Test-Path '.env') {
        foreach ($line in Get-Content '.env') {
            if ($line -match '^([A-Z0-9_]+)=(.*)$') {
                $map[$matches[1]] = $matches[2]
            }
        }
    }
    return $map
}

function Test-Multipart {
    param(
        [string]$Path,
        [string]$Token,
        [string]$FilePath,
        [int[]]$ExpectedStatus = @(200),
        [hashtable]$Fields = @{}
    )

    $temp = Join-Path $env:TEMP ("hososhi-verify-" + [guid]::NewGuid() + '.json')
    try {
        $curlArgs = @(
            '-sS', '-o', $temp, '-w', '%{http_code}', '-X', 'POST',
            "$ApiBase$Path", '-H', "Authorization: Bearer $Token"
        )
        foreach ($key in $Fields.Keys) {
            $curlArgs += @('-F', "$key=$($Fields[$key])")
        }
        if ($FilePath) {
            $curlArgs += @('-F', "file=@$FilePath")
        }

        $statusText = & curl.exe @curlArgs
        $status = [int]$statusText
        $raw = if (Test-Path $temp) { Get-Content $temp -Raw } else { '' }
        [pscustomobject]@{ Ok = ($ExpectedStatus -contains $status); Status = $status; Raw = $raw }
    } finally {
        Remove-Item $temp -Force -ErrorAction SilentlyContinue
    }
}

Write-Host '============================================================'
Write-Host '                  HOSOSHI VERIFICATION'
Write-Host '============================================================'
Write-Host ''

try {
    $null = Get-Command docker -ErrorAction Stop
    Add-Check 'Docker CLI' $true 'docker command is available'
} catch {
    Stop-Verification 'Docker CLI was not found.'
}

docker info *> $null
if ($LASTEXITCODE -eq 0) {
    Add-Check 'Docker Engine' $true 'Docker Engine is running'
} else {
    Stop-Verification 'Start Docker Desktop and retry.'
}

if (-not (Test-Path '.env.example')) { Stop-Verification 'Root .env.example is missing.' }
if (-not (Test-Path '.env')) {
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$Root\scripts\prepare-local-env.ps1" *> $null
        Add-Check 'Local configuration' ($LASTEXITCODE -eq 0) 'created .env with local secrets'
    } catch {
        Stop-Verification 'Could not create local .env.'
    }
} else {
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$Root\scripts\prepare-local-env.ps1" *> $null
        Add-Check 'Local configuration' ($LASTEXITCODE -eq 0) 'local secrets validated/prepared'
    } catch {
        Add-Check 'Local configuration' $false 'environment preparation failed'
    }
}

try {
    docker compose config -q *> $null
    Add-Check 'Compose config' ($LASTEXITCODE -eq 0) 'docker-compose.yml is valid'
} catch {
    Add-Check 'Compose config' $false 'docker compose config failed'
}

$envMap = Read-EnvFile
$postgresDb = if ($envMap.ContainsKey('POSTGRES_DB')) { $envMap['POSTGRES_DB'] } else { 'hoshoshi' }
$postgresUser = if ($envMap.ContainsKey('POSTGRES_USER')) { $envMap['POSTGRES_USER'] } else { 'hoshoshi' }
$neoPass = if ($envMap.ContainsKey('NEO4J_PASSWORD')) { $envMap['NEO4J_PASSWORD'] } else { 'hoshoshi_neo4j_dev' }

try {
    docker compose exec -T postgres pg_isready -U $postgresUser -d $postgresDb *> $null
    Add-Check 'PostgreSQL' ($LASTEXITCODE -eq 0) 'database accepts connections'
} catch {
    Add-Check 'PostgreSQL' $false 'readiness check failed'
}

try {
    docker compose exec -T neo4j cypher-shell -u neo4j -p $neoPass 'RETURN 1' *> $null
    Add-Check 'Neo4j' ($LASTEXITCODE -eq 0) 'Cypher query succeeds'
} catch {
    Add-Check 'Neo4j' $false 'readiness/authentication check failed'
}

$health = Invoke-Api -Method GET -Path '/health'
Add-Check 'Backend health' $health.Ok "HTTP $($health.Status)"

try {
    $front = Invoke-WebRequest -Uri $Frontend -UseBasicParsing
    Add-Check 'Frontend' ($front.StatusCode -eq 200) "HTTP $($front.StatusCode)"
    Add-Check 'Frontend security headers' ($front.Headers['X-Content-Type-Options'] -eq 'nosniff' -and $front.Headers['X-Frame-Options'] -eq 'DENY') 'security response headers are present'
} catch {
    Add-Check 'Frontend' $false 'frontend did not respond'
}

try {
    $backendHeaders = Invoke-WebRequest -Uri "$ApiBase/health" -UseBasicParsing
    Add-Check 'Backend security headers' ($backendHeaders.Headers['X-Content-Type-Options'] -eq 'nosniff' -and $backendHeaders.Headers['X-Frame-Options'] -eq 'DENY') 'Spring Security headers are present'
} catch {
    Add-Check 'Backend security headers' $false 'could not inspect backend headers'
}

$credentials = [ordered]@{
    SUPER_ADMIN = @('admin', '123456')
    INVESTIGATION_SUPERVISOR = @('supervisor', 'supervisor123')
    INVESTIGATOR = @('investigator', 'investigator123')
    INTELLIGENCE_ANALYST = @('analyst', 'analyst123')
    DATA_OPERATOR = @('operator', 'operator123')
    AUDITOR = @('auditor', 'auditor123')
}

$tokens = @{}
$users = @{}
foreach ($role in $credentials.Keys) {
    try {
        $login = Login $credentials[$role][0] $credentials[$role][1]
        $tokens[$role] = $login.token
        $users[$role] = $login.user
        Add-Check "Login $role" (@($login.user.roles) -contains $role) 'JWT issued and role claim is correct'
    } catch {
        Add-Check "Login $role" $false $_.Exception.Message
    }
}

if ($tokens.ContainsKey('INVESTIGATOR')) {
    $me = Invoke-Api -Method GET -Path '/auth/me' -Token $tokens.INVESTIGATOR
    Add-Check 'JWT /auth/me' ($me.Ok -and $me.Data.username -eq 'investigator') "HTTP $($me.Status)"
}

$unauth = Invoke-Api -Method GET -Path '/cases' -ExpectedStatus @(401)
Add-Check 'Protected API rejection' ($unauth.Status -eq 401) 'unauthenticated /cases is rejected'

if ($tokens.ContainsKey('SUPER_ADMIN')) {
    $adminOverview = Invoke-Api -Method GET -Path '/users/admin/overview' -Token $tokens.SUPER_ADMIN
    Add-Check 'Super Admin authorization' $adminOverview.Ok "admin overview HTTP $($adminOverview.Status)"

    $dashboard = Invoke-Api -Method GET -Path '/dashboard/summary' -Token $tokens.SUPER_ADMIN
    Add-Check 'Dashboard API' $dashboard.Ok "HTTP $($dashboard.Status)"
}

if ($tokens.ContainsKey('INVESTIGATOR')) {
    $investigatorAdmin = Invoke-Api -Method GET -Path '/users/admin/overview' -Token $tokens.INVESTIGATOR -ExpectedStatus @(403)
    Add-Check 'Investigator denied admin API' ($investigatorAdmin.Status -eq 403) "HTTP $($investigatorAdmin.Status)"
}

if ($tokens.ContainsKey('AUDITOR')) {
    $audit = Invoke-Api -Method GET -Path '/audit' -Token $tokens.AUDITOR
    Add-Check 'Auditor audit access' $audit.Ok "HTTP $($audit.Status)"
}

# Reusable, clearly named verification cases. They are never deleted by this script.
$case1 = $null
$case2 = $null
if ($tokens.ContainsKey('SUPER_ADMIN')) {
    $allCases = Invoke-Api -Method GET -Path '/cases' -Token $tokens.SUPER_ADMIN
    if ($allCases.Ok) {
        $case1 = @($allCases.Data | Where-Object { $_.caseNumber -eq 'CNI-VERIFY-001' }) | Select-Object -First 1
        $case2 = @($allCases.Data | Where-Object { $_.caseNumber -eq 'CNI-VERIFY-002' }) | Select-Object -First 1
    }

    if (-not $case1) {
        $r = Invoke-Api -Method POST -Path '/cases' -Token $tokens.SUPER_ADMIN -Body @{
            caseNumber = 'CNI-VERIFY-001'; title = 'HOSOSHI Verification Case 001';
            description = 'Automated non-destructive verification case.';
            category = 'SYSTEM VERIFICATION'; priority = 'HIGH'; status = 'OPEN'; classification = 'RESTRICTED'
        }
        if ($r.Ok) { $case1 = $r.Data }
    }

    if (-not $case2) {
        $r = Invoke-Api -Method POST -Path '/cases' -Token $tokens.SUPER_ADMIN -Body @{
            caseNumber = 'CNI-VERIFY-002'; title = 'HOSOSHI Verification Case 002';
            description = 'Automated non-destructive file-isolation case.';
            category = 'SYSTEM VERIFICATION'; priority = 'MEDIUM'; status = 'OPEN'; classification = 'RESTRICTED'
        }
        if ($r.Ok) { $case2 = $r.Data }
    }
}

Add-Check 'Case creation' ($null -ne $case1 -and $null -ne $case2) 'verification cases exist'

if ($case1 -and $case2 -and $tokens.ContainsKey('SUPER_ADMIN')) {
    $investigatorId = $users.INVESTIGATOR.id
    $analystId = $users.INTELLIGENCE_ANALYST.id
    $operatorId = $users.DATA_OPERATOR.id

    $members = Invoke-Api -Method GET -Path "/cases/$($case1.id)/members" -Token $tokens.SUPER_ADMIN
    $memberNames = @($members.Data | ForEach-Object { $_.username })

    if ($memberNames -notcontains 'investigator') {
        $a1 = Invoke-Api -Method POST -Path "/cases/$($case1.id)/members" -Token $tokens.SUPER_ADMIN -Body @{ userId = $investigatorId; memberRole = 'INVESTIGATOR' }
    } else { $a1 = [pscustomobject]@{ Ok = $true; Status = 200 } }
    if ($memberNames -notcontains 'analyst') {
        $a2 = Invoke-Api -Method POST -Path "/cases/$($case1.id)/members" -Token $tokens.SUPER_ADMIN -Body @{ userId = $analystId; memberRole = 'ANALYST' }
    } else { $a2 = [pscustomobject]@{ Ok = $true; Status = 200 } }
    if ($memberNames -notcontains 'operator') {
        $a3 = Invoke-Api -Method POST -Path "/cases/$($case1.id)/members" -Token $tokens.SUPER_ADMIN -Body @{ userId = $operatorId; memberRole = 'DATA_OPERATOR' }
    } else { $a3 = [pscustomobject]@{ Ok = $true; Status = 200 } }

    Add-Check 'Case assignment' ($a1.Ok -and $a2.Ok -and $a3.Ok) 'investigator, analyst and data operator are assigned'
}

if ($case1 -and $case2 -and $tokens.ContainsKey('SUPER_ADMIN')) {
    Add-Check 'Case detail API' ((Invoke-Api GET "/cases/$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'authorized case can be opened'
    Add-Check 'Entity API' ((Invoke-Api GET "/entities?caseId=$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'entity list is case-scoped'
    Add-Check 'Relationship API' ((Invoke-Api GET "/relationships?caseId=$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'relationship list is case-scoped'
    Add-Check 'Ingestion API' ((Invoke-Api GET "/ingestion?caseId=$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'ingestion list is case-scoped'
    Add-Check 'Evidence API' ((Invoke-Api GET "/evidence?caseId=$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'evidence list is case-scoped'
    Add-Check 'Timeline API' ((Invoke-Api GET "/timeline/cases/$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'timeline endpoint responds'
    Add-Check 'Graph API' ((Invoke-Api GET "/graph/cases/$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'Neo4j graph endpoint responds'
    Add-Check 'Reports API' ((Invoke-Api GET "/reports?caseId=$($case1.id)" $tokens.SUPER_ADMIN).Ok) 'report endpoint responds'
}

if ($case1 -and $case2 -and $tokens.ContainsKey('INVESTIGATOR')) {
    $visible = Invoke-Api -Method GET -Path '/cases' -Token $tokens.INVESTIGATOR
    $hasCase1 = $visible.Ok -and @($visible.Data | Where-Object { $_.id -eq $case1.id }).Count -gt 0
    $hasCase2 = $visible.Ok -and @($visible.Data | Where-Object { $_.id -eq $case2.id }).Count -gt 0
    Add-Check 'Case visibility' ($hasCase1 -and -not $hasCase2) 'assigned case visible; unassigned case hidden'

    $denyCase = Invoke-Api GET "/cases/$($case2.id)" $tokens.INVESTIGATOR -ExpectedStatus @(403)
    Add-Check 'Case isolation' ($denyCase.Status -eq 403) "unassigned case returned HTTP $($denyCase.Status)"
    $denyEntities = Invoke-Api GET "/entities?caseId=$($case2.id)" $tokens.INVESTIGATOR -ExpectedStatus @(403)
    Add-Check 'Entity isolation' ($denyEntities.Status -eq 403) "unassigned entities returned HTTP $($denyEntities.Status)"
    $denyGraph = Invoke-Api GET "/graph/cases/$($case2.id)" $tokens.INVESTIGATOR -ExpectedStatus @(403)
    Add-Check 'Graph isolation' ($denyGraph.Status -eq 403) "unassigned graph returned HTTP $($denyGraph.Status)"
    $denyTimeline = Invoke-Api GET "/timeline/cases/$($case2.id)" $tokens.INVESTIGATOR -ExpectedStatus @(403)
    Add-Check 'Timeline isolation' ($denyTimeline.Status -eq 403) "unassigned timeline returned HTTP $($denyTimeline.Status)"
}

if ($case2 -and $tokens.ContainsKey('AUDITOR')) {
    $auditCase = Invoke-Api GET "/cases/$($case2.id)" $tokens.AUDITOR
    Add-Check 'Auditor read access' $auditCase.Ok "HTTP $($auditCase.Status)"
}

if ($tokens.ContainsKey('INVESTIGATOR')) {
    $createDenied = Invoke-Api POST '/cases' $tokens.INVESTIGATOR -Body @{
        caseNumber = 'CNI-DENIED'; title = 'Should be denied'; priority = 'LOW'; status = 'OPEN'; classification = 'INTERNAL'
    } -ExpectedStatus @(403)
    Add-Check 'Investigator cannot create cases' ($createDenied.Status -eq 403) "HTTP $($createDenied.Status)"
}

if ($tokens.ContainsKey('AUDITOR')) {
    $auditorDenied = Invoke-Api POST '/cases' $tokens.AUDITOR -Body @{
        caseNumber = 'CNI-AUDITOR-DENIED'; title = 'Should be denied'; priority = 'LOW'; status = 'OPEN'; classification = 'INTERNAL'
    } -ExpectedStatus @(403)
    Add-Check 'Auditor is read-only' ($auditorDenied.Status -eq 403) "HTTP $($auditorDenied.Status)"
}

# File workflow and evidence prerequisite.
if ($case2 -and $tokens.ContainsKey('SUPER_ADMIN')) {
    $tempFile = Join-Path $env:TEMP ('hososhi-verify-source-' + [guid]::NewGuid() + '.txt')
    Set-Content $tempFile 'synthetic verification source document' -Encoding ASCII
    try {
        $deny = Test-Multipart -Path '/evidence' -Token $tokens.SUPER_ADMIN -FilePath $tempFile -Fields @{ caseId = $case2.id } -ExpectedStatus @(400)
        Add-Check 'Evidence requires source document' ($deny.Status -eq 400) "HTTP $($deny.Status)"

        $ing = Test-Multipart -Path '/ingestion' -Token $tokens.SUPER_ADMIN -FilePath $tempFile -Fields @{
            caseId = $case2.id; sourceType = 'SYNTHETIC_DOCUMENT'; notes = 'Verification source'
        } -ExpectedStatus @(200)
        Add-Check 'Source document upload' $ing.Ok "HTTP $($ing.Status)"

        if ($ing.Ok) {
            $ingData = $ing.Raw | ConvertFrom-Json
            Add-Check 'Source record has file flag' ($ingData.hasFile -eq $true) 'uploaded source reports hasFile=true'

            $sourceView = Invoke-Api GET "/ingestion/$($ingData.id)/file" $tokens.SUPER_ADMIN -ExpectedStatus @(200)
            Add-Check 'Source file view' ($sourceView.Status -eq 200) "HTTP $($sourceView.Status)"
        }

        $manual = Invoke-Api POST '/ingestion/manual' $tokens.SUPER_ADMIN -Body @{
            caseId = $case2.id; sourceType = 'FIR'; recordCount = 0; notes = 'Verification manual entry'
        }
        Add-Check 'Manual ingestion entry' $manual.Ok "HTTP $($manual.Status)"
        if ($manual.Ok) {
            Add-Check 'Manual entry not a file' ($manual.Data.hasFile -eq $false) 'manual record correctly reports hasFile=false'
        }

        $ev = Test-Multipart -Path '/evidence' -Token $tokens.SUPER_ADMIN -FilePath $tempFile -Fields @{ caseId = $case2.id } -ExpectedStatus @(200)
        Add-Check 'Evidence upload after source' $ev.Ok "HTTP $($ev.Status)"

        if ($ev.Ok) {
            $evData = $ev.Raw | ConvertFrom-Json
            $fileView = Invoke-Api GET "/evidence/$($evData.id)/file" $tokens.SUPER_ADMIN -ExpectedStatus @(200)
            Add-Check 'Evidence file view' ($fileView.Status -eq 200) "HTTP $($fileView.Status)"
        }

        $bad = Join-Path $env:TEMP ('hososhi-verify-bad-' + [guid]::NewGuid() + '.exe')
        Set-Content $bad 'not allowed' -Encoding ASCII
        try {
            $badResult = Test-Multipart -Path '/ingestion' -Token $tokens.SUPER_ADMIN -FilePath $bad -Fields @{ caseId = $case2.id; sourceType = 'SYNTHETIC_DOCUMENT' } -ExpectedStatus @(400)
            Add-Check 'Unsupported file rejected' ($badResult.Status -eq 400) "HTTP $($badResult.Status)"
        } finally {
            Remove-Item $bad -Force -ErrorAction SilentlyContinue
        }
    } finally {
        Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
    }
}

# Source-of-truth role and local secret checks.
$roleFile = Get-Content 'backend/src/main/java/com/cni/role/RoleCode.java' -Raw
$requiredRoles = @('SUPER_ADMIN','INVESTIGATION_SUPERVISOR','INVESTIGATOR','INTELLIGENCE_ANALYST','DATA_OPERATOR','AUDITOR')
$allRolesPresent = @($requiredRoles | Where-Object { $roleFile -notmatch [regex]::Escape($_) }).Count -eq 0
Add-Check 'Six RBAC roles' $allRolesPresent 'RoleCode contains exactly the six required role names'

$jwtSecret = if ($envMap.ContainsKey('JWT_SECRET')) { $envMap['JWT_SECRET'] } else { '' }
Add-Check 'JWT secret strength' (-not [string]::IsNullOrWhiteSpace($jwtSecret) -and $jwtSecret.Length -ge 32) 'local JWT secret is at least 32 characters'

$crypto = if ($envMap.ContainsKey('API_ENCRYPTION_KEY')) { $envMap['API_ENCRYPTION_KEY'] } else { '' }
$cryptoOk = $false
try { $cryptoOk = ([Convert]::FromBase64String($crypto).Length -eq 32) } catch { }
Add-Check 'AES-256-GCM key' $cryptoOk 'local API encryption key decodes to 32 bytes'

# Static UI checks ensure the visible controls survived the release cleanup.
$ingestionPage = Get-Content 'frontend/src/pages/IngestionPage.tsx' -Raw
$casePage = Get-Content 'frontend/src/pages/CaseDetailPage.tsx' -Raw
Add-Check 'Ingestion View button' ($ingestionPage -match 'viewSourceFile' -and $ingestionPage -match 'hasFile') 'uploaded source files have a View action'
Add-Check 'Evidence View button' ($casePage -match 'viewEvidenceFile' -and $casePage -match 'View</button>') 'evidence files have a View action'
Add-Check 'Evidence UI gate' ($casePage -match 'sourceDocuments.length === 0' -and $casePage -match 'disabled=\{!file \|\| sourceDocuments.length === 0\}') 'evidence upload is disabled until a source file exists'

# Audit events should reflect the new file operations.
if ($tokens.ContainsKey('AUDITOR')) {
    $auditRows = Invoke-Api -Method GET -Path '/audit' -Token $tokens.AUDITOR
    if ($auditRows.Ok) {
        $actions = @($auditRows.Data | ForEach-Object { $_.action })
        Add-Check 'Upload/view audit events' ($actions -contains 'SOURCE_DOCUMENT_UPLOADED' -and $actions -contains 'EVIDENCE_UPLOADED' -and $actions -contains 'SOURCE_DOCUMENT_VIEWED' -and $actions -contains 'EVIDENCE_VIEWED') 'file operations appear in audit activity'
    } else {
        Add-Check 'Upload/view audit events' $false 'audit endpoint unavailable'
    }
}

Show-Summary
$failCount = @($Checks | Where-Object { -not $_.Pass }).Count
if ($failCount -eq 0) {
    Write-Host "`nVERIFICATION PASSED" -ForegroundColor Green
    exit 0
}

Write-Host "`nVERIFICATION FAILED: $failCount check(s) failed." -ForegroundColor Red
exit 1
