package dev.zbw3790.fashion.client.cape;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.client.render.ElytraTextureDecision;

public final class ClientCapeTextureResolver {
	private final ClientCapeTextureManager textureManager;

	public ClientCapeTextureResolver(ClientCapeTextureManager textureManager) {
		this.textureManager = Objects.requireNonNull(
				textureManager,
				"客户端 Cape Texture Manager 不能为 null。"
		);
	}

	public Optional<Identifier> resolveCape(CapeCosmeticMetadata metadata) {
		Objects.requireNonNull(metadata, "Cape Cosmetic 元数据不能为 null。");
		return textureManager.find(metadata.capeSha256());
	}

	public Optional<Identifier> resolveCape(Optional<CapeCosmeticMetadata> selectedMetadata) {
		return Objects.requireNonNull(selectedMetadata, "已选择 Cape 元数据状态不能为 null。")
				.flatMap(this::resolveCape);
	}

	public ElytraTextureDecision resolveElytra(CapeCosmeticMetadata metadata) {
		Objects.requireNonNull(metadata, "Cape Cosmetic 元数据不能为 null。");

		if (metadata.elytraSha256().isEmpty()) {
			return ElytraTextureDecision.vanillaDefault();
		}

		return textureManager.find(metadata.elytraSha256().orElseThrow())
				.map(ElytraTextureDecision::customTexture)
				.orElseGet(ElytraTextureDecision::vanillaDefault);
	}

	public ElytraTextureDecision resolveElytra(Optional<CapeCosmeticMetadata> selectedMetadata) {
		return Objects.requireNonNull(selectedMetadata, "已选择 Cape 元数据状态不能为 null。")
				.map(this::resolveElytra)
				.orElseGet(ElytraTextureDecision::passThrough);
	}
}
