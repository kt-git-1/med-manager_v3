#!/usr/bin/env python3
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "marketing" / "app-store-screenshots" / "iphone-6.9"
CONTACT_SHEET = ROOT / "marketing" / "app-store-screenshots" / "iphone-6.9-contact-sheet.png"
RAW_SCREENSHOT_DIR = ROOT / "marketing" / "app-store-screenshots" / "raw-real" / "iphone-6.9"
ICON_PATH = ROOT / "ios" / "MedicationApp" / "Assets.xcassets" / "AppIcon.appiconset" / "med_1024_transparent.png"
ROLE_PATIENT_PATH = ROOT / "ios" / "MedicationApp" / "Assets.xcassets" / "RolePatient.imageset" / "role-patient.png"
ROLE_FAMILY_PATH = ROOT / "ios" / "MedicationApp" / "Assets.xcassets" / "RoleFamily.imageset" / "role-family.png"

DESIGN_W, DESIGN_H = 1320, 2868
W, H = 1242, 2688

TEAL = (0, 140, 128)
TEAL_DARK = (0, 110, 102)
BLUE = (31, 122, 209)
ORANGE = (240, 107, 0)
RED = (209, 41, 41)
INK = (31, 41, 55)
MUTED = (91, 105, 122)
BG = (242, 249, 250)
CARD = (255, 255, 255)
LINE = (219, 232, 235)
SOFT = (230, 245, 244)


def font(size: int, weight: str = "regular") -> ImageFont.FreeTypeFont:
    candidates = {
        "regular": [
            "/System/Library/Fonts/ヒラギノ角ゴシック W4.ttc",
            "/System/Library/Fonts/Hiragino Sans GB.ttc",
            "/System/Library/Fonts/SFNS.ttf",
        ],
        "bold": [
            "/System/Library/Fonts/ヒラギノ角ゴシック W7.ttc",
            "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc",
            "/System/Library/Fonts/SFNS.ttf",
        ],
        "rounded": [
            "/System/Library/Fonts/SFNSRounded.ttf",
            "/System/Library/Fonts/ヒラギノ丸ゴ ProN W4.ttc",
            "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc",
        ],
    }[weight]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


F = {
    "brand": font(34, "bold"),
    "eyebrow": font(32, "bold"),
    "hero": font(82, "bold"),
    "sub": font(36, "regular"),
    "screen_title": font(52, "bold"),
    "screen_sub": font(30, "regular"),
    "h1": font(44, "bold"),
    "h2": font(34, "bold"),
    "body": font(27, "regular"),
    "body_b": font(27, "bold"),
    "small": font(22, "regular"),
    "small_b": font(22, "bold"),
    "metric": font(62, "bold"),
    "time": font(84, "rounded"),
}


@dataclass(frozen=True)
class Slide:
    filename: str
    accent: tuple[int, int, int]
    eyebrow: str
    title: str
    subtitle: str
    screen: str


SLIDES = [
    Slide(
        "01_modes_overview.png",
        TEAL,
        "2つのモード",
        "家族が管理し\n本人が記録",
        "家族モードで薬を登録し、本人モードで今日のお薬を確認できます。",
        "modes",
    ),
    Slide(
        "02_one_tap_record.png",
        TEAL,
        "服薬記録",
        "この時間のお薬を\nまとめて記録",
        "朝・昼・夜などの時間帯ごとに、飲めた薬をすばやく残せます。",
        "record",
    ),
    Slide(
        "03_medication_list.png",
        BLUE,
        "薬の管理",
        "定時薬も頓服も\n整理して管理",
        "薬名、飲む量、服用タイミング、残数を一覧で確認できます。",
        "meds",
    ),
    Slide(
        "04_inventory.png",
        ORANGE,
        "在庫管理",
        "残り日数で\n補充に気づく",
        "服薬記録に合わせて在庫を減らし、少なくなった薬を確認しやすくします。",
        "inventory",
    ),
    Slide(
        "05_history.png",
        TEAL,
        "履歴",
        "記録状況を\n家族で共有",
        "飲めた日、未記録、飲み忘れをカレンダーと日別履歴で確認できます。",
        "history",
    ),
    Slide(
        "06_patient_mode.png",
        TEAL,
        "本人モード",
        "本人は大きな文字で\nかんたん記録",
        "今日飲むお薬だけを分かりやすく表示し、迷わず操作できます。",
        "patient",
    ),
    Slide(
        "07_family_notification.png",
        ORANGE,
        "家族へ通知",
        "本人が記録すると\n家族にお知らせ",
        "離れていても、服薬できたことを家族の端末で確認できます。",
        "notification",
    ),
]

