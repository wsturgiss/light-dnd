# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""The unrotated 3D pose must match the static landing drawing pixel for pixel, and every scene must end on it."""
import runpy, pathlib, sys, random
import numpy as np
t = runpy.run_path(str(pathlib.Path(__file__).parent / "tumble.py"))
Image = t["Image"]
for sides in (4, 6, 8, 10, 12, 20):
    a = Image.new("L", (120, 120)); b = Image.new("L", (120, 120))
    t["draw_die"](a, sides, np.eye(3), 60, 60); t["draw_landed"](b, sides, None, 60, 60)
    print(f"d{sides}: {int((np.array(a) != np.array(b)).sum())} px differ between unrotated 3D pose and the static drawing")
# last moving frame vs landed frame of whole scenes: the dice must not shift as they land
rng = random.Random(1); worst = 0
for _ in range(10):
    (fr, ms), _ = t["d100"](rng)
    a = np.array(fr[-2]) >= 200; b = np.array(fr[-1]) >= 200
    ya, xa = np.nonzero(a); yb, xb = np.nonzero(b)
    worst = max(worst, abs(xa.min() - xb.min()), abs(xa.max() - xb.max()), abs(ya.max() - yb.max()))
print("d100 scenes: dice outline shift on landing, worst", worst, "px")
