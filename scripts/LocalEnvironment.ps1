function Read-LocalEnvironment {
    param([Parameter(Mandatory)][string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $values }
    $lineNumber = 0
    foreach ($rawLine in [IO.File]::ReadAllLines($Path)) {
        $lineNumber++
        $line = $rawLine.Trim().TrimStart([char]0xFEFF)
        if (-not $line -or $line.StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { throw "Invalid .env entry at line $lineNumber." }
        $key = $line.Substring(0, $separator).Trim()
        if ($key -cnotmatch '^[A-Z_][A-Z0-9_]*$') { throw "Use uppercase .env keys at line $lineNumber." }
        if ($values.ContainsKey($key)) { throw "Duplicate .env key at line $lineNumber." }
        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        # Values are literal. Never run them as PowerShell or expand variables.
        $values[$key] = $value
    }
    return $values
}

