$colors = @(git grep -n -E "0x[0-9A-Fa-f]{8}|Color\(0x" -- "*.kt")
$text = @(git grep -n -E 'Text\("|text\s*=\s*"|contentDescription\s*=\s*"' -- "*.kt")
$dims = @(git grep -n -E "[0-9]+\.dp|[0-9]+\.sp|tween\([0-9]+|durationMillis\s*=\s*[0-9]+" -- "*.kt" | Where-Object { $_ -notmatch "tokens|MotionTokens" })
$m3 = @(git grep -n -E "material3\.(Slider|Switch|FilterChip|AssistChip|RadioButton|Checkbox)" -- "*.kt")
$stubs = @(git grep -n -i -E "TODO|FIXME|stub|not implemented|placeholder|mock|fake|dummy" -- "*.kt" | Where-Object { $_ -notmatch "/(test|androidTest|desktopTest|commonTest)/" })

Write-Host "Literal Colors: $($colors.Count)"
Write-Host "Raw Text Literals: $($text.Count)"
Write-Host "Raw Dimensions: $($dims.Count)"
Write-Host "Material3 Controls: $($m3.Count)"
Write-Host "TODO/Stubs/Fake (non-test): $($stubs.Count)"
