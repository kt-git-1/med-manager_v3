#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "output" / "pdf"
OUT_PATH = OUT_DIR / "okusuri-mimamori-app-review-guide.pdf"

W, H = 1654, 2339  # A4 at roughly 200 dpi
MARGIN_X = 142
MARGIN_TOP = 126
MARGIN_BOTTOM = 116

APP_NAME = "お薬見守り"
BRAND = (0, 140, 128)
INK = (31, 41, 55)
MUTED = (91, 105, 122)
LINE = (216, 230, 232)
SOFT = (242, 249, 250)
NOTE_BG = (255, 247, 237)
NOTE_LINE = (253, 186, 116)
WHITE = (255, 255, 255)


def font(size: int, weight: str = "regular") -> ImageFont.FreeTypeFont:
    candidates = {
        "regular": [
            "/System/Library/Fonts/ヒラギノ角ゴシック W8.ttc",
            "/System/Library/Fonts/ヒラギノ角ゴシック W9.ttc",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/System/Library/Fonts/ヒラギノ角ゴシック W4.ttc",
        ],
        "bold": [
            "/System/Library/Fonts/ヒラギノ角ゴシック W9.ttc",
            "/System/Library/Fonts/ヒラギノ角ゴシック W8.ttc",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/System/Library/Fonts/ヒラギノ角ゴシック W7.ttc",
            "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc",
        ],
    }[weight]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


F_TITLE = font(45, "bold")
F_H2 = font(29, "bold")
F_BODY = font(22, "regular")
F_BODY_B = font(22, "bold")
F_SMALL = font(18, "regular")
F_FOOTER = font(16, "regular")


def make_page() -> Image.Image:
    return Image.new("RGB", (W, H), WHITE)


def text_width(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont) -> int:
    if not text:
        return 0
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0]


