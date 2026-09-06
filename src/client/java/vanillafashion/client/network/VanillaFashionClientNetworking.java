package vanillafashion.client.network;

import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.client.cape.ClientCapeAssetCache;
import vanillafashion.client.cape.ClientCapeAssetStore;
import vanillafashion.client.cape.ClientCapeAssetSync;
import vanillafashion.client.cape.ClientCapeRegistry;
import vanillafashion.client.cape.ClientCapeTextureManager;
import vanillafashion.client.screen.WardrobeScreen;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.network.PlayerFashionSnapshotPayload;
import vanillafashion.network.PlayerFashionUpdatePayload;
import vanillafashion.network.PlayerFashionRemovePayload;
import vanillafashion.network.CapeSelectionResultPayload;
import vanillafashion.network.CapeAssetDataPayload;
import vanillafashion.network.CapeAssetRequestPayload;
import vanillafashion.network.CapeRegistrySnapshotPayload;
import vanillafashion.network.OpenWardrobePayload;
import vanillafashion.network.WardrobeAvailablePayload;
import vanillafashion.wardrobe.WardrobeServerAvailability;

public final class VanillaFashionClientNetworking {
	private VanillaFashionClientNetworking() {
	}

	public static void register(
			WardrobeServerAvailability availability,
			ClientCapeRegistry capeRegistry,
			ClientPlayerFashionRegistry playerFashions,
			ClientCapeAssetStore assetStore,
			ClientCapeAssetSync assetSync,
			ClientCapeTextureManager textureManager,
			Logger logger
	) {
		var selectionRequests = new ClientCapeSelectionRequestTracker();
		ClientPlayConnectionEvents.INIT.register((handler, client) -> selectionRequests.beginConnection(handler.getConnection()));
		ClientPlayConnectionEvents.INIT.register((handler, client) -> playerFashions.beginConnection(handler));
		boolean selectionResultRegistered = registerCurrentConnectionReceiver(
				CapeSelectionResultPayload.TYPE, (payload, context) -> {
					var handler = context.client().getConnection();
					if (handler == null) {
						return;
					}
					boolean close = ClientCapeSelectionResults.apply(handler.getConnection(),
							context.player().getUUID(), payload, playerFashions, selectionRequests,
							() -> context.client().gui.screen() instanceof WardrobeScreen wardrobe
									? wardrobe.selectionSession() : null,
							id -> capeRegistry.find(id).isPresent());
					if (close && context.client().gui.screen() instanceof WardrobeScreen wardrobe) {
						wardrobe.onClose();
					}
				});
		// 当前 Fabric 接收器在客户端线程执行；这里只修改连接 Registry，不触碰 Renderer。
		boolean fashionSnapshotRegistered = registerCurrentConnectionReceiver(
				PlayerFashionSnapshotPayload.TYPE, (payload, context) -> {
					playerFashions.replace(payload.snapshot());
					logger.debug("玩家时装 Snapshot 已接收：可用={}，条目={}。",
							payload.snapshot().snapshotAvailable(), playerFashions.size());
				});
		boolean fashionUpdateRegistered = registerCurrentConnectionReceiver(
				PlayerFashionUpdatePayload.TYPE, (payload, context) -> playerFashions.update(payload.entry()));
		boolean fashionRemoveRegistered = registerCurrentConnectionReceiver(
				PlayerFashionRemovePayload.TYPE, (payload, context) -> playerFashions.remove(payload.playerId()));
		ClientPlayConnectionEvents.INIT.register((handler, client) ->
				clearConnectionState(
						availability,
						capeRegistry,
						assetStore,
						assetSync,
						textureManager,
						client.getTextureManager()
				));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			scheduleConnectionStateClear(client::execute, () -> {
				if (!playerFashions.disconnect(handler)) {
					return;
				}
				selectionRequests.disconnect(handler.getConnection());
				clearConnectionState(
						availability,
						capeRegistry,
						assetStore,
						assetSync,
						textureManager,
						client.getTextureManager()
				);
				logger.info("Vanilla Fashion 当前连接的玩家时装、衣柜可用性、Cape Registry、Asset Store、pending 与动态纹理状态已清除；磁盘内容缓存保持不变。");
			});
		});

		boolean availabilityReceiverRegistered = registerCurrentConnectionReceiver(
				WardrobeAvailablePayload.TYPE,
				(payload, context) -> {
					availability.markAvailable();
					logger.info("Vanilla Fashion 当前服务器已声明支持衣柜功能。");
				}
		);
		boolean snapshotReceiverRegistered = registerCurrentConnectionReceiver(
				CapeRegistrySnapshotPayload.TYPE,
				(payload, context) -> {
					capeRegistry.replace(payload.snapshot());
					logSnapshot(capeRegistry, logger);
					requestMissingAssets(
							payload.snapshot(),
							assetStore,
							assetSync,
							textureManager,
							context.client().getTextureManager(),
							logger
					);
				}
		);
		boolean assetDataReceiverRegistered = registerCurrentConnectionReceiver(
				CapeAssetDataPayload.TYPE,
				(payload, context) -> {
					ClientCapeAssetSync.ReceiveResult result = assetSync.receive(payload);

					if (result != ClientCapeAssetSync.ReceiveResult.STORED) {
						logger.warn(
								"Vanilla Fashion 已拒绝服务器发送的 Cape 资产：{}，原因={}。",
								shortHash(payload.sha256()),
								result
						);
						return;
					}

					ClientCapeTextureManager.RegistrationResult textureResult =
							textureManager.registerIfAvailable(
									context.client().getTextureManager(),
									assetStore,
									payload.sha256(),
									logger
							);

					assetSync.lastCacheStoreResult().ifPresent(cacheResult -> {
						if (cacheResult == ClientCapeAssetCache.StoreResult.WRITE_FAILED) {
							logger.warn(
									"Vanilla Fashion Cape 资产已进入连接内存，但未能持久化到内容缓存：{}。",
									shortHash(payload.sha256())
							);
						}
					});

					if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
						logger.info(
								"Vanilla Fashion 已接收 Cape 资产：{}，{} bytes；Asset Store 当前 {} 项，纹理结果 {}。",
								shortHash(payload.sha256()),
								payload.pngBytes().length,
								assetStore.size(),
								textureResult
						);
					}
				}
		);
		boolean openReceiverRegistered = registerCurrentConnectionReceiver(
				OpenWardrobePayload.TYPE,
				(payload, context) -> {
					// 当前 Fabric receiver 已在渲染线程执行，无需额外调度。
					if (!availability.isAvailable()) {
						availability.markAvailable();
						logger.debug("Vanilla Fashion 已通过 OpenWardrobe 安全兜底建立衣柜可用性状态。");
					}

					if (!(context.client().gui.screen() instanceof WardrobeScreen)) {
						context.client().gui.setScreen(new WardrobeScreen(
								capeRegistry,
								textureManager,
								playerFashions,
								selectionRequests,
								context.player().getUUID(),
								context.client().getConnection().getConnection()
						));
					}
				}
		);

		if (!availabilityReceiverRegistered
				|| !selectionResultRegistered
				|| !fashionSnapshotRegistered
				|| !fashionUpdateRegistered
				|| !fashionRemoveRegistered
				|| !snapshotReceiverRegistered
				|| !assetDataReceiverRegistered
				|| !openReceiverRegistered) {
			throw new IllegalStateException("Vanilla Fashion 客户端 payload 接收器重复注册。");
		}

		logger.info("Vanilla Fashion 八种 S2C payload 客户端接收器、Cape 资产请求与正式选择发送链已注册。");
	}

	static void scheduleConnectionStateClear(Executor clientExecutor, Runnable cleanup) {
		clientExecutor.execute(cleanup);
	}

	private static <T extends CustomPacketPayload> boolean registerCurrentConnectionReceiver(
			CustomPacketPayload.Type<T> type,
			ClientPlayNetworking.PlayPayloadHandler<T> receiver
	) {
		return ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
				ClientConnectionIdentity.runIfCurrent(
						context.responseSender(),
						currentResponseSender(),
						() -> receiver.receive(payload, context)
				));
	}

	private static Object currentResponseSender() {
		try {
			return ClientPlayNetworking.getSender();
		} catch (IllegalStateException exception) {
			// 连接已关闭而旧任务仍在队列中时，按非当前来源忽略。
			return null;
		}
	}

	private static void clearConnectionState(
			WardrobeServerAvailability availability,
			ClientCapeRegistry capeRegistry,
			ClientCapeAssetStore assetStore,
			ClientCapeAssetSync assetSync,
			ClientCapeTextureManager capeTextureManager,
			TextureManager minecraftTextureManager
	) {
		availability.reset();
		capeTextureManager.clear(minecraftTextureManager);
		capeRegistry.clear();
		assetStore.clear();
		assetSync.clear();
	}

	private static void requestMissingAssets(
			vanillafashion.cape.CapeRegistrySnapshot snapshot,
			ClientCapeAssetStore assetStore,
			ClientCapeAssetSync assetSync,
			ClientCapeTextureManager capeTextureManager,
			TextureManager minecraftTextureManager,
			Logger logger
	) {
		var requests = assetSync.plan(snapshot);
		int releasedTextures = capeTextureManager.retain(
				minecraftTextureManager,
				assetSync.requiredHashes()
		);
		int registeredTextures = capeTextureManager.registerAvailable(
				minecraftTextureManager,
				assetStore,
				assetSync.requiredHashes(),
				logger
		);

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			logger.info(
					"Vanilla Fashion 当前 Snapshot 引用 {} 个 unique 资产：缓存命中 {} 个，新注册纹理 {} 个，释放纹理 {} 个，需要请求 {} 个。",
					assetSync.requiredHashes().size(),
					assetSync.lastCacheHitCount(),
					registeredTextures,
					releasedTextures,
					requests.stream().mapToInt(request -> request.sha256Hashes().size()).sum()
			);
		}

		if (!ClientPlayNetworking.canSend(CapeAssetRequestPayload.TYPE)) {
			return;
		}

		for (CapeAssetRequestPayload request : requests) {
			ClientPlayNetworking.send(request);
			assetSync.markRequested(request);

			if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
				logger.info(
						"Vanilla Fashion 已发送 Cape 资产 C2S 请求：{} 个 unique hash。",
						request.sha256Hashes().size()
				);
			}
		}
	}

	private static void logSnapshot(ClientCapeRegistry capeRegistry, Logger logger) {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			logger.debug("Vanilla Fashion 已接收服务器 Cape Registry：{} 个条目。", capeRegistry.size());
			return;
		}

		logger.info("Vanilla Fashion 已接收服务器 Cape Registry：{} 个条目。", capeRegistry.size());

		for (CapeCosmeticMetadata metadata : capeRegistry.entries()) {
			logger.info(
					"Vanilla Fashion Cape 元数据：ID={}，cape SHA-256={}，elytra SHA-256={}。",
					metadata.id(),
					metadata.capeSha256(),
					metadata.elytraSha256().orElse("none")
			);
		}
	}

	private static String shortHash(String hash) {
		return hash.substring(0, 12);
	}
}
