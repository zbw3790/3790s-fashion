package vanillafashion.network;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import vanillafashion.VanillaFashion;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;
import net.fabricmc.loader.api.FabricLoader;

/** 聚合权威的唯一网络编排；连接路线决定投影，发送前仍逐 Payload 检查能力。 */
public final class PlayerFashionNetworking {
    private final ServerConnections channels = new ServerConnections();
    private PlayerFashionNetworking() { }

    void execute(MinecraftServer server, Consumer<Channels> task) {
        FashionServerTasks.execute(server, () -> channels.find(server), task);
    }

    public static PlayerFashionNetworking register(Logger logger) {
        var networking = new PlayerFashionNetworking();
        OutfitReloadCommand.register(source -> {
            var server=source.getServer(); FashionServerTasks.requireServerThread(server);
            var channel=networking.channels.find(server); var service=VanillaFashion.playerFashionService(server);
            if (channel==null || !channel.running() || service.isEmpty()) return OutfitRegistryReloadService.Result.failed("时装服务尚未就绪或正在停止。");
            var result=new OutfitRegistryReloadService(service.orElseThrow(),OutfitRegistryLoader.rootUnder(FabricLoader.getInstance().getConfigDir()),
                    () -> FashionServerTasks.requireServerThread(server)).reload(publication -> channel.refresh(service.orElseThrow(),publication));
            OutfitDiagnostic.report(result.diagnostics(),logger);
            logger.info("{}",result.message()); return result;
        });
        PayloadTypeRegistry.clientboundPlay().register(PlayerFashionSnapshotPayload.TYPE, PlayerFashionSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerFashionUpdatePayload.TYPE, PlayerFashionUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerFashionRemovePayload.TYPE, PlayerFashionRemovePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CapeSelectionResultPayload.TYPE, CapeSelectionResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetCapeSelectionPayload.TYPE, SetCapeSelectionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullPlayerFashionSnapshotPayload.TYPE, FullPlayerFashionSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullPlayerFashionUpdatePayload.TYPE, FullPlayerFashionUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullPlayerFashionRemovePayload.TYPE, FullPlayerFashionRemovePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FullFashionSelectionResultPayload.TYPE, FullFashionSelectionResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OutfitRegistrySnapshotPayload.TYPE, OutfitRegistrySnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OutfitRegistryRefreshPayload.TYPE, OutfitRegistryRefreshPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OutfitAssetDataPayload.TYPE, OutfitAssetDataPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetFullFashionSelectionPayload.TYPE, SetFullFashionSelectionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OutfitAssetRequestPayload.TYPE, OutfitAssetRequestPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FashionServerTasks.requireServerThread(server);
            networking.channels.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FashionServerTasks.requireServerThread(server);
            var channel = networking.channels.find(server);
            if (networking.channels.beginStopping(server))
                logger.debug("时装网络停止收口：route={}，Outfit 请求连接={}，Cape 请求连接={}。",
                        channel.routes.size(), channel.assets.connectionCount(), channel.capes.connectionCount());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FashionServerTasks.requireServerThread(server);
            networking.channels.finishStopped(server);
            logger.debug("时装网络服务器会话已释放：剩余会话={}。", networking.channels.size());
        });
        if (!ServerPlayNetworking.registerGlobalReceiver(SetCapeSelectionPayload.TYPE, (payload, context) -> networking.execute(context.server(), channel -> {
            var service = VanillaFashion.playerFashionService(context.server()); var sender=context.player();
            if (service.isEmpty() || !service.orElseThrow().isCurrent(sender.getUUID(), sender.connection)) return;
            // v2 不能通过 legacy 请求建立第二条写路径，也不向 v2 发旧 Result。
            if (channel.route(sender.connection) != FashionAuthorityRoute.LEGACY) return;
            CapeSelectionHandler.process(sender.getUUID(), payload, service, channel.route(sender.connection),
                    ServerPayloadSender.canSend(sender.connection, CapeSelectionResultPayload.TYPE), service.orElseThrow().onlineCount()<=FullPlayerFashionSnapshot.MAX_PLAYERS)
                    .ifPresent(outcome -> {
                        if (outcome.update().isPresent()) service.orElseThrow().authority(sender.getUUID()).ifPresent(state -> channel.changed(service.orElseThrow(), new FullPlayerFashionEntry(sender.getUUID(), state)));
                        ServerPayloadSender.sendIfSupported(sender.connection, outcome.result());
                    });
        }))) throw new IllegalStateException("旧时装选择接收器重复注册。");
        if (!ServerPlayNetworking.registerGlobalReceiver(SetFullFashionSelectionPayload.TYPE, (payload, context) -> networking.execute(context.server(), channel -> {
            var service=VanillaFashion.playerFashionService(context.server()); var sender=context.player();
            if (service.isEmpty() || !service.orElseThrow().isCurrent(sender.getUUID(), sender.connection)) return;
            boolean resultSupported=ServerPayloadSender.canSend(sender.connection, FullFashionSelectionResultPayload.TYPE);
            FullFashionSelectionHandler.process(sender.getUUID(), sender.connection, payload, service.orElseThrow(),
                    channel.route(sender.connection), resultSupported).ifPresent(outcome -> {
                FullFashionSelectionHandler.deliver(outcome, entry -> channel.changed(service.orElseThrow(), entry),
                        result -> ServerPayloadSender.sendIfSupported(sender.connection, result));
                logger.debug("完整时装事务已处理：请求={}，期望版本={}，状态={}，当前版本={}，发生修改={}。", payload.requestId(), payload.expectedRevision(), outcome.result().status(), outcome.result().authority().map(FullPlayerFashionState::revision).orElse(-1L), outcome.update().isPresent());
            });
        }))) throw new IllegalStateException("完整时装选择接收器重复注册。");
        if (!ServerPlayNetworking.registerGlobalReceiver(OutfitAssetRequestPayload.TYPE, (payload, context) -> networking.execute(context.server(), channel -> {
            var service=VanillaFashion.playerFashionService(context.server()); var sender=context.player();
            if (service.isEmpty() || !service.orElseThrow().isCurrent(sender.getUUID(), sender.connection)
                    || !ServerPayloadSender.canSend(sender.connection, OutfitAssetDataPayload.TYPE)) return;
            for (String hash : channel.assets.claim(sender.connection, payload)) channel.assets.asset(sender.connection,hash).ifPresent(asset ->
                        { if (ServerPayloadSender.sendIfSupported(sender.connection, new OutfitAssetDataPayload(hash, asset.bytes())))
                            logger.debug("装束内容已发送：hash={}，字节={}。", hash.substring(0,12),asset.size()); });
        }))) throw new IllegalStateException("装束资产请求接收器重复注册。");
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> networking.execute(server, channel -> VanillaFashion.playerFashionService(server).ifPresent(service -> {
            var id=handler.getPlayer().getUUID();
            int previous=service.onlineCount();
            channel.join(service, id, handler, leaving -> channel.left(service, leaving));
            channel.routes.put(handler, channel.detect(handler));
            channel.initializeAssets(service, handler);
            if (!service.fullSnapshot().available() || previous>FullPlayerFashionSnapshot.MAX_PLAYERS) channel.snapshots(service);
            else {
                channel.snapshot(service, handler);
                service.authority(id).ifPresent(state -> channel.joined(service, new FullPlayerFashionEntry(id,state)));
            }
            logger.debug("聚合时装 JOIN 已处理：在线={}，路线={}。", service.onlineCount(), channel.route(handler));
        })));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> networking.execute(server, channel -> {
            VanillaFashion.playerFashionService(server).ifPresent(service -> {
                int previous=service.onlineCount();
                if (!channel.leave(service, handler.getPlayer().getUUID(), handler, leaving -> channel.left(service, leaving))) return;
                if (previous>FullPlayerFashionSnapshot.MAX_PLAYERS || !service.canProvideAuthoritativeSnapshot()) channel.snapshots(service);
                logger.debug("聚合时装 LEFT 已处理：在线={}，该 membership 已释放。", service.onlineCount());
            });
        }));
        ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, ids) -> networking.execute(server, channel -> {
            if (channel.route(handler)!=FashionAuthorityRoute.UNDECIDED) return;
            VanillaFashion.playerFashionService(server).ifPresent(service -> {
                if (!service.isCurrent(handler.getPlayer().getUUID(), handler)) return;
                var route=channel.detect(handler); if (route==FashionAuthorityRoute.UNDECIDED) return;
                channel.routes.put(handler, route); channel.initializeAssets(service, handler); channel.snapshot(service, handler);
            });
        }));
        return networking;
    }

    /** 只持有服务器生命周期，不记录 UUID 历史；读写均由外层线程守卫保护。 */
    static final class ServerConnections {
        private final Map<Object, Channels> sessions = new IdentityHashMap<>();
        Channels start(Object server) {
            if (sessions.containsKey(server)) throw new IllegalStateException("时装网络生命周期不能重复启动。");
            var channel = new Channels(); sessions.put(server, channel); return channel;
        }
        Channels find(Object server) { return sessions.get(server); }
        boolean beginStopping(Object server) {
            var channel = find(server); return channel != null && channel.beginStopping();
        }
        void finishStopped(Object server) {
            var channel = sessions.remove(server); if (channel != null) channel.finishStopped();
        }
        int size() { return sessions.size(); }
    }

    /** 每服务器一个网络生命周期；这些字段只能在通过执行期守卫后访问。 */
    static final class Channels {
        enum Phase { RUNNING, STOPPING, STOPPED }
        private Phase phase = Phase.RUNNING;
        final Map<Object, FashionAuthorityRoute> routes = new IdentityHashMap<>();
        final OutfitAssetRequestTracker assets = new OutfitAssetRequestTracker();
        final CapeAssetRequestTracker capes = new CapeAssetRequestTracker();

        boolean running() { return phase == Phase.RUNNING; }
        boolean beginStopping() {
            if (!running()) return false;
            phase = Phase.STOPPING;
            routes.clear(); assets.clear(); capes.clear();
            return true;
        }
        void finishStopped() { beginStopping(); phase = Phase.STOPPED; }
        void join(PlayerFashionService service, UUID id, Object connection, Consumer<FullPlayerFashionEntry> broadcast) {
            if (!running()) return;
            Object old = service.connections().get(id);
            if (old != null && old != connection) leave(service, id, old, broadcast);
            service.join(id, connection, broadcast);
        }
        boolean leave(PlayerFashionService service, UUID id, Object connection, Consumer<FullPlayerFashionEntry> broadcast) {
            if (!running() || !service.leave(id, connection, broadcast)) return false;
            // LEFT 已发且旧 membership 已释放，才清理这条连接的路由与预算。
            routes.remove(connection); assets.close(connection); capes.close(id, connection);
            return true;
        }
        FashionAuthorityRoute route(Object handler) { return routes.getOrDefault(handler, FashionAuthorityRoute.UNDECIDED); }
        private FashionAuthorityRoute detect(ServerGamePacketListenerImpl handler) {
            return FashionAuthorityRoute.server(ServerPayloadSender.canSend(handler,FullPlayerFashionSnapshotPayload.TYPE),
                    ServerPayloadSender.canSend(handler,FullPlayerFashionUpdatePayload.TYPE), ServerPayloadSender.canSend(handler,FullPlayerFashionRemovePayload.TYPE),
                    ServerPayloadSender.canSend(handler,PlayerFashionSnapshotPayload.TYPE));
        }
        private void initializeAssets(PlayerFashionService service, ServerGamePacketListenerImpl handler) {
            if (route(handler)!=FashionAuthorityRoute.V2) return;
            var snapshot=service.outfits().map(OutfitRegistrySnapshot::from).orElseGet(OutfitRegistrySnapshot::unavailable);
            if (ServerPayloadSender.sendIfSupported(handler,new OutfitRegistrySnapshotPayload(snapshot))) {
                service.outfits().ifPresent(loaded -> assets.open(handler,ConnectionOutfitAssetView.from(service.registryGeneration(),loaded)));
                if (service.registryGeneration()>0)
                    ServerPayloadSender.sendIfSupported(handler,new OutfitRegistryRefreshPayload(service.registryGeneration(),snapshot));
            }
        }
        private OutfitRegistryReloadService.Clients refresh(PlayerFashionService service, OutfitRegistryReloadService.Publication publication) {
            var view=ConnectionOutfitAssetView.from(publication.generation(),publication.candidate());
            var counts=refreshAssets(service.connections().values(),view,
                    value -> ServerPayloadSender.canSend((ServerGamePacketListenerImpl)value,OutfitRegistryRefreshPayload.TYPE),
                    value -> ServerPayloadSender.sendIfSupported((ServerGamePacketListenerImpl)value,new OutfitRegistryRefreshPayload(view.generation(),view.snapshot())));
            publication.commit().authorities().forEach(entry -> changed(service,entry));
            return counts;
        }
        OutfitRegistryReloadService.Clients refreshAssets(Collection<Object> connections, ConnectionOutfitAssetView view,
                java.util.function.Predicate<Object> supported, java.util.function.Predicate<Object> send) {
            var receivers=new ArrayList<Object>(); int pinned=0;
            for (Object connection:connections) {
                if (assets.view(connection).isEmpty()) continue;
                if (supported.test(connection)) {
                    if (assets.refreshAuthorized(connection,view)) receivers.add(connection);
                } else pinned++;
            }
            // 全部连接来源替换后才发刷新；刷新在其引起的 authority Update 之前。
            int sent=0;for (Object receiver:receivers) if (send.test(receiver)) sent++;
            return new OutfitRegistryReloadService.Clients(sent,pinned);
        }
        private void snapshot(PlayerFashionService service, ServerGamePacketListenerImpl handler) {
            if (route(handler)==FashionAuthorityRoute.V2) ServerPayloadSender.sendIfSupported(handler,new FullPlayerFashionSnapshotPayload(service.fullSnapshot()));
            else if (route(handler)==FashionAuthorityRoute.LEGACY) ServerPayloadSender.sendIfSupported(handler,
                    new PlayerFashionSnapshotPayload(PlayerFashionSnapshotBuilder.build(service.connections().keySet(),service)));
        }
        private void snapshots(PlayerFashionService service) { service.connections().values().forEach(value -> snapshot(service,(ServerGamePacketListenerImpl)value)); }
        private void joined(PlayerFashionService service, FullPlayerFashionEntry entry) {
            service.connections().forEach((id,value) -> {
                if (id.equals(entry.playerId())) return; var receiver=(ServerGamePacketListenerImpl)value;
                if (route(receiver)==FashionAuthorityRoute.V2) ServerPayloadSender.sendIfSupported(receiver,new FullPlayerFashionUpdatePayload(entry));
                else update(receiver,entry);
            });
        }
        private void changed(PlayerFashionService service, FullPlayerFashionEntry entry) {
            service.connections().values().forEach(value -> update((ServerGamePacketListenerImpl)value,entry));
        }
        private void update(ServerGamePacketListenerImpl receiver, FullPlayerFashionEntry entry) {
            FullFashionSelectionHandler.projection(route(receiver),entry)
                    .ifPresent(payload -> ServerPayloadSender.sendIfSupported(receiver,payload));
        }
        private void left(PlayerFashionService service, FullPlayerFashionEntry leaving) {
            service.connections().forEach((id,value) -> {
                if (id.equals(leaving.playerId())) return; var receiver=(ServerGamePacketListenerImpl)value;
                if (route(receiver)==FashionAuthorityRoute.V2) ServerPayloadSender.sendIfSupported(receiver,new FullPlayerFashionRemovePayload(leaving.playerId(),leaving.state().revision(),FullPlayerFashionRemovePayload.Reason.LEFT));
                else if (route(receiver)==FashionAuthorityRoute.LEGACY) ServerPayloadSender.sendIfSupported(receiver,new PlayerFashionRemovePayload(leaving.playerId()));
            });
        }
    }
}
