# DragonRun - build script (aapt2 + javac + d8 + zipalign + apksigner)
# Requirements: JDK 17+ (JAVA_HOME) and Android SDK build-tools 35.0.0 + platform android-35.
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File app\build.ps1
# Output: app\build\DragonRun.apk

$ErrorActionPreference = 'Stop'

$app = $PSScriptRoot
$root = Split-Path $app -Parent

function Find-Dir([string]$envName, [string]$fallback) {
    if ($envName -and (Test-Path $envName)) { return $envName }
    return $fallback
}

$sdk = Find-Dir $env:ANDROID_SDK_ROOT (Join-Path $root 'tools\android\sdk')
if (-not (Test-Path $sdk)) { $sdk = Find-Dir $env:ANDROID_HOME (Join-Path $root 'tools\android\sdk') }
$bt = Join-Path $sdk 'build-tools\35.0.0'
$androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
$javaHome = Find-Dir $env:JAVA_HOME (Join-Path $root 'tools\jdk-21')
$env:JAVA_HOME = $javaHome
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$keytool = Join-Path $javaHome 'bin\keytool.exe'

if (-not (Test-Path $androidJar)) { throw "android.jar not found: $androidJar (set ANDROID_SDK_ROOT)" }
if (-not (Test-Path $javac)) { throw "javac not found: $javac (set JAVA_HOME)" }

$build = Join-Path $app 'build'
$gen = Join-Path $build 'gen'
$classes = Join-Path $build 'classes'
$dexDir = Join-Path $build 'dex'
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path $gen, $classes, $dexDir | Out-Null

Write-Host '== aapt2 compile res =='
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $app 'res') -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '== aapt2 link =='
# Note: assets are packed later by the zip step (aapt2 on Windows would write backslash paths).
& (Join-Path $bt 'aapt2.exe') link -o (Join-Path $build 'base.apk') `
    -I $androidJar `
    --manifest (Join-Path $app 'AndroidManifest.xml') `
    --java $gen `
    --min-sdk-version 28 --target-sdk-version 35 `
    --auto-add-overlay `
    (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '== javac =='
$srcs = @()
$srcs += Get-ChildItem -Recurse -Filter *.java (Join-Path $app 'src') | ForEach-Object { $_.FullName }
$srcs += Get-ChildItem -Recurse -Filter *.java (Join-Path $app 'stubs') | ForEach-Object { $_.FullName }
$srcs += Get-ChildItem -Recurse -Filter *.java $gen | ForEach-Object { $_.FullName }
$argFile = Join-Path $build 'sources.txt'
$srcs | Set-Content -Encoding ASCII $argFile
& $javac -encoding UTF-8 --release 8 -classpath $androidJar -d $classes "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '== jar com =='
& $jar cf (Join-Path $build 'app.jar') -C $classes com
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

Write-Host '== d8 =='
& (Join-Path $bt 'd8.bat') --release --lib $androidJar --min-api 28 --output $dexDir (Join-Path $build 'app.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '== merge dex into apk =='
$outApk = Join-Path $build 'unsigned.apk'
Copy-Item (Join-Path $build 'base.apk') $outApk
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($outApk, [System.IO.Compression.ZipArchiveMode]::Update)
$dexPath = Join-Path $dexDir 'classes.dex'
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexPath, 'classes.dex') | Out-Null

$assetsRoot = Join-Path $app 'assets'
Get-ChildItem -Recurse -File $assetsRoot | ForEach-Object {
    $rel = $_.FullName.Substring($assetsRoot.Length + 1).Replace('\', '/')
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, "assets/$rel") | Out-Null
}
$zip.Dispose()

Write-Host '== zipalign =='
& (Join-Path $bt 'zipalign.exe') -f 4 $outApk (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$ks = Join-Path $root 'tools\debug.keystore'
if (-not (Test-Path $ks)) {
    Write-Host '== generate keystore =='
    New-Item -ItemType Directory -Force -Path (Split-Path $ks -Parent) | Out-Null
    & $keytool -genkeypair -v -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

Write-Host '== sign =='
& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey --out (Join-Path $build 'DragonRun.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host ('OK -> ' + (Join-Path $build 'DragonRun.apk'))
