Set-StrictMode -Version Latest

function Get-RequirementDefinitions {
    param([Parameter(Mandatory)][string]$Text)
    $seen = @{}
    foreach ($line in ($Text -split '\r?\n')) {
        $cells = [regex]::Split($line, '(?<!\\)\|')
        if ($cells.Count -lt 5) { continue }
        $id = $cells[1].Trim()
        if ($id -cnotmatch '^[A-Z]+-\d{3}$') { continue }
        if ($seen.ContainsKey($id)) { throw "Duplicate requirement: $id" }
        $seen[$id] = $true
        $definition = $cells[2].Trim() + '|' + $cells[3].Trim()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $fingerprint = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($definition)))).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
        [pscustomobject]@{ Id = $id; Fingerprint = $fingerprint; Definition = $cells[2].Trim(); Status = $cells[3].Trim() }
    }
}

function Test-SmokeCatalog {
    param($Catalog, [object[]]$Requirements)
    $issues = [Collections.Generic.List[string]]::new()
    $ids = @{}; $keys = @{}; $mapped = @{}
    if ($Catalog.version -ne 1) { $issues.Add('Unsupported testcase catalog version.') }
    foreach ($case in $Catalog.cases) {
        if ($ids.ContainsKey($case.id)) { $issues.Add("Duplicate testcase ID: $($case.id)") }
        $ids[$case.id] = $true
        if ($case.kind -notin @('App','EnvironmentPowerShell','EnvironmentGradle','Runner','Project','Manual')) { $issues.Add("Invalid testcase kind: $($case.id)") }
        if ($keys.ContainsKey($case.key)) { $issues.Add("Duplicate testcase selector: $($case.id)") }
        $keys[$case.key] = $true
    }
    foreach ($entry in $Catalog.requirements) {
        if ($mapped.ContainsKey($entry.id)) { $issues.Add("Duplicate requirement mapping: $($entry.id)") }
        $mapped[$entry.id] = $entry
        if ($entry.coverage -notin @('Full','Partial','Manual','Not applicable')) { $issues.Add("Invalid coverage: $($entry.id)") }
        foreach ($id in $entry.cases) { if (-not $ids.ContainsKey($id)) { $issues.Add("Unknown testcase $id for $($entry.id)") } }
        if ($entry.coverage -ne 'Not applicable' -and @($entry.cases).Count -eq 0) { $issues.Add("No testcase disposition: $($entry.id)") }
    }
    $actual = @{}
    foreach ($requirement in $Requirements) {
        $actual[$requirement.Id] = $true
        if (-not $mapped.ContainsKey($requirement.Id)) { $issues.Add("Missing mapping: $($requirement.Id)") }
        elseif ($mapped[$requirement.Id].fingerprint -cne $requirement.Fingerprint) { $issues.Add("Stale mapping: $($requirement.Id)") }
    }
    foreach ($id in $mapped.Keys) { if (-not $actual.ContainsKey($id)) { $issues.Add("Removed requirement still mapped: $id") } }
    foreach ($case in $Catalog.cases) {
        if (@($Catalog.requirements | Where-Object { $case.id -in $_.cases }).Count -eq 0) { $issues.Add("Unreferenced testcase: $($case.id)") }
    }
    return $issues.ToArray()
}

function Read-SmokeJUnit {
    param([Parameter(Mandatory)][string]$Directory)
    foreach ($file in @(Get-ChildItem -LiteralPath $Directory -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)) {
        # Disable DTD/entity resolution; reports are data, never executable instructions.
        $settings = [Xml.XmlReaderSettings]::new()
        $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
        $settings.XmlResolver = $null
        $reader = [Xml.XmlReader]::Create($file.FullName, $settings)
        try { $document = [Xml.XmlDocument]::new(); $document.XmlResolver = $null; $document.Load($reader) }
        finally { $reader.Dispose() }
        foreach ($case in $document.SelectNodes('//testcase')) {
            $status = 'Passed'
            if ($case.SelectSingleNode('failure')) { $status = 'Failed' }
            elseif ($case.SelectSingleNode('error')) { $status = 'Error' }
            elseif ($case.SelectSingleNode('skipped')) { $status = 'Skipped' }
            [pscustomobject]@{ key = $case.GetAttribute('classname') + '#' + $case.GetAttribute('name'); status = $status }
        }
    }
}

