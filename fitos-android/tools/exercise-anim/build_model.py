"""Builds the exercise figure: MakeHuman's CC0 base mesh shaped into a fit young man, with skin,
eyes, brows, lashes, hair, a T-shirt, shorts and trainers fitted to the body and skinned to the
default skeleton. Writes build/model.json and build/tex/.

Usage (from tools/exercise-anim): ./fetch_assets.sh && python3 build_model.py"""
import json, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MH = os.path.join(HERE, 'build/assets/makehuman/makehuman/data/')
AS = os.path.join(HERE, 'build/assets/system/')
OUT = os.path.join(HERE, 'build')
TEX = os.path.join(OUT, 'tex'); os.makedirs(TEX, exist_ok=True)


def load_obj(path):
    v, vt, faces, group = [], [], [], None
    for line in open(path, errors='ignore'):
        if line.startswith('v '): v.append([float(x) for x in line.split()[1:4]])
        elif line.startswith('vt '): vt.append([float(x) for x in line.split()[1:3]])
        elif line.startswith('g '): group = line.split()[1] if len(line.split()) > 1 else None
        elif line.startswith('f '):
            idx = []
            for t in line.split()[1:]:
                p = t.split('/'); idx.append((int(p[0]) - 1, int(p[1]) - 1 if len(p) > 1 and p[1] else -1))
            faces.append((group, idx))
    return np.array(v), np.array(vt) if vt else np.zeros((0, 2)), faces


def load_target(name):
    idx, d = [], []
    for line in open(MH + 'targets/' + name):
        if line.startswith('#') or not line.strip(): continue
        p = line.split(); idx.append(int(p[0])); d.append([float(x) for x in p[1:4]])
    return np.array(idx, dtype=int), np.array(d, dtype=float).reshape(-1, 3)


base_v, base_vt, base_f = load_obj(MH + '3dobjs/base.obj')
muscle = 0.85
targets = {
    'macrodetails/universal-male-young-maxmuscle-averageweight.target': muscle,
    'macrodetails/universal-male-young-averagemuscle-averageweight.target': 1 - muscle,
    'macrodetails/caucasian-male-young.target': 0.5,
    'macrodetails/african-male-young.target': 0.2,
    'macrodetails/asian-male-young.target': 0.3,
    'torso/torso-muscle-pectoral-incr.target': 0.4,
    'torso/torso-muscle-dorsi-incr.target': 0.4,
    'torso/torso-vshape-incr.target': 0.3,
}
for side in 'lr':
    for part, w in [('upperarm-muscle', 0.3), ('upperarm-shoulder-muscle', 0.3), ('lowerarm-muscle', 0.25), ('upperleg-muscle', 0.3), ('lowerleg-muscle', 0.25)]:
        targets[f'armslegs/{side}-{part}-incr.target'] = w
V = base_v.copy()
for name, w in targets.items():
    i, d = load_target(name); V[i] += w * d

# Skeleton
skel = json.load(open(MH + 'rigs/default.mhskel'))
joint = {k: V[v].mean(axis=0) for k, v in skel['joints'].items()}
order, seen = [], set()
def visit(n):
    if n in seen: return
    p = skel['bones'][n]['parent']
    if p: visit(p)
    seen.add(n); order.append(n)
for n in skel['bones']: visit(n)
bones = [{'name': n, 'parent': skel['bones'][n]['parent'], 'head': joint[skel['bones'][n]['head']].round(5).tolist(), 'tail': joint[skel['bones'][n]['tail']].round(5).tolist()} for n in order]
bix = {b['name']: i for i, b in enumerate(bones)}

# Per base vertex bone weights (all 19158, helpers included: proxies are fitted to them).
def weights_table(wfile, nverts):
    W = json.load(open(wfile))['weights']
    table = [dict() for _ in range(nverts)]
    for bn, lst in W.items():
        for v, w in lst: table[v][bix[bn]] = table[v].get(bix[bn], 0) + w
    return table
WB = weights_table(MH + 'rigs/default_weights.mhw', len(V))

def top4(d):
    lst = sorted(((w, b) for b, w in d.items()), reverse=True)[:4]; tot = sum(w for w, _ in lst) or 1
    lst += [(0, 0)] * (4 - len(lst))
    return [b for _, b in lst], [round(w / tot, 4) for w, _ in lst]


