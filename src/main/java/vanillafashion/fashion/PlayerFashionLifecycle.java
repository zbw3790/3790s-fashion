package vanillafashion.fashion;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.slf4j.Logger;
import vanillafashion.cape.CapeRegistryKnowledge;
import vanillafashion.cape.CapeRegistryLifecycle;
import vanillafashion.cape.CapeRegistryService;

/** 唯一启动编排入口，服务只在加载和 reconcile 全部完成后发布。 */
public final class PlayerFashionLifecycle {
	private final CapeRegistryService capeRegistry;
	private final Logger logger;
	private final Map<SavedDataStorage, PlayerFashionService> services = new IdentityHashMap<>();

	public PlayerFashionLifecycle(CapeRegistryService capeRegistry, Logger logger) {
		this.capeRegistry = capeRegistry;
		this.logger = logger;
	}

	public void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> start(
				server.getDataStorage(),
				FabricLoader.getInstance().getConfigDir().resolve("vanilla-fashion/capes"),
				server.getWorldPath(LevelResource.DATA)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop(server.getDataStorage()));
	}

	public Optional<PlayerFashionService> current(MinecraftServer server) {
		return current(server.getDataStorage());
	}

	Optional<PlayerFashionService> current(SavedDataStorage storage) {
		return Optional.ofNullable(services.get(storage));
	}

	PlayerFashionService start(SavedDataStorage storage, Path capesRoot, Path dataDirectory) {
		if (services.containsKey(storage)) {
			throw new IllegalStateException("同一个存档的玩家时装服务不能重复启动。");
		}
		CapeRegistryKnowledge knowledge = CapeRegistryLifecycle.load(capeRegistry, capesRoot, logger);
		PlayerFashionPersistence.LoadResult loaded = PlayerFashionPersistence.load(storage, dataDirectory, logger);
		PlayerFashionService service = new PlayerFashionService(loaded, knowledge);
		PlayerFashionService.ReconciliationResult reconciled = service.reconcile(knowledge);
		services.put(storage, service);
		logger.info("Vanilla Fashion 玩家时装服务已加载：保存记录 {}，明确删除 {}，休眠 {}，状态 {}。",
				service.storedCount(), reconciled.clearedCount(), reconciled.dormantCount(), service.availability());
		return service;
	}

	void stop(SavedDataStorage storage) {
		PlayerFashionService service = services.remove(storage);
		if (service != null) {
			service.stop();
		}
		capeRegistry.clear();
		logger.info("Vanilla Fashion 玩家时装服务引用与 Cape Registry 已在服务器停止后清除。");
	}
}
