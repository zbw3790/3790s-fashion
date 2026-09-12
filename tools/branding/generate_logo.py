"""离线绘制原创衬衫品牌 Logo；几何来自项目 Painter，不读取任何游戏贴图。"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
PAINTER_RELATIVE = "src/client/java/dev/zbw3790/fashion/client/screen/WardrobeGuiPainter.java"
BRAND_BLUE = "#3790FF"
BRAND_RGB = (55, 144, 255)
BACKGROUND = (38, 41, 43, 255)
DIGIT_COLOR = (54, 58, 61, 255)
WHITE = (255, 255, 255, 255)
MASTER_SIZE = 128
PIXEL_SCALE = 6
SHIRT_ORIGIN = (16, 16)
POSITIONS = {"3": (8, 14), "7": (90, 14), "9": (8, 72), "0": (90, 72)}
OUTPUT_SIZES = {"logo.png": 512, "logo-256.png": 256, "logo-128.png": 128, "logo-64.png": 64}
# 独立定义的七段矩形字形，保持旧数字位置、5×7 格与低对比颜色，不提取字体位图。
SEGMENTS = {
    "a": (0, 0, 5, 1), "b": (4, 0, 5, 4), "c": (4, 3, 5, 7),
    "d": (0, 6, 5, 7), "e": (0, 3, 1, 7), "f": (0, 0, 1, 4), "g": (0, 3, 5, 4),
}
DIGITS = {"3": "abcdg", "7": "abc", "9": "abcdfg", "0": "abcdef"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def mix(base: tuple[int, int, int], target: int, percent: int) -> tuple[int, ...]:
    require(0 <= percent <= 100, "混色百分比必须位于 0～100。")
    return tuple((channel * (100 - percent) + target * percent + 50) // 100 for channel in base)


def lighter(base: tuple[int, int, int], percent: int) -> tuple[int, ...]:
    return mix(base, 255, percent)


def darker(base: tuple[int, int, int], percent: int) -> tuple[int, ...]:
    return mix(base, 0, percent)


def palette() -> dict[str, tuple[int, ...]]:
    return {"h": (*darker(BRAND_RGB, 55), 255), "i": (*lighter(BRAND_RGB, 30), 255),
            "j": (*darker(BRAND_RGB, 20), 255), "k": (*BRAND_RGB, 255)}


def shirt_rows(painter: Path) -> list[str]:
    source = painter.read_text(encoding="utf-8")
    require("BRAND_BLUE = 0xFF3790FF;" in source, "Painter 的品牌主色与生成器不一致。")
    match = re.search(r"String\[\] OUTFIT_ICON\s*=\s*\{(.*?)\};", source, re.S)
    require(match is not None, "找不到原创 Outfit 图标几何。")
    rows = re.findall(r'"([.hijk]+)"', match.group(1))
    require(len(rows) == 16 and all(len(row) == 16 for row in rows), "衬衫必须保持 16×16 几何。")
    return rows


def shirt_image(painter: Path) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    colors = palette()
    for y, row in enumerate(shirt_rows(painter)):
        for x, pixel in enumerate(row):
            if pixel != ".":
                image.putpixel((x, y), colors[pixel])
    return image


def add_outline(source: Image.Image) -> Image.Image:
    """向外八邻域膨胀一个像素，仅绘制与画布边缘相通的透明区域。"""
    width, height = source.size
    occupied = {(x, y) for y in range(height) for x in range(width) if source.getpixel((x, y))[3]}
    require(occupied and all(0 < x < width - 1 and 0 < y < height - 1 for x, y in occupied),
            "主体边缘必须为一个外描边像素留出空间。")
    exterior = ({(x, y) for x in range(width) for y in (0, height - 1)}
                | {(x, y) for x in (0, width - 1) for y in range(height)}) - occupied
    queue = deque(sorted(exterior))
    while queue:
        x, y = queue.popleft()
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            point = (x + dx, y + dy)
            if (0 <= point[0] < width and 0 <= point[1] < height
                    and point not in occupied and point not in exterior):
                exterior.add(point)
                queue.append(point)
    expanded = {(x + dx, y + dy) for x, y in occupied for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    result = source.copy()
    for point in (expanded - occupied) & exterior:
        result.putpixel(point, WHITE)
    return result


def background_image() -> Image.Image:
    image = Image.new("RGBA", (MASTER_SIZE, MASTER_SIZE), BACKGROUND)
    draw = ImageDraw.Draw(image)
    for digit, (left, top) in POSITIONS.items():
        for segment in DIGITS[digit]:
            x0, y0, x1, y1 = SEGMENTS[segment]
            draw.rectangle((left + x0 * PIXEL_SCALE, top + y0 * PIXEL_SCALE,
                            left + x1 * PIXEL_SCALE - 1, top + y1 * PIXEL_SCALE - 1), fill=DIGIT_COLOR)
    return image


def pixel_master(painter: Path) -> Image.Image:
    image = background_image()
    shirt = add_outline(shirt_image(painter))
    image.alpha_composite(shirt.resize((96, 96), Image.Resampling.NEAREST), SHIRT_ORIGIN)
    return image


def encode_png(image: Image.Image) -> bytes:
    clean = Image.frombytes("RGBA", image.size, image.tobytes())
    output = io.BytesIO()
    clean.save(output, format="PNG", compress_level=9, optimize=False)
    return output.getvalue()


def regenerate(painter: Path = ROOT / PAINTER_RELATIVE) -> dict[str, bytes]:
    master = pixel_master(painter)
    return {name: encode_png(master.resize((size, size), Image.Resampling.NEAREST))
            for name, size in OUTPUT_SIZES.items()}


def check_current(directory: Path, icon: Path | None = None,
                  painter: Path = ROOT / PAINTER_RELATIVE) -> None:
    expected = regenerate(painter)
    for name, data in expected.items():
        require((directory / name).read_bytes() == data, f"{name} 与确定性生成结果不一致。")
    if icon is not None:
        require(icon.read_bytes() == expected["logo-128.png"], "metadata icon 与 128 像素 Logo 不一致。")


def contact_sheet(outputs: dict[str, bytes], path: Path) -> None:
    """仅输出本地审查图，不作为产品资源；字号标签使用 Pillow 自带字体。"""
    sheet = Image.new("RGBA", (1080, 600), (232, 234, 237, 255))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    x = 24
    for name, size in OUTPUT_SIZES.items():
        draw.text((x, 18), f"{size} x {size}", fill=(40, 40, 40, 255), font=font)
        with Image.open(io.BytesIO(outputs[name])) as png:
            sheet.alpha_composite(png.convert("RGBA"), (x, 48))
        x += size + 24
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encode_png(sheet))


def manifest(outputs: dict[str, bytes], painter: Path) -> dict:
    return {"brand_blue": BRAND_BLUE, "source": PAINTER_RELATIVE,
            "geometry_sha256": digest("\n".join(shirt_rows(painter)).encode("ascii")),
            "master_size": MASTER_SIZE, "shirt_origin": SHIRT_ORIGIN, "pixel_scale": PIXEL_SCALE,
            "background": BACKGROUND, "digit_color": DIGIT_COLOR, "digit_positions": POSITIONS,
            "digit_source": "原创七段矩形，不读取字体贴图", "outline": "外部透明区域八邻域一像素纯白描边",
            "palette": palette(), "resampling": "NEAREST", "ai_generation": False,
            "game_texture_input": False,
            "outputs": {name: {"size": [OUTPUT_SIZES[name]] * 2, "bytes": len(data), "sha256": digest(data)}
                        for name, data in outputs.items()}}


def main() -> None:
    parser = argparse.ArgumentParser(description="离线校验或生成原创衬衫品牌 Logo。")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check", action="store_true", help="只校验已有 PNG；也是默认行为。")
    modes.add_argument("--regenerate", action="store_true", help="无需游戏资源，向新空目录完整生成四个尺寸。")
    parser.add_argument("--output-dir", type=Path, help="校验目录，或生成时必须指定的新空目录。")
    parser.add_argument("--painter-source", type=Path, default=ROOT / PAINTER_RELATIVE,
                        help="显式的项目原创 Painter Java 源文件。")
    parser.add_argument("--icon-output", type=Path, help="校验时额外检查的 metadata icon，不写入。")
    parser.add_argument("--preview", type=Path, help="生成时可选的本地四尺寸审查图。")
    parser.add_argument("--manifest", type=Path, help="生成时可选的确定性审计清单。")
    args = parser.parse_args()
    if args.regenerate:
        if args.output_dir is None or args.icon_output is not None:
            parser.error("完整生成需要 --output-dir；不接受 --icon-output。")
        output = args.output_dir.resolve()
        require(not output.exists() or (output.is_dir() and not any(output.iterdir())),
                "完整生成只写入新的空目录，不覆盖既有图像。")
        images = regenerate(args.painter_source)
        output.mkdir(parents=True, exist_ok=True)
        for name, data in images.items():
            (output / name).write_bytes(data)
        if args.preview:
            contact_sheet(images, args.preview)
        if args.manifest:
            args.manifest.parent.mkdir(parents=True, exist_ok=True)
            args.manifest.write_bytes((json.dumps(manifest(images, args.painter_source),
                                                ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
        print("离线完整生成通过：四个尺寸共用原创像素母版，未使用 AI 或游戏贴图。")
    else:
        if args.preview or args.manifest:
            parser.error("--preview／--manifest 仅用于 --regenerate。")
        directory = args.output_dir.resolve() if args.output_dir else ROOT / "branding"
        icon = args.icon_output
        if icon is None and args.output_dir is None:
            metadata = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text("utf-8"))
            icon = ROOT / "src/main/resources" / metadata["icon"]
        check_current(directory, icon, args.painter_source)
        print("现有 PNG 校验通过：四个尺寸与离线再生结果、指定 icon 一致；未写入文件。")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        print("错误：" + str(error), file=sys.stderr)
        raise SystemExit(1)
