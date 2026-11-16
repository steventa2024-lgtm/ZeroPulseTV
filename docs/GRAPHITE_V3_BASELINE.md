# Graphite v3 baseline

The new branch starts at `51a9288aee14a43e70e4f642e7ce097cc737661b`, the clean local v3 acceptance commit. Remote main was `861f6a3746ddfb127f8659ebcbcbc943f3eb90c2`. The preceding remote feature branch was behind the local fixes. Starting at the local commit retains its tested downloader, waveform, dock format, wallpaper credits, Keystore-backed pairing implementation and cached wallpaper work. The original checkout, prior feature checkout, website and published release remain separate.

## Verified before edits

- Windows host with JDK 17, Android SDK 34/36 and ADB. macOS is a separate build target.
- Baseline JVM core (53), catalog (10), downloader (81), JARVIS (42), live loopback gateway (31), Node provider and website suites ran before changes. The default native Claude fixture is opt-in. Whole-app javac completed with zero warnings.
- One authorized TCL G10 Smart TV on Android 12/API 31. The physical framebuffer is 3840×2160, its launcher surface is 1920×1080 at 320 dpi, and the current HOME resolves to `tv.calmhome.launcher/.MainActivity`.
- The installed app was `3.0.0-beta.1` / `3001`, SHA-256 `dae95b575126810022fa3be756a98991d0b74f08a0b2edb36a590b775db35d9c`. Its certificate fingerprint is `0d6a6f7b254e84003ddbfc274233ecdc0b8f8af5442114f072267b7146dee4cd`. The TV-compatible build requires the owner's private media/Web overlay.
- The system advertises two speech recognition activities and a TTS engine. Discovery alone does not verify microphone capture or audible output.
- The previous TV build measured 715 ms cold launch. A controlled twenty-key focus workload on its Downloads page had 99 janky frames out of 120 and a p95 of 57 ms. It is the historical comparison point. [Previous acceptance](V3_ACCEPTANCE.md)

The new candidate uses `3.0.0-beta.2` / `3002`, with minimum API 26 and target API 34. No D-pad key or new physical installation was needed for the preflight.
