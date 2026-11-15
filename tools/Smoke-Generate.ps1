$ErrorActionPreference = 'Continue'
$adb = 'D:\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.orienteer.app.debug'
$act = 'com.orienteer.app.MainActivity'
$dumpRemote = '/sdcard/window_dump.xml'
$dumpLocal = 'd:\PROJECTS\Running-app-main\.adb_ui_dump.xml'
$report = 'd:\PROJECTS\Running-app-main\app\build\reports\preset-matrix\smoke-summary.txt'

function Dump-Ui {
    & $adb shell uiautomator dump $dumpRemote | Out-Null
    Start-Sleep -Milliseconds 500
    & $adb pull $dumpRemote $dumpLocal | Out-Null
    return (Get-Content $dumpLocal -Raw -Encoding UTF8)
}

function Tap-Exact([string]$xml, [string]$exact) {
    $m = [regex]::Match($xml, 'text="' + [regex]::Escape($exact) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $false }
    $cx = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
    $cy = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    Write-Host "tap '$exact' $cx,$cy"
    & $adb shell input tap $cx $cy | Out-Null
    return $true
}

function Wait-Preview([int]$maxSec = 105) {
    $sw = Get-Date
    while (((Get-Date) - $sw).TotalSeconds -lt $maxSec) {
        Start-Sleep -Seconds 3
        $xml = Dump-Ui
        if ($xml -match 'Route Preview') {
            return @{ ok = $true; xml = $xml; sec = ((Get-Date) - $sw).TotalSeconds }
        }
        if ($xml -match 'Route generation failed') {
            return @{ ok = $false; xml = $xml; sec = ((Get-Date) - $sw).TotalSeconds }
        }
        Write-Host ("  waiting {0:N0}s..." -f ((Get-Date) - $sw).TotalSeconds)
    }
    return @{ ok = $false; xml = (Dump-Ui); sec = ((Get-Date) - $sw).TotalSeconds }
}

function Run-Gen([string]$label) {
    $xml = Dump-Ui
    if (-not (Tap-Exact $xml 'Generate')) {
        $line = "$label=NO_GENERATE"
        Write-Host $line
        return $line
    }
    $r = Wait-Preview
    $feat = ([regex]::Matches($r.xml, 'Path junction|Bench|: Near |: At ')).Count
    $line = ("{0} ok={1} sec={2:N1} featureHits={3}" -f $label, $r.ok, $r.sec, $feat)
    Write-Host $line
    & $adb shell input keyevent 4 | Out-Null
    Start-Sleep -Seconds 2
    return $line
}

New-Item -ItemType Directory -Force -Path (Split-Path $report) | Out-Null

Write-Host '=== launch ==='
& $adb emu geo fix 13.4050 52.5200 | Out-Null
& $adb shell pm grant $pkg android.permission.ACCESS_FINE_LOCATION
& $adb shell pm grant $pkg android.permission.ACCESS_COARSE_LOCATION
& $adb shell am force-stop $pkg | Out-Null
Start-Sleep -Seconds 1
& $adb shell am start -n "$pkg/$act" | Out-Null
Start-Sleep -Seconds 4

$xml = Dump-Ui
for ($i = 0; $i -lt 5; $i++) {
    if (Tap-Exact $xml 'Allow') {
        Start-Sleep -Seconds 2
        $xml = Dump-Ui
        continue
    }
    if (Tap-Exact $xml 'While using the app') {
        Start-Sleep -Seconds 2
        $xml = Dump-Ui
        continue
    }
    break
}

Write-Host '=== texts ==='
([regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique) -join ' | ' | Write-Host

$out = New-Object System.Collections.Generic.List[string]
$out.Add((Run-Gen 'mitte_1')) | Out-Null
$out.Add((Run-Gen 'mitte_2')) | Out-Null
& $adb emu geo fix 13.2000 52.5350 | Out-Null
$out.Add((Run-Gen 'spandau_1')) | Out-Null
$out.Add((Run-Gen 'spandau_2')) | Out-Null

$out | Set-Content $report -Encoding UTF8
Copy-Item $report 'd:\PROJECTS\Running-app-main\build\reports\preset-matrix\smoke-summary.txt' -Force
Write-Host '=== DONE ==='
$out