def mesh_out(pos, uv, faces, wts, keep_face=lambda f: True, extra=None):
    """Splits vertices on UV seams; faces are lists of (v, vt)."""
    key, P, UV, SI, SW, ORIG, IDX = {}, [], [], [], [], [], []
    for f in faces:
        if not keep_face(f): continue
        ids = []
        for v, t in f:
            k = (v, t)
            if k not in key:
                key[k] = len(P); P.append(pos[v]); UV.append(uv[t] if t >= 0 else (0, 0)); ORIG.append(v)
                si, sw = top4(wts[v]); SI += si; SW += sw
            ids.append(key[k])
        IDX += [ids[0], ids[1], ids[2]] + ([ids[0], ids[2], ids[3]] if len(ids) == 4 else [])
    out = {'positions': np.array(P).round(5).ravel().tolist(), 'uvs': np.array(UV).round(5).ravel().tolist(), 'indices': IDX, 'skinIndex': SI, 'skinWeight': SW, 'orig': ORIG}
    if extra: out.update(extra)
    return out


def parse_mhclo(path):
    scales, refs, delete, mode = {}, [], set(), None
    for line in open(path):
        s = line.strip()
        if not s or s.startswith('#'): continue
        p = s.split()
        if p[0] in ('x_scale', 'y_scale', 'z_scale'): scales[p[0][0]] = (int(p[1]), int(p[2]), float(p[3])); continue
        if p[0] == 'verts': mode = 'verts'; continue
        if p[0] == 'delete_verts': mode = 'delete'; continue
        if mode == 'verts' and (len(p) in (1, 9)) and p[0].lstrip('-').isdigit():
            refs.append([int(p[0])] if len(p) == 1 else [int(p[0]), int(p[1]), int(p[2]), float(p[3]), float(p[4]), float(p[5]), float(p[6]), float(p[7]), float(p[8])]); continue
        if mode == 'delete':
            i = 0
            while i < len(p):
                if i + 2 < len(p) and p[i + 1] == '-': delete.update(range(int(p[i]), int(p[i + 2]) + 1)); i += 3
                else: delete.add(int(p[i])); i += 1
            continue
        if mode == 'verts' and refs: mode = None
    return scales, refs, delete


def fit_proxy(folder, name, own_weights=None):
    scales, refs, delete = parse_mhclo(f'{folder}/{name}.mhclo')
    pv, pvt, pf = load_obj(f'{folder}/{name}.obj')
    sc = np.ones(3)
    for ax, k in (('x', 0), ('y', 1), ('z', 2)):
        if ax in scales: a, b, s = scales[ax]; sc[k] = abs(V[a][k] - V[b][k]) / s
    pos, wts = [], []
    for r in refs:
        if len(r) == 1: pos.append(V[r[0]]); wts.append(dict(WB[r[0]])); continue
        v1, v2, v3, w1, w2, w3, d1, d2, d3 = r
        pos.append(w1 * V[v1] + w2 * V[v2] + w3 * V[v3] + np.array([d1, d2, d3]) * sc)
        d = {}
        for v, w in ((v1, w1), (v2, w2), (v3, w3)):
            for b, bw in WB[v].items(): d[b] = d.get(b, 0) + w * bw
        wts.append(d)
    if own_weights:
        W = json.load(open(own_weights))['weights']; wts = [dict() for _ in pos]
        for bn, lst in W.items():
            for v, w in lst: wts[v][bix[bn]] = wts[v].get(bix[bn], 0) + w
    assert len(pos) == len(pv), (name, len(pos), len(pv))
    return np.array(pos), pvt, pf, wts, delete


def tex(src, name, size=None, rgb=None):
    im = Image.open(src)
    if size and im.size[0] > size: im = im.resize((size, size), Image.LANCZOS)
    if rgb: im = im.convert('RGB')
    im.save(os.path.join(TEX, name)); return 'tex/' + name


parts = {}
# Clothes: the casual suit's T-shirt and its jeans cut down to shorts.
knee_y, hip_y = joint['lowerleg01.L____head'][1], joint['upperleg01.L____head'][1]
cut_y = knee_y + 0.52 * (hip_y - knee_y)
cpos, cvt, cf, cw, cdel = fit_proxy(AS + 'clothes/male_casualsuit06', 'male_casualsuit06')
diff = Image.open(AS + 'clothes/male_casualsuit06/male_casualsuit06_diffuse.png').convert('RGB'); dw, dh = diff.size
def is_shirt(f):
    u = np.mean([cvt[t][0] for _, t in f]); v = np.mean([cvt[t][1] for _, t in f])
    r, g, b = diff.getpixel((min(dw - 1, int(u * dw)), min(dh - 1, int((1 - v) * dh))))
    return (r + g + b) / 3 > 150 and abs(r - b) < 40
