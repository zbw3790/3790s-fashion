package dev.zbw3790.fashion.network;

import java.util.Objects;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import dev.zbw3790.fashion.cape.CapeAssetIndex;
import dev.zbw3790.fashion.cape.CapeAssetReadResult;
import dev.zbw3790.fashion.cape.CapeAssetReader;
import dev.zbw3790.fashion.cape.CapeRegistry;
import dev.zbw3790.fashion.cape.CapeRegistryService;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;
import dev.zbw3790.fashion.cape.CapeTextureAsset;

public final class Fashion3790Networking {
	private Fashion3790Networking() {
	}

	public static void register(CapeRegistryService capeRegistryService, Logger logger) {
		Objects.requireNonNull(capeRegistryService, "Cape Registry 服务不能为 null。");
		Objects.requireNonNull(logger, "日志记录器不能为 null。");
		var networking = PlayerFashionNetworking.register(logger);

		PayloadTypeRegistry.clientboundPlay().register(
				OpenWardrobePayload.TYPE,
				OpenWardrobePayload.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				WardrobeAvailablePayload.TYPE,
				WardrobeAvailablePayload.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				CapeRegistrySnapshotPayload.TYPE,
				CapeRegistrySnapshotPayload.CODEC
		);
		PayloadTypeRegistry.clientboundPlay().register(
				CapeAssetDataPayload.TYPE,
				CapeAssetDataPayload.CODEC
		);
		PayloadTypeRegistry.serverboundPlay().register(
				CapeAssetRequestPayload.TYPE,
				CapeAssetRequestPayload.CODEC
		);

		boolean assetRequestReceiverRegistered = ServerPlayNetworking.registerGlobalReceiver(
				CapeAssetRequestPayload.TYPE,
				(payload, context) -> networking.execute(context.server(), channel -> handleAssetRequest(
						payload,
						context.player(),
						capeRegistryService,
						channel.capes,
						logger
				))
		);

		if (!assetRequestReceiverRegistered) {
			throw new IllegalStateException("3790's Fashion Cape 资产请求接收器重复注册。");
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> networking.execute(server, channel -> {
			channel.capes.open(handler.getPlayer().getUUID(), handler);

			ServerPayloadSender.sendIfSupported(handler, WardrobeAvailablePayload.INSTANCE);

			if (!ServerPayloadSender.canSend(handler, CapeRegistrySnapshotPayload.TYPE)) {
				return;
			}

			CapeRegistry registry = capeRegistryService.current();

			if (registry.size() > CapeRegistrySnapshot.MAX_CAPE_ENTRIES) {
				logger.error(
						"3790's Fashion Cape Registry 含有 {} 个条目，超过网络上限 {}；未向玩家发送 Snapshot。",
						registry.size(),
						CapeRegistrySnapshot.MAX_CAPE_ENTRIES
				);
				return;
			}

			try {
				CapeRegistrySnapshot snapshot = CapeRegistrySnapshot.from(registry);
				if (!ServerPayloadSender.sendIfSupported(
						handler, new CapeRegistrySnapshotPayload(snapshot))) {
					return;
				}

				if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
					logger.info(
							"3790's Fashion 已向玩家发送 Cape Registry Snapshot：{} 个条目。",
							snapshot.size()
					);
				}
			} catch (IllegalArgumentException exception) {
				logger.error("3790's Fashion 无法构建 Cape Registry Snapshot；未向玩家发送。", exception);
			}
		}));
        // 普通离开和停服均由同一网络生命周期清理 Cape 连接预算。


		logger.info("3790's Fashion 玩家时装网络协议与连接生命周期已注册。");
	}

	private static void handleAssetRequest(
			CapeAssetRequestPayload payload,
			ServerPlayer player,
			CapeRegistryService capeRegistryService,
			CapeAssetRequestTracker requestTracker,
			Logger logger
	) {
		if (!ServerPayloadSender.canSend(player.connection, CapeAssetDataPayload.TYPE)) {
			return;
		}

		CapeAssetIndex assetIndex = CapeAssetIndex.from(capeRegistryService.current());

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			logger.info(
					"3790's Fashion 已收到 Cape 资产请求：{} 个 unique hash，当前 AssetIndex {} 项。",
					payload.sha256Hashes().size(),
					assetIndex.size()
			);
		}

		for (String hash : payload.sha256Hashes()) {
			CapeAssetRequestTracker.ClaimResult claim = requestTracker.claim(
					player.getUUID(), player.connection, hash);

			if (claim == CapeAssetRequestTracker.ClaimResult.STALE_CONNECTION) {
				return;
			}

			if (claim == CapeAssetRequestTracker.ClaimResult.LIMIT_REACHED) {
				logger.warn("3790's Fashion 已忽略玩家当前连接超出 2048 个 hash 的后续 Cape 资产请求。");
				return;
			}

			if (claim == CapeAssetRequestTracker.ClaimResult.DUPLICATE) {
				logger.debug("3790's Fashion 已忽略当前连接重复请求的 Cape 资产：{}。", shortHash(hash));
				continue;
			}

			CapeTextureAsset asset = assetIndex.find(hash).orElse(null);

			if (asset == null) {
				logger.debug("3790's Fashion 已忽略当前 Registry 中不存在的 Cape 资产请求：{}。", shortHash(hash));
				continue;
			}

			CapeAssetReadResult readResult = CapeAssetReader.readVerified(asset);

			if (readResult.status() == CapeAssetReadResult.Status.SUCCESS) {
				byte[] bytes = readResult.bytes().orElseThrow();
				if (!ServerPayloadSender.sendIfSupported(
						player.connection, new CapeAssetDataPayload(hash, bytes))) {
					return;
				}

				if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
					logger.info(
							"3790's Fashion 已发送 Cape 资产：{}，{} bytes。",
							shortHash(hash),
							bytes.length
					);
				}
				continue;
			}

			if (readResult.status() == CapeAssetReadResult.Status.HASH_MISMATCH) {
				logger.warn(
						"3790's Fashion Cape 资产在 Registry 加载后发生变化，需要重启或重新加载服务器后才能同步：{}。",
						shortHash(hash)
				);
			} else {
				logger.warn(
						"3790's Fashion Cape 资产发送前读取校验失败，本次未发送：{}，状态={}。",
						shortHash(hash),
						readResult.status()
				);
			}
		}
	}

	private static String shortHash(String hash) {
		return hash.substring(0, 12);
	}
}
