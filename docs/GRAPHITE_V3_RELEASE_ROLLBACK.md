# Graphite beta installation and recovery

The public-core APK and private-compatible APK share the same Android package and HOME activity. Use the owner-compatible variant on the TCL that already contains the private Streaming and Web modules. The private source patch, its original pinned dependencies and the owner signer remain local. The public 2.1 APK and website retain their published metadata.

Before installation record the device serial, HOME resolution, package version and signer. Verify the candidate hash and signature, confirm its versionCode exceeds the installed app and obtain approval for that physical update. Use `adb -s SERIAL install -r candidate.apk` only on the confirmed target. Android should report success and `dumpsys package tv.calmhome.launcher` should show 3002. Confirm the previous HOME component, dock, wallpaper settings and PC pairing survived.

If the UI is unusable, select another available launcher through Android Settings → Default apps → Home app. An in-place recovery build must use the same package and signer and a versionCode newer than the installed candidate; build a known-good recovery from the saved prior private-compatible source. For example, a recovery over 3002 can use 3003, and a subsequent successor must advance again. Downgrading to the public 2.1 APK cannot preserve this installation through a normal update.

Keep screenshots, full package dumps and pairing information local. The release procedure does not require publishing, changing OEM launcher ownership, clearing app data or uninstalling the primary launcher.
