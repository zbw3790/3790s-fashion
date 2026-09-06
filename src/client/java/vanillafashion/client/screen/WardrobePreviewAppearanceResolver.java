package vanillafashion.client.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.client.cape.ClientCapeTextureResolver;
import vanillafashion.client.render.ElytraTextureDecision;
import vanillafashion.client.render.WardrobePreviewAppearance;

final class WardrobePreviewAppearanceResolver {
	private final Map<CapeId, CapeCosmeticMetadata> metadataById;
	private final Function<CapeCosmeticMetadata, Optional<Identifier>> capeTextureResolver;
	private final Function<CapeCosmeticMetadata, ElytraTextureDecision> elytraTextureResolver;

	WardrobePreviewAppearanceResolver(
			List<CapeCosmeticMetadata> metadataEntries,
			ClientCapeTextureResolver textureResolver
	) {
		this(
				metadataEntries,
				Objects.requireNonNull(textureResolver, "衣柜预览纹理解析器不能为 null。")::resolveCape,
				textureResolver::resolveElytra
		);
	}

	WardrobePreviewAppearanceResolver(
			List<CapeCosmeticMetadata> metadataEntries,
			Function<CapeCosmeticMetadata, Optional<Identifier>> capeTextureResolver,
			Function<CapeCosmeticMetadata, ElytraTextureDecision> elytraTextureResolver
	) {
		Objects.requireNonNull(metadataEntries, "衣柜预览 Cape 元数据列表不能为 null。");
		this.capeTextureResolver = Objects.requireNonNull(capeTextureResolver, "Cape 纹理解析函数不能为 null。");
		this.elytraTextureResolver = Objects.requireNonNull(elytraTextureResolver, "Elytra 纹理解析函数不能为 null。");
		Map<CapeId, CapeCosmeticMetadata> collectedMetadata = new LinkedHashMap<>();

		for (CapeCosmeticMetadata metadata : metadataEntries) {
			CapeCosmeticMetadata nonNullMetadata = Objects.requireNonNull(
					metadata,
					"衣柜预览 Cape 元数据不能为 null。"
			);
			collectedMetadata.putIfAbsent(nonNullMetadata.id(), nonNullMetadata);
		}

		metadataById = Map.copyOf(collectedMetadata);
	}

	WardrobePreviewAppearance resolve(Optional<CapeId> selectedCapeId) {
		Objects.requireNonNull(selectedCapeId, "衣柜 draft Cape ID 状态不能为 null。");

		if (selectedCapeId.isEmpty()) {
			return WardrobePreviewAppearance.vanilla();
		}

		CapeCosmeticMetadata metadata = metadataById.get(selectedCapeId.orElseThrow());
		if (metadata == null) {
			return WardrobePreviewAppearance.vanilla();
		}

		return WardrobePreviewAppearance.serverCosmetic(
				capeTextureResolver.apply(metadata),
				elytraTextureResolver.apply(metadata)
		);
	}
}
