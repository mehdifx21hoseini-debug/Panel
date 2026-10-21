"""Review sheet: the start, middle and end stills of exercises, a row each, two rows across.
Usage: python3 contact.py out.png key [key ...]"""
import os, sys
from PIL import Image, ImageDraw
here = os.path.dirname(os.path.abspath(__file__))
out, keys = sys.argv[1], sys.argv[2:]
S = 240
cols = 2
rows = (len(keys) + cols - 1) // cols
W = Image.new('RGB', (cols * 3 * S, rows * (S + 16)), (255, 255, 255))
d = ImageDraw.Draw(W)
for i, k in enumerate(keys):
    x0, y0 = (i % cols) * 3 * S, (i // cols) * (S + 16)
    d.text((x0 + 4, y0 + 2), k, fill=(0, 0, 0))
    for j, p in enumerate(['0', '0.5', '1']):
        f = os.path.join(here, 'build/sheets', f'{k}_{p}.png')
        if not os.path.exists(f): continue
        im = Image.open(f).convert('RGBA').resize((S, S), Image.LANCZOS)
        t = Image.new('RGBA', (S, S), (238, 242, 249, 255)); t.alpha_composite(im)
        W.paste(t.convert('RGB'), (x0 + j * S, y0 + 16))
W.save(out)