def wrap_text(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.ImageFont, max_width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        current = ""
        for ch in paragraph:
            trial = current + ch
            if text_width(draw, trial, fnt) <= max_width or not current:
                current = trial
            else:
                lines.append(current)
                current = ch
        lines.append(current)
    return lines


def draw_wrapped(
    draw: ImageDraw.ImageDraw,
    xy: tuple[int, int],
    text: str,
    fnt: ImageFont.ImageFont,
    fill: tuple[int, int, int] = INK,
    max_width: int = 1200,
    line_gap: int = 9,
) -> int:
    x, y = xy
    line_height = fnt.size + line_gap
    for line in wrap_text(draw, text, fnt, max_width):
        draw.text((x, y), line, font=fnt, fill=fill)
        y += line_height
    return y


def draw_footer(draw: ImageDraw.ImageDraw, page_no: int) -> None:
    y = H - 82
    draw.line((MARGIN_X, y, W - MARGIN_X, y), fill=LINE, width=2)
    draw.text((MARGIN_X, y + 24), f"{APP_NAME} App Review Guide", font=F_FOOTER, fill=MUTED)
    draw.text((W - MARGIN_X, y + 24), str(page_no), font=F_FOOTER, fill=MUTED, anchor="ra")


def draw_table(
    draw: ImageDraw.ImageDraw,
    y: int,
    rows: list[tuple[str, str]],
    label_w: int = 310,
    row_pad_y: int = 20,
) -> int:
    x = MARGIN_X
    table_w = W - MARGIN_X * 2
    value_w = table_w - label_w
    row_heights: list[int] = []
    wrapped: list[tuple[list[str], list[str]]] = []
    for label, value in rows:
        label_lines = wrap_text(draw, label, F_SMALL, label_w - 42)
        value_lines = wrap_text(draw, value, F_BODY, value_w - 48)
        wrapped.append((label_lines, value_lines))
        row_heights.append(max(len(label_lines) * 29, len(value_lines) * 33) + row_pad_y * 2)

    total_h = sum(row_heights)
    draw.rounded_rectangle((x, y, x + table_w, y + total_h), radius=8, fill=WHITE, outline=LINE, width=2)
    yy = y
    for idx, ((label_lines, value_lines), row_h) in enumerate(zip(wrapped, row_heights)):
        draw.rectangle((x, yy, x + label_w, yy + row_h), fill=SOFT)
        if idx > 0:
            draw.line((x, yy, x + table_w, yy), fill=LINE, width=1)
        draw.line((x + label_w, yy, x + label_w, yy + row_h), fill=LINE, width=1)
        ly = yy + row_pad_y
        for line in label_lines:
            draw.text((x + 22, ly), line, font=F_SMALL, fill=MUTED)
            ly += 29
        vy = yy + row_pad_y - 2
        for line in value_lines:
            draw.text((x + label_w + 24, vy), line, font=F_BODY, fill=INK)
            vy += 33
        yy += row_h
    return y + total_h


def draw_section(draw: ImageDraw.ImageDraw, y: int, title: str, items: Iterable[str]) -> int:
    y += 36
    draw.text((MARGIN_X, y), title, font=F_H2, fill=INK)
    y += 50
    for item in items:
        y = draw_wrapped(draw, (MARGIN_X, y), item, F_BODY, INK, W - MARGIN_X * 2, 8)
        y += 12
    return y


def draw_note(draw: ImageDraw.ImageDraw, y: int, text: str) -> int:
    x = MARGIN_X
    w = W - MARGIN_X * 2
    lines = wrap_text(draw, text, F_BODY, w - 70)
    h = len(lines) * 33 + 50
    draw.rounded_rectangle((x, y, x + w, y + h), radius=8, fill=NOTE_BG, outline=NOTE_LINE, width=2)
    yy = y + 26
    for line in lines:
        draw.text((x + 34, yy), line, font=F_BODY, fill=INK)
        yy += 33
    return y + h


def page_one() -> Image.Image:
    img = make_page()
    draw = ImageDraw.Draw(img)
    y = MARGIN_TOP
    draw.text((MARGIN_X, y), f"{APP_NAME} App Review 操作説明", font=F_TITLE, fill=BRAND)
    y += 74
    y = draw_wrapped(
        draw,
        (MARGIN_X, y),
        "本資料は、App Reviewで家族モードと本人モードを確認するための簡単な操作案内です。",
        F_BODY,
        MUTED,
        W - MARGIN_X * 2,
    )
    y += 34
    y = draw_table(
        draw,
        y,
        [
            ("アプリ概要", "離れて暮らす家族の服薬予定、服薬記録、薬の在庫を管理・共有するための補助アプリです。"),
            ("審査用メール", "appreview@okusuri-mimamori.com"),
            ("審査用パスワード", "AppReview2026!"),
            ("サンプル患者", "田中 花子"),
            ("登録済みデータ", "血圧の薬 5 mg、整腸剤 50 mg、頭痛薬、眠前の薬"),
        ],
    )
    y = draw_section(
        draw,
        y,
        "1. 家族モードの確認手順",
        [
            "1. アプリ起動後、「家族として使う」を選択します。",
            "2. 審査用メールアドレスとパスワードでログインします。",
            "3. 「今日のお薬」で当日の予定と記録状態を確認します。",
            "4. 「薬を管理」で定時薬、頓服、服用量、服用タイミングを確認します。",
            "5. 「在庫」で薬の残数、残り日数、低在庫表示を確認します。",
            "6. 「履歴」で日別の服薬記録、未記録、飲み忘れの表示を確認します。",
            "7. 「連携/設定」から本人モード用の連携コードを発行できます。",
        ],
    )
    y = draw_section(
        draw,
        y,
        "2. 本人モードの確認手順",
        [
            "1. 家族モードの「連携/設定」から本人用の連携コードを発行します。",
            "2. アプリを戻る、または再起動して「本人として使う」を選択します。",
            "3. 発行した連携コードを入力します。",
            "4. 本人モードで、今日飲むお薬の確認、服薬記録、履歴、通知設定を確認します。",
            "5. 本人モードで記録した内容は、家族モードの履歴にも反映されます。",
        ],
    )
    y = draw_section(draw, y, "注意事項", [])
    draw_note(
        draw,
        y,
        "本人モード用の連携コードはセキュリティ上、有効期限があります。固定コードは記載していません。審査時に家族モードから新しく発行してください。",
    )
    draw_footer(draw, 1)
    return img


def page_two() -> Image.Image:
    img = make_page()
    draw = ImageDraw.Draw(img)
    y = MARGIN_TOP
    draw.text((MARGIN_X, y), "補足情報", font=F_TITLE, fill=BRAND)
    y += 34
    y = draw_section(
        draw,
        y,
        "モードの役割",
        [
            "家族モード: 家族が薬・予定・在庫を管理し、服薬記録や履歴を確認するための画面です。",
            "本人モード: ご本人が今日飲むお薬を大きな文字で確認し、かんたんに記録するための画面です。",
        ],
    )
    y = draw_section(
        draw,
        y,
        "通知について",
        [
            "通知機能を確認する場合、iOSの通知許可ダイアログが表示されることがあります。",
            "通知の許可状態に関わらず、アプリ内の服薬予定、記録、履歴、在庫管理は確認できます。",
        ],
    )
    y = draw_section(
        draw,
        y,
        "医療上の注意",
        [
            "本アプリは服薬予定・記録・在庫を管理するための補助ツールです。",
            "診断、治療、処方変更、薬学的助言を行うものではありません。",
            "服薬内容や体調に関する判断は、医師・薬剤師などの専門家に相談してください。",
        ],
    )
    y = draw_section(draw, y, "関連URL", [])
    draw_table(
        draw,
        y,
        [
            ("サポート", "https://www.okusuri-mimamori.com/support"),
            ("プライバシー", "https://www.okusuri-mimamori.com/privacy"),
            ("利用規約", "https://www.okusuri-mimamori.com/terms"),
        ],
    )
    draw_footer(draw, 2)
    return img


def build_pdf() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    pages = [page_one(), page_two()]
    pages[0].save(OUT_PATH, "PDF", resolution=200.0, save_all=True, append_images=pages[1:])


if __name__ == "__main__":
    build_pdf()
