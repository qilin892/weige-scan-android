# -*- coding: utf-8 -*-
"""伟哥扫描王 launcher icon generator.

Concept: a white document sheet (folded top-right corner) hugged by four white
scan-frame brackets, with an abstract amber "W" light beam struck across the
sheet. Amber gradient plate, deep enough to read on both light/dark launchers.

Run:  C:/Python314/python.exe tools/gen_icon.py
Outputs: icon-src/icon-1024.png, icon-src/play-store-512.png,
         app/src/main/res/mipmap-*/ic_launcher.png,
         app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png
"""
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(ROOT, "icon-src")
os.makedirs(SRC, exist_ok=True)
os.makedirs(os.path.join(RES, "drawable-xxxhdpi"), exist_ok=True)

BASE = 1024          # logical canvas
S = 4                # supersample factor
N = BASE * S

C_TOP = (255, 158, 66)
C_BOT = (222, 84, 10)
INK = (255, 106, 0)      # beam amber
PAGE = (255, 255, 255)
FOLD = (238, 224, 209)
RULE = (232, 230, 226)
FRAME = (255, 255, 255)


def sc(*vals):
    return [v * S for v in vals]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def plate(size, radius_ratio=0.2197):
    """Rounded-square amber gradient plate, same pixel size as `size`."""
    w = size
    grad = Image.new("RGB", (1, w))
    px = grad.load()
    for y in range(w):
        px[0, y] = lerp(C_TOP, C_BOT, y / max(1, w - 1))
    grad = grad.resize((w, w), Image.BICUBIC)

    # top-left radial highlight for a bit of depth
    hl = Image.new("L", (w, w), 0)
    d = ImageDraw.Draw(hl)
    d.ellipse((-int(w * 0.25), -int(w * 0.55), int(w * 0.85), int(w * 0.55)), fill=90)
    hl = hl.filter(ImageFilter.GaussianBlur(w // 8))
    glow = Image.new("RGB", (w, w), (255, 236, 210))
    grad = Image.composite(glow, grad, hl.point(lambda v: v // 2))

    mask = Image.new("L", (w, w), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, w - 1), radius=int(w * radius_ratio), fill=255)
    out = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out


def mark(size):
    """Transparent foreground composition (page + brackets + W beam)."""
    w = size
    k = w / BASE
    img = Image.new("RGBA", (w, w), (0, 0, 0, 0))

    def P(v):
        return v * k

    # ---- scan brackets (behind the page) ----
    bw = P(38)                     # stroke
    arm = P(104)                   # arm length
    offs = P(196)                  # distance from canvas edge
    br = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    bd = ImageDraw.Draw(br)
    corners = [(offs, offs, 1, 1), (w - offs, offs, -1, 1),
               (offs, w - offs, 1, -1), (w - offs, w - offs, -1, -1)]
    for cx, cy, sx, sy in corners:
        hx = cx + arm * sx
        vy = cy + arm * sy
        box_h = (min(cx, hx) - bw / 2, cy - bw / 2, max(cx, hx) + bw / 2, cy + bw / 2)
        box_v = (cx - bw / 2, min(cy, vy) - bw / 2, cx + bw / 2, max(cy, vy) + bw / 2)
        bd.rounded_rectangle(box_h, radius=bw / 2, fill=FRAME + (255,))
        bd.rounded_rectangle(box_v, radius=bw / 2, fill=FRAME + (255,))
    br.putalpha(br.split()[3].point(lambda v: int(v * 0.92)))
    img.alpha_composite(br)

    # ---- page shadow ----
    x0, y0, x1, y1 = P(300), P(258), P(724), P(766)
    r = P(38)
    fold = P(112)
    sh = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    sd.rounded_rectangle((x0, y0 + P(10), x1, y1 + P(18)), radius=r, fill=(60, 26, 0, 120))
    sh = sh.filter(ImageFilter.GaussianBlur(P(26)))
    img.alpha_composite(sh)

    # ---- page mask with folded corner ----
    mask = Image.new("L", (w, w), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((x0, y0, x1, y1), radius=r, fill=255)
    md.polygon([(x1 - fold - r * 0.2, y0 - P(2)), (x1 + P(2), y0 - P(2)),
                (x1 + P(2), y0 + fold + r * 0.2)], fill=0)
    page = Image.new("RGBA", (w, w), PAGE + (255,))
    pd = ImageDraw.Draw(page)
    # fold flap
    pd.polygon([(x1 - fold, y0), (x1, y0 + fold), (x1 - fold, y0 + fold)], fill=FOLD + (255,))
    # subtle document rules above the beam
    for yy, ln in ((P(352), P(196)), (P(404), P(150))):
        pd.rounded_rectangle((P(372), yy, P(372) + ln, yy + P(15)), radius=P(7), fill=RULE + (255,))
    img.paste(page, (0, 0), mask)

    # ---- abstract W beam ----
    beam = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    bm = ImageDraw.Draw(beam)
    stroke = P(56)
    pts = [(P(366), P(566)), (P(448), P(690)), (P(512), P(536)),
           (P(576), P(690)), (P(658), P(566))]
    bm.line(pts, fill=INK + (255,), width=int(stroke), joint="curve")
    for px, py in (pts[0], pts[-1]):
        bm.ellipse((px - stroke / 2, py - stroke / 2, px + stroke / 2, py + stroke / 2),
                   fill=INK + (255,))
    # keep the beam inside the sheet
    inside = Image.new("L", (w, w), 0)
    ImageDraw.Draw(inside).rounded_rectangle((x0, y0, x1, y1), radius=r, fill=255)
    inside = Image.composite(inside, Image.new("L", (w, w), 0), mask)
    beam.putalpha(Image.composite(beam.split()[3], Image.new("L", (w, w), 0), inside))
    img.alpha_composite(beam)
    return img


def emit(size, pad_ratio=0.0):
    """Full icon (plate + mark) at `size` px. pad_ratio shrinks the mark."""
    plate_img = plate(size)
    inner = int(size * (1 - pad_ratio * 2))
    m = mark(inner)
    if pad_ratio:
        off = (size - inner) // 2
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.alpha_composite(m, (off, off))
        m = canvas
    out = plate_img.copy()
    out.alpha_composite(m)
    return out


def save(img, path):
    img.save(path, "PNG", optimize=True)
    print("%-70s %s" % (path, img.size))


# --- master + store ---
master = emit(BASE)
save(master, os.path.join(SRC, "icon-1024.png"))
save(master.resize((512, 512), Image.LANCZOS), os.path.join(SRC, "play-store-512.png"))

# --- legacy mipmaps ---
for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    d = os.path.join(RES, "mipmap-" + dpi)
    os.makedirs(d, exist_ok=True)
    icon = emit(px * 4).resize((px, px), Image.LANCZOS)
    save(icon, os.path.join(d, "ic_launcher.png"))
    # round variant: circular mask
    rmask = Image.new("L", (px * 4, px * 4), 0)
    ImageDraw.Draw(rmask).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
    rnd = emit(px * 4)
    rnd.putalpha(rmask.resize((px * 4, px * 4)))
    save(rnd.resize((px, px), Image.LANCZOS), os.path.join(d, "ic_launcher_round.png"))

# --- adaptive foreground: 108dp canvas, art inside the 72dp safe zone ---
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
art = mark(432 * 3).resize((int(432 * 0.74), int(432 * 0.74)), Image.LANCZOS)
fg.alpha_composite(art, ((432 - art.width) // 2, (432 - art.height) // 2))
save(fg, os.path.join(RES, "drawable-xxxhdpi", "ic_launcher_foreground.png"))

# --- 应用内品牌图（关键）---
# ⚠️ 界面里**不能**直接 painterResource(mipmap.ic_launcher_round)：
#    API 26+ 会解析到 mipmap-anydpi-v26 的 <adaptive-icon> XML，
#    Compose 的 painterResource 只认 vector / 位图，解 adaptive-icon 会直接抛异常
#    ——症状就是「安装后点开即闪退」。这里单独出一份普通 PNG 给界面用。
for dpi, px in (("mdpi", 72), ("hdpi", 108), ("xhdpi", 144), ("xxhdpi", 216), ("xxxhdpi", 288)):
    d = os.path.join(RES, "drawable-" + dpi)
    os.makedirs(d, exist_ok=True)
    save(emit(px * 3).resize((px, px), Image.LANCZOS), os.path.join(d, "brand_mark.png"))

