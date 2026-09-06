package vanillafashion.cape;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

public final class CapeAssetReader {
	private CapeAssetReader() {
	}

	public static CapeAssetReadResult readVerified(CapeTextureAsset asset) {
		Objects.requireNonNull(asset, "待读取的 Cape 资产不能为 null。");

		if (!Files.isRegularFile(asset.source())) {
			return CapeAssetReadResult.failure(CapeAssetReadResult.Status.NOT_REGULAR_FILE);
		}

		try {
			long fileSize = Files.size(asset.source());

			if (fileSize == 0) {
				return CapeAssetReadResult.failure(CapeAssetReadResult.Status.EMPTY);
			}

			if (fileSize > CapeAssetLimits.MAX_ASSET_BYTES) {
				return CapeAssetReadResult.failure(CapeAssetReadResult.Status.TOO_LARGE);
			}

			byte[] bytes = Files.readAllBytes(asset.source());

			if (bytes.length == 0) {
				return CapeAssetReadResult.failure(CapeAssetReadResult.Status.EMPTY);
			}

			if (bytes.length > CapeAssetLimits.MAX_ASSET_BYTES) {
				return CapeAssetReadResult.failure(CapeAssetReadResult.Status.TOO_LARGE);
			}

			if (!asset.sha256().equals(CapeAssetHash.sha256(bytes))) {
				return CapeAssetReadResult.failure(CapeAssetReadResult.Status.HASH_MISMATCH);
			}

			return CapeAssetReadResult.success(bytes);
		} catch (IOException | SecurityException exception) {
			return CapeAssetReadResult.failure(CapeAssetReadResult.Status.IO_ERROR);
		}
	}
}
