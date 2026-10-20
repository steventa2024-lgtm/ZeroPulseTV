# Compatibility — v2.1.0-beta.1

Only what is listed as **Tested** has actually been run. Everything else is expected from the code and
Android's documented APIs, but has **not been verified**.

## Tested

| Device | OS | Install type | Result |
|---|---|---|---|
| TCL G10 (`G10_4K_US_NF`), Google TV | Android 12 (API 31) | **Update** over an earlier private build (same package and key), set as Home with the advanced Google TV steps | Full on-TV acceptance passed: Home, dock edit/reorder, apps, inputs (HDMI switch), wallpapers, settings, local commands, JARVIS pairing/chat/offline/revocation, restart, reboot |

**Fresh installation** (a TV that never had ZeroPulse TV) has **not yet been verified on any device** for this
beta. See the release notes for its status before installing.

## Requirements

| | |
|---|---|
| Android | 8.0 (API 26) or newer; built for Android 12 (target API 31) |
| Form factor | Android TV / Google TV with a D-pad remote. Touch is not required and not designed for |
| Screen | Laid out for 1920×1080 (scales on 4K) |
| CPU | Any (the app contains no native code) |
| Permissions | `INTERNET` only |

## Known limitations

- **Home button on Google TV.** Google TV devices do not let you choose another home app in Settings.
  ZeroPulse TV runs as a normal app there; replacing the home screen needs the advanced ADB steps in
  [INSTALL.md](INSTALL.md), tested only on a TCL G10.
- **Inputs.** HDMI inputs come from Android's TV input framework. The live-TV tuner entry and its launch
  intent are written for TCL's TV app (`com.tcl.tv`, `com.tcl.tvinput`); on other brands the tuner may be
  missing or open differently. Device names over HDMI-CEC appear only while the connected device reports them.
- **Default dock** lists seven common streaming apps (Netflix, Prime Video, Disney+, Hulu, YouTube, YouTube TV,
  YouTube Music) and shows only those installed. Region-specific app variants are not in the default list;
  add them from All Apps.
- **Wallpapers** are bundled at 1920×1080; there is no way to add your own in this version.
- **JARVIS**
  - Needs an OpenPulse gateway with the TV link on a PC on the same network. The TV link is **not yet in a
    public OpenPulse release**, so JARVIS cannot be paired with a public gateway at the time of this beta.
  - IPv4 addresses on the local network only (no host names, no internet relay).
  - Reply speed depends entirely on the model your PC runs (the first reply after a model load can take
    tens of seconds).
  - The pairing form shows an example address ("e.g. 192.168.4.33"); use your PC's own address.
  - Voice input is not implemented.
- **Branding text.** Home shows "Powered by ZeroPulse AI" whether or not JARVIS is paired; without a paired
  gateway, no AI features are active.
- **Package name.** The app's package ID is `tv.calmhome.launcher`, inherited from the project's first
  version ("Calm Home"). It is kept so existing installations keep updating; see SECURITY.md.
- **About screen** shows "Source 95a61a9" for this build: the commit label of the build that was tested; the
  matching public source is tag `v2.1.0-beta.1` (see the release notes).
- **No automatic updates** and no update check.
- **Accessibility**: no TalkBack-specific work has been done beyond content descriptions on focusable items.

## Not supported

- Phones and tablets (no touch layout).
- Fire TV OS and other non-Android-TV systems (not tested; Fire OS restricts third-party home screens).
- Disabling or removing manufacturer apps: ZeroPulse TV never does this.
