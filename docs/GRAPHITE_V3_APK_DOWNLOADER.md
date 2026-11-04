# Graphite Download Center

The native Download Center uses the local download database, source catalog and Android package manager. It works offline for browsing installed apps, viewing history and importing files when a real document picker is available. A new installation has zero active downloads and no saved developer links.

## Remote controls

| Control | Result |
| --- | --- |
| Code or HTTPS field and arrow | Shows the developer host and exact link before starting a transfer. Owner-configured numeric shortcuts resolve locally. An unmapped numeric code can be copied and optionally opened in AFTVnews Downloader when that app is installed. |
| Apps, Movies & TV, Live TV, Games, Tools | Opens that category's saved links and locally imported APK records. Add a name, developer HTTPS URL, optional local code and optional publisher SHA-256. |
| All Categories and App Sources | Browses all catalog groups and link counts. Previously stored SPORTS entries appear under Live TV. Anime remains available with its existing links, codes and file associations. Removing a saved source never uninstalls an app. |
| Active Downloads | Counts queued, downloading, verifying and awaiting-approval records. Shows up to two active records, their real names when known, byte-derived progress when size is known, and transferred bytes otherwise. Checked APKs can supply their own icon. A generic file icon identifies a package whose artwork cannot yet be read. The row opens details. Active transfers offer Cancel. |
| Trending Apps | A labeled editorial shelf of launchable, actually installed apps, using their PackageManager icons. It contains no popularity telemetry or fabricated remote catalog. The plus button opens all installed apps. |
| Install APK | Opens checked files ready for installation or awaiting Android approval. Choose an APK file uses Android's document picker; selecting a row opens the inspection and consent workflow. |
| File Manager | Browses retained, inspected app-private APKs with their sizes and statuses. Select a file to see details, remove it or use the available install/open actions. No broad shared-storage permission is required. |
| Manage Downloads | Lists persistent history and opens contextual details. A failed or canceled item can be retried. Removal requests confirmation and affects only that record and its private file. |
| Assistant shortcut | Opens the separately configured Assistant. The downloader needs neither a gateway nor an assistant provider. |

## Transfer and approval

A native persisted JobScheduler job performs each transfer. HTTPS sources and each redirect hop are validated. Byte totals, network/storage errors, inspection results and callback outcomes come from the actual job and Android APIs. The worker checks APK format, package metadata, SDK compatibility, permissions, the supplied publisher checksum when available, and its locally computed SHA-256. A local digest without an independently trusted publisher checksum does not establish publisher identity.

Downloads are private to the launcher. When the owner selects Install, Android controls special-source permission, its installation confirmation UI and the final callback. The app marks a record installed only after a successful Android result and detection of the expected package/version. Launchable installed packages can be opened and added to the dock. A user-supplied APK cannot replace the active HOME launcher through Download Center. Use the signed launcher update procedure for that package.

## Testing

The JVM downloader suite checks state transitions, redirect policy, checksum failures, percent calculations, queue membership and stale worker/session callbacks. The catalog suite checks legacy persisted category keys, local-code lookup and conflicts. The Android emulator harness uses an independent signed test APK for actual allow/cancel callbacks, package detection, Open, dock persistence, and interrupted-job recovery. The explicit published-release check downloads the project's immutable v2.1 APK over HTTPS and verifies its digest and intentional self-update refusal. Current-run results and limits belong in the device acceptance report.

The TCL previously exposed only the Android TV framework stub for choosing files. The on-device owner can enter a legitimate direct HTTPS URL when a real picker is unavailable. No third-party APK is preconfigured or downloaded automatically.
