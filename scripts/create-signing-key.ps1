#requires -Version 7.0
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
$signerDirectory = Join-Path $env:USERPROFILE '.android'
New-Item -ItemType Directory -Path $signerDirectory -Force | Out-Null
$signerFile = Join-Path $signerDirectory 'orbit-voice-signing.properties'
$keyFile = Join-Path $signerDirectory 'orbit-voice-release.jks'
if ((Test-Path $signerFile) -or (Test-Path $keyFile)) { throw 'Signing configuration already exists. It will not be overwritten.' }
$passwordBytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
$env:ORBIT_SIGN_PASSWORD = [Convert]::ToBase64String($passwordBytes)
try {
    & (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -keystore $keyFile -storetype JKS -alias orbit-voice -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Orbit Voice, OU=Independent apps' -storepass:env ORBIT_SIGN_PASSWORD -keypass:env ORBIT_SIGN_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    $lines = [string[]]@(('storeFile=' + $keyFile.Replace('\','/')),('storePassword=' + $env:ORBIT_SIGN_PASSWORD), 'keyAlias=orbit-voice',('keyPassword=' + $env:ORBIT_SIGN_PASSWORD))
    [IO.File]::WriteAllLines($signerFile,$lines)
    $userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    foreach ($privateFile in @($keyFile,$signerFile)) {
        & icacls.exe $privateFile /inheritance:r /grant:r ('*' + $userSid + ':F') '*S-1-5-18:F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not protect the signing file ACL.' }
    }
    Write-Output 'Release signing files created in your .android directory. Preserve the key for future updates.'
} finally { Remove-Item Env:\ORBIT_SIGN_PASSWORD -ErrorAction SilentlyContinue }
