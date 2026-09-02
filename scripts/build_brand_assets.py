#!/usr/bin/env python3
"""Regenerate every app icon from the one source logo.

Source of truth:
  Logo/Logo.png                      the master artwork (square, ~1500px)
  brand/rakshak-logo.svg             vector trace of the full lockup (with text)
  brand/rakshak-icon.svg             vector trace, artwork only (no text)

The SVGs are produced once with vtracer (see the bottom of this file); this
script only rasterises the PNG / ICO icons the web portal and Android app load,
so the whole set stays in sync with a single `python scripts/build_brand_assets.py`.

    pip install pillow vtracer
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent
MASTER = REPO / "Logo/Logo.png"
WEB = REPO / "nextgen-rakshak-webportal"
RES = REPO / "nextgen-rakshak-mobile/app/src/main/res"

# Light card colour sampled from the master artwork's background.
CARD = (244, 247, 251)


def load_master() -> Image.Image:
    im = Image.open(MASTER).convert("RGBA")
    bg = Image.new("RGBA", im.size, CARD + (255,))
    return Image.alpha_composite(bg, im).convert("RGB")


def artwork_only(full: Image.Image) -> Image.Image:
    """Crop away the wordmark and outer margin, pad back to a centred square."""
    w, h = full.size
    crop = full.crop((int(w * 0.11), int(h * 0.05), int(w * 0.89), int(h * 0.76)))
    side = max(crop.size)
    sq = Image.new("RGB", (side, side), CARD)
    sq.paste(crop, ((side - crop.width) // 2, (side - crop.height) // 2))
    return sq


def padded(square: Image.Image, ratio: float, bg=CARD) -> Image.Image:
    """Shrink `square` to `ratio` of the frame, centred on `bg` — for maskable /
    adaptive icons whose edges get clipped."""
    side = square.width
    inner = square.resize((round(side * ratio),) * 2, Image.LANCZOS)
    out = Image.new("RGB", (side, side), bg)
    out.paste(inner, ((side - inner.width) // 2,) * 2)
    return out


def ensure_viewbox(svg_path: Path) -> None:
    """vtracer emits width/height but no viewBox; without it the SVG will not
    scale inside a CSS-sized <img>. Idempotent."""
    s = svg_path.read_text(encoding="utf8")
    if "viewBox" in s:
        return
    import re
    m = re.search(r'width="(\d+)"\s+height="(\d+)"', s)
    if m:
        w, h = m.group(1), m.group(2)
        s = s.replace("<svg ", f'<svg viewBox="0 0 {w} {h}" ', 1)
        svg_path.write_text(s, encoding="utf8")


def save(img: Image.Image, rel: str, size: int | None = None) -> None:
    p = REPO / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    (img if size is None else img.resize((size, size), Image.LANCZOS)).save(p)
    print(f"  {rel}")


def main() -> None:
    full = load_master()
    icon = artwork_only(full)          # square, artwork, light bg
    maskable = padded(icon, 0.78)      # safe-zone version

    print("web:")
    # Next.js App Router auto-detects these in src/app/.
    (WEB / "src/app").mkdir(parents=True, exist_ok=True)
    for name in ("rakshak-icon.svg", "rakshak-logo.svg"):
        ensure_viewbox(REPO / "brand" / name)
    (WEB / "src/app/icon.svg").write_bytes((REPO / "brand/rakshak-icon.svg").read_bytes())
    print("  nextgen-rakshak-webportal/src/app/icon.svg")
    (WEB / "public/brand").mkdir(parents=True, exist_ok=True)
    for name in ("rakshak-icon.svg", "rakshak-logo.svg"):
        (WEB / "public/brand" / name).write_bytes((REPO / "brand" / name).read_bytes())
        print(f"  nextgen-rakshak-webportal/public/brand/{name}")
    save(maskable, "nextgen-rakshak-webportal/src/app/apple-icon.png", 180)

    save(icon, "nextgen-rakshak-webportal/public/icon.png", 192)   # FCM service worker
    for s in (192, 512):
        save(icon, f"nextgen-rakshak-webportal/public/icons/icon-{s}.png", s)
        save(maskable, f"nextgen-rakshak-webportal/public/icons/maskable-{s}.png", s)
    save(maskable, "nextgen-rakshak-webportal/public/apple-touch-icon.png", 180)

    ico = REPO / "nextgen-rakshak-webportal/public/favicon.ico"
    icon.save(ico, sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])
    print(f"  {ico.relative_to(REPO)}")

    print("android:")
    launcher = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, s in launcher.items():
        save(maskable, f"nextgen-rakshak-mobile/app/src/main/res/mipmap-{d}/ic_launcher.png", s)
        save(maskable, f"nextgen-rakshak-mobile/app/src/main/res/mipmap-{d}/ic_launcher_round.png", s)

    # Adaptive-icon foreground: 108dp canvas, artwork in the 66dp safe zone,
    # transparent margin so the launcher mask can trim the edges.
    fg = artwork_transparent_foreground()
    fgdp = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for d, s in fgdp.items():
        save(fg, f"nextgen-rakshak-mobile/app/src/main/res/drawable-{d}/ic_launcher_foreground.png", s)

    # In-app logo shown on the mobile login screen (density-independent).
    logo = trim_to_content(full)
    save(logo, "nextgen-rakshak-mobile/app/src/main/res/drawable-nodpi/rakshak_logo.png", 480)

    save(icon, "brand/play-store-icon.png", 512)
    print("\ndone.  (adaptive XML + ic_stat_rakshak.xml are hand-authored — see git)")


def trim_to_content(full: Image.Image) -> Image.Image:
    """Full lockup (artwork + wordmark) trimmed of the outer card margin,
    padded back to a centred square on a transparent field."""
    w, h = full.size
    crop = full.crop((int(w * 0.08), int(h * 0.03), int(w * 0.92), int(h * 0.97))).convert("RGBA")
    px = crop.load()
    for y in range(crop.height):
        for x in range(crop.width):
            r, g, b, _ = px[x, y]
            if abs(r - CARD[0]) < 12 and abs(g - CARD[1]) < 12 and abs(b - CARD[2]) < 12:
                px[x, y] = (r, g, b, 0)
    side = max(crop.size)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(crop, ((side - crop.width) // 2, (side - crop.height) // 2), crop)
    return out


def artwork_transparent_foreground() -> Image.Image:
    """Artwork centred in a 1x transparent square at ~61% (adaptive safe zone)."""
    full = load_master()
    art = artwork_only(full)
    side = art.width
    inner = art.resize((round(side * 0.61),) * 2, Image.LANCZOS).convert("RGBA")
    # knock out the near-uniform card background to alpha
    px = inner.load()
    for y in range(inner.height):
        for x in range(inner.width):
            r, g, b, _ = px[x, y]
            if abs(r - CARD[0]) < 14 and abs(g - CARD[1]) < 14 and abs(b - CARD[2]) < 14:
                px[x, y] = (r, g, b, 0)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(inner, ((side - inner.width) // 2,) * 2, inner)
    return out


if __name__ == "__main__":
    main()

# --- how the SVGs in brand/ were produced (run once, not part of main) ---
#   import vtracer
#   opts = dict(colormode="color", hierarchical="stacked", mode="spline",
#               filter_speckle=6, color_precision=7, layer_difference=12,
#               corner_threshold=55, length_threshold=4.0, splice_threshold=45,
#               path_precision=3)
#   vtracer.convert_image_to_svg_py("<flattened full>",  "brand/rakshak-logo.svg", **opts)
#   vtracer.convert_image_to_svg_py("<flattened crop>",  "brand/rakshak-icon.svg", **opts)
#   then: npx svgo --multipass brand/rakshak-logo.svg brand/rakshak-icon.svg
