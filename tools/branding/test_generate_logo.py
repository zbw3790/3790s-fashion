"""验证原创品牌生成、跨进程复现和资源隔离，不启动 Minecraft。"""

from __future__ import annotations

import ast
import importlib.util
import io
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image

SCRIPT = Path(__file__).with_name("generate_logo.py").resolve()
SPEC = importlib.util.spec_from_file_location("current_logo", SCRIPT)
logo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(logo)
PAINTER = logo.ROOT / logo.PAINTER_RELATIVE


class CurrentLogoTest(unittest.TestCase):
    def test_current_pngs_and_metadata_icon_match_regeneration(self) -> None:
        logo.check_current(logo.ROOT / "branding", logo.ROOT / "src/main/resources/assets/vanilla_fashion/icon.png")

    def test_palette_has_exact_brand_and_fixed_integer_derivations(self) -> None:
        self.assertEqual("#3790FF", logo.BRAND_BLUE)
        self.assertEqual((55, 144, 255, 255), logo.palette()["k"])
        self.assertEqual((25, 65, 115, 255), logo.palette()["h"])
        self.assertEqual((115, 177, 255, 255), logo.palette()["i"])
        self.assertEqual((44, 115, 204, 255), logo.palette()["j"])
        self.assertEqual((55, 144, 255), logo.lighter(logo.BRAND_RGB, 0))
        self.assertEqual((0, 0, 0), logo.darker(logo.BRAND_RGB, 100))

    def test_sizes_opaque_background_and_nearest_pixels_without_new_colors(self) -> None:
        master = logo.pixel_master(PAINTER)
        colors = set(logo.palette().values()) | {logo.WHITE, logo.BACKGROUND, logo.DIGIT_COLOR}
        self.assertEqual(colors, set(master.getdata()))
        self.assertEqual((38, 41, 43, 255), master.getpixel((0, 0)))
        outputs = logo.regenerate()
        self.assertEqual({64, 128, 256, 512}, set(logo.OUTPUT_SIZES.values()))
        for name, data in outputs.items():
            with self.subTest(name=name), Image.open(io.BytesIO(data)) as image:
                size = logo.OUTPUT_SIZES[name]
                self.assertEqual((size, size), image.size)
                self.assertEqual("RGBA", image.mode)
                self.assertEqual({}, image.info)
                self.assertEqual({255}, set(image.getchannel("A").getdata()))
                self.assertEqual(master.resize((size, size), Image.Resampling.NEAREST).tobytes(), image.tobytes())
                self.assertEqual(master.tobytes(), image.resize((128, 128), Image.Resampling.NEAREST).tobytes())
                self.assertEqual(colors, set(image.getdata()))

    def test_outline_is_external_one_pixel_and_keeps_subject_rgba_and_holes(self) -> None:
        source = Image.new("RGBA", (7, 7))
        for y in range(2, 5):
            for x in range(2, 5):
                if (x, y) != (3, 3):
                    source.putpixel((x, y), (55, 144, 255, 128 if (x, y) == (2, 2) else 255))
        result = logo.add_outline(source)
        for y in range(7):
            for x in range(7):
                if source.getpixel((x, y))[3]:
                    self.assertEqual(source.getpixel((x, y)), result.getpixel((x, y)))
        self.assertEqual((0, 0, 0, 0), result.getpixel((3, 3)))
        self.assertEqual(logo.WHITE, result.getpixel((1, 1)))
        self.assertEqual((0, 0, 0, 0), result.getpixel((0, 0)))
        self.assertEqual((1, 1, 6, 6), result.getbbox())
        shirt = logo.shirt_image(PAINTER)
        self.assertEqual((1, 1, 15, 15), logo.add_outline(shirt).getbbox())

    def test_old_background_placement_and_colors_are_explicit_geometry(self) -> None:
        self.assertEqual({"3": (8, 14), "7": (90, 14), "9": (8, 72), "0": (90, 72)}, logo.POSITIONS)
        self.assertEqual((54, 58, 61, 255), logo.DIGIT_COLOR)
        self.assertEqual(6, logo.PIXEL_SCALE)
        bg = logo.background_image()
        self.assertEqual({logo.BACKGROUND, logo.DIGIT_COLOR}, set(bg.getdata()))
        self.assertEqual(logo.DIGIT_COLOR, bg.getpixel((8, 14)))
        self.assertEqual(logo.BACKGROUND, bg.getpixel((64, 64)))

    def test_generation_reads_only_project_geometry_and_has_no_sprite_or_network_import(self) -> None:
        original = Path.read_text
        observed = []
        def guarded(path, *args, **kwargs):
            self.assertEqual(PAINTER.resolve(), path.resolve())
            observed.append(path)
            return original(path, *args, **kwargs)
        with patch.object(Path, "read_text", guarded), patch.object(Image, "open", side_effect=AssertionError("生成不能读入已有图片。")):
            logo.regenerate()
        self.assertEqual([PAINTER], observed)
        tree = ast.parse(SCRIPT.read_text("utf-8"))
        imports = {node.module.split(".")[0] for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)}
        imports |= {alias.name.split(".")[0] for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names}
        self.assertFalse(imports & {"zipfile", "socket", "requests", "urllib", "http", "subprocess"})
        self.assertNotIn("assets/minecraft/", SCRIPT.read_text("utf-8"))

    def test_default_check_is_read_only_from_an_unrelated_directory(self) -> None:
        paths = [logo.ROOT / "branding" / name for name in logo.OUTPUT_SIZES]
        before = {p: p.read_bytes() for p in paths}
        with tempfile.TemporaryDirectory() as temp:
            result = self.run_script([], Path(temp))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("未写入文件", result.stdout)
        self.assertEqual(before, {p: p.read_bytes() for p in paths})

    def test_two_independent_processes_reproduce_pngs_and_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in ("a", "b"):
                result = self.run_script(["--regenerate", "--output-dir", str(root / name),
                                          "--manifest", str(root / (name + ".json"))], root)
                self.assertEqual(0, result.returncode, result.stderr)
            for name in logo.OUTPUT_SIZES:
                self.assertEqual((root / "a" / name).read_bytes(), (root / "b" / name).read_bytes())
            self.assertEqual((root / "a.json").read_bytes(), (root / "b.json").read_bytes())

    def test_missing_or_wrong_project_input_never_falls_back(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "painter.java"
            with self.assertRaises(OSError):
                logo.regenerate(source)
            source.write_text(PAINTER.read_text("utf-8").replace("0xFF3790FF", "0xFF347CAC"), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "主色"):
                logo.regenerate(source)
            result = self.run_script(["--regenerate", "--client-jar", str(source)], Path(temp))
            self.assertNotEqual(0, result.returncode)

    def test_tampered_png_and_nonempty_output_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, data in logo.regenerate().items():
                (root / name).write_bytes(data)
            data = bytearray((root / "logo.png").read_bytes()); data[-1] ^= 1
            (root / "logo.png").write_bytes(data)
            with self.assertRaisesRegex(ValueError, "不一致"):
                logo.check_current(root)
            result = self.run_script(["--regenerate", "--output-dir", str(root)], root)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(bytes(data), (root / "logo.png").read_bytes())

    @staticmethod
    def run_script(arguments: list[str], cwd: Path) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, "-B", str(SCRIPT), *arguments], cwd=cwd,
                              capture_output=True, encoding="utf-8",
                              env={**os.environ, "PYTHONIOENCODING": "utf-8"})


if __name__ == "__main__":
    unittest.main()
