# Download Center emulator acceptance

This instrumentation APK only runs on a disposable Android Emulator. The fixture is a locally built, permission-free APK with no code. It has its own package ID. Use a development key shared by this test APK and the **emulator's** launcher candidate. Set `ZP_KEYSTORE_PASS` using the build instructions and build with Python 3.

```sh
python qa/device/downloads/build.py --sdk "$ANDROID_SDK_ROOT" --jdk "$JAVA_HOME" --build-tools 36.0.0 --keystore "$DEV_KEY" --output "$TMPDIR/zp-download-qa.apk"
```

Select an explicit disposable emulator serial. Check that `adb -s "$EMU" shell getprop ro.kernel.qemu` returns `1`. After installing the matching development launcher on that emulator, install this instrumentation APK and grant access to the two test documents.

```sh
adb -s "$EMU" install -r "$TMPDIR/zp-download-qa.apk"
adb -s "$EMU" shell content call --uri content://tv.calmhome.downloadqa.samples --method grant
adb -s "$EMU" shell am instrument -w -e phase verify tv.calmhome.downloadqa/.DownloadRunner
```

The verify phase checks actual JobScheduler transfers, malformed content, a supplied checksum mismatch, cancellation, retry and PackageManager inspection. Each phase must print `result=PASS`; a successful adb process exit alone is insufficient.

To test persistence, run `phase seed`, record the returned `downloadId`, reboot the emulator, and run `phase resume -e id NUMBER`. The latter reads the prior row and removes only that test download. If testing reboot during transfer, capture the queue and resulting terminal state separately.

For system installer callback acceptance, allow ZeroPulse to request installs via Android settings on the emulator. Run `phase install-deny` and choose Cancel with the remote, then run `phase install-allow` and approve Android's dialog. Each phase waits up to three minutes for the real status callback. The allow phase checks package presence. A framework Activity gives the fixture a launchable TV entry point. If necessary, use Continue approval in the download detail screen to reopen Android's consent UI. The signed fixture stays installed only on this disposable emulator.

Run `python qa/device/downloads/emulator.py --serial "$EMU" --evidence "$TMPDIR/ZeroPulse-emulator-evidence"` to perform these phases with real system UI actions. The runner checks the emulator serial and hardware property, captures Home and Download Center screenshots, and verifies D-pad focus restoration.

Run the `content call` again before new test sessions if persisted grants were released by cleanup. Every phase is opt-in. Live TCL acceptance uses a separately reviewed candidate and its own installation approval.
