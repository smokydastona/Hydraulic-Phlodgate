param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SourceLocale = 'en_us.json',
    [int]$MaxSameAsEnglishPercent = 90,
    [int]$MaxSuspiciousPercent = 35,
    [bool]$FailOnSuspiciousFallbacks = $true
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

    throw "No language directory was found under '$root'."
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

    $sourceFiles = Get-ChildItem -Path $langDir -Filter *.json -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)^en(_|-)us\.json$|^en\.json$' } |
        Sort-Object Name

    if ($sourceFiles -and $sourceFiles.Count -gt 0) {
        return $sourceFiles[0].FullName
    }

    $allJson = Get-ChildItem -Path $langDir -Filter *.json -ErrorAction SilentlyContinue | Sort-Object Name
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

function ConvertTo-Hashtable {
    param($input)

    $hash = @{}
    if ($null -eq $input) { return $hash }

    foreach ($property in $input.PSObject.Properties) {
        $hash[$property.Name] = $property.Value
    }

    return $hash
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

function Test-SuspiciousSameValue {
    param([string]$value)

    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    if ($value -notmatch '[A-Za-z]') { return $false }

    $lower = $value.Trim()
    $commonText = @('auto', 'audio', 'ui', 'modded', 'test', 'done', 'cancel', 'status', 'mode')
    if ($commonText -contains $lower.ToLowerInvariant()) { return $false }

    return $true
}

$resolvedRoot = if ([string]::IsNullOrWhiteSpace($RepoRoot)) { (Get-Location).Path } else { $RepoRoot }
if (-not (Test-Path $resolvedRoot)) {
    throw "Repository root '$resolvedRoot' does not exist."
}

$langDir = Find-LanguageDirectory -root $resolvedRoot
$sourcePath = Resolve-SourceLanguageFile -langDir $langDir -sourceLocale $SourceLocale
$sourceObj = Read-JsonObject -path $sourcePath
$sourceOrder = @($sourceObj.PSObject.Properties.Name)
$sourceMap = ConvertTo-Hashtable -input $sourceObj

$allLocaleFiles = Get-ChildItem -Path $langDir -Filter *.json -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -ne $sourcePath } |
    Sort-Object Name

if (-not $allLocaleFiles -or $allLocaleFiles.Count -eq 0) {
    Write-Host "No additional locale files were found to validate under '$langDir'."
    Write-Host "Language directory: $langDir"
    Write-Host "Source locale: $sourcePath"
    Write-Host "Validation passed; only the source locale is present."
    return
}

$failures = New-Object System.Collections.Generic.List[string]
$results = New-Object System.Collections.Generic.List[object]

foreach ($localeFile in $allLocaleFiles) {
    $localeName = $localeFile.Name
    $localeObj = Read-JsonObject -path $localeFile.FullName
    $localeOrder = @($localeObj.PSObject.Properties.Name)
    $localeMap = ConvertTo-Hashtable -input $localeObj

    $missingKeys = @($sourceOrder | Where-Object { -not $localeMap.ContainsKey($_) })
    $extraKeys = @($localeOrder | Where-Object { -not $sourceMap.ContainsKey($_) })
    $orderMatches = Test-SequenceEqual -left $localeOrder -right $sourceOrder

    $sameAsEnglish = 0
    $suspiciousSame = 0

    foreach ($key in $sourceOrder) {
        if (-not $localeMap.ContainsKey($key)) { continue }

        $value = [string]$localeMap[$key]
        $sourceValue = [string]$sourceMap[$key]
        if ($value -eq $sourceValue) {
            $sameAsEnglish++
            if (Test-SuspiciousSameValue -value $value) {
                $suspiciousSame++
            }
        }
    }

    $keyCount = [math]::Max($sourceOrder.Count, 1)
    $samePercent = [math]::Round(($sameAsEnglish / $keyCount) * 100, 1)
    $suspiciousPercent = [math]::Round(($suspiciousSame / $keyCount) * 100, 1)

    $results.Add([pscustomobject]@{
        File = $localeName
        SameAsEnglish = $sameAsEnglish
        SamePercent = $samePercent
        SuspiciousSame = $suspiciousSame
        SuspiciousPercent = $suspiciousPercent
    })

    if ($missingKeys.Count -gt 0) {
        $failures.Add("$localeName is missing $($missingKeys.Count) keys from $($sourcePath | Split-Path -Leaf).")
    }

    if ($extraKeys.Count -gt 0) {
        $failures.Add("$localeName has $($extraKeys.Count) extra keys not present in $($sourcePath | Split-Path -Leaf).")
    }

    if (-not $orderMatches) {
        $failures.Add("$localeName does not preserve the same key order as $($sourcePath | Split-Path -Leaf).")
    }

    if ($samePercent -ge $MaxSameAsEnglishPercent) {
        $failures.Add("$localeName is still mostly English fallback content ($samePercent% identical to $($sourcePath | Split-Path -Leaf); threshold $MaxSameAsEnglishPercent%).")
    }

    if ($FailOnSuspiciousFallbacks -and $suspiciousPercent -ge $MaxSuspiciousPercent) {
        $failures.Add("$localeName has too much suspicious English fallback content ($suspiciousPercent%; threshold $MaxSuspiciousPercent%).")
    }
}

Write-Host 'Locale validation summary:'
$results |
    Sort-Object -Property @(
        @{ Expression = 'SamePercent'; Descending = $true },
        @{ Expression = 'SuspiciousPercent'; Descending = $true }
    ) |
    Select-Object -First 30 |
    Format-Table -AutoSize |
    Out-String |
    Write-Host

if ($failures.Count -gt 0) {
    $message = ($failures -join [Environment]::NewLine)
    throw "Locale validation failed:`n$message"
}

Write-Host "Language directory: $langDir"
Write-Host "Validation passed for $($allLocaleFiles.Count) locale files."
