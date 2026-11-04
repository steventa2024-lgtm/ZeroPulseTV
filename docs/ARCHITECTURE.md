# Architecture

One Android app (`tv.calmhome.launcher`), Java 8 language level, Android framework only.

| Package (`launcher/src/tv/calmhome/launcher/…`) | Role |
|---|---|
| `MainActivity` | Home: wallpaper stage, greeting, clock, dock, navigation chips; hosts the panels |
| `screens/` | Full-screen panels: All Apps, Inputs, Wallpapers, Settings, About, menus |
| `assistant/` | Native Assistant, bounded local transcript, Android speech activity / TTS and Keystore-backed JARVIS credentials |
| `jarvis/` | Pure-Java JARVIS client: JSON, pairing code, pinned TLS trust, WebSocket client, gateway protocol, session/retry policy, TV-action suggestions (unit-testable on a PC) |
| `core/` | Pure logic: dock list, greeting, wallpaper catalogue, typed command parser |
| `data/` | Preferences, installed apps, TV inputs, wallpaper decoding with bitmap reuse |
| `downloads/` | Private SQLite history, persistent Android jobs, bounded HTTPS transfers, APK inspection and PackageInstaller sessions |
| `ui/` | Shared six-section navigation, static graphite backdrop, native glass drawables, theme tokens, logo, glyphs and widgets |

Design rules:

- **Home never waits for the network.** Only the Assistant screen creates a JARVIS session, and it closes it in
  `onStop`.
- **Truthful status.** Every connection state shown comes from the session state machine.
- **No privileged access.** Apps are launched with their launch intents; inputs are switched with the public
  TV input framework intents.
- **Memory.** The default graphite surface shares one cached 960×540 image. The optional Home photo stage reuses its wallpaper bitmaps.
- **Saved data.** The existing `calmhome` and `zp_jarvis` stores retain settings, dock and pairing. Successful launches update a local list of up to six recent apps. The Assistant retains up to 30 text turns and a speech preference. The optional downloader has a separate source catalog, private database and APK directory.
- **Rendering.** Neutral glass surfaces draw gradients, an edge highlight and restrained specular reflection over the static graphite backdrop. No focus or clock update renders the backdrop again. When a photo is selected for Home or previewed, the wallpaper stage retains its attribution and uses a cached one-eighth-resolution filtered layer where needed.
