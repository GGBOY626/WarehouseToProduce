$ErrorActionPreference = 'Stop'
# Import only the shared service credential. Never import the Gemini key into Java.
$aiEnvPath = Join-Path $PSScriptRoot '.env'
if (-not $env:AI_INTERNAL_TOKEN -and (Test-Path -LiteralPath $aiEnvPath)) {
    foreach ($line in [System.IO.File]::ReadAllLines($aiEnvPath)) {
        if ($line -match '^\s*AI_INTERNAL_TOKEN\s*=\s*(.*?)\s*$') {
            $env:AI_INTERNAL_TOKEN = $Matches[1].Trim('"').Trim("'")
        }
    }
}
if (-not $env:AI_INTERNAL_TOKEN -or $env:AI_INTERNAL_TOKEN.Length -lt 32) {
    Write-Warning 'AI is unavailable: set AI_INTERNAL_TOKEN (at least 32 random characters) in ai-service/.env. Starting ordinary warehouse services.'
}
if (-not $env:AI_SERVICE_URL) { $env:AI_SERVICE_URL = 'http://127.0.0.1:8000' }
Push-Location (Join-Path $PSScriptRoot '../backend')
try { & mvn spring-boot:run; if ($LASTEXITCODE -ne 0) { throw 'Spring Boot startup failed.' } }
finally { Pop-Location }
