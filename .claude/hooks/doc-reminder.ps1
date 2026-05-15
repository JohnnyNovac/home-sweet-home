$ErrorActionPreference = 'SilentlyContinue'

$stdin = [Console]::In.ReadToEnd()
try {
    $payload = $stdin | ConvertFrom-Json
} catch {
    exit 0
}

$filePath = $payload.tool_input.file_path
if (-not $filePath) { exit 0 }

if ($filePath -match '\.md$') { exit 0 }

$templatePath = Join-Path $PSScriptRoot 'doc-reminder.txt'
if (-not (Test-Path $templatePath)) { exit 0 }

$template = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8)
$reminder = $template.Replace('{FILE}', $filePath).TrimEnd()

$output = @{
    hookSpecificOutput = @{
        hookEventName     = 'PostToolUse'
        additionalContext = $reminder
    }
} | ConvertTo-Json -Compress

[Console]::Out.WriteLine($output)
exit 0