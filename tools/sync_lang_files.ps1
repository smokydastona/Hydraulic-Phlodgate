param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SourceLocale = 'en_us.json',
    [bool]$PruneExtraKeys = $true,
    [bool]$OverwriteEnglishFallbacks = $false
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Find-LanguageDirectory {
    param([string]$root)

    $candidates = @(
        (Join-Path $root 'shared/src/main/resources/assets'),
        (Join-Path $root 'src/main/resources/assets'),
        (Join-Path $root 'shared/src/main/generated/assets'),
        (Join-Path $root 'src/main/generated/assets'),
        (Join-Path $root 'test/src/main/resources/assets'),
        (Join-Path $root 'test/src/main/generated/assets'),
        (Join-Path $root 'fabric/run/config/hydraulic/storage'),
        (Join-Path $root 'fabric/run/config')
    )

    $rankedLangDirs = New-Object System.Collections.Generic.List[object]

    foreach ($candidate in $candidates) {
        if (-not (Test-Path $candidate)) { continue }

        $dirs = Get-ChildItem -Path $candidate -Recurse -Directory -Filter 'lang' -ErrorAction SilentlyContinue
        foreach ($dir in $dirs) {
            $path = $dir.FullName
            $lower = $path.ToLowerInvariant()
            $priority = 1000

            if ($lower.Contains('src/main/generated') -or $lower.Contains('shared/src/main/generated')) { $priority = 0 }
            elseif ($lower.Contains('src/main/resources') -or $lower.Contains('shared/src/main/resources')) { $priority = 10 }
            elseif ($lower.Contains('fabric/run/config')) { $priority = 100 }

            if ($lower.Contains('/build/') -or $lower.Contains('\\build\\') -or $lower.Contains('/bin/') -or $lower.Contains('\\bin\\')) { $priority += 200 }
            if ($lower.Contains('datagen')) { $priority += 300 }
            if ($lower.Contains('runtimeresources')) { $priority += 400 }

            $rankedLangDirs.Add([pscustomobject]@{ Path = $path; Priority = $priority })
        }
    }

    if ($rankedLangDirs.Count -gt 0) {
        $selected = $rankedLangDirs |
            Sort-Object @{ Expression = 'Priority'; Ascending = $true }, @{ Expression = 'Path'; Ascending = $true } |
            Select-Object -First 1

        return $selected.Path
    }

    $allLangDirs = Get-ChildItem -Path $root -Recurse -Directory -Filter 'lang' -ErrorAction SilentlyContinue |
        Sort-Object FullName

    if ($allLangDirs -and $allLangDirs.Count -gt 0) {
        return $allLangDirs[0].FullName
    }

    throw "No language directory was found under '$root'. Expected assets/<namespace>/lang or generated config language folders."
}

function Resolve-SourceLanguageFile {
    param(
        [string]$langDir,
        [string]$sourceLocale
    )

    $sourcePath = Join-Path $langDir $sourceLocale
    if (Test-Path $sourcePath) {
        return $sourcePath
    }

    $matching = Get-ChildItem -Path $langDir -Filter *.json -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)^en(_|-)us\.json$|^en\.json$' } |
        Sort-Object Name

    if ($matching -and $matching.Count -gt 0) {
        return $matching[0].FullName
    }

    $allJson = Get-ChildItem -Path $langDir -Filter *.json -ErrorAction SilentlyContinue |
        Sort-Object Name

    if (-not $allJson -or $allJson.Count -eq 0) {
        throw "No JSON language files were found in '$langDir'."
    }

    return $allJson[0].FullName
}

function Read-JsonObject {
    param([string]$path)
    $raw = Get-Content -Raw -Path $path
    return ConvertFrom-Json -InputObject $raw
}

function ConvertTo-BstMap {
    param($input)

    $map = @{}
    if ($null -eq $input) { return $map }

    foreach ($property in $input.PSObject.Properties) {
        $map[$property.Name] = $property.Value
    }

    return $map
}

function Test-SequenceEqual {
    param($left, $right)

    if ($null -eq $left -or $null -eq $right) { return $false }
    if ($left.Count -ne $right.Count) { return $false }

    for ($i = 0; $i -lt $left.Count; $i++) {
        if ($left[$i] -ne $right[$i]) {
            return $false
        }
    }

    return $true
}

function Write-OrderedJson {
    param(
        [string]$path,
        $orderedMap
    )

    $json = $orderedMap | ConvertTo-Json -Depth 20
    if (-not $json.EndsWith("`n")) {
        $json += "`n"
    }

    Set-Content -Path $path -Value $json -Encoding UTF8
}

$resolvedRoot = if ([string]::IsNullOrWhiteSpace($RepoRoot)) { (Get-Location).Path } else { $RepoRoot }
if (-not (Test-Path $resolvedRoot)) {
    throw "Repository root '$resolvedRoot' does not exist."
}

$langDir = Find-LanguageDirectory -root $resolvedRoot
$sourcePath = Resolve-SourceLanguageFile -langDir $langDir -sourceLocale $SourceLocale
$sourceObj = Read-JsonObject -path $sourcePath
$sourceOrder = @($sourceObj.PSObject.Properties.Name)
$sourceMap = ConvertTo-BstMap -input $sourceObj

$localeFiles = Get-ChildItem -Path $langDir -Filter *.json -ErrorAction SilentlyContinue |
    Sort-Object Name

$created = 0
$updated = 0

foreach ($localeFile in $localeFiles) {
    if ($localeFile.FullName -eq $sourcePath) {
        continue
    }

    $destObj = Read-JsonObject -path $localeFile.FullName
    $destMap = ConvertTo-BstMap -input $destObj
    $destOrder = @($destObj.PSObject.Properties.Name)

    $output = [ordered]@{}
    foreach ($key in $sourceOrder) {
        if ($destMap.ContainsKey($key)) {
            $existingValue = $destMap[$key]
            if ($OverwriteEnglishFallbacks -and $existingValue -eq $sourceMap[$key]) {
                $output[$key] = $sourceMap[$key]
            }
            else {
                $output[$key] = $existingValue
            }
        }
        else {
            $output[$key] = $sourceMap[$key]
        }
    }

    if (-not $PruneExtraKeys) {
        foreach ($property in $destObj.PSObject.Properties) {
            if (-not $output.Contains($property.Name)) {
                $output[$property.Name] = $property.Value
            }
        }
    }

    $missingKey = $false
    foreach ($key in $sourceOrder) {
        if (-not $destMap.ContainsKey($key)) {
            $missingKey = $true
            break
        }
    }

    $extraKey = $false
    if ($PruneExtraKeys) {
        foreach ($key in $destOrder) {
            if (-not $sourceMap.ContainsKey($key)) {
                $extraKey = $true
                break
            }
        }
    }

    $orderChanged = -not (Test-SequenceEqual -left $destOrder -right @($output.Keys))

    if ($missingKey -or $extraKey -or $orderChanged) {
        Write-OrderedJson -path $localeFile.FullName -orderedMap $output
        $updated++
    }
}

Write-Host "Language directory: $langDir"
Write-Host "Source locale: $sourcePath"
Write-Host "Locale files scanned: $($localeFiles.Count)"
Write-Host "Updated: $updated"