RAW_SCREENSHOTS = {
    "modes": "01_modes.png",
    "record": "02_record.png",
    "meds": "03_medications.png",
    "inventory": "04_inventory.png",
    "history": "05_history.png",
    "patient": "06_patient.png",
    "notification": "07_notification.png",
}


def rounded(draw: ImageDraw.ImageDraw, box, r, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def shadowed_panel(img: Image.Image, box, r=34, fill=CARD, shadow=(0, 0, 0, 36), blur=22, offset=12, outline=None):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((box[0], box[1] + offset, box[2], box[3] + offset), radius=r, fill=shadow)
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline or LINE, width=2)


def text_width(text: str, fnt) -> int:
    return int(fnt.getlength(text))


def wrap_text(text: str, fnt, max_width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        current = ""
        for char in paragraph:
            if text_width(current + char, fnt) <= max_width:
                current += char
            else:
                if current:
                    lines.append(current)
                current = char
        lines.append(current)
    return lines


def draw_text(draw: ImageDraw.ImageDraw, xy, text: str, fnt, fill=INK, max_width=None, line_gap=10, anchor=None):
    x, y = xy
    if max_width is None:
        draw.text((x, y), text, font=fnt, fill=fill, anchor=anchor)
        return y + draw.textbbox((x, y), text, font=fnt, anchor=anchor)[3]
    for line in wrap_text(text, fnt, max_width):
        draw.text((x, y), line, font=fnt, fill=fill)
        y += fnt.size + line_gap
    return y


def blend(color, alpha: float, bg=(255, 255, 255)):
    return tuple(int(bg[i] * (1 - alpha) + color[i] * alpha) for i in range(3))


def pill(draw, box, text, color, selected=False):
    fill = color if selected else blend(color, 0.12)
    text_color = (255, 255, 255) if selected else color
    rounded(draw, box, 24, fill)
    draw.text(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2 - 1), text, font=F["small_b"], fill=text_color, anchor="mm")


def metric_card(draw, box, title, value, color):
    rounded(draw, box, 26, CARD, LINE, 2)
    cx = box[0] + 48
    cy = box[1] + 52
    draw.ellipse((cx - 20, cy - 20, cx + 20, cy + 20), fill=blend(color, 0.14))
    draw.text((box[0] + 86, box[1] + 30), title, font=F["small_b"], fill=MUTED)
    draw.text((box[0] + 86, box[1] + 66), value, font=F["metric"], fill=color)


def row(draw, box, title, detail, color=TEAL, right=None, danger=False):
    rounded(draw, box, 22, (249, 252, 252), (232, 239, 240), 1)
    cy = (box[1] + box[3]) / 2
    draw.ellipse((box[0] + 22, cy - 18, box[0] + 58, cy + 18), fill=blend(color, 0.16))
    draw.ellipse((box[0] + 35, cy - 5, box[0] + 45, cy + 5), fill=color)
    draw.text((box[0] + 80, box[1] + 18), title, font=F["body_b"], fill=RED if danger else INK)
    draw.text((box[0] + 80, box[1] + 58), detail, font=F["small"], fill=MUTED)
    if right:
        draw.text((box[2] - 28, (box[1] + box[3]) / 2), right, font=F["small_b"], fill=color, anchor="rm")


def phone_frame(img: Image.Image):
    x, y, w, h = 54, 724, 1212, 2100
    shadowed_panel(img, (x, y, x + w, y + h), r=88, fill=(22, 35, 42), shadow=(0, 55, 80, 45), blur=36, offset=22, outline=(22, 35, 42))
    inner = (x + 28, y + 28, x + w - 28, y + h - 28)
    d = ImageDraw.Draw(img)
    rounded(d, inner, 64, BG)
    d.rounded_rectangle((x + 486, y + 44, x + 726, y + 72), radius=18, fill=(22, 35, 42))
    return inner


