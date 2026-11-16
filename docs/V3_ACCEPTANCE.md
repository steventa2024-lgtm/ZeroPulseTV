# ZeroPulse TV 3.0.0-beta.1 acceptance

VersionCode 3001 targets Android 14 (API 34) and supports Android 8 (API 26) and newer. Both variants retain package `tv.calmhome.launcher` and HOME activity `.MainActivity`. The physical TV needs the locally built compatible variant to retain its existing private media and Web modules.

## Delivered builds

| APK from application source `270075f79586e7e7f73a270fc806ffa060713f16` | Size | SHA-256 |
| --- | ---: | --- |
| Owner-signed private compatible | 6,670,762 bytes | `4e1e2fba50ef4a81b3d3f9250e1763cc91dbef63a6ba307dbf593acbc15c546b` |
| Owner-signed public core | 5,961,512 bytes | `552ac74c2568b1845b554f36080892e2db50156f16bb5c9c06d85dc4629daea4` |
| Development-signed public core | 5,961,502 bytes | `4ea53fabaf3d998fc6cc3b0480c313dd5ca68c191724e51d7508ca78ae4cdec2` |

The owner-signed APKs have verified v2/v3 signatures and signer SHA-256 `0d6a6f7b254e84003ddbfc274233ecdc0b8f8af5442114f072267b7146dee4cd`, which matches the installed TCL application. The private variant is reproducible from the public commit plus an owner-local patch. APKs, private sources, credentials and device evidence are excluded from the public branch.

## Host and integration tests

| Area | Result |
| --- | --- |
| Windows | JDK 17 and Android Build Tools 36 assembled and verified both public-core APKs. Full Java compile reported zero warnings. |
| Launcher and downloads | 53 core checks, 10 catalog checks and 81 downloader checks passed. Coverage includes the historical dock format, wallpaper credits, URL and redirect validation, integrity failures and stale job or installer callbacks. |
| JARVIS | 42 JVM checks and 31 real loopback OpenPulse gateway checks passed. The suite exercises pairing approval, pinning, scoped chat, reconnect, revocation and cancellation. |
| Claude Code PC adapter | All six provider tests passed when the native CLI fixture was enabled. The installed Claude Code CLI contacted a synthetic loopback Messages API and used no tools. |
| Private module suite | 126 cases passed. Windows skipped one filesystem symlink test. Android lint reported zero errors and 60 warnings. |
| Website | All 18 website contract tests passed. The static build verified the seven published screenshot blobs. The production files and v2.1 release assets have no changes from the upstream base. |
| Apple Silicon | The Android 14 feature branch passed host tests, native APK assembly and signature verification on macOS arm64. [Build run](https://github.com/steventa2024-lgtm/ZeroPulseTV/actions/runs/41769984365) |

## Actual Android TV emulator

The API 34 emulator downloaded 5,744,160 bytes from the developer’s published v2.1 HTTPS URL via a GitHub asset redirect. It computed the published digest, inspected the package and refused replacement of the active HOME launcher. The signed, permission-free fixture separately exercised Android’s installation-source setting and system confirmation. Cancel produced a persisted CANCELED result and Retry. Approval produced INSTALLED, Open app launched the fixture and Add to dock persisted its package.

The emulator rejected malformed content and checksum mismatches, passed a real cancel/retry cycle, restored a completed record after reboot, and resumed an interrupted transfer under the same history ID. In the interrupted case Android reopened the chosen document after reboot with the transfer stalled at 1,024 bytes. The finished 8,532-byte file matched its independent SHA-256. No launcher relaunch or fresh document grant was needed. The test waits for Android’s batched grant write before rebooting. A reboot immediately after local file selection can require choosing the document again.

The tests found an Android job-ID reuse race on immediate retry. A new OS job ID for each schedule keeps late callbacks separate while preserving one history row. A real picker replaced instrumentation during the reboot test because instrumenting a process can force-stop it.

The Android TV API 36 emulator rendered the final public-core Download Center at a measured 3840×2160 with density 640. With animator scale zero, D-pad focus moved from Live Sports to Movies & TV inside the 4K safe area. Screenshots and the Android accessibility bounds were checked. The API 34 TV image limited its app surface to 1920×1080 even on a 4K framebuffer, so it supplied the FHD evidence only.

## Physical TCL G10

The TV started on Android 12/API 31 and ZeroPulse 2.2.0-beta.1, versionCode 2201. Its installed package matched the archived private build. Play Protect refused fresh installations of that historical APK and an interim target-31 candidate on the stock Android 14 emulator. Both refusals were respected. The replacement targets Android 14 and was accepted by the stock emulator. [Android 14 changes](https://developer.android.com/about/versions/14/behavior-changes-14)

The TCL accepted the owner-approved, signer-matched compatible APK `dae95b575126810022fa3be756a98991d0b74f08a0b2edb36a590b775db35d9c` as an in-place update. Android confirmed version 3001 and the original HOME component. A cold launch took 715 ms total and 721 ms wait. The D-pad reached all three source editors, validated invalid input and restored focus on Back. Apps, physical Inputs, Settings, Wallpapers and Assistant opened. Netflix remained the first dock favorite and launched successfully. Streaming stayed enabled, Web stayed disabled, and the PC pairing survived. The gateway was offline and the local typed command “show inputs” worked.

The TCL’s document picker resolves to an OEM stub. The owner selected a published HTTPS test only. Download Center transferred the project’s public v2.1 APK, reported 5.74 MB of 5.74 MB and computed `ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401`. This matched the published checksum and the launcher refused to replace itself. The test record was removed through the TV UI. The TV retained v3 and its HOME setting. No fixture APK was installed on the physical TV. [Published checksum](https://github.com/steventa2024-lgtm/ZeroPulseTV/releases/download/v2.1.0-beta.1/SHA256SUMS.txt)

## Changes from physical testing

The first installed build refreshed unchanged text and an empty library every second, preventing the TCL accessibility tree from settling. It also left the TV keyboard over the download result after submission. The follow-up build updates views only when their data changes, dismisses the keyboard on valid submission and shows the final HTTPS host without a long CDN query string.

A fixed workload on the first installed build sent 20 alternating right/left presses in Downloads at 225 ms intervals. Android reported 120 frames with 99 janky frames (82.50%), p50 53 ms, p95 57 ms and PSS 73,075 KiB. The follow-up composites one cached, sampled wallpaper instead of running a full-screen RenderEffect on both wallpaper layers. The earlier 2.2 baseline used a different workload. [Baseline measurements](V3_BASELINE_AUDIT.md)

## External inputs and recovery

The catalog deliberately starts empty until the owner supplies authorized APK links or files. Subscription-backed Claude relay remains gated. Live Anthropic login, quota and billing were not exercised. Physical installation confirmation of another app was excluded by the owner’s final test choice. Runtime coverage includes API 31 and 34. API 26 remains the minimum and was not tested on a device.

Back returns through the panel stack. Android’s Home app setting can select another available launcher without erasing ZeroPulse data. An in-place recovery APK requires the same package, matching signing certificate and an accepted versionCode. The public 2.1 APK cannot serve as an ordinary downgrade over 3001. A known-good recovery build can use the original signer and a higher code. [Download Center and recovery](V3_DOWNLOAD_CENTER.md) · [JARVIS setup and authorization](JARVIS.md)
