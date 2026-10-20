# ZeroPulse TV v2.1.0-beta.1 — first public beta

A fast, minimal Android TV home screen with offline wallpapers, quick inputs and local TV commands, plus an
optional experimental JARVIS assistant that connects to **your own** PC. Free and open source.

**Beta:** tested on one TV (TCL G10, Google TV, Android 12). Read
[COMPATIBILITY.md](../../COMPATIBILITY.md) and [INSTALL.md](../../INSTALL.md) before installing.

## Download and verify

| | |
|---|---|
| File | `ZeroPulseTV-v2.1.0-beta.1.apk` (5,744,160 bytes) |
| SHA-256 | `ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401` |
| Package | `tv.calmhome.launcher` |
| Version | versionName `2.1`, versionCode `21`, minSdk 26 (Android 8.0), targetSdk 31 |
| Permissions | `android.permission.INTERNET` only |
| Signature | APK Signature Scheme v2 + v3 |
| Certificate | `CN=Calm Home`, RSA 2048, valid 2026-10-19 → 2054-03-06 |
| Certificate SHA-256 | `0d6a6f7b254e84003ddbfc274233ecdc0b8f8af5442114f072267b7146dee4cd` |
| Certificate SHA-1 | `0b8f0e0ee3288dbbb50257b03af136587193dd9f` |

```
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs ZeroPulseTV-v2.1.0-beta.1.apk
```

## Source

- Public source: tag `v2.1.0-beta.1` of this repository. `SOURCE-SHA256SUMS.txt` lists the SHA-256 of every
  file the APK is built from (`launcher/AndroidManifest.xml`, `src/`, `res/`, `assets/`).
- The APK was built and tested from the maintainer's development commit **`95a61a9`**, which the app's
  About screen shows as "Source". The public tag contains exactly the same launcher files (verified file by
  file); the development history is not published.
- Rebuilding the tag gives an APK with identical code, resources, manifest and wallpapers; only the build
  timestamp and the signature differ (verified).

## What's in it

- Home with clock, greeting, dock of favourite apps, rotating offline wallpapers with photo credits.
- All Apps, Inputs (HDMI and tuner), Wallpapers, Settings and About screens; dock editing; remote-first.
- Assistant: typed TV commands that run on the TV without any PC ("open YouTube", "inputs", "switch to HDMI 2").
- **JARVIS (optional, experimental)**
  - requires **your own** compatible OpenPulse/JARVIS gateway on a PC on your network;
  - secure pairing: one-time code shown on the PC, certificate pinning, TLS 1.3, approval on the PC;
  - **no shared AI server, no bundled language model, no paid AI API key** needed by the launcher;
  - chat only from the TV; TV-action suggestions appear as buttons you confirm;
  - **voice control is not implemented** in 2.1;
  - the OpenPulse TV link it needs is **not yet in a public OpenPulse release**; until then JARVIS stays
    "Not paired".

## Testing done

On a TCL G10 (Android 12), installed as an update over an earlier build:

- 20/20 JARVIS checks on the TV: pairing with certificate check and PC approval, real replies from a local
  model, thinking states, multi-line replies and remote scrolling, local commands while connected, suggestion
  buttons, history on reopen, offline in 3 s when the PC stops (Home unaffected), automatic reconnect,
  no connection while the Assistant is closed, revocation and re-pairing, pairing kept across updates and a
  reboot, no secrets in logs, truthful Settings, non-allowed addresses refused, invalid address refused.
- Launcher regression on the TV: Home, dock navigation and editing, Netflix launch/return, All Apps, Inputs and
  HDMI switching, Settings and Search keys, wallpaper credits, reboot back into ZeroPulse TV (reboot run on the
  preceding build, whose Home code is identical). Navigation and layout bugs found during testing were fixed;
  focus after connecting, Up to the conversation and the JARVIS panel layout were re-checked on this exact build.
- Performance on that TV: cold start 381 ms (median of 15), warm return 67 ms, 1 slow frame in 72 during dock
  navigation, idle memory 43 MB flat, 0 % idle CPU, no crashes.
- 53 + 33 JVM unit tests, 27 integration tests of the TV's JARVIS client against a real OpenPulse TV link,
  `javac -Xlint:all` with 0 warnings.

**Fresh installation** on a TV that never had ZeroPulse TV: **not yet verified.** The code starts with no
saved settings (default dock, JARVIS not paired, no network use), but this has not been run on a clean device
for this beta. Reports welcome.

## Known issues and limitations

- Google TV devices cannot select another Home app in Settings; see INSTALL.md (advanced ADB steps, at your own risk).
- Live-TV tuner integration targets TCL's TV app; other brands may differ.
- The pairing form's example address ("e.g. 192.168.4.33") is only an example.
- "Powered by ZeroPulse AI" appears on Home even when JARVIS is not paired.
- No automatic update check. Rollback requires uninstalling (loses settings).

## Install, update, roll back

See [INSTALL.md](../../INSTALL.md): ADB or USB sideload, optional Home setup, and how to return to your
original home screen (`pm enable` commands if you used the advanced steps, then uninstall).