def place_real_screenshot(img: Image.Image, screen: str):
    source_path = RAW_SCREENSHOT_DIR / RAW_SCREENSHOTS[screen]
    if not source_path.exists():
        raise FileNotFoundError(f"Real simulator screenshot is missing: {source_path}")

    source = Image.open(source_path).convert("RGB")
    target_h = 2100
    target_w = round(source.width * target_h / source.height)
    source = source.resize((target_w, target_h), Image.LANCZOS).convert("RGBA")
    x = (DESIGN_W - target_w) // 2
    y = 724
    radius = 82

    shadowed_panel(
        img,
        (x - 14, y - 14, x + target_w + 14, y + target_h + 14),
        r=radius + 14,
        fill=(20, 30, 35),
        shadow=(0, 38, 58, 48),
        blur=34,
        offset=20,
        outline=(20, 30, 35),
    )
    mask = Image.new("L", (target_w, target_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, target_w, target_h), radius=radius, fill=255)
    img.paste(source, (x, y), mask)


def screen_header(draw, box, title, subtitle, accent=TEAL):
    x1, y1, x2, _ = box
    draw.text((x1 + 46, y1 + 48), title, font=F["screen_title"], fill=INK)
    draw.text((x1 + 46, y1 + 112), subtitle, font=F["screen_sub"], fill=MUTED)
    draw.ellipse((x2 - 108, y1 + 48, x2 - 54, y1 + 102), fill=blend(accent, 0.15))
    draw.ellipse((x2 - 92, y1 + 64, x2 - 70, y1 + 86), fill=accent)


def nav_bar(draw, box, selected):
    x1, y1, x2, y2 = box
    rounded(draw, (x1 + 28, y2 - 128, x2 - 28, y2 - 28), 36, CARD, LINE, 2)
    items = [("薬", "meds"), ("今日", "today"), ("履歴", "history"), ("在庫", "inventory"), ("設定", "settings")]
    gap = (x2 - x1 - 80) / len(items)
    for i, (label, key) in enumerate(items):
        cx = x1 + 40 + gap * i + gap / 2
        color = TEAL if key == selected else (132, 145, 160)
        draw.ellipse((cx - 13, y2 - 105, cx + 13, y2 - 79), fill=color)
        draw.text((cx, y2 - 58), label, font=F["small_b"], fill=color, anchor="mm")


def screen_modes(draw, box):
    x1, y1, x2, y2 = box
    draw.ellipse((x1 + 48, y1 + 58, x1 + 106, y1 + 116), fill=blend(TEAL, 0.12))
    draw.text((x1 + 77, y1 + 86), "+", font=F["h2"], fill=TEAL, anchor="mm")
    draw.text((x1 + 124, y1 + 58), "お薬見守り", font=F["screen_sub"], fill=MUTED)
    draw_text(draw, (x1 + 46, y1 + 136), "どちらで\n使いますか？", F["screen_title"], fill=INK, max_width=x2 - x1 - 92, line_gap=10)
    mode_select_card(
        draw,
        (x1 + 46, y1 + 380, x2 - 46, y1 + 720),
        ROLE_PATIENT_PATH,
        "本人モード",
        "本人として使う",
        "今日のお薬を確認します",
        "このモードで始める",
        TEAL,
    )
    mode_select_card(
        draw,
        (x1 + 46, y1 + 760, x2 - 46, y1 + 1100),
        ROLE_FAMILY_PATH,
        "家族モード",
        "家族として使う",
        "薬と在庫を管理します",
        "このモードで始める",
        ORANGE,
    )


