# /// script
# dependencies = ["pillow", "numpy"]
# ///
"""Flat-outline roll previews for every die type (style A), plus a contact sheet."""
import math, random, pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from solids import d4_solid, d6_solid, d10_solid, d12_solid, view
from PIL import Image, ImageDraw, ImageFont

HERE = pathlib.Path(__file__).parent
VGA = str(HERE.parent.parent / "tool/src/main/res/font/ibm_vga.ttf")   # PxPlus IBM VGA 8x16
INK, PAPER, DIM = 235, 0, 110
SIZE, R, N, SCALE = 80, 33, 14, 6   # R 33: the d20 face fits two VGA digits plus halo
font = ImageFont.truetype(VGA, 16)
random.seed(4)

def snap_y(v):
    """Round to a pixel row, halves always down. Python's round() sends halves to even, which depends
    on where the die sits, so the same corner would round differently on different canvases."""
    return math.floor(round(v, 6) + 0.5)

def snap_x(v, axis):
    """Round to a pixel column, halves away from the die's vertical axis, so mirror-image corners
    land on mirror-image pixels wherever the die is."""
    o = round(v - axis, 6)
    return axis + int(math.copysign(math.floor(abs(o) + 0.5), o))

def polar(cx, cy, rad, deg):
    return (snap_x(cx + rad * math.cos(math.radians(deg)), cx), snap_y(cy + rad * math.sin(math.radians(deg))))

def ring(cx, cy, rad, n, start, angle):
    return [polar(cx, cy, rad, angle + start + 360 / n * k) for k in range(n)]

def xy(cx, cy, pts, angle, scale=R):
    """Points given in units of R, rotated by angle about (cx, cy)."""
    a = math.radians(angle); c, s = math.cos(a), math.sin(a)
    return [(snap_x(cx + scale * (x * c - y * s), cx), snap_y(cy + scale * (x * s + y * c))) for x, y in pts]

# Each shape returns (silhouette, inner lines, number centre offset in px). Inner lines are dim.
def d8(cx, cy, a):
    # face-on octahedron: hexagon silhouette, front face on alternate corners
    out = ring(cx, cy, R, 6, -90, a)
    tri = out[::2]
    return out, [tri + [tri[0]]], (0, 3)

def from_solid(shape):
    """A projected solid (unit radius) drawn at radius R, rotated by the die's angle."""
    poly, inner, num = shape
    def draw(cx, cy, a):
        out = xy(cx, cy, poly, a)
        lines = [xy(cx, cy, list(e), a) for e in inner]
        return out, lines, (num[0] * R, num[1] * R + 2)    # VGA digits sit high in their cell
    return draw

# Straight on, a d10 hides its lower faces and a d12 is a near-circle; a slight tilt reads as the real die.
# Straight onto a face, a d4 is just that face; the tumble shows it's a solid. Same for the d6.
_V, _F = d4_solid(); d4 = from_solid(view(_V, _F, 0, _F[0][0]))
# A d6 is a square face, upright: "up" is the middle of its top edge, not a corner.
_V, _F = d6_solid(); d6 = from_solid(view(_V, _F, 0, (0, 1, 1)))
_V, _F = d10_solid(); d10 = from_solid(view(_V, _F, 0, 0, tilt=-15))
_V, _F = d12_solid(); d12 = from_solid(view(_V, _F, 0, _F[0][0], tilt=-18))

def d20(cx, cy, a):
    out = ring(cx, cy, R, 6, -90, a)
    tri = ring(cx, cy, R * 0.618, 3, -90, a)
    lines = [tri + [tri[0]]] + [[t, out[j % 6]] for k, t in enumerate(tri) for j in (2 * k - 1, 2 * k, 2 * k + 1)]
    return out, lines, (0, 3)     # digits centred on the triangle (VGA ink sits 2.5 px above the cell centre)

DICE = {4: d4, 6: d6, 8: d8, 10: d10, 12: d12, 20: d20}

