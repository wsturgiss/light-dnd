# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""export.py's sprites, pasted with their alpha, must draw exactly what tumble.py's draw_die and draw_landed draw,
including over another die (the opaque fill). Run after export.py."""
import math, os, pathlib, random, runpy
import numpy as np
from PIL import Image
HERE = pathlib.Path(__file__).parent
A = HERE.parent.parent / "tool/src/main/assets/dice"
os.environ["CAMERA"] = "follow"
t = runpy.run_path(str(HERE / "tumble.py"))
S = 104

def paste(img, sprite, cx, cy):
    img.paste(sprite.convert("L"), (cx - sprite.width // 2, cy - sprite.height // 2), sprite.getchannel("A"))

face = Image.open(A / "faces/d20.png").crop((0, 16 * 80, 80, 17 * 80))
a = Image.new("L", (160, 160)); t["draw_landed"](a, 8, 3, 70, 80); t["draw_landed"](a, 20, 17, 100, 80)
b = Image.new("L", (160, 160)); t["draw_landed"](b, 8, 3, 70, 80); paste(b, face, 100, 80)
print("landed d20 over a d8, face sprite vs draw_landed:", int((np.array(a) != np.array(b)).sum()), "px differ")

# replay the exporter's rng up to d20, bin 7, variant 1, and redraw that throw with draw_die
rng = random.Random("top-1")
for sides in t["SOLIDS"]:
    for b_ in range(24):
        for v in range(2):
            a_ = 2 * math.pi * b_ / 24
            plan = t["plan"](sides, rng, 1, np.array([math.cos(a_), math.sin(a_)]), 84.0)
            if (sides, b_, v) == (20, 7, 1): break
        else: continue
        break
    else: continue
    break
_, _, M, _, _ = t["SOLIDS"][20]
strip = Image.open(A / "top/d20-b7-1.png"); bad = 0
for i, (Rc, dist, h, _, ms) in enumerate(plan):
    a = Image.new("L", (200, 200)); t["draw_landed"](a, 6, 2, 95, 100)
    b = a.copy()
    t["draw_die"](a, 20, M.T @ Rc @ M, 110, 90, 1 + t["ZOOM"] * h / t["H_TOP"])
    paste(b, strip.crop((0, i * S, S, (i + 1) * S)), 110, 90)
    bad += int((np.array(a) != np.array(b)).sum())
print(f"d20 throw over a d6, {len(plan)} frames, sprite vs draw_die:", bad, "px differ")
