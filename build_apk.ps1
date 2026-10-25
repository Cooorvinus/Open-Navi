# Kompiluje plik APK aplikacji Open Navi do folderu APK\OpenNavi.apk i opcjonalnie wysyla zmiany na GitHub
# Uzycie: powershell -ExecutionPolicy Bypass -File build_apk.ps1
$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$tools = Join-Path $env:LOCALAPPDATA "TransitAndroidBuild"

$env:JAVA_HOME = Join-Path $tools "jdk17"
$env:ANDROID_HOME = Join-Path $tools "sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$gradle = Join-Path $tools "gradle-8.11.1\bin\gradle.bat"

# Konfiguracja local.properties -> lokalizacja Android SDK
$sdkEsc = $env:ANDROID_HOME.Replace('\', '\\').Replace(':', '\:')
Set-Content -Path (Join-Path $projectRoot "local.properties") -Value "sdk.dir=$sdkEsc" -Encoding ASCII

# Klucz podpisujacy Release APK (generowany automatycznie przy pierwszym budowaniu)
$ksDir = Join-Path $projectRoot "keystore"
$ksFile = Join-Path $ksDir "opennavi-release.jks"
$ksProps = Join-Path $ksDir "keystore.properties"
if (-not (Test-Path $ksFile)) {
    New-Item -ItemType Directory -Force -Path $ksDir | Out-Null
    $pw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $ksFile -storetype PKCS12 `
        -alias opennavi -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pw -keypass $pw -dname "CN=Open Navi, O=Open Navi Android, C=PL" | Out-Null
    @(
        "storeFile=keystore/opennavi-release.jks",
        "storePassword=$pw",
        "keyAlias=opennavi",
        "keyPassword=$pw"
    ) | Set-Content -Path $ksProps -Encoding ASCII
}

Push-Location $projectRoot
try {
    & $gradle --no-daemon assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Blad kompilacji Gradle ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

$apkSrc = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
$apkDir = Join-Path $projectRoot "APK"
New-Item -ItemType Directory -Force -Path $apkDir | Out-Null
$apkDst = Join-Path $apkDir "OpenNavi.apk"
Copy-Item $apkSrc $apkDst -Force

$mb = [math]::Round((Get-Item $apkDst).Length / 1MB, 1)
Write-Host "APK GOTOWE: $apkDst ($mb MB)"
