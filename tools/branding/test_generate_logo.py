"""公开品牌工具自测；普通校验不依赖 Minecraft 缓存或 Pillow。"""

from __future__ import annotations

import importlib.util
import builtins
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("generate_logo.py").resolve()
SPEC = importlib.util.spec_from_file_location("current_logo", SCRIPT)
logo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(logo)


class CurrentLogoTest(unittest.TestCase):
    def test_current_pngs_and_metadata_icon_match_frozen_assets(self) -> None:
        logo.check_current(logo.ROOT / "branding",
                           logo.ROOT / "src/main/resources/assets/vanilla_fashion/icon.png")

    def test_default_entry_is_read_only_from_an_unrelated_directory(self) -> None:
        paths = [logo.ROOT / "branding" / name for name in logo.APPROVED]
        before = {p: p.read_bytes() for p in paths}
        with tempfile.TemporaryDirectory() as temporary:
            result = subprocess.run([sys.executable, "-B", str(SCRIPT)], cwd=temporary,
                                    capture_output=True, encoding="utf-8",
                                    env={**os.environ, "PYTHONIOENCODING": "utf-8"})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("未执行完整再生", result.stdout)
        self.assertEqual(before, {p: p.read_bytes() for p in paths})

    def test_check_does_not_import_pillow(self) -> None:
        original_import = builtins.__import__
        def without_pillow(name, *args, **kwargs):
            if name == "PIL" or name.startswith("PIL."):
                raise AssertionError("普通校验不应加载 Pillow。")
            return original_import(name, *args, **kwargs)
        from unittest.mock import patch
        with patch("builtins.__import__", side_effect=without_pillow):
            logo.check_current(logo.ROOT / "branding")

    def test_same_size_png_tampering_is_rejected(self) -> None:
        data = bytearray((logo.ROOT / "branding/logo-128.png").read_bytes())
        data[-1] ^= 1
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            logo.validate_png("logo-128.png", bytes(data))

    def test_missing_explicit_regeneration_inputs_are_rejected(self) -> None:
        result = subprocess.run([sys.executable, "-B", str(SCRIPT), "--regenerate"],
                                capture_output=True)
        self.assertEqual(2, result.returncode)

    def test_unverified_local_jar_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "client.jar"
            jar.write_bytes(b"not-an-approved-client")
            with self.assertRaisesRegex(ValueError, "官方来源不一致"):
                logo.load_resources(jar)


if __name__ == "__main__":
    unittest.main()
