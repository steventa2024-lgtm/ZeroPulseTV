# Building Graphite on macOS

Install JDK 17 and Android SDK Platform 34, Build Tools 36.0.0, and platform-tools. Set `JAVA_HOME` and `ANDROID_HOME` to the local installations. The native build uses aapt2, javac, d8, zipalign and apksigner; there is no network dependency at app startup.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export BUILD_TOOLS_VERSION=36.0.0
bash qa/run-tests.sh
bash launcher/build.sh --development --output "$PWD/launcher/ZeroPulseTV-graphite-dev.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs launcher/ZeroPulseTV-graphite-dev.apk
shasum -a 256 launcher/ZeroPulseTV-graphite-dev.apk
```

Use the optional OpenPulse checkout argument to exercise the isolated real gateway. The development signer is disposable. To build a compatible release, use `--keystore` and enter the password locally when prompted. A TV containing the private media modules also requires its owner-local source patch. Keep all signing material outside the repository. The Windows equivalent accepts `-Tools`, `-Keystore` and `-OutApk`. [Complete build instructions](BUILDING.md)

The branch CI run on `25cc2ce` verified ARM64 on a hosted macOS 15 runner, ran the launcher tests, built the development APK and checked its signature. This is a hosted Apple Silicon result. An owner Mac mini was not connected to this Windows session. [Initial Apple Silicon run](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41917224042) · [Corrected source build and verification](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41920851016)

Inspect `aapt2 dump badging` for package `tv.calmhome.launcher`, versionCode 3002, versionName 3.0.0-beta.2, minSdk 26 and targetSdk 34. Compare the candidate certificate and actual installed package before considering an in-place update.
