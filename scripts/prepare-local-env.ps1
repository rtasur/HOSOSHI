$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.AddRange([string[]](Get-Content '.env'))

function Get-Value([string]$name) {
    foreach ($line in $lines) {
        if ($line -match ('^' + [regex]::Escape($name) + '=(.*)$')) {
            return $matches[1]
        }
    }
    return $null
}

function Set-Value([string]$name, [string]$value) {
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^' + [regex]::Escape($name) + '=')) {
            $lines[$i] = "$name=$value"
            return
        }
    }
    $lines.Add("$name=$value")
}

function New-Base64Random([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($buffer)
}

$jwt = Get-Value 'JWT_SECRET'
if ([string]::IsNullOrWhiteSpace($jwt) -or $jwt.Length -lt 32) {
    Set-Value 'JWT_SECRET' (New-Base64Random 48)
    Write-Host '[INFO] Generated a strong local JWT secret.' -ForegroundColor Cyan
}

$crypto = Get-Value 'API_ENCRYPTION_KEY'
$cryptoBytes = $null
if (-not [string]::IsNullOrWhiteSpace($crypto)) {
    try {
        $cryptoBytes = [Convert]::FromBase64String($crypto)
    } catch {
        $cryptoBytes = $null
    }
}

if ($null -eq $cryptoBytes -or $cryptoBytes.Length -ne 32) {
    Set-Value 'API_ENCRYPTION_KEY' (New-Base64Random 32)
    Write-Host '[INFO] Generated a strong local AES-256-GCM key.' -ForegroundColor Cyan
}

# ASCII is sufficient for generated Base64 secrets and avoids a UTF-8 BOM in .env
# when this script runs under Windows PowerShell 5.1.
Set-Content -Path '.env' -Value $lines -Encoding ASCII
Write-Host '[PASS] Local environment is ready.' -ForegroundColor Green
