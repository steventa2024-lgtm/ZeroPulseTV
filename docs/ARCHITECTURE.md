# Architecture

One Android app (`tv.calmhome.launcher`), Java 8 language level, Android framework only.

| Package (`launcher/src/tv/calmhome/launcher/…`) | Role |
|---|---|
| `MainActivity` | Home: wallpaper stage, greeting, clock, dock, navigation chips; hosts the panels |
| `screens/` | Full-screen panels: All Apps, Inputs, Wallpapers, Settings, About, menus |
| `assistant/` | Assistant screen, holographic reactor view, Keystore-backed JARVIS credentials |
| `jarvis/` | Pure-Java JARVIS client: JSON, pairing code, pinned TLS trust, WebSocket client, gateway protocol, session/retry policy, TV-action suggestions (unit-testable on a PC) |
| `core/` | Pure logic: dock list, greeting, wallpaper catalogue, typed command parser |
| `data/` | Preferences, installed apps, TV inputs, wallpaper decoding with bitmap reuse |
| `ui/` | Theme tokens, logo, glyph icons, shared widgets |

Design rules:

- **Home never waits for the network.** Only the Assistant screen creates a JARVIS session, and it closes it in
  `onStop`.
- **Truthful status.** Every connection state shown comes from the session state machine.
- **No privileged access.** Apps are launched with their launch intents; inputs are switched with the public
  TV input framework intents.
- **Memory.** Two reusable 1920×1080 wallpaper bitmaps; no per-rotation allocation.
- **Saved data.** One preferences file for launcher settings (`calmhome`, kept from version 1.0 so updates keep
  the dock), one for the JARVIS pairing (`zp_jarvis`), both excluded from backup.
