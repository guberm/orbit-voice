#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repoDirectory = Split-Path $PSScriptRoot -Parent
$signerFile = Join-Path $env:USERPROFILE '.android/orbit-voice-signing.properties'
if (-not (Test-Path $signerFile)) { throw 'Create your signing key with scripts/create-signing-key.ps1 first.' }
Push-Location $repoDirectory
try {
    & .\gradlew.bat :core:test :mobile:testDebugUnitTest :mobile:lintDebug :wear:lintDebug :mobile:assembleRelease :wear:assembleRelease --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification/build failed.' }
    $releaseSettings = Get-Content (Join-Path $repoDirectory 'gradle/android-app.gradle') -Raw
    $versionMatch = [regex]::Match($releaseSettings, "versionName '([0-9A-Za-z.-]+)'")
    if (-not $versionMatch.Success) { throw 'Release version was not found.' }
    $releaseVersion = $versionMatch.Groups[1].Value
    $outputDirectory = Join-Path $repoDirectory ('.artifacts/release-' + $releaseVersion)
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    Copy-Item mobile/build/outputs/apk/release/mobile-release.apk (Join-Path $outputDirectory ('orbit-voice-phone-' + $releaseVersion + '.apk'))
    Copy-Item wear/build/outputs/apk/release/wear-release.apk (Join-Path $outputDirectory ('orbit-voice-wear-' + $releaseVersion + '.apk'))
    Get-ChildItem $outputDirectory -Filter '*.apk' | ForEach-Object { $hash = Get-FileHash $_.FullName -Algorithm SHA256; $hash.Hash.ToLowerInvariant() + '  ' + $_.Name } | Set-Content (Join-Path $outputDirectory 'SHA256SUMS.txt')
    Write-Output "Signed artifacts: $outputDirectory"
} finally { Pop-Location }
