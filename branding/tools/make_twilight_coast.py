"""
Generates launcher/assets/wallpapers/zp04.jpg, "Twilight Coast": original ZeroPulse artwork (no photograph, no
third-party image), a dark, misty coastal twilight: deep indigo sea, fog-wrapped pine ridges, a last warm glow at the
horizon. It is the backdrop the Liquid Glass Home is designed against.

Deterministic (fixed seed). Needs numpy and Pillow:

    python branding/tools/make_twilight_coast.py launcher/assets/wallpapers/zp04.jpg
"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
rng = np.random.default_rng(20261105)


def value_noise(w, h, cell_w, cell_h):
    """Smooth 0..1 noise: a random lattice, bicubic-resized to (w, h)."""
    gw, gh = int(w / cell_w) + 3, int(h / cell_h) + 3
    g = rng.random((gh, gw)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((int(gw * cell_w), int(gh * cell_h)), Image.BICUBIC)
    a = np.asarray(im, dtype=np.float32) / 255.0
    return a[:h, :w]


def fbm(w, h, cell_w, cell_h, octaves=5, gain=0.5):
    total, amp, norm = np.zeros((h, w), np.float32), 1.0, 0.0
    for o in range(octaves):
        total += amp * value_noise(w, h, max(2, cell_w / 2 ** o), max(2, cell_h / 2 ** o))
        norm += amp
        amp *= gain
    return total / norm


def hexc(s):
    s = s.lstrip('#')
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


def vgrad(stops, y0, y1):
    """Vertical colour ramp over rows y0..y1 (stops: [(t, '#rrggbb')])."""
    t = np.clip((np.arange(H, dtype=np.float32) - y0) / max(1.0, y1 - y0), 0, 1)
    out = np.zeros((H, 3), np.float32)
    ts = [s[0] for s in stops]
    cols = [hexc(s[1]) for s in stops]
    for c in range(3):
        out[:, c] = np.interp(t, ts, [col[c] for col in cols])
    return out[:, None, :].repeat(W, axis=1)


def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


xs = np.arange(W, dtype=np.float32)[None, :]
ys = np.arange(H, dtype=np.float32)[:, None]
horizon = 0.335 * H

# ---------------------------------------------------------------- sky
img = vgrad([(0, '#0c2038'), (0.45, '#1c4268'), (0.8, '#46709a'), (1, '#86a4bf')], 0, horizon)
# warm last light low on the right
gx = (xs - 0.86 * W) / (0.34 * W)
gy = (ys - horizon * 0.93) / (0.085 * H)
glow = np.exp(-(gx ** 2 + gy ** 2) * 1.6)[:, :, None]
img = img * (1 - 0.85 * glow) + hexc('#e6b48e') * 0.85 * glow
# long, low clouds
cl = fbm(W, H, 420, 40, 5)
cl2 = fbm(W, H, 700, 70, 4)
sky = (ys < horizon + 8)[:, :, None]
light = smooth(cl, 0.50, 0.78)[:, :, None]
dark = smooth(cl2, 0.55, 0.85)[:, :, None]
fadeup = np.clip((horizon - ys) / horizon, 0, 1)[:, :, None]
img = np.where(sky, img * (1 - 0.40 * light) + hexc('#a9bfd2') * 0.40 * light * (0.55 + 0.45 * (1 - fadeup)), img)
img = np.where(sky, img * (1 - 0.45 * dark * fadeup) + hexc('#091a2e') * 0.45 * dark * fadeup, img)

# ---------------------------------------------------------------- sea
sea = vgrad([(0, '#476f92'), (0.12, '#2a5578'), (0.5, '#14375a'), (1, '#0a213c')], horizon, H)
wave = fbm(W, H, 260, 5, 4)[:, :, None]
sea = sea * (0.86 + 0.28 * wave)
refl = np.exp(-(((xs - 0.86 * W) / (0.16 * W)) ** 2)) * np.exp(-((ys - horizon) / (0.14 * H)) ** 2 * 0.9)
sea = sea + (hexc('#e2b08c') * 0.20)[None, None, :] * refl[:, :, None] * (0.55 + 0.9 * wave)
img = np.where((ys >= horizon)[:, :, None], sea, img)
# the horizon haze
haze = np.exp(-((ys - horizon) / (0.03 * H)) ** 2)[:, :, None]
img = img * (1 - 0.30 * haze) + hexc('#a8bdd0') * 0.30 * haze


# ---------------------------------------------------------------- ridges (far to near), each with fog and pines
def ridge_y(base, t_cross, power, amp, cell):
    """A ridge line that starts at `base` (fraction of the height) on the left and falls away to the right, passing the
    bottom edge at x = t_cross * W. Nearer ridges cross earlier, so the land recedes and the sea opens up on the right."""
    n1 = value_noise(W, 4, cell, 4)[0] * 0.65 + value_noise(W, 4, cell / 3.5, 4)[0] * 0.35
    t = np.linspace(0, 1, W, dtype=np.float32)
    drop = (1.06 - base) / t_cross ** power
    return base * H + drop * H * t ** power + (n1 - 0.5) * 2 * amp * H


layers = [
    # base, t_cross, power, amp, colour, fog, pine height (px), pine density
    (0.205, 1.05, 1.30, 0.050, '#35577a', 0.55, 0, 0),
    (0.285, 0.99, 1.55, 0.060, '#274866', 0.50, 9, 0.35),
    (0.395, 0.88, 1.90, 0.060, '#1a3550', 0.45, 15, 0.6),
    (0.540, 0.76, 2.20, 0.070, '#112739', 0.40, 26, 0.85),
    (0.745, 0.60, 2.40, 0.060, '#08161f', 0.30, 44, 1.0),
]
fogmask = fbm(W, H, 520, 60, 5)
for i, (base, t_cross, power, amp, col, fog, tree_h, density) in enumerate(layers):
    line = ridge_y(base, t_cross, power, amp, 220)
    # fog banks sit just above this ridge line and drift over it
    d = (ys - line[None, :]) / (0.07 * H)
    band = np.exp(-(d + 0.35) ** 2) * (0.45 + 0.9 * fogmask)
    img = img * (1 - np.clip(fog * band, 0, 0.7)[:, :, None]) + hexc('#a9bfd3') * np.clip(fog * band, 0, 0.7)[:, :, None]
    mask = Image.new('L', (W, H), 0)
    draw = ImageDraw.Draw(mask)
    draw.polygon([(0, H)] + [(int(x), float(line[int(x)])) for x in range(0, W, 2)] + [(W, line[W - 1]), (W, H)], fill=255)
    if tree_h:
        count = int(W * density * 0.55)
        for _ in range(count):
            x = int(rng.integers(0, W))
            y = float(line[x]) + rng.normal(0, 3)
            hgt = tree_h * (0.5 + rng.random())
            wid = hgt * 0.34
            for k in range(3):                       # stacked tiers make a pine
                top = y - hgt * (1 - k * 0.2)
                base_y = y - hgt * (0.55 - k * 0.2) + hgt * 0.35
                draw.polygon([(x, top), (x - wid * (0.55 + k * 0.25), base_y), (x + wid * (0.55 + k * 0.25), base_y)], fill=255)
    m = np.asarray(mask.filter(ImageFilter.GaussianBlur(0.9)), np.float32)[:, :, None] / 255.0
    # a little vertical falloff inside the layer so it reads as a slope, not a flat cut-out
    fall = np.clip((ys - line[None, :]) / (0.5 * H), 0, 1)[:, :, None]
    layer_rgb = hexc(col) * (1 - 0.35 * fall)
    img = img * (1 - m) + layer_rgb * m
    # mist drifting across the layer just below its crest
    crest = np.exp(-((ys - (line[None, :] + 0.05 * H)) / (0.05 * H)) ** 2)[:, :, None] * (0.25 + 0.7 * fogmask[:, :, None]) * m
    img = img * (1 - 0.34 * crest) + hexc('#8fa9c1') * 0.34 * crest

# a broad low fog sheet through the lower middle, as in a coastal evening
sheet = np.exp(-((ys - 0.66 * H) / (0.10 * H)) ** 2)[:, :, None] * (0.25 + 0.75 * fbm(W, H, 600, 90, 5)[:, :, None])
img = img * (1 - 0.26 * sheet) + hexc('#7f9bb6') * 0.26 * sheet

# ---------------------------------------------------------------- grade, vignette, grain
img = np.clip(img, 0, 1) ** 1.06
vy = 1 - 0.30 * smooth(ys / H, 0.55, 1.0)[:, :, None] * (1 - 0.35 * np.exp(-((xs - 0.85 * W) / (0.3 * W)) ** 2))[:, :, None]
vx = 1 - 0.22 * smooth(np.abs(xs / W - 0.5) * 2, 0.55, 1.05)[:, :, None]
img = img * vy * vx
im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7))
arr = np.asarray(im, np.float32) + rng.normal(0, 1.6, (H, W, 1)).astype(np.float32)
Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).save(sys.argv[1] if len(sys.argv) > 1 else 'zp04.jpg', quality=90, optimize=True, progressive=True)
print('wrote', sys.argv[1] if len(sys.argv) > 1 else 'zp04.jpg')
