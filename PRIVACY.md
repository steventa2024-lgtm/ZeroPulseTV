# Privacy

ZeroPulse TV collects nothing. There is no ZeroPulse account, no analytics, no crash reporting and no advertising.
The Assistant (JARVIS), its AI accounts and its voice features were removed from ZeroPulse TV in 3.0.0-beta.7; the update deletes what they stored on the TV (see [CHANGELOG](CHANGELOG.md)).

## What stays on the TV

| Data | Where | Leaves the TV? |
|---|---|---|
| Dock order and settings | App-private storage | No |
| Saved developer links, local shortcut codes and optional checksums | App-private catalog | The requested URL is sent to its host on download |
| Transfer history, exact requested/final URL, APK metadata, hash and files | Private database and files excluded from backup | Only the selected file is passed to the Android installer |

App data is excluded from Google cloud backup and device-to-device transfer. Uninstalling the app deletes it.

## What the app reads

- The list of installed apps (names, icons, banners) to show the dock and All Apps.
- The TV's inputs and their connection state from Android's TV input service.
None of this is sent anywhere.

## Network

Home, wallpapers and shortcut lookup work offline. When you select Start download, Android connects to the HTTPS source and any permitted HTTPS redirects. Those hosts receive the request and see the TV’s network address. Signed URLs may contain access parameters. Manage Downloads → Remove file and history removes the saved record and staged APK. Remove the saved catalog source separately if needed. A local file import grants read access only to the selected document.

## Wallpaper credits

Photo credits are shown on Home and in About. Images are bundled in the app; nothing is downloaded.

## Contact

Questions: open an issue on this repository. Security problems: see [SECURITY.md](SECURITY.md).

## Data from the removed Assistant

Updating to 3.0.0-beta.7 or later deletes, once, exactly what the removed Assistant stored: the JARVIS pairing (`zp_jarvis`), the AI
Accounts sign-ins (`tv_ai_provider_prefs`, including any ChatGPT/Codex token), the remembered model (`zp_ai_models`), the local chat
history (`zp_chat_v1`), the speak-replies switch (`assistant_voice`), their three Android Keystore keys (`zp_jarvis_device`,
`zp_jarvis_token`, `zp_ai_master`) and the two Assistant switches in the launcher's settings. Nothing else is touched: favorites,
settings, wallpapers, downloads and the optional modules' data stay as they were. Sign-ins made on your PC are not affected; to
revoke the TV on the provider's side, use that provider's own account settings.
