# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""Rolls for one or more dice: each real solid drops in, bounces (each impact kicks it onto a new random spin
axis) and rolls to rest, drawn in the flat line style, landing in the same pose as set.py. A d100 is two d10s.

CAMERA=travel  fixed camera; the dice are thrown in from one side and roll to rest.
CAMERA=travel-top  fixed camera from above: the dice are thrown in from one side, bounces come toward you and rolls
               tip them over their edges (follow's motion without the camera moving).
CAMERA=follow  seen from above; the dice travel in any direction and tip over their edges as they roll, bounce
               height shows as coming closer, and the camera chases them like a camera operator (lagging,
               overshooting a little) and settles on them as they stop."""
import math, os, random, pathlib, runpy, sys
import numpy as np
HERE = pathlib.Path(__file__).parent
sys.path.insert(0, str(HERE))
from solids import d4_solid, d6_solid, d8_solid, d10_solid, d12_solid, d20_solid, landing, rot, project
ns = runpy.run_path(str(HERE / "set.py"))
frame, DICE, Image, ImageDraw = ns["frame"], ns["DICE"], ns["Image"], ns["ImageDraw"]
G = frame.__globals__
SIZE, R, SCALE, INK, DIM, PAPER = G["SIZE"], G["R"], G["SCALE"], G["INK"], G["DIM"], G["PAPER"]
snap_x, snap_y, line = G["snap_x"], G["snap_y"], G["line"]

MODE = os.environ.get("CAMERA", "travel")
FOLLOW = MODE == "follow"                    # the camera moves
TOP = MODE in ("follow", "travel-top")       # seen from above: bounces come toward you, rolls tip over the edges
SPREAD = os.environ.get("SPREAD") == "1"     # experiment: multi-dice start bunched together, then spread out
OPAQUE = os.environ.get("OPAQUE", "1") == "1" # fill each die so it hides what's behind it (OPAQUE=0: see-through)
OUT = HERE / {"travel": "vary", "follow": "vary-follow", "travel-top": "vary-top"}[MODE]

def pose(solid, front, up, tilt=0.0):
    V, F = solid()
    if up is None: up = F[front][0]
    M = landing(V, F, front, up, tilt)
    poly, _ = project(V, F, M)
    origin = np.mean(poly, 0); scale = max(np.linalg.norm(np.array(p) - origin) for p in poly)
    return V, F, M, origin, scale

SOLIDS = {4: pose(d4_solid, 0, None), 6: pose(d6_solid, 0, (0, 1, 1)), 8: pose(d8_solid, 0, None), 10: pose(d10_solid, 0, 0, -15),
          12: pose(d12_solid, 0, None, -18), 20: pose(d20_solid, 0, None)}

SCREEN_W = 180                       # the LP3 is 1080 px wide: 180 canvas px at 6 device px each
REACH = math.ceil(1.25 * R)          # furthest a tumbling die reaches from its centre (a cube corner-on: 1.22 R)
SPACING = 72                         # centre to centre for dice resting side by side
ROW = 70                             # centre to centre between rows (3 dice: two over one; 4: two over two)
MAX_DICE = 4                         # more than this: no animation, just the results
ZOOM = 0.15                          # follow: size gained at the highest bounce, as if coming toward you
SLACK = 14                           # follow: how far off-centre the camera lets the dice get
TRAVEL_H = 128
H_TOP = (TRAVEL_H - 3 - REACH) - REACH - 3      # highest drop, in px of bounce
FRAME_MS = 45
GRAV = 1.6                           # px / frame²: a 40 px drop takes about 7 frames
HOLD_MS = 1400

def silhouette(sides, Rm):
    V, F, M, origin, scale = SOLIDS[sides]
    poly, inner = project(V, F, M @ Rm)     # rotate the die in its own frame, camera fixed
    unit = lambda p: ((p[0] - origin[0]) / scale * R, (p[1] - origin[1]) / scale * R)
    return [unit(p) for p in poly], [(unit(a), unit(b)) for a, b in inner]

def draw_die(img, sides, Rm, cx, cy, zoom=1.0):
    poly, inner = silhouette(sides, Rm)
    if zoom != 1.0:
        poly = [(p[0] * zoom, p[1] * zoom) for p in poly]
        inner = [((a[0] * zoom, a[1] * zoom), (b[0] * zoom, b[1] * zoom)) for a, b in inner]
    d = ImageDraw.Draw(img)
    P = lambda p: (snap_x(cx + p[0], cx), snap_y(cy + p[1]))
    if OPAQUE:
        d.polygon([P(p) for p in poly], fill=PAPER)     # blank out whatever is behind this die
    for a, b in inner: line(d, P(a), P(b), DIM, cx)
    G["outline"](d, [P(p) for p in poly])

def draw_landed(img, sides, result, cx, cy):
    """set.py's static landing drawing, pixel-identical to draw_die with no rotation at the same centre."""
    if OPAQUE:
        poly, _ = silhouette(sides, np.eye(3))
        ImageDraw.Draw(img).polygon([(snap_x(cx + p[0], cx), snap_y(cy + p[1])) for p in poly], fill=PAPER)
    die = frame(DICE[sides], 0, 0, result)              # drawn centred at (SIZE/2, SIZE/2)
    img.paste(die, (cx - SIZE // 2, cy - SIZE // 2), Image.eval(die, lambda v: 255 if v else 0))

def plan(sides, rng, way, d_scr, lim):
    """One die's motion: drop, two or three bounces, then a roll in the direction of travel that eases to rest.
    `way` is the travel sign (travel mode), `d_scr` the screen direction (follow), `lim` the furthest it may start
    from its resting place. Returns [(camera-frame rotation, distance from rest along the travel, height,
    roll progress or None, ms)]."""
    if TOP:
        tip = np.cross((0, 0, 1), (d_scr[0], -d_scr[1], 0))    # tipping-forward axis, camera frame (y up)
        # unwinding this tips the die forward about `tip`, twisting it as it settles so dice don't all end alike
        twist = math.radians(rng.uniform(15, 60)) * rng.choice((-1, 1))
        roll_rot = lambda ang: rot((0, 0, 1), twist * ang / roll_ang) @ rot(tip, -ang) if roll_ang else np.eye(3)
    else:
        roll_rot = lambda ang: rot((0, 0, 1), way * ang)        # clockwise when moving right
    e = rng.uniform(0.42, 0.55)                  # bounciness
    heights = [rng.uniform(H_TOP - 10, H_TOP)]
    for _ in range(rng.choice((2, 2, 3))): heights.append(max(3.0, heights[-1] * e * e))
    segs = []                                    # airborne: (frames, axis, total angle, travel, height curve)
    omega = math.radians(rng.uniform(16, 24))   # spin per frame, under ~25° so it reads
    v = rng.uniform(3.4, 4.4)                    # px / frame along the travel
    for k, hgt in enumerate(heights):
        rise = math.sqrt(2 * hgt / GRAV)
        if k == 0:  n = max(3, round(rise)); arc = lambda s, h=hgt: h * (1 - s * s)            # falling in
        else:       n = max(3, round(2 * rise)); arc = lambda s, h=hgt: h * 4 * s * (1 - s)    # up and down
        axis = np.array([rng.gauss(0, 1) for _ in range(3)])
        if k > 0 and TOP:
            axis = 0.6 * axis - tip              # the table kicks it into tipping forward the way it's going
        elif k > 0:
            axis[2] = way * (abs(axis[2]) + 0.6) # ...or, from the side, into turning the way it's going
        segs.append((n, axis, omega * n, v * n, arc))
        omega *= rng.uniform(0.6, 0.75); v *= 0.88                                    # impact
    # it lands moving at v and rolls on from there: an ease-out over n frames starts at 2·distance/n
    r_roll = 0.8 * R
    n_roll = rng.randint(10, 13)
    roll_ang = v * n_roll / 2 / r_roll
    travel = roll_ang * r_roll + sum(sg[3] for sg in segs)
    if travel > lim:
        f = lim / travel; roll_ang *= f; segs = [(n, a, t, dx * f, arc) for n, a, t, dx, arc in segs]
    seg_list = segs + [("roll",)]
    def orientation(j, s):
        """Remaining rotation from segment j (progress s) to the landing pose."""
        Rc = np.eye(3)
        for k in range(len(seg_list) - 1, j, -1):      # later segments, applied last-first
            sg = seg_list[k]
            Rc = (roll_rot(roll_ang) if sg[0] == "roll" else rot(sg[1], sg[2])) @ Rc
        sg = seg_list[j]
        if sg[0] == "roll":
            ease = 1 - (1 - s) ** 2
            return roll_rot(roll_ang * (1 - ease)) @ Rc, ease
        return rot(sg[1], sg[2] * (1 - s)) @ Rc, s
    def dist(j, s, ease):
        rem = sum(roll_ang * r_roll if seg_list[k][0] == "roll" else seg_list[k][3] for k in range(j + 1, len(seg_list)))
        sg = seg_list[j]
        return rem + (roll_ang * r_roll * (1 - ease) if sg[0] == "roll" else sg[3] * (1 - s))
    out = []
    for j, sg in enumerate(seg_list):
        n = n_roll if sg[0] == "roll" else sg[0]
        for i in range(n):
            s = i / n
            Rc, ease = orientation(j, s)
            out.append((Rc, dist(j, s, ease), 0.0 if sg[0] == "roll" else sg[4](s),
                        s if sg[0] == "roll" else None, FRAME_MS if sg[0] != "roll" else int(FRAME_MS + 50 * s ** 1.5)))
    return out

def scene(dice, rng):
    """Roll up to MAX_DICE dice together. `dice` is [(sides, label)]; they come to rest side by side (two rows for
    3 or 4), each showing its label as it lands. Returns (frames, ms)."""
    n = len(dice); assert 1 <= n <= MAX_DICE
    rows = [list(range(n))] if n <= 2 else [[0, 1], list(range(2, n))]   # top row first
    rest = [None] * n                                                    # resting (x, y) offsets
    for r, row in enumerate(rows):
        for j, k in enumerate(row):
            ry = (r - (len(rows) - 1)) * ROW if not TOP else round((r - (len(rows) - 1) / 2) * ROW)
            rest[k] = (round((j - (len(row) - 1) / 2) * SPACING), ry)
    span_y = ROW * (len(rows) - 1)
    if FOLLOW:
        span_x = SPACING * (max(len(r) for r in rows) - 1); reach = 2 * math.ceil(REACH * (1 + ZOOM))
        W = min(SCREEN_W, reach + 6 + 2 * SLACK + span_x); H = 132 if len(rows) == 1 else 190
        slack_x = min(SLACK, (W - span_x - reach) / 2 - 2)
        slack_y = min(SLACK, (H - span_y - reach) / 2 - 2)
        phi = rng.uniform(0, 2 * math.pi); d_scr = np.array([math.cos(phi), math.sin(phi)]); way = 1
        lims = [84.0] * n                        # same throw lengths as the travel camera's single die
    elif TOP:
        # fixed camera from above: thrown in from the left or right edge, give or take 15°, crossing to the middle.
        # No room for bounce height is needed: from above, height shows as size
        W = 176 if n == 1 else SCREEN_W; H = 2 * math.ceil(REACH * (1 + ZOOM)) + 6 + span_y
        way = rng.choice((-1, 1)); phi = (0 if way > 0 else math.pi) + math.radians(rng.uniform(-15, 15))
        d_scr = np.array([math.cos(phi), math.sin(phi)]); way = 1
        lims = [(W / 2 + (1 if d_scr[0] > 0 else -1) * r[0]) - 4 for r in rest]
    else:
        W = 176 if n == 1 else SCREEN_W; H = TRAVEL_H + span_y          # extra rows sit higher up
        way = rng.choice((-1, 1)); d_scr = np.array([float(way), 0.0])
        # each may start up to half off the edge it comes in from
        lims = [(W / 2 + way * r[0]) - 4 for r in rest]
    rest_y = H // 2 if TOP else H - 3 - REACH
    if TOP:
        # thrown together, but each die heads its own way, up to 25° off the throw (15° when it must cross the screen)
        spread = 25 if FOLLOW else 15
        dirs = []
        for _ in range(n):
            a = phi + math.radians(rng.uniform(-spread, spread)) if n > 1 else phi
            dirs.append(np.array([math.cos(a), math.sin(a)]))
    else:
        dirs = [d_scr] * n
    plans = [plan(s, rng, way, dk, lim) for (s, _), lim, dk in zip(dice, lims, dirs)]
    # a common clock: each die shows the latest of its frames that has started by each 45 ms tick
    starts = [np.cumsum([0] + [st[4] for st in p]) for p in plans]
    end = max(s[-1] for s in starts)
    ticks = list(range(0, int(end) + FRAME_MS, FRAME_MS))
    def state(k, t):
        i = int(np.searchsorted(starts[k], t, side="right")) - 1
        return None if i >= len(plans[k]) else plans[k][i]               # None: landed
    def pos(k, t):
        """Where die k is relative to its resting place: it comes in from behind along the travel."""
        st = state(k, t)
        return -dirs[k] * (st[1] if st else 0.0)
    # follow: camera operator, a slightly underdamped spring chasing the middle of the dice, never letting
    # them get more than SLACK px off-centre, smoothstepping fully onto them over the last 0.7 s
    # SPREAD: each die starts near the middle (a handful), easing out to its resting place before any die lands
    first_land = min(st[-1] for st in starts)
    bunch = [np.array([rng.uniform(-5, 5), rng.uniform(-5, 5)]) for _ in range(n)]
    def place(k, t):
        if not SPREAD or n == 1: return np.array(rest[k], float)
        u = min(1.0, t / (0.85 * first_land)); u = u * u * (3 - 2 * u)
        return bunch[k] * (1 - u) + np.array(rest[k], float) * u
    mid = lambda t: np.mean([pos(k, t) + place(k, t) - np.array(rest[k], float) for k in range(n)], 0)
    offs = [np.zeros(2)] * len(ticks)
    if FOLLOW:
        w0, zeta = rng.uniform(0.28, 0.4), rng.uniform(0.55, 0.75)
        c = mid(0) + d_scr * rng.uniform(-4, 4) + np.array([rng.uniform(-3, 3), rng.uniform(-3, 3)])
        vc = np.zeros(2); offs = []
        for t in ticks:
            p = mid(t)
            vc = vc + w0 * w0 * (p - c) - 2 * zeta * w0 * vc; c = c + vc
            u = min(1.0, max(0.0, (t - (end - 700)) / 700)); settle = u * u * (3 - 2 * u)
            o = (p - c) * (1 - settle)
            offs.append(np.array([max(-slack_x, min(slack_x, o[0])), max(-slack_y, min(slack_y, o[1]))]))
    # stacking order, fixed for the whole throw. Travel: back row first, so the front row overlaps it. Follow: from
    # above, the die highest at the start is nearest, so it goes on top and stays there (re-sorting each frame made
    # dice swap layers mid-air whenever their bounces crossed)
    stack = sorted(range(n), key=lambda k: plans[k][0][2]) if TOP else list(range(n))
    frames, ms, last = [], [], None
    for t, o in zip(ticks + [end + 1], offs + [np.zeros(2)]):
        img = Image.new("L", (W, H), PAPER); key = [None] * n
        order = stack
        for k in order:
            sides, label = dice[k]
            st = state(k, t)
            # the camera is aimed at the middle of the dice, `o` px behind it (follow); fixed (travel)
            p = pos(k, t) - (mid(t) - o if FOLLOW else 0)
            q = place(k, t)
            cx = W // 2 + round(q[0] + p[0])
            cy = rest_y + round(q[1] + p[1]) - (round(st[2]) if st and not TOP else 0)
            if TOP and st is not None:
                # follow: each die travels its own distance, so early on one can run ahead of the group: keep it in
                # shot. travel-top: it may come in over the side edge, but its angled throw mustn't leave the top or bottom
                m = math.ceil(REACH * (1 + ZOOM * st[2] / H_TOP)) + 2
                if FOLLOW: cx = max(m, min(W - 1 - m, cx))
                cy = max(m, min(H - 1 - m, cy))
            if st is None:
                draw_landed(img, sides, label, cx, cy); key[k] = ("landed", cx, cy)
            else:
                _, _, M, _, _ = SOLIDS[sides]
                zoom = 1 + ZOOM * st[2] / H_TOP if TOP else 1.0
                draw_die(img, sides, M.T @ st[0] @ M, cx, cy, zoom); key[k] = (id(st), cx, cy)
        if key == last:
            ms[-1] += FRAME_MS                   # nothing moved this tick: hold the previous frame longer
        else:
            frames.append(img); ms.append(FRAME_MS); last = key
    ms[-1] = HOLD_MS
    return frames, ms

def roll(sides, result, rng):
    return scene([(sides, result)], rng)

def d100(rng):
    """Percentile dice: a tens d10 (00–90) and a units d10 (0–9). 00 + 0 reads as 100."""
    tens, units = rng.randrange(10), rng.randrange(10)
    return scene([(10, f"{tens * 10:02d}"), (10, str(units))], rng), (tens * 10 + units) or 100

def save(fr, ms, path):
    big = [f.resize((f.width * SCALE // 2, f.height * SCALE // 2), Image.NEAREST) for f in fr]
    big[0].save(path, save_all=True, append_images=big[1:], duration=ms, loop=0)

def pct_labels(rng):
    """One d100's two labels: tens (00–90) and units (0–9)."""
    return [f"{rng.randrange(10) * 10:02d}", str(rng.randrange(10))]

# every die type at 1–4 of the same die; d100 (a pair of d10s each) at 1–2
SETS = [(f"{c}d{s}", [s] * c) for s in (4, 6, 8, 10, 12, 20) for c in range(1, MAX_DICE + 1)] + \
       [(f"{c}d100", [10, 10] * c) for c in (1, 2)]

if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    rng = random.Random()
    VARIANTS = 8
    if SPREAD:
        for name, kinds in SETS:
            if len(kinds) < 2: continue
            for k in range(VARIANTS):
                labels = sum((pct_labels(rng) for _ in range(len(kinds) // 2)), []) if name.endswith("d100") else [rng.randint(1, sd) for sd in kinds]
                save(*scene(list(zip(kinds, labels)), rng), OUT / f"spread-{name}-{k}.gif")
        print("ok"); sys.exit()
    for old in OUT.glob("*.gif"):
        if not old.name.startswith("spread-"): old.unlink()
    for name, kinds in SETS:
        for k in range(VARIANTS):
            labels = sum((pct_labels(rng) for _ in range(len(kinds) // 2)), []) if name.endswith("d100") else [rng.randint(1, sd) for sd in kinds]
            save(*scene(list(zip(kinds, labels)), rng), OUT / f"{name}-{k}.gif")
    print("ok", MODE, len(list(OUT.glob("[0-9]*.gif"))), "rolls")