def mode_select_card(draw, box, image_path, detail, title, subtitle, start_text, color):
    rounded(draw, box, 38, CARD, blend(color, 0.24), 2)
    art_box = (box[0] + 34, box[1] + 38, box[0] + 236, box[1] + 240)
    rounded(draw, art_box, 34, blend(color, 0.10), blend(color, 0.18), 2)
    if image_path.exists():
        role = Image.open(image_path).convert("RGBA")
        role.thumbnail((188, 188), Image.LANCZOS)
        draw._image.alpha_composite(role, (int((art_box[0] + art_box[2] - role.width) / 2), int((art_box[1] + art_box[3] - role.height) / 2)))
    pill(draw, (box[0] + 270, box[1] + 42, box[0] + 450, box[1] + 102), detail, color)
    draw.text((box[0] + 270, box[1] + 128), title, font=F["h1"], fill=INK)
    draw.text((box[0] + 270, box[1] + 194), subtitle, font=F["body"], fill=MUTED)
    draw.text((box[0] + 34, box[3] - 58), start_text, font=F["body_b"], fill=color)
    draw.ellipse((box[2] - 92, box[3] - 92, box[2] - 34, box[3] - 34), fill=color)
    draw.text((box[2] - 63, box[3] - 63), "→", font=F["body_b"], fill=(255, 255, 255), anchor="mm")


def screen_today(draw, box):
    x1, y1, x2, y2 = box
    screen_header(draw, box, "田中 花子さん", "今日の予定", TEAL)
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 170, x2 - 46, y1 + 690), ORANGE)
    draw.text((x1 + 86, y1 + 210), "次に記録する時間", font=F["h2"], fill=INK)
    draw.ellipse((x1 + 86, y1 + 285, x1 + 202, y1 + 401), fill=blend(TEAL, 0.14))
    draw.text((x1 + 144, y1 + 344), "12:30", font=F["small_b"], fill=TEAL_DARK, anchor="mm")
    draw.text((x1 + 235, y1 + 288), "昼のお薬", font=F["time"], fill=TEAL_DARK)
    pill(draw, (x1 + 235, y1 + 388, x1 + 345, y1 + 438), "未記録", ORANGE)
    draw.text((x1 + 366, y1 + 398), "未記録2件をまとめて記録", font=F["small_b"], fill=MUTED)
    row(draw, (x1 + 86, y1 + 462, x2 - 86, y1 + 556), "血圧の薬 5 mg", "1回1錠", TEAL, "未")
    row(draw, (x1 + 86, y1 + 570, x2 - 86, y1 + 664), "胃薬", "1回1錠", BLUE, "未")
    metric_card(draw, (x1 + 46, y1 + 694, x1 + 456, y1 + 856), "今日の進み具合", "2/3", TEAL)
    metric_card(draw, (x1 + 482, y1 + 694, x2 - 46, y1 + 856), "次の予定", "昼", ORANGE)
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 870, x2 - 46, y1 + 1160), TEAL)
    draw.text((x1 + 86, y1 + 914), "今日の流れ", font=F["h2"], fill=INK)
    for i, (t, c, s) in enumerate([("朝 08:00", TEAL, "記録済み"), ("昼 12:30", ORANGE, "未記録"), ("夜 20:00", BLUE, "予定")]):
        yy = y1 + 982 + i * 55
        draw.ellipse((x1 + 90, yy, x1 + 116, yy + 26), fill=c)
        draw.text((x1 + 140, yy - 5), t, font=F["body_b"], fill=INK)
        draw.text((x2 - 90, yy - 4), s, font=F["small_b"], fill=c, anchor="ra")
    nav_bar(draw, box, "today")


def shadowed_panel_on_draw(draw, box, accent=None):
    rounded(draw, box, 30, CARD, LINE, 2)
    if accent:
        draw.rounded_rectangle((box[0], box[1] + 18, box[0] + 8, box[3] - 18), radius=4, fill=accent)


