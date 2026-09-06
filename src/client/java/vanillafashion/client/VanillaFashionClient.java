package vanillafashion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vanillafashion.VanillaFashion;
import vanillafashion.client.cape.ClientCapeAssetCache;
import vanillafashion.client.cape.ClientCapeAssetStore;
import vanillafashion.client.cape.ClientCapeAssetSync;
import vanillafashion.client.cape.ClientCapeRegistry;
import vanillafashion.client.cape.ClientCapeTextureManager;
import vanillafashion.client.cape.ClientCapeTextureResolver;
import vanillafashion.client.network.VanillaFashionClientNetworking;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.render.PlayerFashionAppearanceResolver;
import vanillafashion.client.render.PlayerFashionWorldRendering;
import vanillafashion.client.render.VanillaFashionCapeRendering;

public final class VanillaFashionClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("vanilla_fashion/client");
	private static final ClientCapeRegistry CAPE_REGISTRY = new ClientCapeRegistry();
	private static final ClientPlayerFashionRegistry PLAYER_FASHIONS = new ClientPlayerFashionRegistry();
	private static final ClientCapeAssetStore CAPE_ASSET_STORE = new ClientCapeAssetStore();
	private static final ClientCapeAssetCache CAPE_ASSET_CACHE = ClientCapeAssetCache.fromGameDirectory(
			FabricLoader.getInstance().getGameDir(),
			LOGGER
	);
	private static final ClientCapeAssetSync CAPE_ASSET_SYNC =
			new ClientCapeAssetSync(CAPE_ASSET_STORE, CAPE_ASSET_CACHE);
	private static final ClientCapeTextureManager CAPE_TEXTURE_MANAGER = new ClientCapeTextureManager();
	private static final ClientCapeTextureResolver CAPE_TEXTURE_RESOLVER =
			new ClientCapeTextureResolver(CAPE_TEXTURE_MANAGER);

	@Override
	public void onInitializeClient() {
		VanillaFashionClientNetworking.register(
				VanillaFashion.wardrobeServerAvailability(),
				CAPE_REGISTRY,
				PLAYER_FASHIONS,
				CAPE_ASSET_STORE,
				CAPE_ASSET_SYNC,
				CAPE_TEXTURE_MANAGER,
				LOGGER
		);
		PlayerFashionWorldRendering.register(new PlayerFashionAppearanceResolver(
				PLAYER_FASHIONS, CAPE_REGISTRY, CAPE_TEXTURE_RESOLVER));
		VanillaFashionCapeRendering.register(LOGGER);

		LOGGER.info("Vanilla Fashion 客户端初始化完成，资产链、玩家时装同步与世界外观提取已注册。");
	}

	public static ClientCapeRegistry capeRegistry() {
		return CAPE_REGISTRY;
	}

	public static ClientCapeAssetStore capeAssetStore() {
		return CAPE_ASSET_STORE;
	}

	public static ClientCapeAssetCache capeAssetCache() {
		return CAPE_ASSET_CACHE;
	}

	public static ClientCapeTextureManager capeTextureManager() {
		return CAPE_TEXTURE_MANAGER;
	}
}
