# ZeroPulse TV — Vercel website source

This directory is the portable, dependency-free version of the ZeroPulse TV marketing site. It is separate from the existing Lovable project, which has NOT been overwritten. The design keeps ZeroPulse's navy/blue identity and uses real repository screenshots, not the reference site's images or statistics.

## Build from the existing repository

These files belong inside `steventa2024-lgtm/ZeroPulseTV`, with its existing `docs/`, `branding/`, and `THIRD_PARTY_NOTICES.md` files. This ZIP is an overlay, not a standalone copy of the Android repository.

With Node.js 22+ at the repository root:

```sh
node --check website/app.js
node --test website/test.mjs
node website/build.mjs
```

Output: `website/dist/`. Serve that directory over HTTP for a local preview. Do not open `index.html` directly with a file URL; prompt loading requires HTTP.

The build verifies the Git blob hashes of seven original screenshots from the published beta and copies them unchanged into the site. It copies the existing logo and `docs/claude-install-prompt.txt`, checks the release URL/hash, replaces the HTML template values, and writes the static output. Missing or changed source assets cause the build to fail. The APK is never built, re-signed, or copied into the website: download links lead to the existing GitHub release.

## Vercel configuration

Import the repository into the owner's authorized Vercel account/team after reviewing and committing the remaining files.

- Framework preset: **Other**.
- Root Directory: repository root (`.`), NOT `website/`.
- Node.js: 22 or newer supported by the deployment.
- Build Command: `node --test website/test.mjs && node website/build.mjs`.
- Output Directory: `website/dist`.
- Install Command: empty/skipped; no npm dependencies.
- Environment variables: none required.

The included root `vercel.json` declares these settings and security headers. Production: https://zeropulse-tv.vercel.app/ (Vercel project `zeropulse-tv`, Git-connected; `main` deploys to production, other branches to authentication-protected previews). Do not change DNS, delete the Lovable project, or modify ZeroPulseDev.com.

## UX behavior

- Primary CTA: guided setup with local Claude Code.
- Secondary CTA: exact version-specific APK download.
- Three steps: On your TV / On your computer / Follow along.
- Full prompt copy, readable preview and download; manual-copy dialog if clipboard is denied.
- Windows/macOS/Linux contextual hints.
- Opaque sticky header, mobile menu, reduced-motion support and screenshot lightbox.
- Public verification fingerprints are available in a closed-by-default disclosure.
- JARVIS public pairing remains explicitly unavailable; local commands are independent.

## Safety and attribution

The website never executes ADB, uses a microphone, discovers devices or connects to a visitor's LAN. The user must run Claude Code locally and approve installation. Default Home changes require separate device-specific approval. No universal backup, debloat, fresh-device compatibility, or lossless rollback claim is made.

The first seven existing repository screenshots are reused with their original pixels and visible wallpaper credits. Refer to THIRD_PARTY_NOTICES.md. No active pairing screens are promoted.

## Validation scope

18 Node source-contract tests pass locally, in GitHub Actions and in the Vercel build, which also verifies the 7 screenshot blobs and the byte-identical prompt. The live production site (2026-10-20) serves files identical to a local build; the APK downloaded through its link matches SHA-256 `ac6ac5a4…52f0401` and 5,744,160 bytes; repository files (`.git`, sources, `vercel.json`) return 404; security headers are present. Copy, manual-copy fallback, disclosures, lightbox focus return and the mobile menu were checked in Chromium at 375 px and desktop width. A real paste into a Claude Code session and a real TV installation from the website were not tested.
