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

- Permissions: `INTERNET` only. No storage, location, microphone, camera, contacts or accessibility access.
- It launches apps and switches inputs through public Android APIs. It does not install, uninstall, disable
  or modify other apps or system settings.
- It makes **no network connections** except the optional JARVIS link, and only while the Assistant screen is
  open, to the IPv4 address you entered when pairing.
- Cleartext network traffic is disabled in the manifest; the JARVIS client only speaks TLS 1.3.
- App data is excluded from Android cloud backup and device-to-device transfer.

## JARVIS link security model

Details: [docs/JARVIS.md](docs/JARVIS.md).

- **PC identity**: self-signed certificate on the PC, pinned on the TV by SHA-256. The fingerprint is not
  trusted because it arrived over the network: the one-time pairing code shown on the PC contains a check
  value that the TV verifies against the certificate during the TLS handshake, before sending anything.
- **TV identity**: an ECDSA P-256 key generated in Android Keystore (not exportable) signs every connection.
- **Authorisation**: the gateway owner approves the TV on the PC. The TV gets a device token (stored
  AES-GCM-encrypted with a Keystore key) that only works on the TV link, whose method set is limited to
  status, the TV's own conversation, and unpairing. TV conversations run without tools on the PC.
- **Revocation**: from the PC (`openpulse devices remove`) or the TV (*Forget this PC*); the TV then deletes
  its key, token and pin.
- **Not covered**: anyone who can approve devices on your PC, or who controls your PC, controls what the TV is
  told. Keep OpenPulse's owner token private and restrict the TV link to your TV's address.

## Building your own copy

A build signed with your own key cannot update the official release (and vice versa). Never share your
keystore or its password; never commit them. See [docs/BUILDING.md](docs/BUILDING.md).
