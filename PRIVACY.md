# Privacy

ZeroPulse TV collects nothing. There are no accounts, no analytics, no crash reporting, no advertising and no
third-party libraries.

## What stays on the TV

| Data | Where | Leaves the TV? |
|---|---|---|
| Dock order and settings | App-private storage | No |
| JARVIS pairing: PC address, PC certificate fingerprint, device token (encrypted with an Android Keystore key) | App-private storage `zp_jarvis.xml` | No |
| JARVIS device key | Android Keystore (not exportable) | No (only signatures are sent, to your PC) |

App data is excluded from Google cloud backup and device-to-device transfer. Uninstalling the app deletes it.

## What the app reads

- The list of installed apps (names, icons, banners) to show the dock and All Apps.
- The TV's inputs and their connection state from Android's TV input service.
- The device name from Android settings, used as the TV's name when pairing with JARVIS.

None of this is sent anywhere, except the device name, which is sent to your own PC during JARVIS pairing.

## Network

Without JARVIS, the app makes **no network connections at all**.

With JARVIS paired, and only while the Assistant screen is open, the TV connects to the PC address you entered
(TLS 1.3, certificate pinned). It sends the messages you type and receives the replies. Your conversations
are stored by **your** OpenPulse gateway on **your** PC under its own settings; the TV shows them but does
not store them. There is no ZeroPulse server.

The AI model is whatever your OpenPulse gateway is configured to use. If that gateway uses a cloud model
provider, your messages go to that provider under the gateway's configuration — ZeroPulse TV has no control
over or knowledge of that.

## Wallpaper credits

Photo credits are shown on Home and in About. Images are bundled in the app; nothing is downloaded.

## Contact

Questions: open an issue on this repository. Security problems: see [SECURITY.md](SECURITY.md).