def screen_record(draw, box):
    x1, y1, x2, y2 = box
    screen_header(draw, box, "昼のお薬", "12:30 / 2種類・合計2錠", TEAL)
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 172, x2 - 46, y1 + 780), TEAL)
    draw.text((x1 + 86, y1 + 222), "この時間のお薬を飲んだ", font=F["h1"], fill=INK)
    row(draw, (x1 + 86, y1 + 318, x2 - 86, y1 + 432), "血圧の薬 5 mg", "1回1錠", TEAL, "未記録")
    row(draw, (x1 + 86, y1 + 454, x2 - 86, y1 + 568), "胃薬", "1回1錠", BLUE, "未記録")
    rounded(draw, (x1 + 86, y1 + 600, x2 - 86, y1 + 704), 26, TEAL)
    draw.text(((x1 + x2) / 2, y1 + 652), "飲んだ", font=F["h1"], fill=(255, 255, 255), anchor="mm")
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 830, x2 - 46, y1 + 1138), BLUE)
    draw.text((x1 + 86, y1 + 872), "記録後は家族にも反映", font=F["h2"], fill=INK)
    row(draw, (x1 + 86, y1 + 948, x2 - 86, y1 + 1048), "昼 12:30", "血圧の薬・胃薬", TEAL, "記録済み")
    draw.text((x1 + 86, y1 + 1072), "家族画面の履歴にも同じ記録が残ります。", font=F["body"], fill=MUTED)
    nav_bar(draw, box, "today")


def screen_meds(draw, box):
    x1, y1, x2, y2 = box
    screen_header(draw, box, "薬を管理", "定時薬・頓服・期間終了", BLUE)
    metric_card(draw, (x1 + 46, y1 + 170, x1 + 338, y1 + 318), "使用中", "3", TEAL)
    metric_card(draw, (x1 + 362, y1 + 170, x1 + 654, y1 + 318), "定時", "2", BLUE)
    metric_card(draw, (x1 + 678, y1 + 170, x2 - 46, y1 + 318), "頓服", "1", ORANGE)
    pill(draw, (x1 + 46, y1 + 360, x1 + 174, y1 + 418), "すべて", TEAL, True)
    pill(draw, (x1 + 190, y1 + 360, x1 + 302, y1 + 418), "定時", BLUE)
    pill(draw, (x1 + 318, y1 + 360, x1 + 430, y1 + 418), "頓服", ORANGE)
    draw.text((x1 + 46, y1 + 468), "定時", font=F["h2"], fill=INK)
    med_row(draw, (x1 + 46, y1 + 520, x2 - 46, y1 + 680), "血圧の薬 5 mg", "毎日 朝・昼", "1回1錠", "残り18錠", BLUE)
    med_row(draw, (x1 + 46, y1 + 704, x2 - 46, y1 + 864), "整腸剤 50 mg", "毎日 夜", "1回1錠", "残り10錠", TEAL)
    draw.text((x1 + 46, y1 + 914), "頓服", font=F["h2"], fill=INK)
    med_row(draw, (x1 + 46, y1 + 966, x2 - 46, y1 + 1126), "頭痛薬", "必要な時", "1回1錠", "", ORANGE)
    nav_bar(draw, box, "meds")


def med_row(draw, box, name, timing, dose, inventory, color):
    rounded(draw, box, 28, CARD, LINE, 2)
    draw.ellipse((box[0] + 28, box[1] + 36, box[0] + 90, box[1] + 98), fill=blend(color, 0.14))
    draw.rectangle((box[0] + 55, box[1] + 50, box[0] + 64, box[1] + 84), fill=color)
    draw.rectangle((box[0] + 42, box[1] + 63, box[0] + 76, box[1] + 72), fill=color)
    draw.text((box[0] + 118, box[1] + 30), name, font=F["body_b"], fill=INK)
    draw.text((box[0] + 118, box[1] + 70), timing, font=F["small"], fill=MUTED)
    draw.text((box[0] + 118, box[1] + 102), dose, font=F["small_b"], fill=color)
    if inventory:
        pill(draw, (box[2] - 150, box[1] + 54, box[2] - 28, box[1] + 108), inventory, color)


