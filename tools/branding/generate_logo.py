"""以纯 Python 确定性绘制项目 Logo。"""

from __future__ import annotations

import argparse
import binascii
import hashlib
import struct
import zlib
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT_DIRECTORY = PROJECT_ROOT / "branding"
DEFAULT_ICON_PATH = (
    PROJECT_ROOT / "src/main/resources/assets/vanilla_fashion/icon.png"
)
LOGICAL_SIZE = 64


class Canvas:
    """提供只包含硬边缘像素图形的最小 RGBA 画布。"""

    def __init__(self, size: int, color: tuple[int, int, int, int]) -> None:
        self.size = size
        self.pixels = bytearray(color * (size * size))

    def pixel(self, x: int, y: int, color: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.size and 0 <= y < self.size:
            offset = (y * self.size + x) * 4
            self.pixels[offset : offset + 4] = bytes(color)

    def rectangle(
        self,
        x0: int,
        y0: int,
        x1: int,
        y1: int,
        color: tuple[int, int, int, int],
    ) -> None:
        for y in range(max(0, y0), min(self.size, y1)):
            for x in range(max(0, x0), min(self.size, x1)):
                self.pixel(x, y, color)

    def polygon(
        self,
        points: tuple[tuple[int, int], ...],
        color: tuple[int, int, int, int],
    ) -> None:
        min_x = max(0, min(x for x, _ in points))
        max_x = min(self.size - 1, max(x for x, _ in points))
        min_y = max(0, min(y for _, y in points))
        max_y = min(self.size - 1, max(y for _, y in points))
        for y in range(min_y, max_y + 1):
            for x in range(min_x, max_x + 1):
                if point_in_polygon(x + 0.5, y + 0.5, points):
                    self.pixel(x, y, color)

    def line(
        self,
        x0: int,
        y0: int,
        x1: int,
        y1: int,
        color: tuple[int, int, int, int],
        thickness: int = 1,
    ) -> None:
        dx = abs(x1 - x0)
        sx = 1 if x0 < x1 else -1
        dy = -abs(y1 - y0)
        sy = 1 if y0 < y1 else -1
        error = dx + dy
        while True:
            radius = thickness // 2
            self.rectangle(
                x0 - radius,
                y0 - radius,
                x0 - radius + thickness,
                y0 - radius + thickness,
                color,
            )
            if x0 == x1 and y0 == y1:
                break
            doubled = 2 * error
            if doubled >= dy:
                error += dy
                x0 += sx
            if doubled <= dx:
                error += dx
                y0 += sy


def point_in_polygon(
    x: float,
    y: float,
    points: tuple[tuple[int, int], ...],
) -> bool:
    inside = False
    previous_x, previous_y = points[-1]
    for current_x, current_y in points:
        crosses = (current_y > y) != (previous_y > y)
        if crosses:
            boundary = (
                (previous_x - current_x) * (y - current_y)
                / (previous_y - current_y)
                + current_x
            )
            if x < boundary:
                inside = not inside
        previous_x, previous_y = current_x, current_y
    return inside


def draw_logo() -> bytes:
    """绘制打开的木质衣柜与中央空盔甲架。"""
    transparent = (0, 0, 0, 0)
    background = (25, 30, 43, 255)
    background_light = (35, 42, 56, 255)
    outline = (45, 27, 24, 255)
    darkest_wood = (72, 39, 27, 255)
    dark_wood = (102, 57, 36, 255)
    wood = (143, 83, 48, 255)
    light_wood = (187, 119, 67, 255)
    inner_shadow = (18, 20, 28, 255)
    inner_light = (31, 34, 43, 255)
    stand_dark = (111, 80, 47, 255)
    stand_wood = (176, 135, 78, 255)
    stand_light = (213, 173, 103, 255)
    stone_dark = (75, 79, 82, 255)
    stone = (124, 128, 127, 255)
    metal = (218, 168, 70, 255)
    blue_fabric = (55, 108, 170, 255)
    rose_fabric = (163, 67, 83, 255)

    canvas = Canvas(LOGICAL_SIZE, transparent)
    canvas.rectangle(3, 3, 61, 61, background)
    canvas.rectangle(5, 5, 59, 58, background_light)
    canvas.rectangle(5, 52, 59, 59, background)
    canvas.rectangle(3, 3, 8, 8, transparent)
    canvas.rectangle(56, 3, 61, 8, transparent)
    canvas.rectangle(3, 56, 8, 61, transparent)
    canvas.rectangle(56, 56, 61, 61, transparent)

    # 柜体和暗色内部。
    canvas.rectangle(10, 5, 54, 59, outline)
    canvas.rectangle(12, 7, 52, 57, dark_wood)
    canvas.rectangle(16, 11, 48, 53, inner_shadow)
    canvas.rectangle(18, 13, 46, 51, inner_light)
    canvas.rectangle(20, 15, 44, 49, inner_shadow)
    canvas.rectangle(9, 5, 55, 10, darkest_wood)
    canvas.rectangle(11, 6, 53, 8, wood)
    canvas.rectangle(13, 7, 51, 8, light_wood)
    canvas.rectangle(9, 52, 55, 59, darkest_wood)
    canvas.rectangle(12, 53, 52, 57, wood)
    canvas.rectangle(14, 53, 50, 54, light_wood)
    canvas.rectangle(12, 59, 18, 61, outline)
    canvas.rectangle(46, 59, 52, 61, outline)

    # 打开的柜门采用像素化梯形，不使用任何外部纹理。
    canvas.polygon(((2, 10), (15, 13), (15, 54), (2, 58)), outline)
    canvas.polygon(((4, 12), (13, 15), (13, 52), (4, 55)), dark_wood)
    canvas.polygon(((5, 14), (11, 16), (11, 50), (5, 53)), wood)
    canvas.line(6, 17, 10, 18, light_wood)
    canvas.line(6, 48, 10, 47, darkest_wood)
    canvas.rectangle(7, 23, 10, 32, blue_fabric)
    canvas.rectangle(7, 23, 9, 25, (82, 142, 205, 255))

    canvas.polygon(((49, 13), (62, 10), (62, 58), (49, 54)), outline)
    canvas.polygon(((51, 15), (60, 12), (60, 55), (51, 52)), dark_wood)
    canvas.polygon(((53, 16), (59, 14), (59, 53), (53, 50)), wood)
    canvas.line(54, 18, 58, 17, light_wood)
    canvas.line(54, 47, 58, 48, darkest_wood)
    canvas.rectangle(54, 23, 57, 32, rose_fabric)
    canvas.rectangle(55, 23, 57, 25, (196, 91, 105, 255))
    canvas.rectangle(11, 32, 14, 35, metal)
    canvas.rectangle(50, 32, 53, 35, metal)

    # 中央盔甲架保持完全空置：只有木架、支腿与石质底座。
    canvas.rectangle(29, 16, 35, 22, stand_dark)
    canvas.rectangle(30, 16, 34, 20, stand_wood)
    canvas.rectangle(31, 16, 33, 18, stand_light)
    canvas.rectangle(31, 21, 33, 28, stand_wood)
    canvas.line(22, 28, 42, 28, stand_dark, 3)
    canvas.line(23, 27, 41, 27, stand_wood, 2)
    canvas.rectangle(31, 28, 33, 43, stand_wood)
    canvas.line(31, 33, 26, 41, stand_dark, 2)
    canvas.line(33, 33, 38, 41, stand_dark, 2)
    canvas.line(31, 42, 28, 49, stand_wood, 2)
    canvas.line(33, 42, 36, 49, stand_wood, 2)
    canvas.rectangle(23, 49, 41, 53, stone_dark)
    canvas.rectangle(25, 48, 39, 51, stone)
    canvas.rectangle(27, 48, 37, 49, (165, 168, 163, 255))

    # 少量方块高光让小尺寸下仍能分离衣柜、内腔与盔甲架。
    canvas.rectangle(18, 13, 20, 16, (46, 49, 57, 255))
    canvas.rectangle(44, 13, 46, 16, (12, 14, 21, 255))
    canvas.rectangle(6, 56, 58, 58, (14, 17, 25, 255))
    canvas.rectangle(24, 55, 40, 56, (55, 58, 63, 255))
    return bytes(canvas.pixels)


def scale_nearest(pixels: bytes, source_size: int, target_size: int) -> bytes:
    if target_size % source_size != 0:
        raise ValueError("目标尺寸必须是逻辑画布尺寸的整数倍。")
    scale = target_size // source_size
    output = bytearray(target_size * target_size * 4)
    for source_y in range(source_size):
        for source_x in range(source_size):
            source_offset = (source_y * source_size + source_x) * 4
            color = pixels[source_offset : source_offset + 4]
            for offset_y in range(scale):
                target_y = source_y * scale + offset_y
                row_offset = target_y * target_size * 4
                for offset_x in range(scale):
                    target_x = source_x * scale + offset_x
                    target_offset = row_offset + target_x * 4
                    output[target_offset : target_offset + 4] = color
    return bytes(output)


def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    checksum = binascii.crc32(chunk_type)
    checksum = binascii.crc32(data, checksum) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", checksum)


def encode_png(size: int, pixels: bytes) -> bytes:
    stride = size * 4
    raw = b"".join(
        b"\x00" + pixels[row * stride : (row + 1) * stride]
        for row in range(size)
    )
    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", header)
        + png_chunk(b"IDAT", zlib.compress(raw, level=9))
        + png_chunk(b"IEND", b"")
    )


