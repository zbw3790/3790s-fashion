package vanillafashion.cape;

import java.nio.file.Path;
import java.util.Objects;

public record CapeTextureAsset(
		Path source,
		String sha256,
		int width,
		int height,
		CapeTextureType type
) {
	public static final int REQUIRED_WIDTH = 64;
	public static final int REQUIRED_HEIGHT = 32;

	public CapeTextureAsset {
		Objects.requireNonNull(source, "纹理来源路径不能为 null。");
		Objects.requireNonNull(type, "纹理角色不能为 null。");

		CapeAssetHash.requireValid(sha256, "SHA-256 ");

		if (width != REQUIRED_WIDTH || height != REQUIRED_HEIGHT) {
			throw new IllegalArgumentException("披风纹理尺寸必须严格为 64×32 px。");
		}
	}
}
