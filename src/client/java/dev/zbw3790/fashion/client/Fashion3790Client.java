package dev.zbw3790.fashion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.client.cape.ClientCapeAssetCache;
import dev.zbw3790.fashion.client.cape.ClientCapeAssetStore;
import dev.zbw3790.fashion.client.cape.ClientCapeAssetSync;
import dev.zbw3790.fashion.client.cape.ClientCapeRegistry;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureManager;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureResolver;
import dev.zbw3790.fashion.client.network.Fashion3790ClientNetworking;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.render.PlayerFashionAppearanceResolver;
import dev.zbw3790.fashion.client.render.PlayerFashionWorldRendering;
import dev.zbw3790.fashion.client.render.Fashion3790CapeRendering;

public final class Fashion3790Client implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("fashion_3790/client");
	private static final dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry OUTFIT_REGISTRY = new dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry();
    private static final dev.zbw3790.fashion.client.outfit.ClientOutfitAssetStore OUTFIT_ASSETS = new dev.zbw3790.fashion.client.outfit.ClientOutfitAssetStore();
    private static final dev.zbw3790.fashion.client.outfit.ClientOutfitTextureManager OUTFIT_TEXTURES = new dev.zbw3790.fashion.client.outfit.ClientOutfitTextureManager();
    private static final dev.zbw3790.fashion.client.outfit.ClientOutfitAssetSync OUTFIT_SYNC = new dev.zbw3790.fashion.client.outfit.ClientOutfitAssetSync(
            OUTFIT_REGISTRY, OUTFIT_ASSETS, dev.zbw3790.fashion.client.outfit.ClientOutfitAssetCache.fromGameDirectory(FabricLoader.getInstance().getGameDir(), LOGGER));
    private static final dev.zbw3790.fashion.client.armor.ClientArmorResources ARMOR = new dev.zbw3790.fashion.client.armor.ClientArmorResources(FabricLoader.getInstance().getGameDir(),LOGGER);
    public static dev.zbw3790.fashion.client.armor.ClientArmorResources armorResources() {return ARMOR;}
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
		Fashion3790ClientNetworking.register(
				Fashion3790.wardrobeServerAvailability(),
				CAPE_REGISTRY,
				PLAYER_FASHIONS,
				CAPE_ASSET_STORE,
				CAPE_ASSET_SYNC,
				CAPE_TEXTURE_MANAGER,
				LOGGER
		);
		dev.zbw3790.fashion.client.network.ClientFullFashionNetworking.register(PLAYER_FASHIONS, OUTFIT_REGISTRY, OUTFIT_ASSETS, OUTFIT_SYNC, OUTFIT_TEXTURES, LOGGER);
        dev.zbw3790.fashion.client.network.ClientArmorNetworking.register(ARMOR,LOGGER);
        var appearances = new PlayerFashionAppearanceResolver(PLAYER_FASHIONS, CAPE_REGISTRY, CAPE_TEXTURE_RESOLVER);
        PlayerFashionWorldRendering.register(appearances);
        dev.zbw3790.fashion.client.render.InventoryFashionRendering.register(PLAYER_FASHIONS, appearances);
		Fashion3790CapeRendering.register(LOGGER);
        dev.zbw3790.fashion.client.render.armor.ArmorRendering.register(PLAYER_FASHIONS,ARMOR);
		var production = new dev.zbw3790.fashion.client.render.outfit.NetworkOutfitAppearanceProvider(PLAYER_FASHIONS,
                new dev.zbw3790.fashion.client.outfit.ClientOutfitTextureResolver(OUTFIT_REGISTRY, OUTFIT_ASSETS, OUTFIT_TEXTURES));
        dev.zbw3790.fashion.client.render.outfit.OutfitRendering.register(production);

		LOGGER.info("3790's Fashion 客户端初始化完成，资产链、玩家时装同步与世界外观提取已注册。");
	}

    public static dev.zbw3790.fashion.client.screen.WardrobeOutfitSource wardrobeOutfits() {
        var client=net.minecraft.client.Minecraft.getInstance();
        return new dev.zbw3790.fashion.client.screen.WardrobeOutfitSource(OUTFIT_REGISTRY,
                new dev.zbw3790.fashion.client.outfit.ClientOutfitTextureResolver(OUTFIT_REGISTRY,OUTFIT_ASSETS,OUTFIT_TEXTURES),
                OUTFIT_SYNC,client::getConnection,() -> client.player==null?null:
                dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance.modelOf(client.player.getSkin().model()));
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
