"""校验当前正式 Logo；使用显式本地资源可完整再生已冻结的数字盔甲架图像。"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import struct
import sys
from collections import deque
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
APPROVED = {
    "logo.png": (512, 2695, "6301eebeda43cc47623cd3886fb47b21f77cf2abab3a3e2a36970b279618c6ce"),
    "logo-256.png": (256, 1447, "31bc223b080557920154886092d16e26b35927fff914d5692aa82822c1e17eed"),
    "logo-128.png": (128, 887, "52efa02194417c62039ae011db3afabf867a52bb8ec64a9a03ff9404baa5e8d6"),
}
CLIENT_SHA256 = "40896ee9f1e2bec3c934daac7e93d41e9e3d9c2f8ae0ca366d52ffbfd1afa290"
RESOURCE_HASHES = {
    "assets/minecraft/textures/item/armor_stand.png": "54d95c69cf2bb9e566c6a013c8258bd44e998de5d0578fc79acb8d16b38b1c8a",
    "assets/minecraft/textures/font/ascii.png": "e8646f1ed1f4bfd597d262cca3d8fac88ceaaa62807bbd5e607b2e3fe41c928a",
    "assets/minecraft/font/default.json": "32942032bb9cc9a482088dcbd617a9ce339fb9b722e9696c6cac13bac5949832",
    "assets/minecraft/font/include/default.json": "e17f8a4289db15bf662df3ab623ad4dd9bda9d2b2e3e9e9e743234ff186bd0c4",
    "assets/minecraft/items/armor_stand.json": "e82ff3ff89b4aa96eacf20dbec0932c17861edde073ffd064053aff3c7b104e9",
    "assets/minecraft/models/item/armor_stand.json": "5e5136e656cca28b3be7233f36b636b562757e52efe70c0dd0bcf11a6f8c3a6f",
}
POSITIONS = {"3": (8, 14), "7": (90, 14), "9": (8, 72), "0": (90, 72)}
BACKGROUND = (38, 41, 43, 255)
DIGIT_COLOR = (54, 58, 61, 255)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate_png(name: str, data: bytes) -> None:
    size, length, expected = APPROVED[name]
    require(len(data) == length and digest(data) == expected,
            f"{name} 与当前批准 PNG 的大小或 SHA-256 不一致。")
    require(data[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", data[16:24]) == (size, size),
            f"{name} 的 PNG 尺寸不正确。")


def check_current(directory: Path, icon: Path | None = None) -> None:
    """只校验已存在的 PNG；不读取游戏资源，不声称已经重新生成。"""
    for name in APPROVED:
        validate_png(name, (directory / name).read_bytes())
    if icon is not None:
        require(icon.read_bytes() == (directory / "logo-128.png").read_bytes(),
                "metadata icon 与当前 128 像素 Logo 不一致。")


def load_resources(client_jar: Path) -> dict[str, bytes]:
    require(client_jar.is_file(), "找不到显式指定的本地 Minecraft 26.2 客户端 JAR。")
    data = client_jar.read_bytes()
    require(digest(data) == CLIENT_SHA256, "客户端 JAR 与冻结的 Minecraft 26.2 官方来源不一致。")
    with ZipFile(io.BytesIO(data)) as archive:
        require(json.loads(archive.read("version.json"))["id"] == "26.2", "Minecraft 版本不符。")
        resources = {entry: archive.read(entry) for entry in RESOURCE_HASHES}
    for entry, expected in RESOURCE_HASHES.items():
        require(digest(resources[entry]) == expected, f"原版资源摘要不符：{entry}")
    return resources


def regenerate(client_jar: Path) -> dict[str, bytes]:
    """从物品 PNG 和默认字体字形完整再生，不嵌入或下载原始资产。"""
    resources = load_resources(client_jar)
    try:
        from PIL import Image, ImageDraw
    except ImportError as error:
        raise ValueError("完整再生需要 Pillow；冻结图像使用 Pillow 10.3.0。普通校验不需要 Pillow。") from error
    with Image.open(io.BytesIO(resources["assets/minecraft/textures/item/armor_stand.png"])) as png:
        source = png.convert("RGBA")
    bbox = source.getchannel("A").getbbox()
    require(source.size == (16, 16) and bbox == (3, 0, 12, 16), "盔甲架主体尺寸与冻结来源不一致。")
    crop = source.crop(bbox)
    base = Image.new("RGBA", (11, 18), (0, 0, 0, 0))
    occupied = {(x+1, y+1) for y in range(16) for x in range(9) if crop.getpixel((x,y))[3]}
    exterior = ({(x,y) for x in range(11) for y in (0,17)}
                | {(x,y) for x in (0,10) for y in range(18)}) - occupied
    queue = deque(sorted(exterior))
    while queue:
        x, y = queue.popleft()
        for dx, dy in ((-1,0),(1,0),(0,-1),(0,1)):
            point = (x+dx, y+dy)
            if (0 <= point[0] < 11 and 0 <= point[1] < 18
                    and point not in occupied and point not in exterior):
                exterior.add(point)
                queue.append(point)
    expanded = {(x+dx,y+dy) for x,y in occupied for dx in (-1,0,1) for dy in (-1,0,1)}
    for point in (expanded - occupied) & exterior:
        base.putpixel(point, (255,255,255,255))
    for x, y in occupied:
        base.putpixel((x,y), crop.getpixel((x-1,y-1)))
    foreground = Image.new("RGBA", (128,128), (0,0,0,0))
    foreground.paste(base.resize((66,108), Image.Resampling.NEAREST), (31,10))

    default = json.loads(resources["assets/minecraft/font/default.json"])
    require(any(p.get("id") == "minecraft:include/default" and p.get("filter") == {"uniform": False}
                for p in default["providers"]), "默认字体引用不符合冻结方案。")
    providers = json.loads(resources["assets/minecraft/font/include/default.json"])["providers"]
    with Image.open(io.BytesIO(resources["assets/minecraft/textures/font/ascii.png"])) as png:
        atlas = png.convert("RGBA")
    canvas = Image.new("RGBA", (128,128), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    for digit, (left, top) in POSITIONS.items():
        provider = next(p for p in providers if p["type"] == "bitmap"
                        and any(digit in row for row in p["chars"]))
        require(provider["file"] == "minecraft:font/ascii.png", "数字未引用冻结的原版字体贴图。")
        rows = provider["chars"]
        width, height = atlas.width // len(rows[0]), atlas.height // len(rows)
        row = next(i for i, line in enumerate(rows) if digit in line)
        column = rows[row].index(digit)
        mask = atlas.crop((column*width,row*height,(column+1)*width,(row+1)*height)).getchannel("A")
        mask = mask.crop(mask.getbbox())
        require(mask.size == (5,7) and set(mask.getdata()) == {0,255}, "数字字形尺寸或透明度不符。")
        for y in range(7):
            for x in range(5):
                if mask.getpixel((x,y)):
                    px, py = left + x*6, top + y*6
                    draw.rectangle((px,py,px+5,py+5), fill=DIGIT_COLOR)
    canvas.alpha_composite(foreground)
    require(all(a == b for a,b in zip(foreground.getdata(),canvas.getdata()) if a[3]),
            "再生过程中主体像素发生改变。")
    outputs = {}
    for name, (size, _, _) in APPROVED.items():
        image = canvas.resize((size,size), Image.Resampling.NEAREST)
        clean = Image.frombytes("RGBA", image.size, image.tobytes())
        buffer = io.BytesIO()
        clean.save(buffer, format="PNG", compress_level=9, optimize=False)
        outputs[name] = buffer.getvalue()
        validate_png(name, outputs[name])
    return outputs


def main() -> None:
    parser = argparse.ArgumentParser(description="校验正式 Logo；完整再生必须显式指定本地资源。")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check", action="store_true", help="只校验已有正式 PNG；也是默认行为。")
    modes.add_argument("--regenerate", action="store_true", help="从显式本地客户端 JAR 完整再生。")
    parser.add_argument("--client-jar", type=Path, help="完整再生所需的 Minecraft 26.2 原版客户端 JAR。")
    parser.add_argument("--output-dir", type=Path, help="校验目录，或完整再生时必须指定的新空目录。")
    parser.add_argument("--icon-output", type=Path, help="校验时额外检查的 metadata icon 路径，不写入。")
    args = parser.parse_args()
    if args.regenerate:
        if args.client_jar is None or args.output_dir is None or args.icon_output is not None:
            parser.error("完整再生需要 --client-jar 和 --output-dir；不接受 --icon-output。")
        output = args.output_dir.resolve()
        require(not output.exists() or (output.is_dir() and not any(output.iterdir())),
                "完整再生只写入新的空目录，不覆盖正式或历史图像。")
        images = regenerate(args.client_jar.resolve())
        output.mkdir(parents=True, exist_ok=True)
        for name, data in images.items():
            (output / name).write_bytes(data)
        print("完整再生通过：三份 PNG 与当前批准的 SHA-256 逐一一致。")
    else:
        if args.client_jar is not None:
            parser.error("--client-jar 仅用于 --regenerate；校验现有图像不需要游戏资源。")
        directory = args.output_dir.resolve() if args.output_dir else ROOT / "branding"
        icon = args.icon_output
        if icon is None and args.output_dir is None:
            icon = ROOT / "src/main/resources/assets/vanilla_fashion/icon.png"
        check_current(directory, icon)
        print("现有 PNG 校验通过：尺寸、冻结 SHA-256 与指定 icon 一致；未执行完整再生。")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        print("错误：" + str(error), file=sys.stderr)
        raise SystemExit(1)
