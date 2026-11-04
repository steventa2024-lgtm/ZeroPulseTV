# Runs the JVM unit tests for the launcher's pure logic (launcher/src/.../core) and a strict javac lint
# pass over the whole app. No emulator or device needed.
# Usage:  powershell -ExecutionPolicy Bypass -File qa\run-tests.ps1 [-OpenPulse <OpenPulse checkout with the TV link>]
#         Expects the toolchain in tools\ (see docs/BUILDING.md).
#         -OpenPulse also runs the TV's JARVIS client against a real OpenPulse TV link on this PC
#         (needs Node.js and that checkout's dependencies installed).
param([string]$OpenPulse = "", [string]$Tools = (Join-Path $PSScriptRoot "..\tools"))
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$tools = (Resolve-Path -LiteralPath $Tools).Path
. (Join-Path $root "launcher\toolchain.ps1")
$bt = Get-BuildTools $tools
$env:JAVA_HOME = Join-Path $tools "jdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$out = Join-Path $root "qa\build"
if (Test-Path $out) { Clear-BuildDirectory $PSScriptRoot $out }
New-Item -ItemType Directory -Force $out | Out-Null

"== unit tests (core + jarvis, plain JVM)"
$src = Join-Path $root "launcher\src\tv\calmhome\launcher"
$core = @(Get-ChildItem (Join-Path $src "core"), (Join-Path $src "jarvis") -Filter *.java | ForEach-Object FullName)
$core += (Join-Path $src "downloads\DownloadPolicy.java"), (Join-Path $src "downloads\DownloadRecord.java")
$tests = @(Get-ChildItem (Join-Path $root "qa\tests") -Recurse -Filter *.java | ForEach-Object FullName)
& javac -encoding UTF-8 -d $out @core @tests
if ($LASTEXITCODE -ne 0) { throw "test compile failed" }
& java -cp $out CoreTests (Join-Path $root "launcher\assets\wallpapers\credits.tsv")
$testExit = $LASTEXITCODE
& java -cp $out tv.calmhome.launcher.core.VoiceTests
if ($LASTEXITCODE -ne 0) { $testExit = 1 }
& java -cp $out tv.calmhome.launcher.jarvis.JarvisTests (Join-Path $root "launcher\src")
if ($LASTEXITCODE -ne 0) { $testExit = 1 }

foreach ($suite in @("tv.calmhome.launcher.downloads.CatalogTests", "tv.calmhome.launcher.downloads.DownloadTests")) {
    & java -cp $out $suite
    if ($LASTEXITCODE -ne 0) { $testExit = 1 }
}
if (Get-Command node -ErrorAction SilentlyContinue) {
    & node --test (Join-Path $root "qa\tests\claude-code-provider.test.mjs") (Join-Path $root "qa\tests\recognizer-routing.test.mjs") (Join-Path $root "website\test.mjs")
    if ($LASTEXITCODE -ne 0) { $testExit = 1 }
} else { "SKIP Node provider and website tests: Node.js unavailable." }

if ($OpenPulse) {
    "== integration: TV client <-> OpenPulse TV link ($OpenPulse)"
    & java -cp $out tv.calmhome.launcher.jarvis.JarvisIntegrationTests (Join-Path $OpenPulse "packages\gateway")
    if ($LASTEXITCODE -ne 0) { $testExit = 1 }
}

"== static analysis: javac -Xlint:all over the app against android.jar"
$jar = Join-Path $tools "sdk\platforms\android-34\android.jar"
$stubs = Join-Path $bt "core-lambda-stubs.jar"
$all = @(Get-ChildItem (Join-Path $root "launcher\src") -Recurse -Filter *.java | ForEach-Object FullName)
# Generate resources and a QA build label from the current tree, even on a fresh checkout.
$gen = Join-Path $out "gen"
$pkg = Join-Path $gen "tv\calmhome\launcher"
New-Item -ItemType Directory -Force $pkg | Out-Null
& "$bt\aapt2.exe" compile --dir (Join-Path $root "launcher\res") -o "$out\resources.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
& "$bt\aapt2.exe" link -I $jar --manifest (Join-Path $root "launcher\AndroidManifest.xml") --java $gen --auto-add-overlay -o "$out\lint.apk" "$out\resources.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }
[IO.File]::WriteAllText((Join-Path $pkg "BuildInfo.java"), 'package tv.calmhome.launcher; public final class BuildInfo { public static final String BUILD = "QA", COMMIT = "QA"; }')
$all += @(Get-ChildItem $gen -Recurse -Filter *.java | ForEach-Object FullName)
New-Item -ItemType Directory -Force "$out\lint" | Out-Null
# Through cmd so javac's warnings on stderr don't become terminating errors in Windows PowerShell 5.1.
$argFile = Join-Path $out "lint-args.txt"
$quoted = @("-Xlint:all,-options,-serial", "-source", "8", "-target", "8", "-bootclasspath", "`"$jar;$stubs`"",
    "-encoding", "UTF-8", "-d", "`"$out\lint`"") + ($all | ForEach-Object { "`"$_`"" })
[IO.File]::WriteAllLines($argFile, ($quoted | ForEach-Object { $_ -replace '\\', '/' }))  # @argfiles treat \ as escape
$lint = cmd /c "`"$env:JAVA_HOME\bin\javac.exe`" @`"$argFile`" 2>&1"
$lintExit = $LASTEXITCODE
$warnings = @($lint | Select-String "warning:")
$lint | Select-String "warning:|error:" | ForEach-Object { "  $_" }
"lint: $($warnings.Count) warning(s)"
if ($lintExit -ne 0) { throw "lint compile failed" }

exit $testExit
