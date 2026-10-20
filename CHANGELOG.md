# Changelog

## v2.1.0-beta.1 — first public beta (versionCode 21, "2.1")

First public release. Builds on two private versions (Calm Home 1.0 and ZeroPulse TV 2.0) used on one TV.

### Launcher
- Cinematic Home: clock, greeting, dock of favourite apps over rotating wallpapers with photo credits.
- All Apps, Inputs (HDMI and tuner with connection state), Wallpapers, Settings, About panels.
- Dock editing: add from All Apps, move, remove (never uninstalls).
- Wallpapers: 9 Wikimedia Commons photographs and 3 original artworks, bundled; interval 30 s – 15 min or off.
- Settings: greeting, clock, dimming, scale, focus glow, accent, reduced motion, app names, assistant.
- Remote navigation: dock stops at both ends; focus stays where you left it.

### Assistant and JARVIS (new in 2.1)
- Local typed TV commands work without a PC.
- Optional JARVIS link to the user's own OpenPulse gateway: TLS 1.3, certificate pinned via a one-time code,
  P-256 device key in Android Keystore, token encrypted with a Keystore key, approval on the PC.
- Conversation view with thinking/answering states, multi-line replies, remote scrolling, history on reconnect.
- Connects only while the Assistant is open; bounded timeouts and retries; works offline.
- Disconnect, forget, re-verify after the PC's certificate changes; revocation from the PC.
- JARVIS may suggest a TV action, shown as a button that runs only on OK.

### Fixed during on-TV testing
- PC address must be an IPv4 address (a mistyped code could otherwise have been looked up in DNS).
- JARVIS panel button clipped; Up from the command box now reaches the conversation.
- Loading the conversation no longer moves the remote's focus.

### Privacy and security
- One permission (`INTERNET`, used only by JARVIS). No analytics, ads or third-party libraries.
- App data excluded from cloud backup and device-to-device transfer.