def screen_inventory(draw, box):
    x1, y1, x2, y2 = box
    screen_header(draw, box, "在庫を確認", "残数と補充目安", ORANGE)
    metric_card(draw, (x1 + 46, y1 + 170, x1 + 456, y1 + 320), "要確認", "1", ORANGE)
    metric_card(draw, (x1 + 482, y1 + 170, x2 - 46, y1 + 320), "管理中", "2", BLUE)
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 368, x2 - 46, y1 + 548), ORANGE)
    draw.text((x1 + 86, y1 + 410), "補充が必要な薬があります", font=F["h2"], fill=INK)
    draw.text((x1 + 86, y1 + 462), "血圧の薬 5 mg が残り少なくなっています。", font=F["body"], fill=MUTED)
    pill(draw, (x1 + 46, y1 + 596, x1 + 174, y1 + 654), "すべて", TEAL, True)
    pill(draw, (x1 + 190, y1 + 596, x1 + 354, y1 + 654), "低在庫のみ", ORANGE)
    inventory_row(draw, (x1 + 46, y1 + 702, x2 - 46, y1 + 922), "血圧の薬 5 mg", "4", "錠", "あと2日分", ORANGE, True)
    inventory_row(draw, (x1 + 46, y1 + 952, x2 - 46, y1 + 1172), "整腸剤 50 mg", "10", "錠", "あと5日分", TEAL, False)
    nav_bar(draw, box, "inventory")


def inventory_row(draw, box, name, qty, unit, days, color, attention):
    rounded(draw, box, 30, CARD, blend(color, 0.45) if attention else LINE, 3 if attention else 2)
    draw.text((box[0] + 34, box[1] + 32), name, font=F["body_b"], fill=INK)
    draw.text((box[0] + 34, box[1] + 74), "服薬記録に合わせて自動で減ります。", font=F["small"], fill=MUTED)
    draw.text((box[0] + 34, box[1] + 132), days, font=F["body_b"], fill=color)
    draw.text((box[2] - 122, box[1] + 64), qty, font=F["metric"], fill=color, anchor="mm")
    draw.text((box[2] - 62, box[1] + 72), unit, font=F["body_b"], fill=color, anchor="lm")


def screen_history(draw, box):
    x1, y1, x2, y2 = box
    screen_header(draw, box, "服薬履歴", "6月の記録状況", TEAL)
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 168, x2 - 46, y1 + 760), TEAL)
    draw.text((x1 + 86, y1 + 212), "2026年6月", font=F["h1"], fill=INK)
    week = ["月", "火", "水", "木", "金", "土", "日"]
    for i, day in enumerate(week):
        draw.text((x1 + 104 + i * 122, y1 + 296), day, font=F["small_b"], fill=MUTED, anchor="mm")
    statuses = [TEAL, TEAL, TEAL, RED, TEAL, (220, 226, 230), TEAL, ORANGE, TEAL, TEAL, TEAL, (220, 226, 230), TEAL, TEAL, TEAL, TEAL, ORANGE, TEAL, TEAL, TEAL, TEAL]
    for i in range(21):
        col, row_i = i % 7, i // 7
        cx, cy = x1 + 104 + col * 122, y1 + 372 + row_i * 104
        color = statuses[i]
        draw.ellipse((cx - 34, cy - 34, cx + 34, cy + 34), fill=blend(color, 0.17))
        draw.ellipse((cx - 13, cy - 13, cx + 13, cy + 13), fill=color)
        draw.text((cx, cy + 54), str(i + 1), font=F["small"], fill=MUTED, anchor="mm")
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 810, x2 - 46, y1 + 1168), BLUE)
    draw.text((x1 + 86, y1 + 850), "6月11日（木）", font=F["h2"], fill=INK)
    row(draw, (x1 + 86, y1 + 924, x2 - 86, y1 + 1024), "朝のお薬", "08:00", TEAL, "記録済み")
    row(draw, (x1 + 86, y1 + 1044, x2 - 86, y1 + 1144), "昼のお薬", "12:30", ORANGE, "未記録")
    nav_bar(draw, box, "history")


