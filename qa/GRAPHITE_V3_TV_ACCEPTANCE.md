# Graphite beta 2 acceptance

**Build** `tv.calmhome.launcher/.MainActivity` · `3.0.0-beta.2` · versionCode `3002` · minSdk 26 · targetSdk 34. Windows development and disposable Google TV emulator runs took place on 4 November 2026. Results refer to this build unless marked historical. Local raw screenshots and package dumps are excluded from Git.

**Installed on the TCL.** The corrected owner-only APK is `ZeroPulseTV-3.0.0-beta.2-Graphite-private-fc8d7174.apk`, SHA-256 `6d93b99c661bc213f8256ac6802c68a3c95c68bd85859839733d09c2c12ed93d`. It passed all six physical navigation sections, actual HTTPS transfer, text commands and retention of the original HOME and eight favorites. The owner heard the TTS preview. Normal-motion frame pacing missed its target, Google returned no editable transcript in the attended attempt, and live Claude stayed gated by the owner's choice.

**Branch.** `feat/zeropulsetv-v3-graphite-glass-claude-voice-20261104` · original source `51a9288` · corrected Android runtime `fc8d717` · authenticated gateway and Android QA milestone `a469b0d`. Operating and raw diagnostic evidence remains local throughout this report. [Feature branch](https://github.com/steventa2024-lgtm/ZeroPulseTV/tree/feat/zeropulsetv-v3-graphite-glass-claude-voice-20261104)

## Device identity and build variants

The original TCL G10 Google TV, Android 12/API 31, was the only authorized device during preflight. Its physical output is 3840×2160 and its launcher surface is 1920×1080 at 320 dpi. Its selected HOME was ZeroPulse. The installed 3001 owner build had SHA-256 `dae95b575126810022fa3be756a98991d0b74f08a0b2edb36a590b775db35d9c` and certificate SHA-256 `0d6a6f7b254e84003ddbfc274233ecdc0b8f8af5442114f072267b7146dee4cd`. The public core and the owner's private-compatible build share a package ID. Only the latter contains the TCL's existing media and Web features. [Baseline](../docs/GRAPHITE_V3_BASELINE.md) · [Recovery](../docs/GRAPHITE_V3_RELEASE_ROLLBACK.md)

| Capability | Status | Evidence |
|---|---|---|
| Original launcher and Git isolation | PASS | The new feature branch starts at `51a9288aee14a43e70e4f642e7ce097cc737661b`. Graphite UI milestone `76f4ec5`, Android runtime refinement `269f977`. |
| Whole-app Windows compile and regressions | PASS | 63 core, 37 speech-text, 50 JARVIS, 14 catalog and 99 downloader checks passed. Strict Android compilation emitted zero warnings. The final corrected Windows run reported 27 Node tests passed and one native CLI opt-in skipped (28 total), in addition to the separate native CLI run. |
| Graphite layout, focus and real icons | PASS | At 1920×1080 the settled screenshot shows graphite curves, all six categories in a single row, the active count, a genuine installed YouTube icon, the round Go action and all four bottom actions. A brighter raised tab identifies Downloads. A separate focus ring identifies Apps. Overscan margins and card label/clock alignment were checked against both supplied references after a second build. |
| Android background jobs and file checks | PASS | API 34 emulator exercised a real job, APK parser, actual cancel/retry, persisted completed history, malformed input and safe self-update refusal. Local code, redirect, unauthorized URL, hash mismatch and stale callbacks also have JVM coverage. |
| Transfer from the publisher over HTTPS | PASS | Emulator downloaded 5,744,160 bytes of the [official public v2.1.0-beta.1 APK](https://github.com/steventa2024-lgtm/ZeroPulseTV/releases/download/v2.1.0-beta.1/ZeroPulseTV-v2.1.0-beta.1.apk). Final host `release-assets.githubusercontent.com`, digest `ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401`. Launcher self-update via this downloader was refused. |
| Real progress, interrupted job and reboot | PASS | Independent single-purpose emulator fixture, 8,538 bytes. The active center displayed 11% at 1,024 bytes of 8,538. Boot count advanced from 4 to 5. The same record (ID 8) finished with independent digest `99e9b2786f4c72129f76370fe32201d08d89f3cc8b97853d178943c949c0b594`. |
| Android install approval and post-install | PASS | Disposable API 34 Google TV. Opened system source permission, canceled one system confirmation, approved a second. Android reported the expected installed package, which opened and persisted in the dock. |
| TV picker on the TCL | BLOCKED | Its framework file picker is a stub. The launcher provides the source-link fallback. Physical downloader testing is limited to the published HTTPS APK under the owner's instructions. |
| Authenticated PC transport and conversation | PASS | 34 real local gateway integration assertions cover pinned pairing, authenticated exchange with a scripted model, saved history, reset, Stop reconciliation, offline/reconnect, re-pairing, scope and revocation. Scripted replies test transport. |
| Claude Code → authenticated Anthropic response | BLOCKED | No approved owner-billed runtime configured for a live request. Tests exercised streaming text, status, cancellation and tool-free subprocess isolation. A separate installed-CLI test used a local synthetic Messages endpoint and no real billing. |
| Android recognition capability | PASS | TCL advertised two system speech activity handlers before installation. App checks handler resolution, cancellation, editable results and text fallback. It does not request microphone permission. |
| Actual remote speech → editable transcript | BLOCKED | The physical TCL presented a system chooser. Google was selected once, and the real Google TV VoiceInputActivity opened. No editable transcript was observed during the first attended window. Android Back returned to the app. The owner chose to skip a second attempt. |
| Actual local TTS output | PASS | On the TCL the engine completed the local voice preview, Stop interrupted another sample, and the owner confirmed hearing the preview over the TV audio output. The helper restored the original off setting. |
| Actual spoken Claude reply | BLOCKED | The owner chose to keep authenticated Claude gated. The local preview and synthetic provider tests do not exercise a spoken live model reply. |

## Physical screenshot follow-up

Review of the real Assistant screen exposed two issues in the initial 3002 APK. The keyboard appeared immediately on clean entry and the sidebar could draw commands behind the composer. The correction in `fc8d717` clips the sidebar to its viewport and hides the keyboard on entry. Typing remains available after the composer is selected. The corrected signed candidate preserves version 3002, the app identity and the original certificate. Its filename is `launcher/build-private/ZeroPulseTV-3.0.0-beta.2-Graphite-private-fc8d7174.apk`, 6,511,046 bytes, SHA-256 `6d93b99c661bc213f8256ac6802c68a3c95c68bd85859839733d09c2c12ed93d`. The earlier immutable build below remains the initial migration artifact.

**Corrected build checks.** Android installed the corrected APK in place on the TCL under its original UID, first-install timestamp and HOME selection. The installed bytes match `6d93b99c661bc213f8256ac6802c68a3c95c68bd85859839733d09c2c12ed93d`. Cold launch was 728 ms (734 ms wait). Remote entry on the corrected FHD emulator showed the full dashboard with the keyboard hidden and the sidebar above the composer. Evidence is in `graphite-corrected-assistant/remote-fhd/remote-assistant-dashboard.png`.

**Physical navigation — PASS.** The corrected TCL traversed Home, Apps, Downloads, Inputs, Assistant and Settings with the remote, including the wallpaper chooser, five category editors, All Categories, legacy Anime source access, all four footer actions, invalid-link fallback and Back to Home. The final Assistant capture shows its complete header, saved PC pairing, offline gateway status and composer with no overlapping commands. The six native screen captures are under `graphite-tcl/corrected/navigation/`, including `02-download-center.png` and `11-assistant.png`. Default HOME stayed selected. The crash/ANR event check found zero matching entries in the observation interval.

| Corrected Assistant and offline behavior | Status | Evidence |
|---|---|---|
| Real emulator UI pairing and authenticated chat | PASS | Used a disposable gateway and a scripted local model. The TV and PC displayed matching verification codes. Owner-side fixture approval established pinned TLS. A typed question produced a real TV chat exchange with the scripted model. Provider status accurately read “Not reported”. |
| Keyboard, local commands and audible-output controls | PASS | On the corrected emulator the keyboard stayed hidden on entry and D-pad OK opened it on demand. “Show my downloads” opened Manage Downloads, “open apps” opened All Apps, and Back preserved the pair. The local TTS engine completed the preview and Stop interrupted playback. |
| Offline, reconnect and revocation | PASS | Stopping the gateway preserved the pair and text on the screen. Restarting reconnected using the stored Keystore identity. PC revocation removed the device. Evidence `assistant-1793829598600/result.txt`. |
| Cold Home with no launcher network access | PASS | The emulator denied network access only to ZeroPulse. Android confirmed the installed Play Store package. Its genuine banner appeared in the temporarily pinned dock during a 376 ms cold Home launch. The original dock, app network rule and disabled chain were restored. Evidence `assistant-1793830038180/result.txt`. |
| Published HTTPS download on TCL | PASS | The original and corrected builds both fetched the publisher's APK through Download Center → Review source → Start download. Both showed the expected digest and self-update refusal. Each test removed its own history entry and preserved HOME. Final evidence `graphite-tcl/corrected/published/published-release.txt`. |

## TCL update and private migration

| Test | Status | Result |
|---|---|---|
| Exact private 3001 → 3002 rehearsal | PASS | Fresh stock Android TV API 36 accepted the same 3001 APK installed on the TCL. Streaming and Web preferences were set through its UI. A Windows emulator renderer crash interrupted the install transport. A normal `install -r` retry on the same emulator disk restored the exact 3002 package, with its prior setting and both private activities working. Android security protections stayed enabled. |
| Native 4K output | PASS | 3840×2160 real app bounds and PNG on the API 36 emulator. Reduced-motion Apps → Movies & TV moved focus correctly. Display settings and default HOME were restored. |
| Owner-approved in-place TCL install | PASS | Android installed the exact verified 3002 APK. The installed file digest, unchanged app UID, original first-install timestamp and default HOME component were independently checked. All eight visible favorites were present in their original order. The measured cold launch was 724 ms (752 ms wait). |
| Actual TCL framebuffer | PASS | The physical panel is 4K. Direct screencaps are 1920×1080 because the TCL's Android launcher surface is FHD. Native 4K proof is reported separately. |

The private evidence directory is `qa/device-evidence/graphite-tcl`. `old-home.png` and `new-home.png` show the before/after launcher, and `favorites-comparison.txt` verifies the eight entries. Under `graphite-4k-private-269f9773`, `migration-resume.log` documents the preserved private setting and `native-ui/13-4k-reduced-motion.png` is the true 4K capture.

## CI

**PASS** on public source milestone `25cc2ce`. The Apple Silicon runner checked its ARM64 architecture, compiled and verified a development APK, and ran the host tests. The independent Linux Android TV runner built temporary emulator packages, then passed navigation, real transfer, both installer decisions and the separate in-flight reboot test. The workflows only published disposable CI evidence. [Apple Silicon](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41917224042) · [Android TV](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41917223973)

**PASS** on corrected code milestone `a469b0d`. The first Apple Silicon attempt ended before any step executed because GitHub could not allocate a runner. Attempt two passed architecture verification, all tests, APK build and signature verification. The Android TV job passed both reboot scenarios. [Corrected macOS job](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41920851016) · [Corrected Android TV job](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41920851019)

## Corrected TCL performance and app checks

| Test | Status | Result |
|---|---|---|
| Installed library, app launch and local commands | PASS | Favorites and Games filters, a search with no matches, actual launch of installed YouTube, and its recent-app entry all passed on the TCL. The typed commands “show my downloads” and “open apps” opened Manage Downloads and All Apps. Back retained the original PC pairing and HOME. See `graphite-tcl/installed-library` and `graphite-tcl/corrected/typed-local.log`. |
| Normal-motion focus smoothness | FAIL | 20 alternating D-pad keys, 225 ms cadence. Focus ended on the expected tile and the process stayed alive, but Android classified 77 of 120 frames (64.17%) as janky. Median, p90 and p95 were 53 ms. P99 was 57 ms. The earlier 3001 run on the same TCL reported 99 of 120 (82.50%), p50 53 ms and p95 57 ms. Graphite's trace recorded zero slow UI-thread frames, zero slow bitmap uploads and 77 slow draw-command events. Aggregate counters do not identify an individual view as the cause. |
| Reduced motion comparison | PASS | With the launcher's own Reduced motion setting enabled, all 20 D-pad moves still reached their targets. Android rendered 20 frames, with p50 31 ms and p95/p99 34 ms. All 20 missed the Android deadline. The two modes produced different numbers of frames. The test restored the original Reduced motion setting to Off and returned the TCL to its original Home launcher. |
| Memory across repeated navigation | PASS | The same process handled all three cycles. PSS was 55,706 KB immediately after focus and 80,969 / 82,257 / 83,697 KB in the three subsequent cycles. These short samples cannot rule out slow growth. Evidence `graphite-tcl/profile`. |

The final corrected APK also passed on a native 3840×2160 emulator raster. The app and capture dimensions agreed, the keyboard stayed hidden when Assistant opened, and D-pad OK opened it on demand. The sidebar remained inside its bounds. Native screenshots are under `graphite-4k-private-fc8d7174/native-ui` and `graphite-4k-private-fc8d7174/assistant-remote`.

## FHD navigation and performance

| Test | Status | Result |
|---|---|---|
| All six sections, focus and Back | PASS | On API 34 Home, Apps, Downloads, Inputs, Settings and Assistant opened. All five direct category editors, All Categories, named bottom actions, route return and HOME resolution were exercised. |
| Installed search, filters, real app launching and recents | PASS | Verified Favorites, Games and an empty search. Installed YouTube opened and appeared on Home after the real launch. |
| Reduced motion | PASS | A 1920×1080 capture and D-pad Apps → Movies & TV focus change were verified with animator scale zero. Scale and default HOME were restored. |
| Focus timing and memory observation | PASS | 20 alternating keys. 198 frames, 36 janky (18.18%), p50 24 ms, p95 34 ms and p99 46 ms. After focus, PSS was 45,466 KB. After three Home/Apps/Inputs/Settings/Downloads navigation cycles, samples were 55,469, 56,493 and 57,549 KB. The short trace does not establish long-term memory behavior. |

## Initial owner-compatible artifact

The signed 6,506,751-byte candidate is stored locally at `launcher/build-private/ZeroPulseTV-3.0.0-beta.2-Graphite-private-269f9773.apk`. Its SHA-256 is `5a6417e8cf57b9d6fbcdc965d94c46fe43ec9a1ac8b206cf211df2274bacb208`. Android v2/v3 signature verification gave the same certificate as the TCL's installed 3001 APK. It contains the private Streaming, Web and Player entry points. The private release build and lint completed with 0 lint errors and 66 warnings (37 are text localization warnings). The 141-case private regression run reported 140 passed and one Windows symlink case skipped, with zero failures. Strict whole-app javac warnings and Android lint warnings are separate counts.

The public-core dev APK tested on the FHD emulator had SHA-256 `30263eadcc16d8259c3c09f4069a83de7df7a9493064ca4a0ed8535a91d30e79`, with disposable certificate `3adaef78741bd5a309da4b55fbd90f9c5cc3f02a7997854dda6bea29eb7046f7`. The retained local copy is `qa/device-evidence/graphite-fhd/ZeroPulseTV-3.0.0-beta.2-public-development.apk`. Use the owner-compatible artifact on the TCL. The opt-in run against the actual installed PC CLI passed 9 of 9 at `assistant-provider/loopback-native-cli.txt`, using a fake key and local endpoint. The owner selected local-only provider testing.

## Test records

The ignored local evidence tree is `qa/device-evidence/`. `graphite-host/final-corrected-windows.txt` contains the final Windows host suites. Under `graphite-fhd/`, `final-center-fhd.png` is the real FHD center, `downloads-rerun/results.txt` is the eight-group consent run, and `inflight/inflight-result.txt` and `inflight/inflight-active-center.png` record the interrupted transfer. The final API 34 acceptance run measured 439 ms from cold launch to Android's reported activity display. Initial software-renderer launches crashed the Windows QEMU process. The successful run used hardware rendering with Play Protect active.

Device screenshots may show installed apps or pairing status. They remain in the private local evidence folders. Evidence from the older 3001 release is described separately in the baseline document.
