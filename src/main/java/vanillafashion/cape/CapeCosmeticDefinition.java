package vanillafashion.cape;

import java.util.Objects;
import java.util.Optional;

public record CapeCosmeticDefinition(
		CapeId id,
		CapeTextureAsset cape,
		Optional<CapeTextureAsset> elytra
) {
	public CapeCosmeticDefinition {
		Objects.requireNonNull(id, "Cape Cosmetic ID 不能为 null。");
		Objects.requireNonNull(cape, "Cape Cosmetic 必须包含 cape 纹理。");
		elytra = Objects.requireNonNull(elytra, "elytra 状态必须使用 Optional 表达。");

		if (cape.type() != CapeTextureType.CAPE) {
			throw new IllegalArgumentException("cape 资产的纹理角色必须为 CAPE。");
		}

		if (elytra.isPresent() && elytra.get().type() != CapeTextureType.ELYTRA) {
			throw new IllegalArgumentException("elytra 资产的纹理角色必须为 ELYTRA。");
		}
	}
}
