package dev.zbw3790.fashion.fashion;

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
import dev.zbw3790.fashion.cape.CapeRegistryKnowledge;
import dev.zbw3790.fashion.cape.CapeRegistryLifecycle;
import dev.zbw3790.fashion.cape.CapeRegistryService;

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
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            requireServerThread(server);
            start(
				server.getDataStorage(),
				migratedConfigRoot().resolve("capes"),
				server.getWorldPath(LevelResource.DATA));
        });
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            requireServerThread(server);
            beginStopping(server.getDataStorage());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            requireServerThread(server);
            stop(server.getDataStorage());
        });
	}

    private Path migratedConfigRoot() {
        try {
            return dev.zbw3790.fashion.identity.LegacyIdentityMigration.configRoot(FabricLoader.getInstance().getConfigDir());
        } catch (java.io.IOException exception) {
            // 不能把迁移失败当作可信的空 Registry，否则会触发错误的删除 reconciliation。
            logger.error("3790's Fashion 配置迁移失败，启动已停止；旧资产与存档保持。", exception);
            throw new IllegalStateException("配置身份迁移未完成，禁止加载空资源目录。", exception);
        }
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
		var outfits = new dev.zbw3790.fashion.outfit.OutfitRegistryLoader(4096).load(capesRoot.resolveSibling("outfits"));
		dev.zbw3790.fashion.outfit.OutfitDiagnostic.report(outfits.diagnostics(), logger);
		PlayerFashionService.ReconciliationResult reconciled = service.reconcile(knowledge, outfits, entry -> { });
		logger.info("3790's Fashion 装束 Registry：可信={}，定义={}，内容={}。", outfits.knowledge().trustworthy(), outfits.registry().size(), outfits.assets().size());
		services.put(storage, service);
		logger.info("3790's Fashion 玩家时装服务已加载：保存记录 {}，明确删除 {}，休眠 {}，状态 {}。",
				service.storedCount(), reconciled.clearedCount(), reconciled.dormantCount(), service.availability());
		return service;
	}

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("时装服务生命周期必须在服务器线程执行。");
    }

    void beginStopping(SavedDataStorage storage) {
        var service = services.get(storage);
        if (service != null && service.availability() != PlayerFashionService.Availability.STOPPED) {
            service.stop();
            logger.debug("聚合时装停止收口：在线={}；运行期 revision 已释放，持久化选择保持。", service.onlineCount());
        }
    }

	void stop(SavedDataStorage storage) {
        // 正常路径已在 STOPPING 清空；缺失该阶段时仅作一次幂等兜底。
        beginStopping(storage);
        services.remove(storage);
		capeRegistry.clear();
		logger.info("3790's Fashion 玩家时装服务引用与 Cape Registry 已在服务器停止后清除。");
	}
}
