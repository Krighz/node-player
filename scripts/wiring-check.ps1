param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$Symbols
)

if (-not $Symbols -or $Symbols.Count -eq 0) {
    Write-Host "Usage: ./scripts/wiring-check.ps1 <Symbol1> <Symbol2> ..."
    exit 0
}

foreach ($s in $Symbols) {
    $all = git grep --untracked -n -w -- $s -- "*.kt" 2>$null
    $decl = "(class|object|interface|fun|val|var|typealias)\s+$s\b"
    $defs = @($all | Where-Object { $_ -match $decl })
    $uses = @($all | Where-Object { $_ -notmatch $decl -and $_ -notmatch "/(test|androidTest|desktopTest|commonTest)/" })
    "{0,-42} defs={1} uses(non-test)={2}" -f $s, $defs.Count, $uses.Count
}
