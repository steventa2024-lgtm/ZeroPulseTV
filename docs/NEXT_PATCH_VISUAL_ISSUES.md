# Next targeted patch: visual issues recorded from the 3005 test on the TCL

Recorded after the controlled on-device test of 3005 (3.0.0-beta.5, SHA-256 `BA554AEB…CF04`) on the owner's TCL G10, 2026-11-06.
Nothing here was fixed in the 3005 bytes. The measurements come from **actual TCL captures** (the launcher surface is 1920 x 1080 on
a 3840 x 2160 panel); the evidence is in `device-tests/20261106-tcl-3005/` (outside the repository). Fixture and TCL captures are kept apart.

The owner's second TV reference is the visual target. **It is not available locally** (`design/reference/approved-target.png` does not
exist, and no file by that name was found anywhere on this PC), so every comparison with it is **NOT RUN**. When it is supplied, put it
at `design/reference/approved-target.png` and open it before claiming a comparison.

## The six issues the owner listed

### A. Wallpapers pills and Settings controls look thick and heavily outlined next to Home — CONFIRMED
- **Measured on the TV** (luminance 0-255 down a column across the top edge): a Wallpapers collection pill has a **3 px band at 214-229**
  (about 90 % white); a Wallpapers control card the same (217-229, 3 px); a Home quick-action pill has **1 px at 84**; a Home tile frame
  has 2 px peaking at 117. The panels' rims are about three times wider and more than twice as bright as Home's.
- **Root cause (code):** in Liquid Glass the panels do not use the approved glass. `Theme.glass()` returns `LiquidGlass.all(LiquidSpec.panel(..))`,
  the older clear-glass painter, whose specular rim is `LiquidTokens.RIM_TOP = 0xE6FFFFFF` at 1.5 dp (3 px at this density). On top of that,
  `WallpapersPanel.paintTabs` outlines a focused tab in Electric Blue at 2 dp, `Rows.base` outlines a focused row at 1.5 dp, and `Theme.outlined`
  draws the active tab at 1 dp. The Settings sidebar highlight and every panel card go through the same painter.
- **Plan (not done):** one treatment from the approved system. Pills and cards become `GraphiteGlassSurface` (CAPSULE / CARD) inside a
  `ZeroPulseGlassControl`, exactly as Home's actions and tiles, with the same finish and the same focus language (the glass's own focus
  material, no stroke). Settings and Wallpapers rows become flat text rows whose focus is a single glass capsule behind the row, like
  the header's pill. The panel container becomes one large `GraphiteGlassSurface` CARD with the finish at low strength. Delete the
  painter's rim for panels (keep it for the graphite fall-back). Verify with the same column profile: a rim should be about 1-2 px and
  at most about 120 on that scale.

### B. The clock reads as hollow outlines — CONFIRMED on the TV, not yet adjusted
- 3x crop saved (`analysis/clock-3x.png`). Each numeral is a thin bright outline over a dark glass interior; the interior is almost the
  same luminance as the sky around it (85.8 against 81.3), so the digit reads as an outline only.
- **Do not change the interior shading before the owner decides.** Options to evaluate on the TV, in order: leave the glass and raise only
  the interior's own lighting (the renderer's emphasised frame already brightens the body; the clock uses it), then a faint interior fill
  toward the rim colour, then a slightly heavier lit edge. Each is a change to `GlassClockView`, not to the renderer or its approved material.

### C. The ZeroPulse waveform mark vs the owner's original approved logo — PARTIAL, reference NOT RUN
- The original reference the owner means was **not supplied**. The repository holds `branding/logo/zeropulse-pulse-mark.svg` (blue `#2F80FF`
  line with a cyan `#35D6FF` glow, aspect 2.30 : 1) and `PulseLogo.PULSE` is documented to share its geometry.
- On the TV the 3005 header mark measures **56 x 26 px, aspect 2.15 : 1**, so the polyline is the same shape within about 7 %. Its
  **treatment differs**: white, no glow, no badge. The installed 3002 header showed the mark inside a silver ring badge (`analysis/logo-comparison.png`).
- **No other heartbeat symbol has been substituted.** Whether the owner's original is the blue master, the 3002 badge, or something else is theirs
  to say; until then do not change the mark.

### D. Are the Home action pills too dark on the TV? — MEASURED, owner to judge
- Pill interiors measure **17-35** (of 255) against the wallpaper just above them: Cape at Dusk 114 %, Amber and Ice 124 %, Deep Blue Wave 90 %,
  Violet Ribbon 76 %, Teal Currents 70 % of the wallpaper's brightness. A tile's glass frame measures 29.5.
- On the bright wallpapers the pills are as bright as their surroundings; on the darker ones they are 25-30 % darker. The approved material
  (9 % graphite smoke) was **not** altered on the strength of a software render, and is not altered here either.

