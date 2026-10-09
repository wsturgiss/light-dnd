# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""Writes the roll animations the app plays, into tool/src/main/assets/dice/.

tumble.py's scene() rolls up to four dice together. The app runs the scene itself (rest layout, the shared clock,
where each die is, the follow camera's spring, stacking order), so every roll is different. What it can't do is
draw the dice the way set.py does, pixel for pixel, so this bakes each die's own motion as sprites:

  top/d{n}-b{bin}-{v}.png   seen from above (follow, travel-top): a throw heading at bin x 15 degrees
  side/d{n}-{r|l}-{v}.png   seen from the side (travel): a throw heading right or left

Each is a vertical strip of sprite-sized frames, the die drawn opaque round the sprite's centre on a transparent
ground, with the zoom for bounce height baked in from above. Landing is the face, drawn by the app at the resting
spot, exactly as draw_landed draws it:

  faces/d{n}.png              the landed die showing 1..n, transparent outside the die
  faces/pct10.png, pct1.png   percentile d10s: 00..90 and 0..9

index.json holds, per throw, each frame's time, the distance still to travel and the bounce height, plus the
scene constants. It mirrors tumble.py's plan(): if that changes, run this again.

    uv run export.py
"""
import json, math, os, pathlib, random, runpy
import numpy as np
from PIL import Image, ImageChops, ImageDraw

HERE = pathlib.Path(__file__).parent
ASSETS = HERE.parent.parent / "tool/src/main/assets/dice"
BINS = 24                    # throw directions from above, every 15 degrees
TOP_VARIANTS = 2
SIDE_VARIANTS = 4
LIM = 84.0                   # furthest a die starts from where it rests: tumble.py's single-die throw


def load(camera):
    os.environ["CAMERA"] = camera
    return runpy.run_path(str(HERE / "tumble.py"))


def silhouette_mask(t, sides, Rm, c, size, zoom=1.0):
    """The die's filled silhouette, snapped exactly as draw_die snaps it."""
    poly, _ = t["silhouette"](sides, Rm)
    G = t["G"]
    m = Image.new("L", (size, size))
    ImageDraw.Draw(m).polygon([(G["snap_x"](c + p[0] * zoom, c), G["snap_y"](c + p[1] * zoom)) for p in poly], fill=255)
    return m


def with_alpha(img, mask):
    """Opaque over the silhouette and wherever there's ink (the 2 px outline reaches past the fill)."""
    lit = Image.eval(img, lambda v: 255 if v else 0)
    return Image.merge("LA", (img, ImageChops.lighter(mask, lit)))


def throw(t, sides, plan, size, name):
    """One die's throw as an LA strip; returns its frame times, distances and heights."""
    _, _, M, _, _ = t["SOLIDS"][sides]
    c = size // 2
    s = Image.new("LA", (size, size * len(plan)))
    for i, (Rc, dist, h, _, ms) in enumerate(plan):
        zoom = 1 + t["ZOOM"] * h / t["H_TOP"] if t["TOP"] else 1.0
        img = Image.new("L", (size, size), t["PAPER"])
        Rm = M.T @ Rc @ M
        t["draw_die"](img, sides, Rm, c, c, zoom)
        s.paste(with_alpha(img, silhouette_mask(t, sides, Rm, c, size, zoom)), (0, i * size))
    s.save(name, optimize=True)
    return {"ms": [p[4] for p in plan], "dist": [round(float(p[1]), 2) for p in plan], "h": [round(float(p[2]), 2) for p in plan]}


def fresh(d):
    d.mkdir(parents=True, exist_ok=True)
    for f in d.glob("*.png"): f.unlink()
    return d


top = load("follow")
SPRITE = 2 * (math.ceil(top["REACH"] * (1 + top["ZOOM"])) + 3)
index = {"sprite": SPRITE, "bins": BINS, "top": {}, "side": {}}

# from above: shared by follow and travel-top, whose plan() is the same; only the camera differs
out = fresh(ASSETS / "top"); rng = random.Random("top-1")
for sides in top["SOLIDS"]:
    index["top"][str(sides)] = [
        [throw(top, sides, top["plan"](sides, rng, 1, np.array([math.cos(a), math.sin(a)]), LIM), SPRITE,
               out / f"d{sides}-b{b}-{v}.png") for v in range(TOP_VARIANTS)]
        for b, a in ((b, 2 * math.pi * b / BINS) for b in range(BINS))
    ]

# from the side: travel
side = load("travel")
out = fresh(ASSETS / "side"); rng = random.Random("side-1")
for sides in side["SOLIDS"]:
    index["side"][str(sides)] = [
        [throw(side, sides, side["plan"](sides, rng, way, np.array([float(way), 0.0]), LIM), SPRITE,
               out / f"d{sides}-{tag}-{v}.png") for v in range(SIDE_VARIANTS)]
        for way, tag in ((1, "r"), (-1, "l"))
    ]

# landed faces
FACE = top["SIZE"]; fc = FACE // 2
faces = fresh(ASSETS / "faces")
def face_strip(sides, labels, name):
    s = Image.new("LA", (FACE, FACE * len(labels)))
    for i, label in enumerate(labels):
        img = Image.new("L", (FACE, FACE), top["PAPER"])
        top["draw_landed"](img, sides, label, fc, fc)
        s.paste(with_alpha(img, silhouette_mask(top, sides, np.eye(3), fc, FACE)), (0, i * FACE))
    s.save(faces / f"{name}.png", optimize=True)
for sides in top["SOLIDS"]:
    face_strip(sides, list(range(1, sides + 1)), f"d{sides}")
face_strip(10, [f"{k * 10:02d}" for k in range(10)], "pct10")
face_strip(10, [str(k) for k in range(10)], "pct1")

# how far a landed die reaches from its centre, for laying text out beside the dice
half = 0
for sides in top["SOLIDS"]:
    face = Image.new("L", (FACE, FACE)); top["draw_landed"](face, sides, None, fc, fc)
    ys, xs = np.nonzero(np.array(ImageChops.lighter(face, silhouette_mask(top, sides, np.eye(3), fc, FACE))))
    half = max(half, fc - xs.min(), xs.max() + 1 - fc, fc - ys.min(), ys.max() + 1 - fc)
index["face"] = FACE
index["landedHalf"] = int(half)
index["consts"] = {k: top[k] for k in ("REACH", "SPACING", "ROW", "ZOOM", "SLACK", "H_TOP", "FRAME_MS", "TRAVEL_H",
                                       "SCREEN_W", "MAX_DICE")}
(ASSETS / "index.json").write_text(json.dumps(index, separators=(",", ":")))

for old in ("follow", "travel"):     # the earlier one-die-per-strip export
    d = ASSETS / old
    if d.exists():
        for f in d.iterdir(): f.unlink()
        d.rmdir()
print("ok", SPRITE, "px sprites,", sum(f.stat().st_size for f in ASSETS.rglob("*") if f.is_file()) // 1024, "KB")
