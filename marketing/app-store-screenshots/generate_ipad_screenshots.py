#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
from typing import Sequence

from PIL import Image, ImageDraw, ImageFilter

import generate_iphone_screenshots as base


ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "marketing" / "app-store-screenshots" / "ipad-13"
CONTACT_SHEET = ROOT / "marketing" / "app-store-screenshots" / "ipad-13-contact-sheet.png"
RAW_SCREENSHOT_DIR = ROOT / "marketing" / "app-store-screenshots" / "raw-real" / "ipad-13"

W, H = 2064, 2752


def place_real_screenshot(img: Image.Image, screen: str):
    source_path = RAW_SCREENSHOT_DIR / base.RAW_SCREENSHOTS[screen]
    if not source_path.exists():
        raise FileNotFoundError(f"Real iPad simulator screenshot is missing: {source_path}")

    source = Image.open(source_path).convert("RGB")
    target_h = 1908
    target_w = round(source.width * target_h / source.height)
    source = source.resize((target_w, target_h), Image.LANCZOS).convert("RGBA")
    x = (W - target_w) // 2
    y = 760
    radius = 52

    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow = ImageDraw.Draw(layer)
    shadow.rounded_rectangle(
        (x - 18, y + 8, x + target_w + 18, y + target_h + 44),
        radius=radius + 18,
        fill=(0, 55, 80, 44),
    )
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(38)))

    frame = ImageDraw.Draw(img)
    frame.rounded_rectangle(
        (x - 18, y - 18, x + target_w + 18, y + target_h + 18),
        radius=radius + 18,
        fill=(22, 35, 42),
    )
    mask = Image.new("L", (target_w, target_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, target_w, target_h), radius=radius, fill=255)
    img.paste(source, (x, y), mask)


def draw_ipad_frame(img: Image.Image):
    x, y, w, h = 112, 760, 1840, 1908
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow = ImageDraw.Draw(layer)
    shadow.rounded_rectangle((x, y + 24, x + w, y + h + 24), radius=82, fill=(0, 55, 80, 42))
    layer = layer.filter(ImageFilter.GaussianBlur(38))
    img.alpha_composite(layer)

    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((x, y, x + w, y + h), radius=82, fill=(22, 35, 42), outline=(22, 35, 42), width=2)
    inner = (x + 42, y + 42, x + w - 42, y + h - 42)
    draw.rounded_rectangle(inner, radius=52, fill=base.BG)
    cx = x + w // 2
    draw.ellipse((cx - 12, y + 22, cx + 12, y + 46), fill=(52, 67, 75))
    return inner


def draw_brand(draw: ImageDraw.ImageDraw, img: Image.Image):
    if base.ICON_PATH.exists():
        icon = Image.open(base.ICON_PATH).convert("RGBA")
        icon.thumbnail((96, 96), Image.LANCZOS)
        img.alpha_composite(icon, (112, 86))
    draw.text((230, 112), "お薬見守り", font=base.F["brand"], fill=base.INK)


def draw_slide(slide: base.Slide) -> Image.Image:
    img = Image.new("RGBA", (W, H), base.BG + (255,))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, W, H), fill=base.BG)
    draw.ellipse((-340, -260, 780, 760), fill=base.blend(slide.accent, 0.10, base.BG))
    draw.ellipse((1320, 250, 2360, 1290), fill=base.blend(base.BLUE, 0.08, base.BG))

    draw_brand(draw, img)
    draw.text((112, 248), slide.eyebrow, font=base.F["eyebrow"], fill=slide.accent)
    hero_bottom = base.draw_text(
        draw,
        (112, 322),
        slide.title,
        base.F["hero"],
        fill=base.INK,
        max_width=1500,
        line_gap=20,
    )
    base.draw_text(
        draw,
        (112, hero_bottom + 34),
        slide.subtitle,
        base.F["sub"],
        fill=base.MUTED,
        max_width=1660,
        line_gap=14,
    )

    place_real_screenshot(img, slide.screen)
    return img.convert("RGB")


def make_contact_sheet(images: Sequence[Path]):
    thumb_w, thumb_h = 344, 459
    rows = (len(images) + 2) // 3
    sheet = Image.new("RGB", (thumb_w * 3, thumb_h * rows), (246, 249, 250))
    for i, path in enumerate(images):
        im = Image.open(path).convert("RGB")
        im.thumbnail((thumb_w, thumb_h), Image.LANCZOS)
        x = (i % 3) * thumb_w + (thumb_w - im.width) // 2
        y = (i // 3) * thumb_h + (thumb_h - im.height) // 2
        sheet.paste(im, (x, y))
    sheet.save(CONTACT_SHEET)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for old_png in OUT_DIR.glob("*.png"):
        old_png.unlink()

    paths: list[Path] = []
    for slide in base.SLIDES:
        img = draw_slide(slide)
        path = OUT_DIR / slide.filename
        img.save(path, optimize=True)
        paths.append(path)

    make_contact_sheet(paths)
    metadata = {
        "target": "App Store iPad screenshots",
        "device_family": "iPad 13-inch compatible",
        "size_px": [W, H],
        "format": "PNG",
        "ui_source": "real light-mode iPad simulator screenshots with DEBUG sample data",
        "raw_screenshot_directory": str(RAW_SCREENSHOT_DIR.relative_to(ROOT)),
        "count": len(paths),
        "files": [str(path.relative_to(ROOT)) for path in paths],
        "contact_sheet": str(CONTACT_SHEET.relative_to(ROOT)),
    }
    (OUT_DIR / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