def screen_patient(draw, box):
    x1, y1, x2, y2 = box
    screen_header(draw, box, "今日のお薬", "7月6日（月）", TEAL)
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 172, x2 - 46, y1 + 760), TEAL)
    draw.text((x1 + 86, y1 + 220), "次に飲むお薬", font=F["h1"], fill=INK)
    draw.ellipse((x1 + 86, y1 + 310, x1 + 220, y1 + 444), fill=blend(TEAL, 0.14))
    draw.text((x1 + 153, y1 + 377), "昼", font=F["h1"], fill=TEAL_DARK, anchor="mm")
    draw.text((x1 + 260, y1 + 320), "12:30", font=F["time"], fill=TEAL_DARK)
    draw.text((x1 + 86, y1 + 490), "2種類 / 合計2錠", font=F["body_b"], fill=MUTED)
    row(draw, (x1 + 86, y1 + 550, x2 - 86, y1 + 648), "血圧の薬 5 mg", "1回1錠", TEAL)
    rounded(draw, (x1 + 86, y1 + 664, x2 - 86, y1 + 736), 28, TEAL)
    draw.text(((x1 + x2) / 2, y1 + 700), "この時間のお薬を飲んだ", font=F["h2"], fill=(255, 255, 255), anchor="mm")
    shadowed_panel_on_draw(draw, (x1 + 46, y1 + 812, x2 - 46, y1 + 1062), ORANGE)
    draw.text((x1 + 86, y1 + 858), "必要な時のお薬", font=F["h2"], fill=INK)
    draw.text((x1 + 86, y1 + 906), "頓服も本人画面から記録できます。", font=F["body"], fill=MUTED)
    rounded(draw, (x1 + 86, y1 + 974, x2 - 86, y1 + 1032), 22, blend(ORANGE, 0.12))
    draw.text((x1 + 116, y1 + 1002), "頭痛薬  1回1錠", font=F["body_b"], fill=ORANGE, anchor="lm")
    patient_nav(draw, box, "today")


def patient_nav(draw, box, selected):
    x1, y1, x2, y2 = box
    rounded(draw, (x1 + 80, y2 - 128, x2 - 80, y2 - 28), 36, CARD, LINE, 2)
    items = [("今日", "today"), ("履歴", "history"), ("通知", "settings")]
    gap = (x2 - x1 - 180) / len(items)
    for i, (label, key) in enumerate(items):
        cx = x1 + 90 + gap * i + gap / 2
        color = TEAL if key == selected else (132, 145, 160)
        draw.ellipse((cx - 15, y2 - 105, cx + 15, y2 - 75), fill=color)
        draw.text((cx, y2 - 52), label, font=F["body_b"], fill=color, anchor="mm")


def draw_brand(draw, img):
    if ICON_PATH.exists():
        icon = Image.open(ICON_PATH).convert("RGBA")
        icon.thumbnail((82, 82), Image.LANCZOS)
        img.alpha_composite(icon, (72, 72))
    draw.text((172, 92), "お薬見守り", font=F["brand"], fill=INK)


def draw_slide(slide: Slide) -> Image.Image:
    img = Image.new("RGBA", (DESIGN_W, DESIGN_H), BG + (255,))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, DESIGN_W, DESIGN_H), fill=BG)
    draw.ellipse((-260, -210, 520, 520), fill=blend(slide.accent, 0.10, BG))
    draw.ellipse((930, 200, 1560, 830), fill=blend(BLUE, 0.08, BG))
    draw_brand(draw, img)
    draw.text((72, 218), slide.eyebrow, font=F["eyebrow"], fill=slide.accent)
    hero_bottom = draw_text(draw, (72, 284), slide.title, F["hero"], fill=INK, max_width=1140, line_gap=20)
    draw_text(draw, (72, hero_bottom + 34), slide.subtitle, F["sub"], fill=MUTED, max_width=1160, line_gap=14)
    place_real_screenshot(img, slide.screen)
    return img.convert("RGB").resize((W, H), Image.LANCZOS)


def make_contact_sheet(images: Sequence[Path]):
    thumb_w, thumb_h = 330, 717
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
    for slide in SLIDES:
        img = draw_slide(slide)
        path = OUT_DIR / slide.filename
        img.save(path, optimize=True)
        paths.append(path)
    make_contact_sheet(paths)
    metadata = {
        "target": "App Store iPhone screenshots",
        "device_family": "iPhone 6.9-inch compatible",
        "size_px": [W, H],
        "format": "PNG",
        "ui_source": "real light-mode iPhone simulator screenshots with DEBUG sample data",
        "raw_screenshot_directory": str(RAW_SCREENSHOT_DIR.relative_to(ROOT)),
        "count": len(paths),
        "files": [str(path.relative_to(ROOT)) for path in paths],
        "contact_sheet": str(CONTACT_SHEET.relative_to(ROOT)),
    }
    (OUT_DIR / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
