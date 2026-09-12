package dev.zbw3790.fashion.client.render;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.client.cape.ClientCapeRegistry;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureResolver;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;

public final class PlayerFashionAppearanceResolver {
	private final ClientPlayerFashionRegistry fashions;
	private final ClientCapeRegistry capes;
	private final Function<CapeCosmeticMetadata, Optional<Identifier>> capeTexture;
	private final Function<CapeCosmeticMetadata, ElytraTextureDecision> elytraTexture;

	public PlayerFashionAppearanceResolver(ClientPlayerFashionRegistry fashions, ClientCapeRegistry capes,
			ClientCapeTextureResolver textures) {
		this(fashions, capes, textures::resolveCape, textures::resolveElytra);
	}

	PlayerFashionAppearanceResolver(ClientPlayerFashionRegistry fashions, ClientCapeRegistry capes,
			Function<CapeCosmeticMetadata, Optional<Identifier>> capeTexture,
			Function<CapeCosmeticMetadata, ElytraTextureDecision> elytraTexture) {
		this.fashions = fashions;
		this.capes = capes;
		this.capeTexture = capeTexture;
		this.elytraTexture = elytraTexture;
	}

	public PlayerFashionRenderAppearance resolve(UUID playerId) {
		var known = fashions.getKnownState(playerId);
		if (known.isEmpty()) {
			return PlayerFashionRenderAppearance.unknown();
		}
		var selection = known.orElseThrow().effectiveSelection();
		if (selection.isEmpty()) {
			return PlayerFashionRenderAppearance.vanilla();
		}
		// 元数据或 GPU 纹理未到达只影响本帧，绝不改写权威 CapeId。
		var metadata = capes.find(selection.orElseThrow());
		return PlayerFashionRenderAppearance.serverCosmetic(
				metadata.flatMap(capeTexture),
				metadata.map(elytraTexture).orElseGet(ElytraTextureDecision::vanillaDefault));
	}
}
