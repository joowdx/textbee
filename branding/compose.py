"""Compose textbeeqtt icon layers from bee.png.

usage: compose.py OUT [--mode full|bg|fg|mono] [--width FRAC] [--hex-scale K] [--size PX]
  full  gradient + honeycomb + bee (the master)
  bg    gradient + honeycomb only (adaptive icon background)
  fg    bee only on transparent (adaptive icon foreground)
  mono  white bee silhouette on transparent (notification / themed icon)
"""
import argparse
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))

p = argparse.ArgumentParser()
p.add_argument('out')
p.add_argument('--mode', default='full', choices=['full', 'bg', 'fg', 'mono'])
p.add_argument('--width', type=float, default=0.62, help='bee width as a fraction of the canvas')
p.add_argument('--hex-scale', type=float, default=1.0)
p.add_argument('--size', type=int, default=1024)
a = p.parse_args()
S = a.size


def background():
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    d = np.hypot(xx - S / 2, yy - S / 2) / (S / 2 * math.sqrt(2))
    inner, outer = np.array([255, 200, 90]), np.array([251, 150, 60])
    t = np.clip(d, 0, 1)[..., None] ** 1.1
    bg = inner * (1 - t) + outer * t

    # flat-top hex grid centered on the canvas, fading toward the edges
    r = S * 0.075 * a.hex_scale
    w, h = 2 * r, math.sqrt(3) * r
    n = int(S / h) + 2
    lines = Image.new('L', (S, S), 0)
    dr = ImageDraw.Draw(lines)
    for col in range(-n, n + 1):
        for row in range(-n, n + 1):
            cx = S / 2 + col * 0.75 * w
            cy = S / 2 + row * h + (h / 2 if col % 2 else 0)
            pts = [(cx + r * 0.94 * math.cos(math.radians(60 * k)), cy + r * 0.94 * math.sin(math.radians(60 * k))) for k in range(7)]
            dr.line(pts, fill=255, width=max(2, round(S / 340 * a.hex_scale)))
    lines = np.asarray(lines.filter(ImageFilter.GaussianBlur(S / 900)), np.float32) / 255
    fade = np.clip(1 - d * 1.15, 0, 1) ** 1.4
    bg = bg + (np.array([255, 235, 170]) - bg) * (lines * fade * 0.42)[..., None]
    return Image.fromarray(bg.clip(0, 255).astype(np.uint8)).convert('RGBA')


def bee_layer():
    bee = Image.open(os.path.join(HERE, 'bee.png')).convert('RGBA')
    bee = bee.crop(bee.getchannel('A').point(lambda v: 255 if v > 8 else 0).getbbox())
    scale = S * a.width / bee.width
    bee = bee.resize((round(bee.width * scale), round(bee.height * scale)), Image.LANCZOS)
    layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ox, oy = (S - bee.width) // 2, (S - bee.height) // 2
    shadow = Image.new('RGBA', bee.size, (150, 70, 10, 0))
    shadow.putalpha(bee.getchannel('A').point(lambda v: v * 0.28))
    shadow = shadow.filter(ImageFilter.GaussianBlur(S / 110))
    layer.alpha_composite(shadow, (ox, oy + S // 90))
    layer.alpha_composite(bee, (ox, oy))
    return layer, (ox, oy, bee)


def mono():
    _, (ox, oy, bee) = bee_layer()
    px = np.asarray(bee).astype(np.float32)
    solid = px[..., 3] > 128
    # inner silhouette: drop the white sticker border, then punch the dark line work
    # back out so the face, dots, stripe and wings read as cut-outs
    white = (px[..., :3].min(axis=2) > 215)
    body = ndi.binary_fill_holes(solid & ~white)
    dark = px[..., :3].max(axis=2) < 110
    inner_dark = dark & ndi.binary_erosion(body, iterations=max(1, round(S / 120)))
    shape = body & ~inner_dark
    shape = ndi.binary_opening(shape, iterations=max(1, round(S / 400)))
    alpha = ndi.gaussian_filter(shape.astype(np.float32), 0.6) * 255
    sil = np.zeros((*shape.shape, 4), np.uint8)
    sil[..., :3] = 255
    sil[..., 3] = alpha.clip(0, 255).astype(np.uint8)
    layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    layer.alpha_composite(Image.fromarray(sil), (ox, oy))
    return layer


if a.mode == 'bg':
    out = background().convert('RGB')
elif a.mode == 'fg':
    out = bee_layer()[0]
elif a.mode == 'mono':
    out = mono()
else:
    out = background()
    out.alpha_composite(bee_layer()[0])
    out = out.convert('RGB')
out.save(a.out)