_ink = {}
def ink_centre(s):
    """Optical horizontal centre of the digits: halfway between their bounding box and their pixel mass.
    In a monospace font the cell centre isn't it (a VGA "1" leans left, a "3" right)."""
    if s not in _ink:
        im = Image.new("L", (8 * len(s) + 8, 20)); ImageDraw.Draw(im).text((0, 0), s, font=font, fill=255)
        x0, _, x1, _ = im.getbbox()
        lit = [x for x in range(im.width) for y in range(im.height) if im.getpixel((x, y))]
        _ink[s] = ((x0 + x1) / 2 + sum(lit) / len(lit) + 0.5) / 2
    return _ink[s]

def number(d, cx, cy, n):
    # the dice are symmetric about the middle of pixel column cx, i.e. x = cx + 0.5
    s = str(n); x, y = round(cx + 0.5 - ink_centre(s)), cy - 9
    for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        d.text((x + ox, y + oy), s, font=font, fill=PAPER)   # 1px black halo over the facet lines
    d.text((x, y), s, font=font, fill=INK)

def line(d, a, b, fill, axis):
    """1px line whose rounding ties break away from the die's vertical axis, so mirror-image lines
    rasterise to mirror-image pixels (Pillow's ties all break the same way)."""
    def rx(v):
        o = v - axis
        return round(axis + math.copysign(math.floor(abs(o) + 0.5), o))
    (x0, y0), (x1, y1) = a, b
    steps = max(abs(x1 - x0), abs(y1 - y0)) or 1
    for i in range(steps + 1):
        t = i / steps
        d.point((rx(x0 + (x1 - x0) * t), math.floor(y0 + (y1 - y0) * t + 0.5)), fill=fill)

def outline(d, pts):
    """The 2px silhouette. Pillow puts a 2px line's extra pixel on one side of its direction, so every
    outline is drawn the same way round: clockwise on screen, starting from the top corner."""
    pts = list(pts)
    if sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1])) < 0:
        pts.reverse()
    top = min(range(len(pts)), key=lambda i: (pts[i][1], pts[i][0]))
    pts = pts[top:] + pts[:top]
    d.line(pts + [pts[0]], fill=INK, width=2, joint="curve")

def frame(shape, angle, dy, n):
    img = Image.new("L", (SIZE, SIZE), PAPER); d = ImageDraw.Draw(img)
    cx, cy = SIZE // 2, SIZE // 2 + dy
    out, lines, (nx, ny) = shape(cx, cy, angle)
    for l in lines:
        for a, b in zip(l, l[1:]):
            line(d, a, b, DIM, cx)
    outline(d, out)
    if n is not None:
        r = math.radians(angle)
        number(d, round(cx + nx * math.cos(r) - ny * math.sin(r)), round(cy + nx * math.sin(r) + ny * math.cos(r)), n)
    return img

def timings(n):
    return [int(40 + 110 * (i / (n - 1)) ** 2.2) for i in range(n)]

def clip(sides, result):
    frames = []
    for i in range(N):
        t = i / (N - 1); landed = i >= N - 2
        angle = 0 if landed else (1 - (1 - (1 - t) ** 3)) * 300 * (1 if i % 2 else -0.6)
        dy = 0 if landed else -abs(math.sin(t * math.pi * 3)) * (1 - t) * 7
        n = result if landed else (random.randint(1, sides) if FLICKER else None)
        frames.append(frame(DICE[sides], angle, dy, n))
    return frames

if __name__ == "__main__":
    for FLICKER, OUT in ((True, HERE / "set"), (False, HERE / "set-blank")):
        random.seed(4)
        OUT.mkdir(exist_ok=True)
        all_clips = {}
        for sides in DICE:
            result = random.randint(1, sides)
            fr = clip(sides, result); all_clips[sides] = fr
            big = [f.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST) for f in fr]
            big[0].save(OUT / f"d{sides}.gif", save_all=True, append_images=big[1:],
                        duration=timings(N)[:-1] + [1400], loop=0)
        # contact sheet: each die's landed frame, at 4x
        sheet = Image.new("L", (6 * (SIZE + 4), SIZE), 40)
        for k, fr in enumerate(all_clips.values()):
            sheet.paste(fr[-1], (k * (SIZE + 4), 0))
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(OUT / "sheet.png")
    print("ok")
