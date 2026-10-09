"""Real polyhedra, projected to 2D line drawings for the flat dice style."""
import math, itertools
import numpy as np

def _outward(V, F):
    c = V.mean(0)
    out = []
    for f in F:
        P = V[list(f)]
        n = np.cross(P[1] - P[0], P[2] - P[0])
        out.append(tuple(f) if n @ (P.mean(0) - c) > 0 else tuple(reversed(f)))
    return out

def d10_solid(z0=0.105):
    """Pentagonal trapezohedron. Kites are planar only when the apex height h = z0(1+cos36)/(1-cos36)."""
    c = math.cos(math.radians(36)); h = z0 * (1 + c) / (1 - c)
    U = [(math.cos(math.radians(72 * k)), math.sin(math.radians(72 * k)), z0) for k in range(5)]
    L = [(math.cos(math.radians(72 * k + 36)), math.sin(math.radians(72 * k + 36)), -z0) for k in range(5)]
    V = np.array([(0, 0, h)] + U + L + [(0, 0, -h)], float)       # 0 = top, 1..5 = U, 6..10 = L, 11 = bottom
    F = [(0, 1 + k, 6 + k, 1 + (k + 1) % 5) for k in range(5)] + [(11, 6 + k, 1 + (k + 1) % 5, 6 + (k + 1) % 5) for k in range(5)]
    return V, _outward(V, F)

def d12_solid():
    p = (1 + 5 ** .5) / 2
    V = np.array([v for v in itertools.product([-1, 1], repeat=3)] + [(0, s / p, t * p) for s in (-1, 1) for t in (-1, 1)]
                 + [(s / p, t * p, 0) for s in (-1, 1) for t in (-1, 1)] + [(s * p, 0, t / p) for s in (-1, 1) for t in (-1, 1)], float)
    # faces: the 5 vertices nearest each of the 12 face directions
    dirs = [(0, s * p, t) for s in (-1, 1) for t in (-1, 1)] + [(s, 0, t * p) for s in (-1, 1) for t in (-1, 1)] + [(s * p, t, 0) for s in (-1, 1) for t in (-1, 1)]
    F = []
    for d in dirs:
        d = np.array(d, float); d /= np.linalg.norm(d)
        idx = list(np.argsort(-(V @ d))[:5])
        cen = V[idx].mean(0); a = V[idx[0]] - cen; b = np.cross(d, a)
        idx.sort(key=lambda i: math.atan2((V[i] - cen) @ b, (V[i] - cen) @ a))
        F.append(tuple(idx))
    return V, _outward(V, F)

def view(V, F, front, up_vertex, tilt=0.0):
    """Look straight at face `front` with `up_vertex` at the top, optionally tilted (degrees) toward the top.
    Returns 2D (silhouette polygon, inner edges, number centre), y down, scaled so the silhouette radius is 1."""
    P = V[list(F[front])]
    n = np.cross(P[1] - P[0], P[2] - P[0]); n /= np.linalg.norm(n)
    cen = P.mean(0)
    up = (V[up_vertex] if np.ndim(up_vertex) == 0 else np.asarray(up_vertex, float)) - cen; up -= (up @ n) * n; up /= np.linalg.norm(up)
    t = math.radians(tilt)
    n, up = n * math.cos(t) + up * math.sin(t), up * math.cos(t) - n * math.sin(t)
    right = np.cross(up, n)
    to2 = lambda v: np.array([v @ right, -(v @ up)])
    vis = [f for f in F if np.cross(V[f[1]] - V[f[0]], V[f[2]] - V[f[0]]) @ n > 1e-9]
    edges = {}
    for f in vis:
        for a, b in zip(f, f[1:] + f[:1]):
            k = (min(a, b), max(a, b)); edges[k] = edges.get(k, 0) + 1
    # silhouette edges belong to one visible face; inner edges to two
    sil = [k for k, c in edges.items() if c == 1]
    inner = [k for k, c in edges.items() if c == 2]
    # chain the silhouette into a polygon
    adj = {}
    for a, b in sil:
        adj.setdefault(a, []).append(b); adj.setdefault(b, []).append(a)
    poly = [sil[0][0]]; prev = None
    while True:
        nxt = [x for x in adj[poly[-1]] if x != prev][0]
        if nxt == poly[0]: break
        prev = poly[-1]; poly.append(nxt)
    pts = {i: to2(V[i]) for i in set(itertools.chain(*edges))}
    origin = np.mean([pts[i] for i in poly], 0)
    scale = max(np.linalg.norm(pts[i] - origin) for i in poly)
    norm = lambda i: tuple((pts[i] - origin) / scale)
    num = tuple((to2(cen) - origin) / scale)
    return [norm(i) for i in poly], [(norm(a), norm(b)) for a, b in inner], num

