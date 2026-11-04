# Privacy

ZeroPulse TV collects nothing. There are no accounts, no analytics, no crash reporting, no advertising and no
third-party libraries.

## What stays on the TV

| Data | Where | Leaves the TV? |
|---|---|---|
| Dock order and settings | App-private storage | No |
| Up to 30 user and assistant text turns, plus the speak-replies preference | App-private storage excluded from backup | Submitted questions go to the paired PC. Replaying local history makes no request. |
| Saved developer links, local shortcut codes and optional checksums | App-private catalog | The requested URL is sent to its host on download |
| Transfer history, exact requested/final URL, APK metadata, hash and files | Private database and files excluded from backup | Only the selected file is passed to the Android installer |
| JARVIS pairing: PC address, PC certificate fingerprint, device token (encrypted with an Android Keystore key) | App-private storage `zp_jarvis.xml` | No |
| JARVIS device key | Android Keystore (not exportable) | No (only signatures are sent, to your PC) |

App data is excluded from Google cloud backup and device-to-device transfer. Uninstalling the app deletes it.

## What the app reads

- The list of installed apps (names, icons, banners) to show the dock and All Apps.
- The TV's inputs and their connection state from Android's TV input service.
- The device name from Android settings, used as the TV's name when pairing with JARVIS.

None of this is sent anywhere, except the device name, which is sent to your own PC during JARVIS pairing.

## Network

Home, wallpapers and shortcut lookup work offline. When you select Start download, Android connects to the HTTPS source and any permitted HTTPS redirects. Those hosts receive the request and see the TV’s network address. Signed URLs may contain access parameters. Manage Downloads → Remove file and history removes the saved record and staged APK. Remove the saved catalog source separately if needed. A local file import grants read access only to the selected document.

With JARVIS paired, and only while the Assistant screen is open, the TV connects to the PC address you entered
(TLS 1.3, certificate pinned). It sends the messages you type and receives the replies. Your conversations
are stored by the OpenPulse gateway on the paired PC under its own settings. The TV keeps a bounded text copy for offline reading. Clear TV history deletes that copy and prevents re-import of older PC messages. New PC conversation resets the paired conversation after gateway confirmation. Forgetting the PC or receiving revocation clears the local transcript. Only message roles and text enter that store. Pairing forms, device tokens and provider keys are excluded.

Push-to-talk launches Android’s installed recognizer only when the viewer selects Talk. That service may process audio online. The returned text is editable and sent only when the viewer selects Send. ZeroPulse does not request microphone permission, record audio, retain recordings or intercept the system microphone key. Optional spoken replies pass reply text to Android’s selected TTS engine and use media volume. Select Stop speaking or turn off Speak replies to silence it.

The AI model is whatever your OpenPulse gateway is configured to use. If that gateway uses a cloud model
provider, your messages go to that provider under the gateway's configuration — ZeroPulse TV has no control
over or knowledge of that.

## Wallpaper credits

Photo credits are shown on Home and in About. Images are bundled in the app; nothing is downloaded.

## Contact

Questions: open an issue on this repository. Security problems: see [SECURITY.md](SECURITY.md).
