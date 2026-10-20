# Building ZeroPulse TV

ZeroPulse TV is built with the plain Android SDK command-line tools: no Gradle, no libraries. The scripts are
PowerShell (Windows). The same steps work on other systems with the equivalent commands.

## 1. Toolchain

Create `tools/` next to `launcher/` with this layout:

```
tools/
  jdk/                                   JDK 17 (e.g. Eclipse Temurin 17)
  sdk/build-tools/android-14/            Android SDK Build-Tools 34 (aapt2, d8, zipalign, apksigner)
  sdk/platforms/android-34/android.jar   Android SDK Platform 34
```

Install Build-Tools and the platform with Google's `sdkmanager` (accept the Android SDK licence):

```
sdkmanager --sdk_root=tools\sdk "build-tools;34.0.0" "platforms;android-34"
```

`sdkmanager` installs Build-Tools 34.0.0 as `build-tools\34.0.0`; rename or copy it to
`build-tools\android-14`, or adjust the path at the top of `launcher\build.ps1`.

## 2. Signing key

Use your **own** release key. Create one once and keep it and its password outside this repository:

```
tools\jdk\bin\keytool -genkeypair -keystore C:\keys\zeropulse-release.jks -alias zeropulse ^
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Your Name"
```

An APK signed with your key cannot update the official release, and the official release cannot update yours.

## 3. Build

```
$env:ZP_KEYSTORE_PASS = "<your keystore password>"
powershell -ExecutionPolicy Bypass -File launcher\build.ps1 -Keystore C:\keys\zeropulse-release.jks
```

Output: `launcher\ZeroPulseTV.apk`. The About screen shows the build time and the git commit of your checkout.

## 4. Tests

```
powershell -ExecutionPolicy Bypass -File qa\run-tests.ps1
```

Runs the JVM unit tests (dock, greeting, wallpaper catalogue, command parser; JARVIS JSON, pairing code,
certificate pinning, P-256 signatures, WebSocket framing, plaintext-downgrade refusal, no logging of secrets)
and a `javac -Xlint:all` pass against `android.jar`.

`-OpenPulse <checkout>` additionally runs the TV's JARVIS client against a real OpenPulse TV link on this PC
(needs an OpenPulse checkout that includes the TV link, Node.js and its dependencies).

## Reproducing the official APK

Building tag `v2.1.0-beta.1` produces an APK whose code, resources, manifest and wallpapers are identical to
the official release; only the build timestamp in `BuildInfo` and the signature differ. Compare with
`dexdump` and `unzip -p` as described in the release notes.