function Read-SmokeJsonResults {
    param([string]$Path)
    $parsed = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    # Windows PowerShell 5.1 does not enumerate JSON arrays like PowerShell 7.
    foreach ($item in $parsed) { $item }
}

function Get-SmokeResults {
    param($Catalog, [object[]]$Observed)
    $byKey = @{}
    foreach ($item in $Observed) {
        if ($byKey.ContainsKey($item.key)) { throw "Duplicate execution result: $($item.key)" }
        if ($item.status -notin @('Passed','Failed','Skipped','Error')) { throw 'Invalid execution result status.' }
        $byKey[$item.key] = $item
    }
    $known = @{}
    foreach ($case in $Catalog.cases) { $known[$case.key] = $true }
    foreach ($key in $byKey.Keys) { if (-not $known.ContainsKey($key)) { throw "Executed testcase missing from catalog: $key" } }
    foreach ($case in $Catalog.cases) {
        $status = 'Error'
        if ($case.kind -eq 'Manual') { $status = 'Not run' }
        elseif ($byKey.ContainsKey($case.key)) { $status = $byKey[$case.key].status }
        [pscustomobject]@{ id = $case.id; key = $case.key; title = $case.title; kind = $case.kind; status = $status }
    }
}

function Get-SmokeTotals {
    param([object[]]$Results)
    $automated = @($Results | Where-Object { $_.kind -ne 'Manual' })
    [pscustomobject]@{
        total = $automated.Count
        passed = @($automated | Where-Object status -eq 'Passed').Count
        failed = @($automated | Where-Object status -eq 'Failed').Count
        skipped = @($automated | Where-Object status -eq 'Skipped').Count
        errors = @($automated | Where-Object status -eq 'Error').Count
        manual = @($Results | Where-Object kind -eq 'Manual').Count
    }
}

function ConvertTo-SmokeCell {
    param([string]$Text)
    return $Text.Replace('|', '\|').Replace("`r", ' ').Replace("`n", ' ')
}

function Update-RequirementTestRows {
    param([string]$Path, $Catalog, [object[]]$Results, [string]$RunLabel, [string]$Outcome)
    $text = [IO.File]::ReadAllText($Path).Replace("`r`n", "`n")
    $definitions = @(Get-RequirementDefinitions $text)
    $definitionsById = @{}; $entries = @{}; $byId = @{}
    foreach ($item in $definitions) { $definitionsById[$item.Id] = $item }
    foreach ($item in $Catalog.requirements) { $entries[$item.id] = $item }
    foreach ($item in $Results) { $byId[$item.id] = $item }
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($line in ($text -split "`n")) {
        if ($line -match '^\| Tests for [A-Z]+-\d{3} \|') { continue }
        $lines.Add($line)
        if ($line -notmatch '^\| ([A-Z]+-\d{3}) \|') { continue }
        $id = $Matches[1]
        $state = 'Not run'; $descriptions = [Collections.Generic.List[string]]::new()
        $coverage = 'Missing mapping'
        if ($entries.ContainsKey($id)) {
            $entry = $entries[$id]
            $coverage = $entry.coverage + ': ' + $entry.gap
            $statuses = @()
            foreach ($testId in $entry.cases) {
                $case = @($Catalog.cases | Where-Object id -eq $testId)[0]
                $testState = if ($byId.ContainsKey($testId)) { $byId[$testId].status } else { 'Not run' }
                if ($entry.fingerprint -cne $definitionsById[$id].Fingerprint) { $testState = 'Stale' }
                $statuses += $testState
                $descriptions.Add("**$testId - $testState**: $($case.title)")
            }
            if ($entry.coverage -eq 'Not applicable') { $state = 'Not applicable'; $descriptions.Add('No active testcase; superseded requirement.') }
            elseif ('Stale' -in $statuses) { $state = 'Stale' }
            elseif ('Failed' -in $statuses) { $state = 'Failed' }
            elseif ('Error' -in $statuses) { $state = 'Error' }
            elseif ('Not run' -in $statuses) { $state = 'Not run / partial results' }
            elseif ('Skipped' -in $statuses) { $state = 'Skipped / partial results' }
            elseif ($statuses.Count -gt 0) { $state = 'Passed' }
        } else { $descriptions.Add('Test mapping must be reviewed before execution.') }
        $lines.Add('| Tests for ' + $id + ' | ' + (ConvertTo-SmokeCell ($descriptions -join '; ')) +
            ' | ' + $state + ' | ' + (ConvertTo-SmokeCell $coverage) + ' |')
    }
    $text = $lines -join "`n"
    $text = [regex]::Replace($text, '(?s)<!-- smoke-results:start -->.*?<!-- smoke-results:end -->\s*', '')
    $totals = Get-SmokeTotals $Results
    $block = "<!-- smoke-results:start -->`n## Latest automated smoke results`n`n" +
        "Run: $RunLabel (Europe/Berlin). Outcome: **$Outcome**. " +
        "Automated checks: **$($totals.passed)/$($totals.total) passed**, $($totals.failed) failed, $($totals.skipped) skipped, $($totals.errors) errors. " +
        "Manual cases: $($totals.manual) not run by this suite. A Passed test row describes its mapped checks only; the coverage/gap column retains untested criteria. " +
        "Results describe this run, not changes made afterward. Generated test rows are maintained by test-smoke.ps1.`n<!-- smoke-results:end -->`n`n"
    $anchor = '## How to read this catalog'
    if (-not $text.Contains($anchor)) { throw 'Requirement catalog report anchor is missing.' }
    $text = $text.Replace($anchor, $block + $anchor)
    [IO.File]::WriteAllText($Path, $text, [Text.UTF8Encoding]::new($false))
}

