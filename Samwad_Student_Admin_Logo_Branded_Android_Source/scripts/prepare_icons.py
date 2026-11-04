#!/usr/bin/env python3
"""Generate both launcher icons from the exact Samwad logo published by the LMS.
Fails closed if the approved logo cannot be retrieved. Dependencies: Pillow.
"""
import base64
from io import BytesIO
from pathlib import Path
import re
from urllib.request import urlopen, Request
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ICON_URL = 'https://preview--samwad-learning-feed.apper.so/samwad-icon.svg'
local = ROOT / 'branding' / 'samwad-icon.svg'
source = local.read_text(encoding='utf-8') if local.is_file() else urlopen(
    Request(ICON_URL, headers={'User-Agent':'Samwad-Android-Asset-Build/1.0'}), timeout=30
).read().decode('utf-8')
m = re.search(r'data:image/(?:jpeg|png);base64,([A-Za-z0-9+/=]+)', source)
if not m:
    raise RuntimeError('The original Samwad logo is missing from samwad-icon.svg. No placeholder icon will be shipped.')
logo = Image.open(BytesIO(base64.b64decode(m.group(1)))).convert('RGBA')
res = ROOT / 'app' / 'src' / 'main' / 'res'

# The circular original logo is preserved with transparent padding for adaptive launchers.
def make(size, pad_frac=0.075, background=(255,249,241,255)):
    canvas = Image.new('RGBA', (size, size), background)
    pad = int(size * pad_frac)
    image = ImageOps.contain(logo, (size - pad*2, size - pad*2), Image.Resampling.LANCZOS)
    canvas.alpha_composite(image, ((size-image.width)//2, (size-image.height)//2))
    return canvas

sizes = {'mdpi':48, 'hdpi':72, 'xhdpi':96, 'xxhdpi':144, 'xxxhdpi':192}
for density,size in sizes.items():
    directory = res / ('mipmap-' + density)
    directory.mkdir(parents=True, exist_ok=True)
    make(size).convert('RGB').save(directory/'ic_launcher.png', 'PNG', optimize=True)

# 108dp adaptive foreground design, 432px reference canvas with safe central area.
drawable = res/'drawable-nodpi'
drawable.mkdir(parents=True, exist_ok=True)
make(432, pad_frac=0.21).save(drawable/'samwad_mark.png', optimize=True)
print('Generated exact Samwad-logo Android icons for all densities and adaptive launcher.')
