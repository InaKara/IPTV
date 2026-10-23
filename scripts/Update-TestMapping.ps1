[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$RequirementId,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ReviewNote
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SmokeSupport.ps1')
$root = Split-Path $PSScriptRoot -Parent
$path = Join-Path $PSScriptRoot 'test-catalog.json'
$catalog = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
$definitions = @(Get-RequirementDefinitions ([IO.File]::ReadAllText((Join-Path $root 'docs/requirements/REQUIREMENTS.md'))))
$updates = @()
foreach ($id in $RequirementId) {
    $definition = @($definitions | Where-Object Id -eq $id)
    $entry = @($catalog.requirements | Where-Object id -eq $id)
    if ($definition.Count -ne 1 -or $entry.Count -ne 1) { throw "Add/reconcile the requirement and testcase mapping before reviewing $id." }
    $updates += [pscustomobject]@{ entry=$entry[0]; fingerprint=$definition[0].Fingerprint }
}
foreach ($update in $updates) {
    $update.entry.fingerprint = $update.fingerprint
    $update.entry.reviewNote = $ReviewNote
}
$issues = @(Test-SmokeCatalog $catalog $definitions)
$blocking = @($issues | Where-Object { $_ -notlike 'Stale mapping:*' })
if ($blocking.Count) { throw ($blocking -join '; ') }
[IO.File]::WriteAllText($path,(ConvertTo-Json -InputObject $catalog -Depth 12),[Text.UTF8Encoding]::new($false))
Write-Host ('Recorded explicit test-mapping review for: ' + ($RequirementId -join ', ') + '. Run test-smoke.ps1 to refresh results.')
