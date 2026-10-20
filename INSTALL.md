# Installing ZeroPulse TV

ZeroPulse TV is sideloaded: it is not in the Google Play Store. Installing it adds one app and changes
nothing else on your TV. Read [COMPATIBILITY.md](COMPATIBILITY.md) first.

> **Beta:** this release was tested on one TCL Google TV, as an update. A fresh install on any other device
> has not been verified yet. Nothing in these steps can harm your TV, and section 5 explains how to undo them.

## 1. Download and verify

1. Download `ZeroPulseTV-v2.1.0-beta.1.apk` and `SHA256SUMS.txt` from the
   [v2.1.0-beta.1 release](https://github.com/steventa2024-lgtm/ZeroPulseTV/releases/tag/v2.1.0-beta.1).
2. Check the checksum. It must be exactly
   `ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401`.

   | System | Command |
   |---|---|
   | Windows (PowerShell) | `Get-FileHash .\ZeroPulseTV-v2.1.0-beta.1.apk -Algorithm SHA256` |
   | macOS | `shasum -a 256 ZeroPulseTV-v2.1.0-beta.1.apk` |
   | Linux | `sha256sum ZeroPulseTV-v2.1.0-beta.1.apk` |

3. Optional, with the Android SDK build-tools: `apksigner verify --print-certs ZeroPulseTV-v2.1.0-beta.1.apk`
   must show certificate SHA-256 `0d6a6f7b254e84003ddbfc274233ecdc0b8f8af5442114f072267b7146dee4cd`.

Do not install a file whose checksum differs.

## 2. Install

### Option A — ADB from a computer (recommended)

1. On the TV: **Settings → System → About**, select **Android TV OS build** 7 times to unlock developer options.
   Then **Settings → System → Developer options**: turn on **USB debugging** (on many TVs this also allows
   debugging over the network). Menu names vary by brand.
2. Find the TV's IP address (**Settings → Network & Internet →** your network).
3. On the computer, with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools):

   ```
   adb connect <tv-ip>:5555
   adb install ZeroPulseTV-v2.1.0-beta.1.apk
   ```

   Accept the "Allow USB debugging?" prompt on the TV the first time.
4. When you are done, you can turn debugging off again.

### Option B — from a USB drive or a file-transfer app

Copy the APK to the TV (USB drive, or an app such as *Send Files to TV* or *Downloader*), open it with a file
manager on the TV and allow **Install unknown apps** for that file manager when Android asks.

## 3. Open it

ZeroPulse TV appears in your apps list like any other app. On first start it:
- shows the dock with whichever of these apps are installed: Netflix, Prime Video, Disney+, Hulu, YouTube,
  YouTube TV, YouTube Music (or an **Add apps** tile if none are);
- starts with JARVIS **not paired** and makes no network connections;
- needs no account, no PC and no internet connection.

Add or remove dock apps: **All Apps**, long-press OK on an app → *Add to dock*; long-press a dock app →
*Move* / *Remove from dock*. Removing an app from the dock never uninstalls it.

## 4. Use it as your Home screen (optional, device-dependent)

The Home button is controlled by Android, and devices differ:

| Device type | What to do |
|---|---|
| Android TV devices that offer a choice | Press **Home**; if Android asks which app to use, choose **ZeroPulse TV** → *Always*. Some devices have **Settings → Apps → Default apps → Home**. |
| **Google TV** devices (including TCL Google TV) | Google TV's home cannot be replaced from Settings. ZeroPulse TV works as a normal app. The only way to make it the Home screen is the advanced ADB procedure below. |

### Advanced: make ZeroPulse TV the Home screen on Google TV (at your own risk)

This disables Google TV's own home screen app. It is reversible with ADB, but it can affect Google TV
features (profiles, recommendations, "For you") and you must undo it **before** uninstalling ZeroPulse TV,
or the TV will have no home screen. Only tested on a TCL G10 (Android 12).

```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity tv.calmhome.launcher/.MainActivity
```

The second line is needed on TCL Google TV: otherwise Google's setup "recovery" screen takes over the Home
button. Wait about 20 seconds before restarting the TV so Android saves the change, then restart with
`adb shell svc power reboot` (not `adb reboot`).

## 5. Go back to your original Home screen

| You did | To undo |
|---|---|
| Only installed the app | Nothing to undo. Uninstall if you like: **Settings → Apps → ZeroPulse TV → Uninstall**, or `adb uninstall tv.calmhome.launcher`. |
| Chose ZeroPulse TV as Home in Android's chooser/settings | Pick the original launcher in **Settings → Apps → Default apps → Home** (or the chooser), then uninstall if you like. |
| Used the advanced Google TV steps | Re-enable Google TV first, then uninstall: |

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb shell svc power reboot
adb uninstall tv.calmhome.launcher
```

If the TV ever shows no home screen, connect with ADB and run the two `pm enable` lines above. A factory
reset is never needed for this.

## 6. Updates and rollback

- **Update**: install a newer release over the old one (`adb install -r <new>.apk`). Your dock, settings and
  JARVIS pairing are kept. Releases are signed with the same key; Android refuses an update signed with a
  different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) — do not uninstall to get around this unless you accept
  losing your settings.
- **Rollback**: Android does not allow installing an older version over a newer one. To return to an older
  release, uninstall ZeroPulse TV (this deletes its settings and pairing) and install the older APK. Undo the
  advanced Home steps first if you used them.
- There is no automatic update check; watch the Releases page.

## 7. JARVIS (optional)

Pairing needs a PC running an OpenPulse gateway with the TV link. Setup and security model:
[docs/JARVIS.md](docs/JARVIS.md). The TV never needs JARVIS to work.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb connect` fails | Debugging off, wrong IP, or the TV is in standby — wake it and retry. |
| App installed but Home still opens Google TV | Expected on Google TV; see section 4. |
| Inputs screen shows no tuner / wrong names | Inputs come from Android's TV input service; brands differ. See COMPATIBILITY.md. |
| JARVIS says "Can't reach the PC" | PC asleep, OpenPulse not running, TV link not enabled, or the TV's address is not allowed in the gateway's `allowFrom`. |
| JARVIS says "PC identity changed" | The PC's TV-link certificate was replaced; enter a new code from `openpulse tv invite` under **Re-verify PC**. |
