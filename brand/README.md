# Brand assets

Everything here derives from **one source**: `../Logo/Logo.png`.

| File | What | Used by |
|------|------|---------|
| `../Logo/Logo.png` | master artwork (raster, ~1500px) | the source of truth — edit/replace this |
| `rakshak-logo.svg` | vector trace of the full lockup (artwork + wordmark) | README, docs, mobile login screen |
| `rakshak-icon.svg` | vector trace, artwork only (no text) | web favicon (`/icon.svg`), web sidebar mark |
| `play-store-icon.png` | 512×512 store listing icon | Play Console upload |

## Regenerating the icons

All the raster icons in both apps are rebuilt from the source by one script:

```bash
pip install pillow
python scripts/build_brand_assets.py
```

It writes:

**Web** (`nextgen-rakshak-webportal/`)
- `src/app/icon.svg`, `src/app/apple-icon.png` — Next.js file-convention favicons
- `public/favicon.ico` (16/32/48/64), `public/apple-touch-icon.png`
- `public/icon.png` — consumed by `public/firebase-messaging-sw.js`
- `public/icons/icon-{192,512}.png` + `maskable-{192,512}.png` — PWA (`public/manifest.webmanifest`)
- `public/brand/rakshak-{icon,logo}.svg` — served to `src/components/brand-logo.tsx`

**Mobile** (`nextgen-rakshak-mobile/app/src/main/res/`)
- `mipmap-*/ic_launcher.png` + `ic_launcher_round.png` — legacy launcher icons
- `drawable-*/ic_launcher_foreground.png` — adaptive-icon foreground (transparent margin)
- `drawable-nodpi/rakshak_logo.png` — in-app logo on the login screen

Hand-authored, kept in sync by eye (they can't be raster):
- `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` — adaptive icon (bg = `@color/ic_launcher_background`, fg = the density PNGs)
- `res/drawable/ic_stat_rakshak.xml` — status-bar notification glyph (system tints it flat, so it's a plain shield+child silhouette, not the colour logo)

## Regenerating the SVG traces (rarely needed)

Only if `Logo/Logo.png` itself changes:

```bash
pip install vtracer
# flatten alpha on the card colour, crop the wordmark off for the icon variant,
# then:
python - <<'PY'
import vtracer
o = dict(colormode="color", hierarchical="stacked", mode="spline",
         filter_speckle=6, color_precision=7, layer_difference=12,
         corner_threshold=55, length_threshold=4.0, splice_threshold=45, path_precision=3)
vtracer.convert_image_to_svg_py("full_flat.png", "brand/rakshak-logo.svg", **o)
vtracer.convert_image_to_svg_py("crop_flat.png", "brand/rakshak-icon.svg", **o)
PY
npx svgo --multipass brand/rakshak-logo.svg brand/rakshak-icon.svg
python scripts/build_brand_assets.py   # adds viewBox + fans out every icon
```

## Palette

| | hex |
|---|---|
| Primary blue | `#1E56D6` |
| Deep navy | `#0E2A66` |
| Sky | `#5AB0EA` |
| Amber (heart) | `#F5A623` |
| Card / light bg | `#F4F7FB` |
