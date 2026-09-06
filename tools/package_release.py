"""生成并审计 Vanilla Fashion v0.1.0 的确定性完整发布包。"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import shutil
import subprocess
import zipfile
from pathlib import Path, PurePosixPath


PROJECT_ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.1.0"
PACKAGE_NAME = f"vanilla-fashion-{VERSION}"
JAR_NAME = f"{PACKAGE_NAME}.jar"
JAR_PATH = PROJECT_ROOT / "build/libs" / JAR_NAME
RELEASE_ROOT = PROJECT_ROOT / "build/release"
PACKAGE_DIRECTORY = RELEASE_ROOT / PACKAGE_NAME
ZIP_PATH = RELEASE_ROOT / f"{PACKAGE_NAME}-release.zip"
README_PATH = PROJECT_ROOT / "README.md"
LICENSE_PATH = PROJECT_ROOT / "LICENSE"
VALIDATOR_SOURCE = PROJECT_ROOT / "tools/PackageReleaseValidator.java"
MAIN_CLASSES = PROJECT_ROOT / "build/classes/java/main"
INTERNAL_TEMPLATE_ROOT = PROJECT_ROOT / "dev-assets/capes"
PUBLIC_TEMPLATE_ROOT = PROJECT_ROOT / "templates/capes"
TEMPLATE_ROOT = (
    INTERNAL_TEMPLATE_ROOT
    if INTERNAL_TEMPLATE_ROOT.is_dir()
    else PUBLIC_TEMPLATE_ROOT
)
TEMPLATES = {
    "blue-migrator": ("SHARED", ("cape_elytra.png",)),
    "rose-red-migrator": ("SPLIT", ("cape.png", "elytra.png")),
}
RECOGNIZED_FILES = {"cape.png", "elytra.png", "cape_elytra.png"}
OS_JUNK = {".DS_Store", "Thumbs.db", "desktop.ini"}
FIXED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)
FORBIDDEN_SEGMENTS = {
    ".git",
    ".gradle",
    "build",
    "cache",
    "dev-assets",
    "gradle",
    "logs",
    "run",
    "tools",
    "world",
    "worlds",
}
FORBIDDEN_NAMES = {
    "build.gradle",
    "eula.txt",
    "gradlew",
    "gradlew.bat",
    "server.jar",
    "settings.gradle",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require_project_version() -> None:
    properties = (PROJECT_ROOT / "gradle.properties").read_text(encoding="utf-8")
    versions = {
        key.strip(): value.strip()
        for line in properties.splitlines()
        if line and not line.startswith("#") and "=" in line
        for key, value in (line.split("=", 1),)
    }
    if versions.get("mod_version") != VERSION:
        raise ValueError(
            f"打包版本与项目版本不一致：脚本={VERSION}，项目={versions.get('mod_version')}"
        )


def require_safe_generated_path(path: Path) -> None:
    release_root = RELEASE_ROOT.resolve()
    resolved = path.resolve(strict=False)
    if resolved == release_root or not resolved.is_relative_to(release_root):
        raise ValueError(f"拒绝操作发布目录以外的路径：{path}")
    if path.is_symlink():
        raise ValueError(f"拒绝操作符号链接生成目标：{path}")


def inspect_template_tree(directory: Path) -> None:
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError(f"模板目录不存在、不是普通目录或是符号链接：{directory}")
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"模板中不允许符号链接：{path}")
        if path.name in OS_JUNK:
            raise ValueError(f"模板中发现 OS 垃圾文件，未删除也未打包：{path}")
        if not path.is_file() and not path.is_dir():
            raise ValueError(f"模板中存在不支持的文件系统对象：{path}")


def validate_template(name: str, layout: str, recognized_files: tuple[str, ...]) -> None:
    directory = TEMPLATE_ROOT / name
    inspect_template_tree(directory)

    actual_recognized = {
        path.name for path in directory.iterdir() if path.name in RECOGNIZED_FILES
    }
    if actual_recognized != set(recognized_files):
        raise ValueError(
            f"模板 recognized 文件不符：{name}，预期={sorted(recognized_files)}，"
            f"实际={sorted(actual_recognized)}"
        )
    for file_name in recognized_files:
        asset = directory / file_name
        if not asset.is_file() or asset.is_symlink():
            raise ValueError(f"recognized 资产不是普通文件：{asset}")

    command = [
        "java",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "--class-path",
        str(MAIN_CLASSES),
        str(VALIDATOR_SOURCE),
        str(directory),
        layout,
    ]
    completed = subprocess.run(
        command,
        cwd=PROJECT_ROOT,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if completed.returncode != 0:
        details = (completed.stdout + completed.stderr).strip()
        raise ValueError(f"正式 Validator 拒绝模板 {name}：\n{details}")
    print(completed.stdout.strip())


def validate_inputs() -> None:
    require_project_version()
    for required in (JAR_PATH, README_PATH, LICENSE_PATH, VALIDATOR_SOURCE):
        if not required.is_file():
            raise FileNotFoundError(f"发布输入不存在：{required}")
    if not MAIN_CLASSES.is_dir():
        raise FileNotFoundError(
            f"正式 Validator class 不存在，请先执行 clean build：{MAIN_CLASSES}"
        )
    for name, (layout, recognized_files) in TEMPLATES.items():
        validate_template(name, layout, recognized_files)


def checksum_paths() -> list[Path]:
    paths = [Path(JAR_NAME)]
    for name, (_, recognized_files) in TEMPLATES.items():
        paths.extend(
            Path("templates/capes") / name / file_name
            for file_name in recognized_files
        )
    return sorted(paths, key=lambda path: path.as_posix())


def write_checksums() -> None:
    lines = [
        f"{sha256(PACKAGE_DIRECTORY / relative)}  {relative.as_posix()}"
        for relative in checksum_paths()
    ]
    (PACKAGE_DIRECTORY / "SHA256SUMS.txt").write_bytes(
        ("\n".join(lines) + "\n").encode("utf-8")
    )


def prepare_package_directory() -> None:
    RELEASE_ROOT.mkdir(parents=True, exist_ok=True)
    require_safe_generated_path(PACKAGE_DIRECTORY)
    require_safe_generated_path(ZIP_PATH)

    if PACKAGE_DIRECTORY.exists():
        shutil.rmtree(PACKAGE_DIRECTORY)
    if ZIP_PATH.exists():
        ZIP_PATH.unlink()

    PACKAGE_DIRECTORY.mkdir()
    shutil.copyfile(JAR_PATH, PACKAGE_DIRECTORY / JAR_NAME)
    shutil.copyfile(README_PATH, PACKAGE_DIRECTORY / "README.md")
    shutil.copyfile(LICENSE_PATH, PACKAGE_DIRECTORY / "LICENSE")

    target_root = PACKAGE_DIRECTORY / "templates/capes"
    target_root.mkdir(parents=True)
    for name in TEMPLATES:
        shutil.copytree(
            TEMPLATE_ROOT / name,
            target_root / name,
            copy_function=shutil.copyfile,
        )
    write_checksums()


def release_entries() -> list[tuple[Path, str, bool]]:
    paths = [PACKAGE_DIRECTORY, *PACKAGE_DIRECTORY.rglob("*")]
    entries: list[tuple[Path, str, bool]] = []
    for path in paths:
        if path.is_symlink():
            raise ValueError(f"发布目录中不允许符号链接：{path}")
        relative = path.relative_to(RELEASE_ROOT).as_posix()
        is_directory = path.is_dir()
        entries.append((path, relative + ("/" if is_directory else ""), is_directory))
    return sorted(entries, key=lambda item: item[1])


def write_release_zip(destination: Path) -> None:
    require_safe_generated_path(destination)
    if destination.exists():
        destination.unlink()
    with zipfile.ZipFile(destination, "w") as archive:
        for path, archive_name, is_directory in release_entries():
            info = zipfile.ZipInfo(archive_name, FIXED_ZIP_TIME)
            info.create_system = 3
            info.flag_bits |= 0x800
            if is_directory:
                info.compress_type = zipfile.ZIP_STORED
                info.external_attr = (0o40755 << 16) | 0x10
                archive.writestr(info, b"")
            else:
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(
                    info,
                    path.read_bytes(),
                    compress_type=zipfile.ZIP_DEFLATED,
                    compresslevel=9,
                )


def audit_runtime_jar(jar_bytes: bytes) -> None:
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as archive:
        names = archive.namelist()
        forbidden = [
            name
            for name in names
            if any(
                marker in name
                for marker in (
                    "blue-migrator",
                    "rose-red-migrator",
                    "templates/",
                    "dev-assets/",
                )
            )
            or name.endswith(("Test.class", ".java", ".kt"))
        ]
        if forbidden:
            raise ValueError(f"Runtime JAR 包含发布禁用内容：{forbidden}")
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata.get("version") != VERSION or metadata.get("environment") != "*":
            raise ValueError("Runtime JAR 的版本或 environment 不符合 v0.1.0 冻结要求")


def expected_checksum_names() -> set[str]:
    return {path.as_posix() for path in checksum_paths()}


def audit_checksums(archive: zipfile.ZipFile, checksum_text: str) -> None:
    found: dict[str, str] = {}
    for line in checksum_text.splitlines():
        digest, separator, relative = line.partition("  ")
        if separator != "  " or len(digest) != 64 or digest.lower() != digest:
            raise ValueError(f"SHA256SUMS.txt 格式不合法：{line}")
        int(digest, 16)
        found[relative] = digest
    if set(found) != expected_checksum_names():
        raise ValueError(
            f"SHA256SUMS.txt 条目不符：预期={sorted(expected_checksum_names())}，"
            f"实际={sorted(found)}"
        )
    for relative, expected in found.items():
        actual = hashlib.sha256(
            archive.read(f"{PACKAGE_NAME}/{relative}")
        ).hexdigest()
        if actual != expected:
            raise ValueError(f"checksum 不匹配：{relative}")


def audit_release_zip(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"完整发布 ZIP 不存在：{path}")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        expected_names = [archive_name for _, archive_name, _ in release_entries()]
        if names != expected_names:
            raise ValueError("ZIP 条目顺序或内容与发布目录不一致")
        top_levels: set[str] = set()
        for name in names:
            if "\\" in name:
                raise ValueError(f"ZIP 路径使用了非标准分隔符：{name}")
            pure = PurePosixPath(name.rstrip("/"))
            if pure.is_absolute() or ".." in pure.parts or not pure.parts:
                raise ValueError(f"ZIP 包含不安全路径：{name}")
            top_levels.add(pure.parts[0])
            if any(part in FORBIDDEN_SEGMENTS for part in pure.parts):
                raise ValueError(f"ZIP 包含禁止目录：{name}")
            if pure.name in FORBIDDEN_NAMES or pure.name.endswith("Test.class"):
                raise ValueError(f"ZIP 包含禁止文件：{name}")
        if top_levels != {PACKAGE_NAME}:
            raise ValueError(f"ZIP 顶层必须只有 {PACKAGE_NAME}/：{sorted(top_levels)}")

        for path_on_disk, archive_name, is_directory in release_entries():
            if not is_directory and archive.read(archive_name) != path_on_disk.read_bytes():
                raise ValueError(f"ZIP 文件字节与发布目录不一致：{archive_name}")

        nested_jar_name = f"{PACKAGE_NAME}/{JAR_NAME}"
        nested_jar = archive.read(nested_jar_name)
        if nested_jar != JAR_PATH.read_bytes():
            raise ValueError("ZIP 中的 Runtime JAR 与 build/libs 正式 JAR 不一致")
        audit_runtime_jar(nested_jar)

        checksum_name = f"{PACKAGE_NAME}/SHA256SUMS.txt"
        audit_checksums(archive, archive.read(checksum_name).decode("utf-8"))


def verify_reproducible_zip() -> None:
    check_path = RELEASE_ROOT / f".{PACKAGE_NAME}-reproducible-check.zip"
    require_safe_generated_path(check_path)
    try:
        write_release_zip(check_path)
        if check_path.read_bytes() != ZIP_PATH.read_bytes():
            raise ValueError("相同输入重复打包得到不同 ZIP 字节")
    finally:
        if check_path.exists():
            check_path.unlink()


def report() -> None:
    with zipfile.ZipFile(ZIP_PATH) as archive:
        print(f"发布 JAR：{JAR_PATH.relative_to(PROJECT_ROOT).as_posix()}")
        print(f"JAR SHA-256：{sha256(JAR_PATH)}")
        print(f"完整发布包：{ZIP_PATH.relative_to(PROJECT_ROOT).as_posix()}")
        print(f"ZIP 字节数：{ZIP_PATH.stat().st_size}")
        print(f"ZIP SHA-256：{sha256(ZIP_PATH)}")
        print(f"ZIP 条目数：{len(archive.infolist())}")
        print("checksum、路径安全、白名单与可重复性审计：PASS")


def main() -> None:
    parser = argparse.ArgumentParser(description="生成并验证 v0.1.0 完整发布包。")
    parser.add_argument(
        "--verify",
        action="store_true",
        help="只读验证现有发布目录与 ZIP，不重新生成。",
    )
    args = parser.parse_args()

    validate_inputs()
    if not args.verify:
        prepare_package_directory()
        write_release_zip(ZIP_PATH)
    audit_release_zip(ZIP_PATH)
    verify_reproducible_zip()
    report()


if __name__ == "__main__":
    main()
