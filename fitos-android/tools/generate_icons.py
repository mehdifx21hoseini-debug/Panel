# Regenerates FitIcons.kt from the design-system icon sprite.
# Usage (from fitos-android/): python3 tools/generate_icons.py docs/design-system/_sprite.part.html core/designsystem/src/main/java/app/fitos/core/designsystem/icon/FitIcons.kt
import re, sys
src = open(sys.argv[1], encoding="utf-8").read()
out = sys.argv[2]
DIR = {"back", "chevron-right", "chevron-left"}

def num(v):
    f = float(v)
    return ("%g" % f)

def circle(cx, cy, r):
    cx, cy, r = float(cx), float(cy), float(r)
    return f"M{num(cx - r)} {num(cy)}a{num(r)} {num(r)} 0 1 0 {num(2*r)} 0a{num(r)} {num(r)} 0 1 0 {num(-2*r)} 0z"

def rect(x, y, w, h, rx):
    x, y, w, h, rx = map(float, (x, y, w, h, rx))
    if rx == 0:
        return f"M{num(x)} {num(y)}h{num(w)}v{num(h)}h{num(-w)}z"
    return (f"M{num(x+rx)} {num(y)}h{num(w-2*rx)}a{num(rx)} {num(rx)} 0 0 1 {num(rx)} {num(rx)}"
            f"v{num(h-2*rx)}a{num(rx)} {num(rx)} 0 0 1 {num(-rx)} {num(rx)}h{num(-(w-2*rx))}"
            f"a{num(rx)} {num(rx)} 0 0 1 {num(-rx)} {num(-rx)}v{num(-(h-2*rx))}a{num(rx)} {num(rx)} 0 0 1 {num(rx)} {num(-rx)}z")

def attrs(tag):
    return dict(re.findall(r'([a-z-]+)="([^"]*)"', tag))

def camel(name):
    parts = name.split("-")
    return parts[0].capitalize() + "".join(p.capitalize() for p in parts[1:])

icons = []
for m in re.finditer(r'<symbol id="i-([a-z-]+)" viewBox="0 0 24 24">(.*?)</symbol>', src):
    name, body = m.group(1), m.group(2)
    strokes, fills = [], []
    for el in re.finditer(r'<(path|circle|rect)\b([^>]*)/>', body):
        kind, a = el.group(1), attrs(el.group(2))
        if kind == "path": d = a["d"]
        elif kind == "circle": d = circle(a["cx"], a["cy"], a["r"])
        else: d = rect(a["x"], a["y"], a["width"], a["height"], a.get("rx", "0"))
        filled = a.get("fill") == "currentColor"
        stroked = a.get("stroke") != "none"
        if filled: fills.append(d)
        if stroked: strokes.append(d)
    icons.append((name, strokes, fills))

lines = [
    "// Generated from the design-system icon sprite (docs/design-system/_sprite.part.html) by tools/generate_icons.py. Edit the sprite, not this file.",
    "package app.fitos.core.designsystem.icon",
    "",
    "import androidx.compose.ui.graphics.vector.ImageVector",
    "",
    "/** Outlined 24dp UI icons (stroke 1.8, round caps). Emoji are content markers, never UI icons. */",
    "object FitIcons {",
]
for name, strokes, fills in icons:
    s = ", ".join('"%s"' % d for d in strokes)
    f = ", ".join('"%s"' % d for d in fills)
    args = [f'"{name}"']
    args.append(f"strokes = listOf({s})" if strokes else "strokes = emptyList()")
    if fills: args.append(f"fills = listOf({f})")
    if name in DIR: args.append("autoMirror = true")
    lines.append(f"    val {camel(name)}: ImageVector by lazy {{ fitIcon({', '.join(args)}) }}")
lines.append("}")
open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(len(icons), "icons")