def generated_files() -> dict[str, bytes]:
    logical = draw_logo()
    return {
        "logo.png": encode_png(512, scale_nearest(logical, LOGICAL_SIZE, 512)),
        "logo-128.png": encode_png(128, scale_nearest(logical, LOGICAL_SIZE, 128)),
    }


def describe(path: Path, content: bytes) -> str:
    return f"{path}：{len(content)} 字节，SHA-256={hashlib.sha256(content).hexdigest()}"


def check_file(path: Path, expected: bytes) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"生成文件不存在：{path}")
    actual = path.read_bytes()
    if actual != expected:
        raise ValueError(f"生成文件与确定性绘制结果不一致：{path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="确定性生成 Vanilla Fashion 像素 Logo。")
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Logo 输出目录；省略时写入仓库 branding 目录并同步 metadata icon。",
    )
    parser.add_argument(
        "--icon-output",
        type=Path,
        help="可选的 128×128 metadata icon 输出路径。",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="只校验现有文件，不执行写入。",
    )
    args = parser.parse_args()

    output_directory = (
        args.output_dir.resolve()
        if args.output_dir is not None
        else DEFAULT_OUTPUT_DIRECTORY
    )
    icon_path = args.icon_output.resolve() if args.icon_output is not None else None
    if args.output_dir is None and args.icon_output is None:
        icon_path = DEFAULT_ICON_PATH

    files = generated_files()
    targets = {output_directory / name: content for name, content in files.items()}
    if icon_path is not None:
        targets[icon_path] = files["logo-128.png"]

    if args.check:
        for path, content in targets.items():
            check_file(path, content)
            print("校验通过：" + describe(path, content))
        return

    for path, content in targets.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        print("已生成：" + describe(path, content))


if __name__ == "__main__":
    main()
