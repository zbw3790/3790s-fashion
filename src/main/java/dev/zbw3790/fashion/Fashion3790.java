package dev.zbw3790.fashion;

import java.util.Optional;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.zbw3790.fashion.cape.CapeRegistry;
import dev.zbw3790.fashion.cape.CapeRegistryService;
import dev.zbw3790.fashion.fashion.PlayerFashionLifecycle;
import dev.zbw3790.fashion.fashion.PlayerFashionService;
import dev.zbw3790.fashion.network.Fashion3790Networking;
import dev.zbw3790.fashion.wardrobe.WardrobeServerAvailability;

public final class Fashion3790 implements ModInitializer {
	public static final String MOD_ID = "fashion_3790";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final CapeRegistryService CAPE_REGISTRY_SERVICE = new CapeRegistryService();
	private static final PlayerFashionLifecycle PLAYER_FASHION_LIFECYCLE =
			new PlayerFashionLifecycle(CAPE_REGISTRY_SERVICE, LOGGER);
	private static final WardrobeServerAvailability WARDROBE_SERVER_AVAILABILITY =
			new WardrobeServerAvailability();

	@Override
	public void onInitialize() {
		Fashion3790Networking.register(CAPE_REGISTRY_SERVICE, LOGGER);
		PLAYER_FASHION_LIFECYCLE.register();
		LOGGER.info("3790's Fashion 通用端初始化完成，双向资产协议、服务器生命周期已注册；衣柜通过原版交互返回点接入。");
	}

	public static CapeRegistry currentCapeRegistry() {
		return CAPE_REGISTRY_SERVICE.current();
	}

	public static Optional<PlayerFashionService> playerFashionService(MinecraftServer server) {
		return PLAYER_FASHION_LIFECYCLE.current(server);
	}

	public static WardrobeServerAvailability wardrobeServerAvailability() {
		return WARDROBE_SERVER_AVAILABILITY;
	}
}
