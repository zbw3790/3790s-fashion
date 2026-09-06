package vanillafashion.cape;

import java.util.Objects;
import java.util.Optional;

public record CapeCosmeticMetadata(CapeId id, String capeSha256, Optional<String> elytraSha256) {
	public static final int SHA_256_LENGTH = CapeAssetHash.SHA_256_LENGTH;

	public CapeCosmeticMetadata {
		id = Objects.requireNonNull(id, "Cape Cosmetic 元数据 ID 不能为 null。");
		validateSha256(capeSha256, "cape");
		elytraSha256 = Objects.requireNonNull(elytraSha256, "elytra SHA-256 状态必须使用 Optional 表达。");
		elytraSha256.ifPresent(hash -> validateSha256(hash, "elytra"));
	}

	public boolean hasElytra() {
		return elytraSha256.isPresent();
	}

	private static void validateSha256(String sha256, String textureName) {
		CapeAssetHash.requireValid(sha256, textureName + " SHA-256 ");
	}
}