# Vote per connected piece of cloth, so light patches on the jeans do not count as shirt.
faces = [f for _, f in cf]
parent = list(range(len(cpos)))
def find(x):
    while parent[x] != x: parent[x] = parent[parent[x]]; x = parent[x]
    return x
for f in faces:
    for v, _ in f[1:]: parent[find(v)] = find(f[0][0])
votes = {}
for f in faces: r = find(f[0][0]); votes.setdefault(r, [0, 0])[0 if is_shirt(f) else 1] += 1
shirt_piece = {r for r, (a, b) in votes.items() if a > b}
shirt_faces = [f for f in faces if find(f[0][0]) in shirt_piece]
# Keep faces across the hem; the shader cuts the hem itself on a smooth line.
short_faces = [f for f in faces if find(f[0][0]) not in shirt_piece and max(cpos[v][1] for v, _ in f) > cut_y - 0.05]
print('cloth pieces', len(votes), 'shirt pieces', len(shirt_piece))
parts['shirt'] = mesh_out(cpos, cvt, shirt_faces, cw, extra={'kind': 'shirt', 'normalMap': tex(AS + 'clothes/male_casualsuit06/male_casualsuit06_normal.png', 'suit_normal.png', 1024), 'aoMap': tex(AS + 'clothes/male_casualsuit06/male_casualsuit06_ao.png', 'suit_ao.png', 1024)})
parts['shorts'] = mesh_out(cpos, cvt, short_faces, cw, extra={'kind': 'shorts', 'normalMap': 'tex/suit_normal.png', 'aoMap': 'tex/suit_ao.png', 'cutY': float(cut_y)})
# Hide body skin under the shirt and shorts (but keep it below the shorts' hem).
covered = {v for v in cdel if V[v][1] > cut_y + 0.35}

spos, svt, sf, sw, sdel = fit_proxy(AS + 'clothes/shoes05', 'shoes05')
parts['shoes'] = mesh_out(spos, svt, [f for _, f in sf], sw, extra={'kind': 'shoes', 'map': tex(AS + 'clothes/shoes05/shoes05_diffuse.png', 'shoes.png', 1024)})
covered |= sdel

hpos, hvt, hf, hw, _ = fit_proxy(AS + 'hair/short02', 'short02')
parts['hair'] = mesh_out(hpos, hvt, [f for _, f in hf], hw, extra={'kind': 'hair', 'map': tex(AS + 'hair/short02/short02_diffuse.png', 'hair.png', 1024), 'normalMap': tex(AS + 'hair/short02/short02_normal.png', 'hair_normal.png', 1024)})
bpos, bvt, bf, bw, _ = fit_proxy(AS + 'eyebrows/eyebrow001', 'eyebrow001')
parts['brows'] = mesh_out(bpos, bvt, [f for _, f in bf], bw, extra={'kind': 'brows', 'map': tex(AS + 'eyebrows/eyebrow001/eyebrow001.png', 'brows.png')})
lpos, lvt, lf, lw, _ = fit_proxy(AS + 'eyelashes/eyelashes01', 'eyelashes01')
parts['lashes'] = mesh_out(lpos, lvt, [f for _, f in lf], lw, extra={'kind': 'lashes', 'map': tex(AS + 'eyelashes/eyelashes01/eyelashes01.png', 'lashes.png')})
epos, evt, ef, ew, _ = fit_proxy(AS + 'eyes/high-poly', 'high-poly')
parts['eyes'] = mesh_out(epos, evt, [f for _, f in ef], ew, extra={'kind': 'eyes', 'map': tex(AS + 'eyes/materials/brown_eye.png', 'eye.png', 512)})

skin = 'young_caucasian_male2/young_lightskinned_male_diffuse2.png'
body_faces = [f for g, f in base_f if g == 'body' and not all(v in covered for v, _ in f)]
parts['body'] = mesh_out(V, base_vt, body_faces, WB, extra={'kind': 'body', 'map': tex(AS + 'skins/' + skin, 'skin.jpg', 2048, rgb=True)})

json.dump({'bones': bones, 'parts': parts}, open(os.path.join(OUT, 'model.json'), 'w'))
for k, p in parts.items(): print(k, len(p['positions']) // 3, 'verts', len(p['indices']) // 3, 'tris')
