# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""Per die: is the outline mirror-symmetric about column 40, and does its number sit on that axis?"""
import runpy, pathlib
import numpy as np
ns = runpy.run_path(str(pathlib.Path(__file__).parent / "set.py"))
frame, DICE, Image = ns["frame"], ns["DICE"], ns["Image"]
AX = 40
rows = []
for sides, nums in ((4, (1, 3, 4)), (6, (1, 3, 6)), (8, (1, 3, 8)), (10, (1, 7, 10)), (12, (3, 11, 12)), (20, (1, 17, 20))):
    bare = np.array(frame(DICE[sides], 0, 0, None)) > 0
    mirror = bare[:, ::-1][:, 80 - 2 * AX - 1 + 80 - 80:] if False else np.roll(bare[:, ::-1], 2 * AX + 1 - 80, axis=1)
    asym = int((bare != mirror).sum())
    out = []
    for n in nums:
        a = np.array(frame(DICE[sides], 0, 0, n)); num = (a == 235) & ~bare
        cols = np.nonzero(num)[1]
        out.append(f"{n}: {AX - cols.min()}|{cols.max() - AX}")
    print(f"d{sides:<2} outline asymmetric px: {asym:>3}   number left|right of axis: " + "  ".join(out))
    for n in nums[-1:]:
        f = frame(DICE[sides], 0, 0, n).convert("RGB"); px = f.load()
        for y in range(4): px[AX, y] = (255, 60, 60)
        rows.append(f)
sheet = Image.new("RGB", (6 * 84, 80), (40, 40, 40))
for k, f in enumerate(rows): sheet.paste(f, (k * 84, 0))
sheet.resize((sheet.width * 3, 240), Image.NEAREST).save("centre-all.png")