def d8_solid():
    V = np.array([(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)], float)
    F = [(a, b, c) for a in (0, 1) for b in (2, 3) for c in (4, 5)]
    return V, _outward(V, F)

def d20_solid():
    p = (1 + 5 ** 0.5) / 2
    V = np.array([(-1, p, 0), (1, p, 0), (-1, -p, 0), (1, -p, 0), (0, -1, p), (0, 1, p),
                  (0, -1, -p), (0, 1, -p), (p, 0, -1), (p, 0, 1), (-p, 0, -1), (-p, 0, 1)], float)
    F = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6), (7, 1, 8),
         (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1)]
    return V, _outward(V, F)

def landing(V, F, front, up_vertex, tilt=0.0):
    """The camera that shows face `front` upright (see view()): rows right, up, toward-viewer."""
    P = V[list(F[front])]
    n = np.cross(P[1] - P[0], P[2] - P[0]); n /= np.linalg.norm(n)
    cen = P.mean(0)
    up = (V[up_vertex] if np.ndim(up_vertex) == 0 else np.asarray(up_vertex, float)) - cen; up -= (up @ n) * n; up /= np.linalg.norm(up)
    t = math.radians(tilt)
    n, up = n * math.cos(t) + up * math.sin(t), up * math.cos(t) - n * math.sin(t)
    return np.array([np.cross(up, n), up, n])

def rot(axis, ang):
    x, y, z = np.asarray(axis, float) / np.linalg.norm(axis); c, s = math.cos(ang), math.sin(ang)
    return np.array([[c + x*x*(1-c), x*y*(1-c) - z*s, x*z*(1-c) + y*s],
                     [y*x*(1-c) + z*s, c + y*y*(1-c), y*z*(1-c) - x*s],
                     [z*x*(1-c) - y*s, z*y*(1-c) + x*s, c + z*z*(1-c)]])

def project(V, F, M):
    """Visible edges of a convex solid under camera M, in camera units (x right, y down).
    Returns (silhouette polygon, inner edges)."""
    C = V @ M.T
    vis = [f for f in F if np.cross(C[f[1]] - C[f[0]], C[f[2]] - C[f[0]])[2] > 1e-9]
    edges = {}
    for f in vis:
        for a, b in zip(f, f[1:] + f[:1]):
            k = (min(a, b), max(a, b)); edges[k] = edges.get(k, 0) + 1
    sil = [k for k, c in edges.items() if c == 1]
    adj = {}
    for a, b in sil:
        adj.setdefault(a, []).append(b); adj.setdefault(b, []).append(a)
    poly = [sil[0][0]]; prev = None
    while True:
        nxt = [x for x in adj[poly[-1]] if x != prev][0]
        if nxt == poly[0]: break
        prev = poly[-1]; poly.append(nxt)
    to2 = lambda i: (C[i][0], -C[i][1])
    return [to2(i) for i in poly], [(to2(a), to2(b)) for (a, b), c in edges.items() if c == 2]

def d4_solid():
    V = np.array([(1, 1, 1), (1, -1, -1), (-1, 1, -1), (-1, -1, 1)], float)
    F = [(0, 1, 2), (0, 1, 3), (0, 2, 3), (1, 2, 3)]
    return V, _outward(V, F)

def d6_solid():
    V = np.array([(x, y, z) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)], float)
    # face 0 is +z, the landing face
    F = [(1, 3, 7, 5), (0, 4, 6, 2), (0, 1, 5, 4), (2, 6, 7, 3), (0, 2, 3, 1), (4, 5, 7, 6)]
    return V, _outward(V, F)
