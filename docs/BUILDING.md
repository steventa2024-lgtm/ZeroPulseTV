# Building ZeroPulse TV

The launcher builds with JDK 17, Android SDK Platform 34 and Build-Tools. Both pipelines compile Java 8 bytecode for API 26+, package the offline assets, align the APK, sign it, and verify the certificate. The package is `tv.calmhome.launcher`. A development signature works on a fresh emulator. An in-place TV update requires the installed app's signing certificate and a higher version code.

## macOS, including Apple Silicon

Install a native JDK 17 and Android SDK Command-line Tools through Android Studio. In SDK Manager, install Platform 34, Build-Tools 34.0.0, and Platform-Tools. If your SDK is outside the standard location set `ANDROID_HOME`. Set `BUILD_TOOLS_VERSION` to select a different installed revision. The scripts check 34.0.0, 36.0.0 and the project's legacy directory by default.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
bash qa/run-tests.sh
bash launcher/build.sh --development --output "$PWD/launcher/ZeroPulseTV-dev.apk"
```

`--development` generates a disposable signing key with each build. For repeated emulator updates, generate a separate test keystore once and pass it with `--keystore`. For an owner-signed candidate, supply the original keystore outside the repository. The script prompts for its password with input hidden, or reads `ZP_KEYSTORE_PASS` from the environment if already set. The key and keystore passwords must match.

```sh
bash launcher/build.sh --keystore "/path/to/owner-key.jks" --output "$PWD/launcher/ZeroPulseTV.apk"
```

The build prints the signer and SHA-256. It writes generated classes and intermediate APKs in a unique temporary directory and removes them on exit. No Google services, Gradle daemon, or connected TV is required to build.

## Windows

Point `-Tools` at a directory containing `jdk\bin\javac.exe`, `sdk\platforms\android-34\android.jar`, and `sdk\build-tools\34.0.0\aapt2.exe`. The original `tools\` layout and legacy `android-14\` build-tools directory still work. The script also recognizes 36.0.0 and the `BUILD_TOOLS_VERSION` override. Use a local key outside the checkout. To create a dedicated development key, run JDK 17 `keytool -genkeypair -keystore C:\keys\tv-dev.p12 -alias tv-dev -keyalg RSA -keysize 2048` and follow its prompts.

```powershell
$toolchain = "C:\path\to\tools"
pwsh -File qa\run-tests.ps1 -Tools $toolchain
$prompt = Read-Host 'Keystore password' -AsSecureString
$env:ZP_KEYSTORE_PASS = [Net.NetworkCredential]::new('', $prompt).Password
try {
    & .\launcher\build.ps1 -Tools $toolchain -Keystore C:\keys\tv-dev.p12 -OutApk .\launcher\ZeroPulseTV-dev.apk
} finally {
    Remove-Item Env:\ZP_KEYSTORE_PASS
}
Get-FileHash .\launcher\ZeroPulseTV-dev.apk -Algorithm SHA256
```

## QA and device updates

The runners execute the core, JARVIS and downloader JVM suites and compile the entire app against the Android framework. Install Node.js 22+ to include the provider and website tests. A compatible OpenPulse checkout with its dependencies installed enables the loopback TLS pairing and revocation suite. Pass its path as the sole argument to `bash qa/run-tests.sh` or as `-OpenPulse` to the PowerShell runner. The independent public website contract also runs with `node --check website/app.js`, `node --test website/test.mjs` and `node website/build.mjs` from the repository root.

Before a physical TV update, record `adb devices -l`, the selected serial, model, Android version, existing package version, and certificate. Compare with `aapt dump badging` and `apksigner verify --verbose --print-certs` on the candidate. Confirm owner approval before running `adb -s SERIAL install -r CANDIDATE.apk`. A signing mismatch or downgrade requires stopping. Preserve app data and current HOME ownership. Testing a development candidate on a disposable TV AVD requires a separate Android TV system image and emulator.

For an already-approved v3 installation, run `node qa/device-smoke.mjs SERIAL` from the checkout with Node 22+ and ADB on PATH, or set `ADB` to the full platform-tools executable. Begin with the launcher at Home. The script checks the package version before sending any input, navigates via real D-pad events, checks source forms, focus restoration, invalid-link handling, apps, settings and inputs, and saves PNG, accessibility XML, startup timing, memory and frame diagnostics under the ignored `qa/device-evidence/` directory. It refuses to send key events outside the launcher. Inspect screenshots for attribution, contrast and layout; automated focus bounds alone cannot verify those.

The feature-branch CI job runs the QA suite and signs a throwaway development APK on GitHub's standard Apple Silicon `macos-15` runner. Its image includes Android Platform 34 and Build-Tools 36.0.0. The workflow selects those versions and requires read-only repository access. [GitHub runner labels](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job) · [Installed macOS arm64 SDKs](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-arm64-Readme.md).