function Invoke-SmokeCheck {
    param([string]$Key, [scriptblock]$Check)
    $status = 'Passed'
    try { & $Check | Out-Null } catch { $status = 'Failed'; Write-Verbose ("Check ${Key}: " + $_.Exception.Message) }
    [pscustomobject]@{ key = $Key; status = $status }
}

function Update-TestCaseCatalog {
    param([string]$Path, $Catalog, [object[]]$Results, [string]$RunLabel)
    $byId = @{}
    foreach ($result in $Results) { $byId[$result.id] = $result.status }
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('# Smoke testcase catalog')
    $lines.Add('')
    $lines.Add('Generated from scripts/test-catalog.json by test-smoke.ps1. Edit the JSON and actual tests, then rerun; do not hand-edit this generated view. Stable testcase IDs can cover multiple requirements. Latest run: ' + $RunLabel + ' (Europe/Berlin).')
    $lines.Add('')
    $lines.Add('The default suite prioritizes useful regression coverage within a 90-second warm-run target, excluding first-time downloads/build setup. This is a maintenance target, not a device-performance guarantee or a 100% coverage goal. Manual cases are not included in automated pass totals. Release duplicates are not extra unique cases.')
    $lines.Add('')
    $lines.Add('| Testcase | Check | Latest result | Requirements | Execution / source |')
    $lines.Add('| --- | --- | --- | --- | --- |')
    foreach ($case in $Catalog.cases) {
        $state = if ($byId.ContainsKey($case.id)) { $byId[$case.id] } else { 'Not run' }
        $linked = @($Catalog.requirements | Where-Object { $case.id -in $_.cases } | ForEach-Object id) -join ', '
        $lines.Add('| <a id="' + $case.id.ToLowerInvariant() + '"></a>' + $case.id + ' | ' +
            (ConvertTo-SmokeCell $case.title) + ' | ' + $state + ' | ' + $linked + ' | ' +
            (ConvertTo-SmokeCell ($case.kind + ': ' + $case.key + ' - ' + $case.source)) + ' |')
    }
    [IO.File]::WriteAllText($Path, ($lines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
}

function Assert-Smoke {
    param([bool]$Condition)
    if (-not $Condition) { throw 'Smoke assertion failed.' }
}
