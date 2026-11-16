# Official Downloader by AFTVnews (`com.esaba.downloader`)

The launcher opens, helps install, and feeds codes to the real **Downloader by AFTVnews** app. It does not replace the Download Center
or the Liquid Glass screens; it adds to them. (3.0.0-beta.7 removed the Assistant, and with it the spoken and typed "open downloader"
and "download code" commands; the Download Center's Downloader button and screen are unchanged.)

## What it does

| You do | What happens |
|---|---|
| **Download Center > Downloader** button | The Downloader screen |
| **Downloader screen** | Open or get Downloader; your saved codes (select one to send it, Remove to forget it); Save code; APK link install |
| Download Center, type a code that no ZeroPulse source uses | The existing menu now also has **Send code to Downloader** |

## How it is built

- `aftv/OfficialDownloaderLauncher.java`: `openOrInstallDownloader(context)`; the same helper for VLC (`org.videolan.vlc`); `sendCode(context, code)`.
  Installed is decided by `getLeanbackLaunchIntentForPackage`, then `getLaunchIntentForPackage`. Every start is a new task and guarded
  against `ActivityNotFoundException` / `SecurityException`. (The brief asked for Kotlin; the project is Java-only, so this is Java.)
- `aftv/DownloaderStore.java`: the codes and the optional APK link, in the private `zp_downloader` preferences. Nothing is preset.
- `core/DownloaderCodes.java`: the code rules (1 to 16 digits).
- `screens/DownloaderPanel.java`: the screen, built from the Download Center's own parts.
- No manifest change and no new permission: the launcher already sees launchable apps, and `startActivity` needs no permission.

## The APK fallback, and why it has no built-in link

AFTVnews publishes Downloader on **Google Play** and the **Amazon Appstore**, and offers **no direct APK link** (checked on aftvnews.com).
The only direct copies are on third-party mirrors, which are not official. So the launcher ships no link, in line with the Download
Center's own rule ("no preconfigured APK links"). A TV with no Play Store gets the **Install from an APK link** card: paste an HTTPS
link and, ideally, its SHA-256. It then goes through the same reviewed install as every other APK here: HTTPS only, size and archive
checks, the SHA-256 compared if given, the package and signer shown, and Android's own confirmation before anything installs.
That is one review and one Android confirmation, not a silent install.

## Verified on the TCL (2026-11-09)

Tested live on the TCL with Downloader 2.0.4 installed from the Play Store (report: `REPORT-DOWNLOADER-TV-TEST.md` in the
`20261109-downloader-tv` evidence folder):

- Downloader's manifest has one activity, `.ui.main.MainActivity`, that accepts `VIEW` for `http` and `https` with no host filter,
  so `http://aftv.news/<code>` matches it. The launcher already sends exactly that, pinned to `com.esaba.downloader`.
- The code link is accepted: Downloader loads the code itself (it rewrites it to `go.aftvnews.com/<code>`), warm or cold start. The
  clipboard fallback was not needed. Tested with made-up codes, which Downloader answers with "The Short URL (or Code) that you entered
  does not exist".
- Open Downloader works from the Download Center (the Assistant routes tested then were removed in 3.0.0-beta.7).
- Get Downloader opens the Play Store on the app page.

## Not verified

- A code that leads to a real file: the download and install steps afterwards are Downloader's own and were not exercised.
- Codes spoken through the remote's microphone (typed forms, including spoken digit words, were tested).
