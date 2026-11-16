# OrienteerRun — capture Play Store phone screenshots on a booted emulator.
# Requires: Android SDK, a running emulator, network for OSM tiles.
# Demo map: Berlin Tiergarten only (not personal coordinates).

$ErrorActionPreference = "Stop"
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdk21 = "C:\Users\pc\.jdks\jbr-21.0.11"
    if (Test-Path "$jdk21\bin\java.exe") { $env:JAVA_HOME = $jdk21 }
}
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$ProjectRoot\gradlew.bat")) {
    $ProjectRoot = "D:\PROJECTS\Running-app-main"
}
$OutDir = Join-Path $ProjectRoot "store-assets\screenshots"
$Desk = Join-Path $env:USERPROFILE "Desktop\PlayStore-OrienteerRun-Assets"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $Desk | Out-Null

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "D:\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

Write-Host "Waiting for device..."
& $adb wait-for-device
$emu = & $adb devices | Select-String "emulator-\d+\s+device"
$phy = & $adb devices | Select-String "^\w+\s+device" | Where-Object { $_ -notmatch "emulator-" }
if ($emu) {
    $serial = ($emu[0].ToString() -split "\s+")[0]
} elseif ($env:ORIENTEER_ALLOW_PHYSICAL -eq "1" -and $phy) {
    $serial = ($phy[0].ToString() -split "\s+")[0]
    Write-Host "WARNING: no emulator; using physical device $serial (ORIENTEER_ALLOW_PHYSICAL=1)"
} else {
    throw "No online emulator found. Start an AVD, or set ORIENTEER_ALLOW_PHYSICAL=1 with a USB device."
}
Write-Host "Using device: $serial"

# Berlin Tiergarten (lon lat for emu geo fix; no-op on physical devices)
if ($serial -match "^emulator-") {
    & $adb -s $serial emu geo fix 13.3501 52.5145
}

Write-Host "Building and installing debug APK..."
Push-Location $ProjectRoot
try {
    & .\gradlew.bat :app:installDebug
    if ($LASTEXITCODE -ne 0) { throw "installDebug failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

# Debug APK uses applicationIdSuffix ".debug"
$pkg = "com.orienteer.app.debug"
$activity = "com.orienteer.app.MainActivity"

& $adb -s $serial shell pm grant $pkg android.permission.ACCESS_FINE_LOCATION
& $adb -s $serial shell pm grant $pkg android.permission.ACCESS_COARSE_LOCATION
& $adb -s $serial shell pm grant $pkg android.permission.POST_NOTIFICATIONS 2>$null

function Capture-Scene([string]$scene, [string]$fileBase, [int]$waitSec) {
    Write-Host "Scene: $scene -> $fileBase.png (wait ${waitSec}s)"
    & $adb -s $serial shell am force-stop $pkg
    Start-Sleep -Seconds 2
    & $adb -s $serial shell am start -n "${pkg}/${activity}" `
        --ez com.orienteer.app.EXTRA_SEED_SCREENSHOT_DEMO true `
        --es com.orienteer.app.EXTRA_SCREENSHOT_SCENE $scene
    # Wait until activity has focus, then remaining settle time (map tiles / Compose).
    $deadline = (Get-Date).AddSeconds([Math]::Max(6, $waitSec))
    do {
        Start-Sleep -Seconds 1
        $focus = & $adb -s $serial shell dumpsys window 2>$null |
            Select-String "mCurrentFocus=Window\{[^ ]+ u0 $([regex]::Escape($pkg))/"
        if ($focus) { break }
    } while ((Get-Date) -lt $deadline)
    Start-Sleep -Seconds ([Math]::Max(3, $waitSec - 4))
    $remote = "/sdcard/$fileBase.png"
    & $adb -s $serial shell screencap -p $remote
    & $adb -s $serial pull $remote (Join-Path $OutDir "$fileBase.png") | Out-Null
    Copy-Item -Force (Join-Path $OutDir "$fileBase.png") (Join-Path $Desk "$fileBase.png")
}

Capture-Scene "setup" "01-setup" 8
# Map/run need long waits so OSM tiles finish before screencap.
Capture-Scene "map" "02-map" 28
# Expanded route preview sheet with Export PDF (sheet not collapsed).
Capture-Scene "map_pdf" "08-map-pdf" 28
Capture-Scene "run" "03-active-run" 28
Capture-Scene "history" "04-history" 8
Capture-Scene "favorites" "05-favorites" 8
Capture-Scene "settings" "06-settings" 8

Write-Host "Done. Files in:"
Write-Host "  $OutDir"
Write-Host "  $Desk"
Get-ChildItem $OutDir\0*.png | Format-Table Name, Length
