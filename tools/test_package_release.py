"""公共打包自测；只使用本仓库工具、标准库和临时 fixture，不执行正式发布。"""

from __future__ import annotations

import hashlib
import io
import json
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest.mock import patch

import package_release as release


TEST_ROOT = release.PROJECT_ROOT / "run/release-tooling-tests"


def jar_bytes(metadata_changes: dict | None = None, extra: tuple[str, ...] = ()) -> bytes:
    metadata = {
        "version": release.VERSION,
        "name": "3790's Fashion",
        "id": "fashion_3790",
        "license": "MIT",
        "environment": "*",
    }
    metadata.update(metadata_changes or {})
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w") as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        archive.writestr("assets/fashion_3790/icon.png", b"icon")
        for name in extra:
            archive.writestr(name, b"fixture")
    return result.getvalue()


class ReleaseToolingTest(unittest.TestCase):
    def setUp(self) -> None:
        TEST_ROOT.mkdir(parents=True, exist_ok=True)
        if not TEST_ROOT.resolve().is_relative_to(release.PROJECT_ROOT.resolve()):
            raise ValueError("工具测试输出必须位于当前仓库的忽略目录中。")
        self.temporary = tempfile.TemporaryDirectory(prefix="case-", dir=TEST_ROOT)
        self.root = Path(self.temporary.name).resolve()
        if not self.root.is_relative_to(TEST_ROOT.resolve()):
            raise ValueError("临时测试目录不在已验证的输出目录内。")
        self.addCleanup(self.temporary.cleanup)

    def test_installation_readme_uses_explicit_public_template_in_both_layouts(self) -> None:
        (self.root / "public").mkdir()
        internal = self.root / "public/INSTALL.md"
        public = self.root / "INSTALL.md"
        internal.write_text("安装说明", encoding="utf-8")
        (self.root / "README.md").write_text("内部开发报告", encoding="utf-8")
        self.assertEqual(internal, release.installation_readme(self.root))
        internal.rename(public)
        self.assertEqual(public, release.installation_readme(self.root))
        self.assertEqual("安装说明", release.installation_readme(self.root).read_text("utf-8"))

    def test_authoritative_version_supports_current_and_future_versions(self) -> None:
        properties = self.root / "gradle.properties"
        for version in ("0.1.0", "0.2.0", "0.2.1", "0.3.0-rc.1"):
            with self.subTest(version=version):
                properties.write_text(f"\ufeff# 版本由 Gradle 管理\n mod_version = {version}\n", encoding="utf-8")
                self.assertEqual(version, release.read_project_version(properties))
        self.assertEqual(release.read_project_version(release.PROJECT_ROOT / "gradle.properties"), release.VERSION)

    def test_missing_duplicate_and_unsafe_versions_are_rejected(self) -> None:
        properties = self.root / "gradle.properties"
        for text in (
            "minecraft_version=26.2\n",
            "mod_version=0.2.0\nmod_version=0.1.0\n",
            "mod_version=../0.2.0\n",
            "mod_version=D:\\outside\n",
            "mod_version=0.2.0 release\n",
        ):
            with self.subTest(text=text):
                properties.write_text(text, encoding="utf-8")
                with self.assertRaises(ValueError):
                    release.read_project_version(properties)

    def test_version_change_during_packaging_is_rejected(self) -> None:
        (self.root / "gradle.properties").write_text("mod_version=0.1.0\n", encoding="utf-8")
        with patch.multiple(release, PROJECT_ROOT=self.root, VERSION="0.2.0"):
            with self.assertRaises(ValueError):
                release.require_project_version()

    def test_runtime_metadata_uses_version_and_stable_identity(self) -> None:
        release.audit_runtime_jar(jar_bytes())
        for key, value in (
            ("version", "0.1.0" if release.VERSION != "0.1.0" else "0.0.9"),
            ("name", "其他名称"),
            ("id", "other_mod"),
            ("license", "其他许可"),
            ("environment", "client"),
        ):
            with self.subTest(key=key):
                with self.assertRaises(ValueError):
                    release.audit_runtime_jar(jar_bytes({key: value}))

    def test_runtime_jar_rejects_internal_files_and_unsafe_paths(self) -> None:
        for name in (
            "reference/Creative Inventory 01.png",
            "docs/v0.2-release-notes.md",
            "AGENTS.md",
            "tools/notify_task_complete.ps1",
            "build/v02r-design/standard.png",
            "run/latest.log",
            "tests/fixture.txt",
            "dev/zbw3790/fashion/ReleaseTest.class",
            ".gradle/cache.bin",
            ".git/config",
            ".idea/workspace.xml",
            ".vscode/settings.json",
            "archive/old.bin",
            "assets/fashion_3790/textures/gui/frame.png",
            "../outside.txt",
            "run\\latest.log",
        ):
            with self.subTest(name=name):
                with self.assertRaises(ValueError):
                    release.audit_runtime_jar(jar_bytes(extra=(name,)))

    def test_runtime_jar_rejects_duplicate_entries(self) -> None:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            duplicate = jar_bytes(extra=("fabric.mod.json",))
        with self.assertRaises(ValueError):
            release.audit_runtime_jar(duplicate)

    def prepare_fixture(self):
        package_name = f"3790s-fashion-{release.VERSION}"
        jar_name = f"{package_name}.jar"
        jar = self.root / jar_name
        jar.write_bytes(jar_bytes())
        readme = self.root / "README.md"
        readme.write_text("发布工具自测说明\n", encoding="utf-8")
        license_path = self.root / "LICENSE"
        license_path.write_text("许可自测内容\n", encoding="utf-8")
        templates = self.root / "templates"
        for name, (_, files) in release.TEMPLATES.items():
            directory = templates / name
            directory.mkdir(parents=True)
            for file_name in files:
                (directory / file_name).write_bytes(f"{name}/{file_name}".encode())
            (directory / "内部说明.txt").write_text("不应进入发布包\n", encoding="utf-8")
        release_root = self.root / "release"
        return patch.multiple(
            release,
            JAR_PATH=jar,
            README_PATH=readme,
            LICENSE_PATH=license_path,
            TEMPLATE_ROOT=templates,
            RELEASE_ROOT=release_root,
            PACKAGE_DIRECTORY=release_root / package_name,
            ZIP_PATH=release_root / f"{package_name}-release.zip",
        )

    def test_fixture_package_is_deterministic_and_contains_only_allowed_payloads(self) -> None:
        with self.prepare_fixture():
            release.prepare_package_directory()
            release.write_release_zip(release.ZIP_PATH)
            first_bytes = release.ZIP_PATH.read_bytes()
            release.audit_release_zip(release.ZIP_PATH)
            release.verify_reproducible_zip()
            release.write_release_zip(release.ZIP_PATH)
            self.assertEqual(first_bytes, release.ZIP_PATH.read_bytes())
            with zipfile.ZipFile(release.ZIP_PATH) as archive:
                files = {info.filename for info in archive.infolist() if not info.is_dir()}
                expected = release.expected_checksum_names() | {"README.md", "LICENSE", "SHA256SUMS.txt"}
                self.assertEqual({f"{release.PACKAGE_NAME}/{name}" for name in expected}, files)
                checksum_text = archive.read(f"{release.PACKAGE_NAME}/SHA256SUMS.txt").decode()
                for line in checksum_text.splitlines():
                    digest, name = line.split("  ", 1)
                    self.assertEqual(hashlib.sha256(archive.read(f"{release.PACKAGE_NAME}/{name}")).hexdigest(), digest)

    def test_release_zip_rejects_unexpected_file_even_if_directory_matches(self) -> None:
        with self.prepare_fixture():
            release.prepare_package_directory()
            (release.PACKAGE_DIRECTORY / "内部说明.txt").write_text("不能公开\n", encoding="utf-8")
            release.write_release_zip(release.ZIP_PATH)
            with self.assertRaises(ValueError):
                release.audit_release_zip(release.ZIP_PATH)

    def test_release_zip_rejects_unexpected_empty_directory(self) -> None:
        with self.prepare_fixture():
            release.prepare_package_directory()
            (release.PACKAGE_DIRECTORY / "internal").mkdir()
            release.write_release_zip(release.ZIP_PATH)
            with self.assertRaises(ValueError):
                release.audit_release_zip(release.ZIP_PATH)

    def test_checksums_reject_duplicates_and_wrong_bytes(self) -> None:
        with self.prepare_fixture():
            release.prepare_package_directory()
            release.write_release_zip(release.ZIP_PATH)
            with zipfile.ZipFile(release.ZIP_PATH) as archive:
                checksum_text = archive.read(f"{release.PACKAGE_NAME}/SHA256SUMS.txt").decode()
                with self.assertRaises(ValueError):
                    release.audit_checksums(archive, checksum_text + checksum_text.splitlines()[0] + "\n")
                with self.assertRaises(ValueError):
                    release.audit_checksums(archive, "0" * 64 + checksum_text[64:])


if __name__ == "__main__":
    unittest.main()
