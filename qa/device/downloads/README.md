# Download Center emulator acceptance

Use a disposable Android TV emulator. The runner checks both the explicit emulator serial and the device's emulator property. Its tests build a small permission-free APK with a framework TV activity and a companion instrumentation APK. Both use the same development key as the emulator's ZeroPulse launcher. The production signing key is unnecessary.

Build the companion from the repository root using Python 3 and the SDK/JDK used for the development launcher. Set `ZP_KEYSTORE_PASS` for the development key as described in the build guide.

```sh
python qa/device/downloads/build.py --sdk "$ANDROID_SDK_ROOT" --jdk "$JAVA_HOME" --build-tools 36.0.0 --keystore "$DEV_KEY" --output "$TMPDIR/zp-download-qa.apk"
adb -s "$EMU" shell getprop ro.kernel.qemu   # must return 1
adb -s "$EMU" install -r "$TMPDIR/zp-download-qa.apk"
python qa/device/downloads/emulator.py --serial "$EMU" --evidence "$TMPDIR/ZeroPulse-emulator-evidence"
```

The run assumes the matching development launcher is already installed. It prepares only the fixture's dock entry, exercises D-pad category selection and Back focus restoration, and captures Home and Download Center. It submits real JobScheduler transfers for a malformed document, a checksum mismatch, cancellation and retry. Its direct HTTPS case transfers the project's known v2.1 public APK through the developer's redirect, checks the published size and SHA-256, and verifies that Download Center rejects replacing the active HOME launcher. It also verifies a completed record survives an emulator reboot.

For installer tests, instrumentation seeds an inspected fixture and exits. The host then operates **Manage Downloads → download details → Install with Android approval** through the visible launcher UI. It locates ZeroPulse's source-permission switch in Android TV settings. It chooses Cancel and Install in separate real system confirmation dialogs and captures the results. The allow case activates **Add to dock** and **Open app** in download details. It checks the fixture's actual foreground activity. A subsequent assertion opens the persisted history, verifies Android's callback result, checks the stored dock package, and removes only the test row. The installed fixture and favorite remain available for launcher smoke tests.

The output directory contains screenshots, native view hierarchies, per-phase text results and emulator diagnostics. Check the aggregated outcomes as well as `result=PASS` in individual instrumentation logs. The feature-branch CI workflow uploads emulator evidence separately from physical-TV evidence.

To reboot during an actual transfer, use the same emulator-only runner with `--inflight-only`. A signed fixture stream provides 1,024 bytes and withholds the remainder throughout the first boot. The runner reboots after observing those transferred bytes, then waits for Android to reopen the document through its persisted job and read grant. It verifies the same history ID, independent fixture SHA-256, full byte count and parsed package after reboot.

```sh
python qa/device/downloads/emulator.py --serial "$EMU" --evidence "$TMPDIR/ZeroPulse-inflight-reboot" --inflight-only
```
