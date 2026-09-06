package vanillafashion;

import java.util.Optional;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vanillafashion.cape.CapeRegistry;
import vanillafashion.cape.CapeRegistryService;
import vanillafashion.fashion.PlayerFashionLifecycle;
import vanillafashion.fashion.PlayerFashionService;
import vanillafashion.network.VanillaFashionNetworking;
import vanillafashion.wardrobe.WardrobeInteractionHandler;
import vanillafashion.wardrobe.WardrobeServerAvailability;

public final class VanillaFashion implements ModInitializer {
	public static final String MOD_ID = "vanilla_fashion";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final CapeRegistryService CAPE_REGISTRY_SERVICE = new CapeRegistryService();
	private static final PlayerFashionLifecycle PLAYER_FASHION_LIFECYCLE =
			new PlayerFashionLifecycle(CAPE_REGISTRY_SERVICE, LOGGER);
	private static final WardrobeServerAvailability WARDROBE_SERVER_AVAILABILITY =
			new WardrobeServerAvailability();

	@Override
	public void onInitialize() {
		VanillaFashionNetworking.register(CAPE_REGISTRY_SERVICE, LOGGER);
		PLAYER_FASHION_LIFECYCLE.register();
		WardrobeInteractionHandler.register(WARDROBE_SERVER_AVAILABILITY, LOGGER);
		LOGGER.info("Vanilla Fashion 通用端初始化完成，双向资产协议、服务器生命周期与衣柜交互回调已注册。");
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
