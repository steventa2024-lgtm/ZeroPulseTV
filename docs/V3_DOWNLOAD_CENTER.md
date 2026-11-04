# Using the private v3 Download Center

Open Downloads from the top navigation or the Home shortcut. Select Live Sports, Movies & TV or Anime to see the links saved on this TV. Each category starts empty. **Add a trusted source** saves a name, the exact developer HTTPS URL, an optional local numeric shortcut and an optional separately obtained publisher SHA-256. Select a saved source to see its hostname, start a download or remove it. **Choose an APK file** opens Android’s document picker, including available USB storage. The TV needs a file picker for local imports.

**Go** accepts a direct HTTPS URL or a local numeric shortcut, retaining leading zeros. An unmapped code offers to copy it and open the installed AFTVnews Downloader app. Enter the code there. ZeroPulse makes no request to a third-party code resolver. [AFTVnews Downloader](https://www.aftvnews.com/downloader/)

## Transfer and installation

The user reviews the source before an HTTPS transfer. Android JobScheduler runs the transfer with network constraints and persists it across reboots. The worker validates every redirect before contacting its destination, uses private APK staging, and checks space and a 512 MiB per-download limit. Restarting an interrupted attempt may restart its byte counter. Status and size come from the job and file. The history is limited to 100 entries. Use Manage Downloads to cancel, retry or remove an entry.

After transfer, Android parses the standalone signed APK. The detail view shows the source and final destination, computed digest, optional publisher checksum, package, version, SDK and declared permissions. A matched checksum is meaningful to the extent that you trust its independent source. Split APK bundles require a different installer. An SDK match alone does not certify device compatibility.

Select **Install with Android approval**. If Android requests source permission, return from Settings and select Install again. Approve or cancel on Android’s confirmation screen. Resume a pending prompt through **Continue Android approval**. If delivery was interrupted, use **Reset installer session** and retry. An installed, launchable package offers Open app and Add to dock. Removing its history deletes the staged file and does not uninstall the app. To withdraw install permission, use Android’s Special app access → Install unknown apps.

## Recovery on the owner TV

Back closes the current panel. Home returns to the launcher. Use Android Settings → Default apps → Home app to choose a different available Home app if needed. An in-place recovery APK must have the installed package name, compatible signer and a version Android accepts. If the current code blocks an older release, build a known-good recovery candidate with the same signer and a higher versionCode. Preserve data, the OEM launcher and the current HOME configuration during testing. The public 2.1 release is not an in-place downgrade path for the private 2.2 or 3.0 beta.

## Artwork

Built-in image generation produced the three original, offline category images using the prompts below. The app scales each WebP into the native card and draws the labels and focus ring on top. Wallpaper photography and its existing credits remain separate.

- `launcher/res/drawable-nodpi/category_sports.webp` — Photorealistic cinematic widescreen 16:9 editorial image, low sideline view of vibrant natural green grass inside a floodlit football stadium at blue hour. One realistic black and white soccer ball in the right half. Darker lower left for app text. No logos, words, borders or UI.
- `launcher/res/drawable-nodpi/category_movies.webp` — Photorealistic cinematic 16:9 still of a vintage twin-reel film projector on the right, shining a warm amber beam into an atmospheric dark red velvet movie theatre. Rich, restrained color. Lower left dark for overlay. No words, logos, borders or UI.
- `launcher/res/drawable-nodpi/category_anime.webp` — Original cinematic hand-painted 16:9 anime illustration, a cloaked traveler seen from behind on the right, looking across a moonlit lakeside Japanese village with dark teal roofs, indigo night sky, soft cherry blossoms and a few warm lanterns. Lower left darker for overlay. No franchise characters, words, logos, borders or UI.
