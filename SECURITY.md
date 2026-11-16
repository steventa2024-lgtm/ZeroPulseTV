# Security

## Reporting a vulnerability

Please report security problems privately through GitHub: **Security → Report a vulnerability** on this
repository. If that button is not available, open an issue titled "Security contact request" **without any
details**, and we will arrange a private channel. Do not post vulnerability details publicly. We aim to
acknowledge reports within 7 days.

Supported version: the latest release (currently v2.1.0-beta.1).

## Verifying releases

Every release lists the APK's SHA-256 and the signing certificate. Releases are signed with one key:

| | |
|---|---|
| Certificate | `CN=Calm Home`, RSA 2048, SHA256withRSA, valid 2026-10-19 → 2054-03-06 |
| Certificate SHA-256 | `0D:6A:6F:7B:25:4E:84:00:3D:DB:FC:27:42:33:EC:DC:0B:8F:8A:F5:44:21:14:F0:72:26:7B:71:46:DE:E4:CD` |
| Package | `tv.calmhome.launcher` |

The private key is kept offline by the maintainer and is never in this repository. If a release is ever
signed with a different certificate, treat it as untrusted unless an announcement on this repository
explains the change. Android also refuses to update an installed copy with a differently signed APK.

The package ID `tv.calmhome.launcher` comes from the project's first version. It will not change, because
changing it would turn every update into a separate app and lose users' settings.

## What the app can and cannot do

There is no assistant, voice input or spoken output (they were removed in 3.0.0-beta.7), and the app never requests `RECORD_AUDIO`.

- The private v3 beta requests `INTERNET`, `ACCESS_NETWORK_STATE` for Android 14 download job network constraints, `RECEIVE_BOOT_COMPLETED` for persisted jobs, and `REQUEST_INSTALL_PACKAGES` for the system installer. Public v2.1 requests only `INTERNET`.
- The TV can launch apps, switch inputs, and stage an inspected APK when the owner selects Install. Android controls permission to install from this source and prompts for installation.
- Network traffic originates from an explicit APK download or the optional Streaming and Web modules. Home and its wallpaper rotation work offline.
- Cleartext network traffic is disabled in the manifest, and no code in this build speaks plain HTTP.
- App data is excluded from Android cloud backup and device-to-device transfer.

## Building your own copy

A build signed with your own key cannot update the official release (and vice versa). Never share your
keystore or its password; never commit them. See [docs/BUILDING.md](docs/BUILDING.md).
