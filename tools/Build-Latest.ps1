# OrienteerRun — one-click build
# Physical phone connected (adb "device", not emulator) → debug build + install
# No physical phone → signed release AAB → store-assets\releases\OrienteerRun-<ver>-vc<code>.aab

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectRoot "gradlew.bat"))) {
    $ProjectRoot = "D:\PROJECTS\Running-app-main"
}
Set-Location $ProjectRoot

function Find-JavaHome {
    $candidates = @(
        $env:JAVA_HOME,
        "D:\Android\jdk\jdk-17.0.21+9",
        "C:\Program Files\Android\Android Studio\jbr",
        "D:\Android\jdk\jdk-17.0.20.1+1"
    ) | Where-Object { $_ -and $_.Trim() -ne "" }
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "bin\java.exe")) { return $c }
    }
    return $null
}

function Find-Sdk {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }
    $fallback = "D:\Android\Sdk"
    if (Test-Path $fallback) { return $fallback }
    return $null
}

function Get-ConnectedPhones {
    param([string]$Adb)
    & $Adb start-server 2>$null | Out-Null
    $lines = & $Adb devices 2>$null
    $phones = @()
    foreach ($line in $lines) {
        if ($line -match "^(\S+)\s+device\s*$") {
            $serial = $Matches[1]
            # Physical USB/Wi‑Fi phones only — emulators never count as "connected"
            if ($serial -notmatch "^emulator-") {
                $phones += $serial
            }
        }
    }
    return $phones
}

$javaHome = Find-JavaHome
if (-not $javaHome) {
    Write-Host "ERROR: No JDK found. Install Temurin 17 or Android Studio JBR." -ForegroundColor Red
    exit 1
}
$sdk = Find-Sdk
if (-not $sdk) {
    Write-Host "ERROR: Android SDK not found (expected D:\Android\Sdk)." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:Path = "$javaHome\bin;$sdk\platform-tools;$env:Path"

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Host "ERROR: adb not found at $adb" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "OrienteerRun build" -ForegroundColor Cyan
Write-Host "  Project : $ProjectRoot"
Write-Host "  JAVA    : $javaHome"
Write-Host "  SDK     : $sdk"
Write-Host ""

$phones = @(Get-ConnectedPhones -Adb $adb)
$gradlew = Join-Path $ProjectRoot "gradlew.bat"

if ($phones.Count -gt 0) {
    $serial = $phones[0]
    Write-Host "Phone connected: $serial" -ForegroundColor Green
    Write-Host "Building + installing DEBUG..." -ForegroundColor Yellow
    Write-Host ""

    & $gradlew ":app:installDebug" "-Pandroid.injected.invoked.from.ide=true"
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "DEBUG install FAILED (exit $LASTEXITCODE)." -ForegroundColor Red
        exit $LASTEXITCODE
    }

    $apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    Write-Host ""
    Write-Host "DEBUG installed on $serial" -ForegroundColor Green
    if (Test-Path $apk) { Write-Host "APK: $apk" }

    # Launch app if possible
    & $adb -s $serial shell am start -n "com.orienteer.app.debug/com.orienteer.app.MainActivity" 2>$null | Out-Null
    exit 0
}

Write-Host "No phone connected." -ForegroundColor DarkYellow
Write-Host "Building signed RELEASE AAB (ready version)..." -ForegroundColor Yellow
Write-Host ""

& $gradlew ":app:bundleRelease"
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "RELEASE build FAILED (exit $LASTEXITCODE)." -ForegroundColor Red
    Write-Host "Check signing keys in local.properties (STORE_FILE / passwords)." -ForegroundColor DarkYellow
    exit $LASTEXITCODE
}

$releaseDir = Join-Path $ProjectRoot "app\build\outputs\bundle\release"
$aab = Get-ChildItem $releaseDir -Filter "*.aab" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

Write-Host ""
Write-Host "RELEASE build OK" -ForegroundColor Green
if ($aab) {
    $gradleFile = Join-Path $ProjectRoot "app\build.gradle.kts"
    $gradleText = Get-Content $gradleFile -Raw
    $versionName = if ($gradleText -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { "unknown" }
    $versionCode = if ($gradleText -match 'versionCode\s*=\s*(\d+)') { $Matches[1] } else { "0" }

    $outDir = Join-Path $ProjectRoot "store-assets\releases"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $copyTo = Join-Path $outDir "OrienteerRun-$versionName-vc$versionCode.aab"
    Copy-Item -Force $aab.FullName $copyTo
    Write-Host "Saved: $copyTo" -ForegroundColor Green
    Start-Process explorer.exe "/select,`"$copyTo`""
} else {
    Write-Host "Bundle folder: $releaseDir"
    if (Test-Path $releaseDir) { Start-Process explorer.exe $releaseDir }
}
exit 0
