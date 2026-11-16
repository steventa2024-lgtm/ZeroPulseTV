# V3 baseline

The development branch starts at upstream main `861f6a3746ddfb127f8659ebcbcbc943f3eb90c2`. The chat workspace was an unborn `master` with no remote or tracked files. The build uses a separate worktree of the fetched commit. The existing owner checkout and public release remain separate.

| Baseline at that revision | Result |
| --- | --- |
| Windows Android build with a temporary development identity | APK assembled, aligned and signed |
| JVM core and JARVIS suites | 53 core and 33 JARVIS checks passed |
| Full app javac lint | Zero warnings |
| Website | 18 tests passed, including published screenshot integrity checks. Syntax and static build passed |
| Initial ADB inventory | Empty until wireless service discovery found the already-authorized TCL |
| Installed physical baseline | TCL G10, Android 12/API 31, 2.2.0-beta.1 code 2201. Actual Home screenshot captured locally |
| Existing process counters | 495 frames, 18 janky (3.64%), p50 18 ms, p95 27 ms. PSS 39,664 KB, RSS 92,416 KB. Warm already-running activity delivery took 5 ms. No comparable cold start was measured |

The v2.1 manifest uses package `tv.calmhome.launcher`, HOME component `.MainActivity`, versionCode 21 and minimum API 26. Home reads the existing `calmhome` preferences and ordered dock, filters installed apps, and starts them through leanback or standard launch intents. Inputs uses Android TV input APIs. WallpaperStage rotates bundled credited photos using two reusable image buffers. The old theme provides translucent panels. The assistant uses a pinned local PC connection, Android Keystore and a local command parser.

The original build expects a specific Windows tools layout. This host provides Windows, JDK 17, Android platforms 34/36 and Build Tools 36.0.0. An Apple Silicon execution result requires a Mac. No developer catalog or owner-approved source APK accompanied the request.
