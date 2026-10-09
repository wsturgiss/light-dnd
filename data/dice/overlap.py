# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""For every face of every die, count outline pixels its number (with halo) covers when landed."""
import runpy, pathlib
import numpy as np
ns = runpy.run_path(str(pathlib.Path(__file__).parent / "set.py"))
frame, DICE, Image = ns["frame"], ns["DICE"], ns["Image"]
row = []
for sides in DICE:
    bare = np.array(frame(DICE[sides], 0, 0, None))
    hits = {n: int(((bare > 0) & (np.array(frame(DICE[sides], 0, 0, n)) != bare)).sum()) for n in range(1, sides + 1)}
    bad = {n: h for n, h in hits.items() if h}
    print(f"d{sides}, all {sides} faces: " + (f"overlaps {bad}" if bad else "no outline px covered"))
    if sides == 10:   # percentile dice: the tens d10 shows 00–90, the units d10 0–9
        pct = {n: int(((bare > 0) & (np.array(frame(DICE[10], 0, 0, n)) != bare)).sum()) for n in [f"{k * 10:02d}" for k in range(10)] + [str(k) for k in range(10)]}
        bad = {n: h for n, h in pct.items() if h}
        print("d10 as percentile (00–90, 0–9): " + (f"overlaps {bad}" if bad else "no outline px covered"))
    row.append(frame(DICE[sides], 0, 0, {4: 1, 6: 1, 8: 1, 10: 10, 12: 11, 20: 17}[sides]))
sheet = Image.new("L", (6 * 84, 80), 40)
for k, f in enumerate(row): sheet.paste(f, (k * 84, 0))
sheet.resize((sheet.width * 3, 240), Image.NEAREST).save("landed.png")
