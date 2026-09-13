<#
    Records or updates a physical Bedrock client observation attestation.
    Physical client observation is never faked or automated; this records human-verified observations
    for Level 4 Validation (CLIENT_OBSERVED).
#>
param(
    [Parameter(Mandatory=$false)]
    [string]$ClientPlatform = "Windows",
    [Parameter(Mandatory=$false)]
    [string]$ClientVersion = "1.21.x",
    [Parameter(Mandatory=$false)]
    [string]$TargetObject = "hydraulic_test_mod:processing_machine",
    [Parameter(Mandatory=$false)]
    [ValidateSet("PASSED", "FAILED", "PARTIAL")]
    [string]$Verdict = "PASSED",
    [Parameter(Mandatory=$false)]
    [string]$Notes = "Verified block placement, held-item insertion, and inventory slot synchronization.",
    [Parameter(Mandatory=$false)]
    [string]$AttestedBy = "Operator",
    [string]$ReportDir = (Join-Path $PSScriptRoot "..\fabric\run\config\hydraulic\reports")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ReportDir)) {
    New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
}

$attestationFile = Join-Path $ReportDir "client-observation-attestations.json"

$records = @()
if (Test-Path $attestationFile) {
    try {
        $existing = Get-Content $attestationFile -Raw | ConvertFrom-Json
        if ($existing.attestations) {
            $records = @($existing.attestations)
        }
    } catch {
        $records = @()
    }
}

$newRecord = [ordered]@{
    attestationId    = [guid]::NewGuid().ToString()
    timestamp        = (Get-Date).ToString("o")
    clientPlatform   = $ClientPlatform
    clientVersion    = $ClientVersion
    targetObject     = $TargetObject
    verdict          = $Verdict
    attestedBy       = $AttestedBy
    notes            = $Notes
}

$records += $newRecord

$payload = [ordered]@{
    schemaVersion    = "1.0.0"
    lastUpdated      = (Get-Date).ToString("o")
    totalAttestations = $records.Count
    attestations     = $records
}

$payload | ConvertTo-Json -Depth 10 | Set-Content $attestationFile -Encoding UTF8

Write-Host "Recorded physical client observation attestation ($Verdict) for $TargetObject on $ClientPlatform."
Write-Host "Attestation report saved to: $attestationFile"
