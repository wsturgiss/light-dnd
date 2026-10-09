# /// script
# dependencies = ["pillow", "numpy"]
# ///
import runpy, pathlib, sys, random, os, glob
import numpy as np
os.environ["CAMERA"] = "follow"; sys.argv = ["x"]
t = runpy.run_path(str(pathlib.Path(__file__).parent / "tumble.py"))
W, H = t["W"], t["H"]
# per-frame die centre (from lit pixels' bounding box) for a few rolls
for seed in (1, 2, 3):
    fr, ms = t["roll"](20, 17, random.Random(seed))
    cs = []
    for f in fr:
        ys, xs = np.nonzero(np.array(f) >= 200); cs.append(((xs.min() + xs.max()) / 2 - W / 2, (ys.min() + ys.max()) / 2 - H / 2))
    jumps = [np.hypot(cs[i + 1][0] - cs[i][0], cs[i + 1][1] - cs[i][1]) for i in range(len(cs) - 1)]
    print(f"seed {seed}: off-centre max {max(np.hypot(*c) for c in cs):.1f}px, biggest frame-to-frame shift {max(jumps):.1f}px, last {cs[-1]}")
    print("   ", " ".join(f"{np.hypot(*c):.0f}" for c in cs))
clip = 0
for f in glob.glob(str(t["OUT"] / "*.gif")):
    from PIL import Image
    g = Image.open(f)
    for i in range(g.n_frames):
        g.seek(i); a = np.array(g.convert("L"))
        if a[:3].max() or a[-3:].max() or a[:, :3].max() or a[:, -3:].max(): clip += 1
print("clipped frames:", clip)
