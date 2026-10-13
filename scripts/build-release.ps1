#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repoDirectory = Split-Path $PSScriptRoot -Parent
$signerFile = Join-Path $env:USERPROFILE '.android/orbit-voice-signing.properties'
if (-not (Test-Path $signerFile)) { throw 'Create your signing key with scripts/create-signing-key.ps1 first.' }
Push-Location $repoDirectory
try {
    & .\gradlew.bat :core:test :mobile:lintDebug :wear:lintDebug :mobile:assembleRelease :wear:assembleRelease --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification/build failed.' }
    $outputDirectory = Join-Path $repoDirectory '.artifacts/release'
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    Copy-Item mobile/build/outputs/apk/release/mobile-release.apk (Join-Path $outputDirectory 'orbit-voice-phone-0.1.0-alpha.1.apk')
    Copy-Item wear/build/outputs/apk/release/wear-release.apk (Join-Path $outputDirectory 'orbit-voice-wear-0.1.0-alpha.1.apk')
    Get-ChildItem $outputDirectory -Filter '*.apk' | ForEach-Object { $hash = Get-FileHash $_.FullName -Algorithm SHA256; $hash.Hash.ToLowerInvariant() + '  ' + $_.Name } | Set-Content (Join-Path $outputDirectory 'SHA256SUMS.txt')
    Write-Output "Signed artifacts: $outputDirectory"
} finally { Pop-Location }
