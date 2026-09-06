package vanillafashion.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import vanillafashion.VanillaFashion;
import vanillafashion.fashion.PlayerFashionOnlineSessions;
import vanillafashion.fashion.PlayerFashionSnapshot;
import vanillafashion.fashion.PlayerFashionSyncPlanner;

public final class PlayerFashionNetworking {
	private PlayerFashionNetworking() {
	}

	public static void register(Logger logger) {
		PayloadTypeRegistry.clientboundPlay().register(PlayerFashionSnapshotPayload.TYPE, PlayerFashionSnapshotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerFashionUpdatePayload.TYPE, PlayerFashionUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerFashionRemovePayload.TYPE, PlayerFashionRemovePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CapeSelectionResultPayload.TYPE, CapeSelectionResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetCapeSelectionPayload.TYPE, SetCapeSelectionPayload.CODEC);
		Map<MinecraftServer, PlayerFashionOnlineSessions<ServerGamePacketListenerImpl>> servers = new HashMap<>();
		var missingResultReported = new HashSet<MinecraftServer>();
		ServerLifecycleEvents.SERVER_STARTING.register(server ->
				servers.put(server, new PlayerFashionOnlineSessions<>()));
		ServerLifecycleEvents.SERVER_STOPPED.register(servers::remove);
		ServerLifecycleEvents.SERVER_STOPPED.register(missingResultReported::remove);

		// 当前 Fabric 在服务器接收线程调用；execute 同线程直接执行，不另排延迟结果。
		if (!ServerPlayNetworking.registerGlobalReceiver(SetCapeSelectionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> {
					var server = context.server();
					var sender = context.player();
					var sessions = servers.get(server);
					var online = sessions == null ? Map.<UUID, ServerGamePacketListenerImpl>of() : sessions.snapshot();
					if (online.get(sender.getUUID()) != sender.connection) {
						return;
					}
					boolean resultSupported = ServerPayloadSender.canSend(
							sender.connection, CapeSelectionResultPayload.TYPE);
					if (!resultSupported && missingResultReported.add(server)) {
						logger.warn("Vanilla Fashion 已拒绝无法接收选择结果的客户端请求；该服务器实例后续同类请求不重复警告。");
					}
					CapeSelectionHandler.process(sender.getUUID(), payload, VanillaFashion.playerFashionService(server),
							resultSupported, online.size() <= PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES)
							.ifPresent(outcome -> {
								CapeSelectionHandler.deliver(outcome, online.values(),
									receiver -> ServerPayloadSender.canSend(
											receiver, PlayerFashionUpdatePayload.TYPE),
									ServerPayloadSender::sendIfSupported,
									result -> ServerPayloadSender.sendIfSupported(sender.connection, result));
							});
				}))) {
			throw new IllegalStateException("时装选择请求接收器重复注册。");
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			var sessions = servers.get(server);
			if (sessions == null) {
				return;
			}
			int previousCount = sessions.size();
			UUID joining = handler.getPlayer().getUUID();
			sessions.join(joining, handler);
			var online = sessions.snapshot();
			var service = VanillaFashion.playerFashionService(server);
			if (service.isEmpty()) {
				broadcast(online, new PlayerFashionSnapshotPayload(PlayerFashionSnapshot.unavailable()));
				return;
			}
			var plan = PlayerFashionSyncPlanner.join(online.keySet(), previousCount, joining, service.orElseThrow());
			if (plan.snapshotForAll()) {
				broadcast(online, new PlayerFashionSnapshotPayload(plan.snapshot()));
			} else {
				sendIfSupported(handler, new PlayerFashionSnapshotPayload(plan.snapshot()));
				var update = new PlayerFashionUpdatePayload(plan.update().orElseThrow());
				online.forEach((id, receiver) -> {
					if (!id.equals(joining)) {
						sendIfSupported(receiver, update);
					}
				});
			}
			logger.debug("玩家加入后的时装同步：在线={}，完整 Snapshot={}，向全体发送={}。",
					online.size(), plan.snapshot().snapshotAvailable(), plan.snapshotForAll());
		}));
		// Fabric 可能从 channelInactive 调用 DISCONNECT；不在网络线程查询服务或修改在线集合。
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
			var sessions = servers.get(server);
			UUID leaving = handler.getPlayer().getUUID();
			if (sessions == null) {
				return;
			}
			int previousCount = sessions.size();
			if (!sessions.leave(leaving, handler)) {
				return;
			}
			var online = sessions.snapshot();
			var service = VanillaFashion.playerFashionService(server);
			if (service.isEmpty()) {
				broadcast(online, new PlayerFashionSnapshotPayload(PlayerFashionSnapshot.unavailable()));
				return;
			}
			var plan = PlayerFashionSyncPlanner.leave(online.keySet(), previousCount, leaving, service.orElseThrow());
			broadcast(online, new PlayerFashionRemovePayload(leaving));
			if (plan.snapshotForAll()) {
				broadcast(online, new PlayerFashionSnapshotPayload(plan.snapshot()));
			}
			logger.debug("玩家离开后的时装同步：在线={}，完整 Snapshot={}，向全体发送={}。",
					online.size(), plan.snapshot().snapshotAvailable(), plan.snapshotForAll());
		}));
	}

	private static void broadcast(Map<UUID, ServerGamePacketListenerImpl> online, CustomPacketPayload payload) {
		online.values().forEach(handler -> sendIfSupported(handler, payload));
	}

	private static void sendIfSupported(ServerGamePacketListenerImpl handler, CustomPacketPayload payload) {
		ServerPayloadSender.sendIfSupported(handler, payload);
	}
}
