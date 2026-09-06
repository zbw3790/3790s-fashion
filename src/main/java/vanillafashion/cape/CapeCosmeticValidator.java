package vanillafashion.cape;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CapeCosmeticValidator {
	public CapeCosmeticValidationResult validate(Path directory) {
		Objects.requireNonNull(directory, "Cape Cosmetic 目录路径不能为 null。");

		if (!Files.isDirectory(directory)) {
			return failure(CapeValidationErrorCode.NOT_A_DIRECTORY, directory, "输入路径不是目录。");
		}

		Path fileName = directory.getFileName();
		if (fileName == null) {
			return failure(CapeValidationErrorCode.INVALID_ID, directory, "无法从目录路径确定 Cosmetic ID。");
		}

		CapeId id;
		try {
			id = new CapeId(fileName.toString());
		} catch (IllegalArgumentException exception) {
			return failure(CapeValidationErrorCode.INVALID_ID, directory, "Cape Cosmetic 目录名称不是合法 Cosmetic ID。");
		}

		// 先按实际、严格区分大小写的直接子项名称判断布局。目录和断链也参与存在性判断，
		// 不能先用图片有效性或普通文件判断把损坏的 recognized path 当成缺失。
		Set<String> names;
		try (Stream<Path> children = Files.list(directory)) {
			names = children.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
		} catch (IOException | UncheckedIOException | SecurityException exception) {
			return failure(CapeValidationErrorCode.IO_ERROR, directory, "读取纹理目录布局时发生 I/O 错误。");
		}

		boolean hasCape = names.contains(TextureFile.CAPE.fileName);
		boolean hasElytra = names.contains(TextureFile.ELYTRA.fileName);
		boolean hasShared = names.contains(TextureFile.SHARED.fileName);

		if (hasShared && (hasCape || hasElytra)) {
			return failure(
					CapeValidationErrorCode.ASSET_LAYOUT_CONFLICT,
					directory,
					"已识别的资产布局冲突：cape_elytra.png 不能与 cape.png 或 elytra.png 同时存在。"
			);
		}
		if (!hasShared && !hasCape) {
			return failure(
					CapeValidationErrorCode.MISSING_CAPE,
					directory.resolve(TextureFile.CAPE.fileName),
					"Cape Cosmetic 必须提供 cape.png 或单独的 cape_elytra.png。"
			);
		}

		List<CapeValidationIssue> issues = new ArrayList<>();
		Optional<ValidatedTexture> cape;
		Optional<ValidatedTexture> elytra;

		if (hasShared) {
			// SHARED 只读取、校验和计算一次物理内容，再构造两个不同角色的逻辑资产。
			cape = validateTexture(directory, TextureFile.SHARED, issues);
			elytra = cape;
		} else {
			// 已确定为 SPLIT 时，损坏的 elytra.png 必须拒绝整个条目，不降级 CAPE_ONLY。
			cape = validateTexture(directory, TextureFile.CAPE, issues);
			elytra = hasElytra ? validateTexture(directory, TextureFile.ELYTRA, issues) : Optional.empty();
		}

		if (!issues.isEmpty()) {
			return CapeCosmeticValidationResult.failure(issues);
		}

		return CapeCosmeticValidationResult.success(new CapeCosmeticDefinition(
				id,
				cape.orElseThrow().asAsset(CapeTextureType.CAPE),
				elytra.map(texture -> texture.asAsset(CapeTextureType.ELYTRA))
		));
	}

	private Optional<ValidatedTexture> validateTexture(
			Path directory,
			TextureFile file,
			List<CapeValidationIssue> issues
	) {
		Path path = directory.resolve(file.fileName);
		// 沿用服务器资产的普通文件检查：跟随链接检查目标；不放宽为任意可读取路径。
		if (!Files.isRegularFile(path)) {
			issues.add(new CapeValidationIssue(file.notRegularCode, path, file.fileName + " 必须是普通文件。"));
			return Optional.empty();
		}

		byte[] pngBytes;
		try {
			if (Files.size(path) > CapeAssetLimits.MAX_ASSET_BYTES) {
				issues.add(tooLargeIssue(path, file));
				return Optional.empty();
			}
			pngBytes = Files.readAllBytes(path);
		} catch (IOException | SecurityException exception) {
			issues.add(new CapeValidationIssue(
					CapeValidationErrorCode.IO_ERROR, path, "读取纹理文件时发生 I/O 错误。"
			));
			return Optional.empty();
		}

		if (pngBytes.length > CapeAssetLimits.MAX_ASSET_BYTES) {
			issues.add(tooLargeIssue(path, file));
			return Optional.empty();
		}

		CapePngValidator.ValidationResult pngValidation = CapePngValidator.validate(pngBytes);
		if (pngValidation == CapePngValidator.ValidationResult.INVALID_PNG) {
			issues.add(new CapeValidationIssue(
					file.invalidPngCode, path, file.fileName + " 必须是能够正常解码的真实 PNG 文件。"
			));
			return Optional.empty();
		}
		if (pngValidation == CapePngValidator.ValidationResult.INVALID_DIMENSIONS) {
			issues.add(new CapeValidationIssue(
					file.dimensionsCode, path, file.fileName + " 尺寸必须严格为 64×32 px。"
			));
			return Optional.empty();
		}
		return Optional.of(new ValidatedTexture(path, CapeAssetHash.sha256(pngBytes)));
	}

	private static CapeValidationIssue tooLargeIssue(Path path, TextureFile file) {
		return new CapeValidationIssue(
				file.tooLargeCode, path, file.fileName + " 不能超过 " + CapeAssetLimits.MAX_ASSET_BYTES + " 字节。"
		);
	}

	private static CapeCosmeticValidationResult failure(CapeValidationErrorCode code, Path path, String message) {
		return CapeCosmeticValidationResult.failure(List.of(new CapeValidationIssue(code, path, message)));
	}

	// 只描述物理文件及诊断，不把共享文件发明为第三种逻辑纹理角色。
	private enum TextureFile {
		CAPE("cape.png", CapeValidationErrorCode.CAPE_NOT_REGULAR_FILE,
				CapeValidationErrorCode.CAPE_FILE_TOO_LARGE, CapeValidationErrorCode.INVALID_CAPE_PNG,
				CapeValidationErrorCode.INVALID_CAPE_DIMENSIONS),
		ELYTRA("elytra.png", CapeValidationErrorCode.ELYTRA_NOT_REGULAR_FILE,
				CapeValidationErrorCode.ELYTRA_FILE_TOO_LARGE, CapeValidationErrorCode.INVALID_ELYTRA_PNG,
				CapeValidationErrorCode.INVALID_ELYTRA_DIMENSIONS),
		SHARED("cape_elytra.png", CapeValidationErrorCode.SHARED_NOT_REGULAR_FILE,
				CapeValidationErrorCode.SHARED_FILE_TOO_LARGE, CapeValidationErrorCode.INVALID_SHARED_PNG,
				CapeValidationErrorCode.INVALID_SHARED_DIMENSIONS);

		private final String fileName;
		private final CapeValidationErrorCode notRegularCode;
		private final CapeValidationErrorCode tooLargeCode;
		private final CapeValidationErrorCode invalidPngCode;
		private final CapeValidationErrorCode dimensionsCode;

		TextureFile(String fileName, CapeValidationErrorCode notRegularCode, CapeValidationErrorCode tooLargeCode,
				CapeValidationErrorCode invalidPngCode, CapeValidationErrorCode dimensionsCode) {
			this.fileName = fileName;
			this.notRegularCode = notRegularCode;
			this.tooLargeCode = tooLargeCode;
			this.invalidPngCode = invalidPngCode;
			this.dimensionsCode = dimensionsCode;
		}
	}

	private record ValidatedTexture(Path source, String sha256) {
		CapeTextureAsset asAsset(CapeTextureType type) {
			return new CapeTextureAsset(source, sha256, CapeTextureAsset.REQUIRED_WIDTH,
					CapeTextureAsset.REQUIRED_HEIGHT, type);
		}
	}
}
