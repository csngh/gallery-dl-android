"""Regenerates the brand assets in this folder from the artwork constants below.

Requires: pip install pillow fonttools
Usage:    python generate_brand_assets.py /path/to/inter_variable.ttf

The artwork matches app/src/main/res/drawable/ic_launcher_*.xml (108 x 108 viewport). Text in the lockup SVG is
converted to outlines, so the SVG does not depend on any installed font.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

HERE = Path(__file__).resolve().parent
FONT_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parents[1] / "app/src/main/res/font/inter_variable.ttf"

BG_FROM, BG_TO = (0x4A, 0x2B, 0xD1), (0x0E, 0x8F, 0x9A)
NAVY, TEAL, INK, MOUNTAIN, TEXT = "#0B1020", "#2EE6C9", "#0B1020", "#2B2F77", "#E3E6F3"
SHIFT = (-3.5, -2.0)  # the foreground group is centred inside the adaptive-icon safe zone

# ------------------------------------------------------------------ SVG master
MASTER_BODY = f"""  <g transform="translate({SHIFT[0]} {SHIFT[1]})">
    <rect x="38" y="27" width="40" height="40" rx="5" fill="#fff" fill-opacity=".45"/>
    <rect x="32" y="34" width="40" height="40" rx="5" fill="#fff"/>
    <path d="M35 71V67.5L43 57L49.5 64.5L56 55L69 68.5V71z" fill="{MOUNTAIN}"/>
    <circle cx="63" cy="39.5" r="3.5" fill="#0E8F9A"/>
    <circle cx="71" cy="73" r="12.5" fill="{NAVY}"/>
    <circle cx="71" cy="73" r="10.5" fill="{TEAL}"/>
    <rect x="69.6" y="66.5" width="2.8" height="7" fill="{INK}"/>
    <path d="M64.8 72.8H77.2L71 79.5z" fill="{INK}"/>
  </g>"""
GRADIENT = """<linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="108" y2="108">
      <stop offset="0" stop-color="#4A2BD1"/><stop offset="1" stop-color="#0E8F9A"/>
    </linearGradient>"""


def master_svg() -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <defs>
    {GRADIENT}
  </defs>
  <rect width="108" height="108" fill="url(#bg)"/>
{MASTER_BODY}
</svg>
"""


# ------------------------------------------------------------------ raster drawing
def render_mark(size: int, zoom: float = 1.0, corner: float = 0.0, ss: int = 4) -> Image.Image:
    """The mark as an RGBA image. zoom > 1 crops in; corner is the corner radius as a fraction of size."""
    n = size * ss
    # Diagonal gradient: the average of a horizontal and a vertical ramp equals (x + y) / 2.
    ramp = Image.linear_gradient("L").resize((n, n))
    mask = ImageChops.add(ramp.rotate(90), ramp, scale=2)
    img = Image.composite(Image.new("RGB", (n, n), BG_TO), Image.new("RGB", (n, n), BG_FROM), mask).convert("RGBA")

    scale = n / (108 / zoom)
    off = (108 - 108 / zoom) / 2

    def p(x, y):
        return ((x + SHIFT[0] - off) * scale, (y + SHIFT[1] - off) * scale)

    def box(x, y, w, h):
        return [*p(x, y), *p(x + w, y + h)]

    layer = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle(box(38, 27, 40, 40), 5 * scale, fill=(255, 255, 255, round(255 * 0.45)))
    img = Image.alpha_composite(img, layer)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box(32, 34, 40, 40), 5 * scale, fill="white")
    d.polygon([p(*q) for q in [(35, 71), (35, 67.5), (43, 57), (49.5, 64.5), (56, 55), (69, 68.5), (69, 71)]], fill=MOUNTAIN)

    def disc(cx, cy, r, fill):
        d.ellipse([*p(cx - r, cy - r), *p(cx + r, cy + r)], fill=fill)

    disc(63, 39.5, 3.5, "#0E8F9A")
    disc(71, 73, 12.5, NAVY)
    disc(71, 73, 10.5, TEAL)
    d.rectangle(box(69.6, 66.5, 2.8, 7), fill=INK)
    d.polygon([p(64.8, 72.8), p(77.2, 72.8), p(71, 79.5)], fill=INK)

    img = img.resize((size, size), Image.LANCZOS)
    if corner:
        m = Image.new("L", (size * ss, size * ss), 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, size * ss - 1, size * ss - 1], corner * size * ss, fill=255)
        img.putalpha(m.resize((size, size), Image.LANCZOS))
    return img


# ------------------------------------------------------------------ lockup
LOCKUP_W, LOCKUP_H, MARK = 1440, 360, 240
TEXT_SIZE, LABEL = 118, "Gallery DL Android"
WGHT, OPSZ = 700, 32


def lockup_png() -> Image.Image:
    canvas = Image.new("RGBA", (LOCKUP_W, LOCKUP_H), NAVY)
    canvas.alpha_composite(render_mark(MARK, corner=0.22), (60, (LOCKUP_H - MARK) // 2))
    font = ImageFont.truetype(str(FONT_PATH), TEXT_SIZE)
    font.set_variation_by_axes([OPSZ, WGHT])  # axis order in Inter: opsz, wght
    ImageDraw.Draw(canvas).text((60 + MARK + 48, LOCKUP_H // 2 + 6), LABEL, font=font, fill=TEXT, anchor="lm")
    return canvas


def lockup_svg() -> str:
    font = instancer.instantiateVariableFont(TTFont(str(FONT_PATH)), {"wght": WGHT, "opsz": OPSZ})
    glyphs, cmap, hmtx = font.getGlyphSet(), font.getBestCmap(), font["hmtx"]
    scale = TEXT_SIZE / font["head"].unitsPerEm
    ascent, descent = font["hhea"].ascent, font["hhea"].descent
    baseline = LOCKUP_H // 2 + 6 + (ascent + descent) / 2 * scale   # centred like PIL's "lm" anchor
    x = 60 + MARK + 48
    paths = []
    for ch in LABEL:
        name = cmap[ord(ch)]
        pen = SVGPathPen(glyphs)
        glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
        if pen.getCommands():
            paths.append(pen.getCommands())
        x += hmtx[name][0] * scale
    mark_scale = MARK / 108
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {LOCKUP_W} {LOCKUP_H}" width="{LOCKUP_W}" height="{LOCKUP_H}">
  <defs>
    {GRADIENT}
    <clipPath id="tile"><rect width="108" height="108" rx="{0.22 * 108:.2f}"/></clipPath>
  </defs>
  <rect width="{LOCKUP_W}" height="{LOCKUP_H}" fill="{NAVY}"/>
  <g transform="translate(60 {(LOCKUP_H - MARK) // 2}) scale({mark_scale:.4f})" clip-path="url(#tile)">
    <rect width="108" height="108" fill="url(#bg)"/>
{MASTER_BODY}
  </g>
  <path fill="{TEXT}" d="{' '.join(paths)}"/>
</svg>
"""


if __name__ == "__main__":
    (HERE / "logo-master.svg").write_text(master_svg())
    render_mark(512, zoom=1.2).convert("RGB").save(HERE / "store-icon-512.png")  # full bleed; the store applies its own mask
    (HERE / "logo-lockup.svg").write_text(lockup_svg())
    lockup_png().convert("RGB").save(HERE / "logo-lockup.png")
    print("written:", *sorted(p.name for p in HERE.iterdir() if p.suffix in {".svg", ".png"}))
