<#
    Aggregates existing JUnit results and the latest compatibility handoff envelope into a harness-only
    config/hydraulic/reports/integration-test-report.json summary. Never invokes gradle. The
    bedrockClientCheck field is always written as PENDING_MANUAL_CLIENT_CHECK - a human confirms the real
    Bedrock client outcome and updates it manually; it is never derived automatically from encoding or
    delivery status alone.
#>
param(
    [string]$RunDir = (Join-Path $PSScriptRoot "..\fabric\run"),
    [string]$SharedBuildDir = (Join-Path $PSScriptRoot "..\shared\build")
)

$ErrorActionPreference = "Stop"

$testResultsDir = Join-Path $SharedBuildDir "test-results\test"
$runtimeTestResultFiles = @(
    "TEST-org.geysermc.hydraulic.compat.runtime.SyncPlannerTest.xml",
    "TEST-org.geysermc.hydraulic.compat.runtime.GeyserSyncTransportTest.xml",
    "TEST-org.geysermc.hydraulic.compat.runtime.TransferBridgeRuntimeTest.xml",
    "TEST-org.geysermc.hydraulic.compat.runtime.RuntimeDispatchTableTest.xml",
    "TEST-org.geysermc.hydraulic.compat.runtime.CompatibilityRuntimeDiagnosticsTest.xml"
)

$runtimeTestsPassed = $true
$runtimeTestsRun = 0
$runtimeTestsFailed = 0
$missingResultFiles = @()

foreach ($fileName in $runtimeTestResultFiles) {
    $path = Join-Path $testResultsDir $fileName
    if (-not (Test-Path $path)) {
        $missingResultFiles += $fileName
        $runtimeTestsPassed = $false
        continue
    }
    [xml]$xml = Get-Content $path -Raw
    $tests = [int]$xml.testsuite.tests
    $failures = [int]$xml.testsuite.failures
    $errors = [int]$xml.testsuite.errors
    $runtimeTestsRun += $tests
    $runtimeTestsFailed += ($failures + $errors)
    if (($failures + $errors) -gt 0) {
        $runtimeTestsPassed = $false
    }
}

if ($missingResultFiles.Count -gt 0) {
    Write-Warning "Missing JUnit result files (run 'Hydraulic: Run Runtime Pipeline Tests' first): $($missingResultFiles -join ', ')"
}

$exportsDir = Join-Path $RunDir "config\hydraulic\cache\handoff-queue\exports"
$latestEnvelopeId = $null
$latestEnvelopeSummary = $null

if (Test-Path $exportsDir) {
    $envelopes = Get-ChildItem -Path $exportsDir -Filter *.json -File | Sort-Object LastWriteTime -Descending
    if ($envelopes.Count -gt 0) {
        $latest = $envelopes[0]
        $json = Get-Content $latest.FullName -Raw | ConvertFrom-Json
        $latestEnvelopeId = $json.envelopeId
        $modProfiles = @()
        if ($json.report -and $json.report.mods) {
            $modProfiles = @(($json.report.mods | Get-Member -MemberType NoteProperty).Name)
        }
        $latestEnvelopeSummary = [ordered]@{
            hydraulicVersion       = $json.hydraulicVersion
            targetMinecraftVersion = $json.targetMinecraftVersion
            targetBedrockVersion   = $json.targetBedrockVersion
            geyserVersion          = $json.geyserVersion
            reportGeneratedAt      = $json.report.generatedAt
            modProfiles            = $modProfiles
        }
    }
}

$packValidationPassed = $null
$packReportPath = Join-Path $RunDir "config\hydraulic\reports\pack-validation-report.json"
if (Test-Path $packReportPath) {
    $packJson = Get-Content $packReportPath -Raw | ConvertFrom-Json
    if ($packJson.perMod) {
        $modIds = ($packJson.perMod | Get-Member -MemberType NoteProperty).Name
        $packValidationPassed = $true
        foreach ($modId in $modIds) {
            if (-not $packJson.perMod.$modId.valid) {
                $packValidationPassed = $false
            }
        }
    }
}

$reportsDir = Join-Path $RunDir "config\hydraulic\reports"
if (-not (Test-Path $reportsDir)) {
    New-Item -ItemType Directory -Path $reportsDir -Force | Out-Null
}

$attestationsSummary = $null
$attestationsFile = Join-Path $reportsDir "client-observation-attestations.json"
if (Test-Path $attestationsFile) {
    try {
        $attJson = Get-Content $attestationsFile -Raw | ConvertFrom-Json
        if ($attJson.attestations) {
            $attestationsSummary = @{
                totalAttestations = $attJson.totalAttestations
                lastUpdated       = $attJson.lastUpdated
                latestVerdict     = ($attJson.attestations | Select-Object -Last 1).verdict
                latestPlatform    = ($attJson.attestations | Select-Object -Last 1).clientPlatform
                latestTarget      = ($attJson.attestations | Select-Object -Last 1).targetObject
            }
        }
    } catch {
        $attestationsSummary = $null
    }
}

$report = [ordered]@{
    generatedAt             = (Get-Date).ToString("o")
    runtimeTestsPassed      = $runtimeTestsPassed
    runtimeTestsRun         = $runtimeTestsRun
    runtimeTestsFailed      = $runtimeTestsFailed
    missingRuntimeTestFiles = $missingResultFiles
    packValidationPassed    = $packValidationPassed
    latestHandoffEnvelopeId = $latestEnvelopeId
    latestHandoffSummary    = $latestEnvelopeSummary
    clientObservationAttestations = $attestationsSummary
    bedrockClientCheck      = $(if ($attestationsSummary -and $attestationsSummary.latestVerdict -eq "PASSED") { "PASSED_ATTESTED" } else { "PENDING_MANUAL_CLIENT_CHECK" })
    bedrockClientCheckNote  = "Connect the real Minecraft Bedrock client (Windows/Mobile) to 127.0.0.1:19132, perform the target action, confirm the resulting trace in the latest handoff export, then record or update via scripts/record-client-attestation.ps1."
}

$outPath = Join-Path $reportsDir "integration-test-report.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $outPath -Encoding UTF8

Write-Host "Wrote integration test report to $outPath"
if (-not $runtimeTestsPassed) {
    Write-Warning "One or more runtime pipeline tests failed or were not run; see $outPath"
}
if ($packValidationPassed -eq $false) {
    Write-Warning "Pack validation reported at least one invalid pack; see $outPath"
}