### E. Four wallpapers are below 1080p — PRESERVED as supplied
- No substitutes, no generative upscaling. Measured on the TV, fine-detail energy (Laplacian variance, 400 x 250 patch): Cape at Dusk 153,
  Violet Ribbon 179, Deep Blue Wave 186 (1024 x 572 sources, enlarged about 1.9x) against Teal Currents 256 (2752 x 1536). Zoom 130 % on a small
  source is about 2.5x and is visibly soft (`analysis/wallpaper-detail-cape-2x.png`).
- A higher-resolution original of any of the four would drop in by file name; nothing in the code needs to change.

### F. Artwork legibility beneath the finish overlay — checked, no change needed
- Flat patches inside each real banner, 3002 (raw, edge to edge) against 3005 (inset, under the finish), largest channel difference: Netflix 0,
  YouTube Music 0, YouTube 1, Prime Video 11, Hulu 11, YouTube TV 11, Disney+ 11 (a gradient; the patches sit at slightly different heights).
  Whites are unchanged and the brand colours hold; **blacks are lifted by about 11 of 255** (YouTube TV's black reads (11,11,11)). Logos stay sharp
  (rendered 1:1). If the haze on black banners bothers the owner, lower the finish's illumination alpha (`GlassFinishView`, 0.06 + 0.05 x level).

## Defects found on the TV that were not on the owner's list

| # | Finding | Evidence | Likely fix |
| --- | --- | --- | --- |
| G | With eight favorites the row scrolls and the last tile lands **flush with the screen edge** (right edge x = 1918, past the 96 px safe margin; its label too) | `after/screens/13-home-last-tile-streaming-module.png` | scroll to the end with the safe-margin padding (`dockScroll` right padding is ignored by focus scrolling), or scroll by whole tiles |
| H | **Focus leaks out of rows:** Right on the last action (Inputs) jumps to the half-visible eighth tile; Right on Settings in the header jumps to YouTube Music; Left on Home in the header jumps to Netflix | `after/nav-walk-1.txt`, `nav-walk-2.txt` | set explicit `nextFocusRight/Left` on the row ends (the tile row already does) |
| I | On Wallpapers, the choice cards sit side by side and Left/Right change a value, so **Dim overlay, Information, Focus across and Focus down are reachable only by going Down from a preview above them** (3002 had the same for Dim) | `after/wallpaper-nav.txt` | stack the cards, or make Down/Up the only way between rows and give each card its own row |
| J | The "Framing for ..." caption sits against the top of the Zoom card | `nav2-opened-Wallpapers.png` | 8-10 dp of margin |
| K | **First-scene glass cost on the TCL:** 21 renders, 49 ms average, 438 ms longest in the first 8 s; later 39-40 ms average (PC: 4 ms) | logcat `ZeroPulseGlass`; About > Glass timing | smaller cache scale on this SoC, or render fewer surfaces at start |
| L | **One missed frame per focus move:** 20 keys give 20 janky frames (13.6-14.3 %), p50 23 ms, p95 24-42 ms; 0 slow UI-thread frames | `after/perf/p1-gfx.txt`, `p2`, `p3` | the draw of the glass crossfade; profile before changing |
| M | The wallpaper crossfade runs at about 29-31 ms per frame (26 % janky) | `after/perf/p5-idle-rotation-gfx.txt` | bake the scrim into the scene, avoid re-creating the gradient (lint `DrawAllocation` in `AdaptiveScrimView.onDraw`) |
| N | Process memory is 92-130 MB PSS (3002: 43.5 MB) on a TV with 1.8 GB; flat across five panel cycles (about 100 MB) | `after/perf/*mem*` | acceptable, but watch it |
| O | Moving focus is not render-free on the TV: about 9 extra glass renders over about 56 moves (rotation paused), some from scrolling | About > Glass timing, before and after | expected for moved tiles; the Robolectric proof is for a still row |
| P | Focus emphasis on a tile is subtle at normal viewing distance (a lighter frame, a 5 % swell, the label) | `after/screens/11-home-focused-tile-disney.png` | owner to judge; the finish's focus alpha can rise a little |

## Lint, 3004 against 3005 (reconstructed 3004 reproduces the recorded 71)
71 -> 77 findings, all warnings, none removed. Six new: `DrawAllocation` x1 (`AdaptiveScrimView`, see M), `SetTextI18n` x3 (`WallpapersPanel`
literals and a concatenation; the app is English-only), `ViewConstructor` x2 (`GlassFinishView`, `TileArtView`, built in code, never inflated).
None touches app discovery, saved data, resources, accessibility (both views are inert and hidden from accessibility) or stability.
