package dev.zbw3790.fashion.client.network;

import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.outfit.*;
import dev.zbw3790.fashion.client.screen.WardrobeScreen;
import dev.zbw3790.fashion.network.*;

/** 新协议仍走可靠 PLAY 接收队列；没有本地目录直读或 Integrated 特例。 */
public final class ClientFullFashionNetworking {
    private static boolean resultReceiverReady;
    public static boolean resultReceiverReady() { return resultReceiverReady; }
    private ClientFullFashionNetworking() { }
    public static void register(ClientPlayerFashionRegistry authority, ClientOutfitRegistry registry, ClientOutfitAssetStore store,
            ClientOutfitAssetSync sync, ClientOutfitTextureManager textures, Logger logger) {
        boolean[] warned={false,false};
        ClientPlayConnectionEvents.INIT.register((handler,client) -> { sync.begin(handler); textures.begin(handler); warned[0]=false; warned[1]=false; });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> client.execute(() -> {
            if (!sync.disconnect(handler)) return;
            textures.disconnect(handler);
            logger.debug("当前连接的装束定义、内容、pending 和 GPU 所有权已释放。");
        }));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client) -> capabilities(client,authority,registry,sync,store,textures,logger));
        ServerboundPlayChannelEvents.REGISTER.register((handler,sender,client,ids) -> client.execute(() -> {
            if (client.getConnection()==handler) capabilities(client,authority,registry,sync,store,textures,logger);
        }));
        boolean snapshots=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(FullPlayerFashionSnapshotPayload.TYPE,(payload,context) -> {
            authority.receiveFullSnapshot(context.client().getConnection(),payload.snapshot());
            logger.debug("完整玩家权威已接收：可用={}，在线={}。",payload.snapshot().available(),payload.snapshot().entries().size());
        });
        boolean updates=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(FullPlayerFashionUpdatePayload.TYPE,(payload,context) -> {
            authority.receiveFullUpdate(context.client().getConnection(),payload.entry()); protocolWarning(authority,logger,warned);
        });
        boolean removes=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(FullPlayerFashionRemovePayload.TYPE,(payload,context) -> {
            authority.receiveFullRemove(context.client().getConnection(),payload); protocolWarning(authority,logger,warned);
        });
        boolean results=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(FullFashionSelectionResultPayload.TYPE,(payload,context) -> {
            var handler=context.client().getConnection(); if (handler==null) return;
            ClientFullFashionSelectionResults.apply(handler,handler.getConnection(),context.player().getUUID(),payload,authority,
                    () -> context.client().gui.screen() instanceof WardrobeScreen screen?screen.selectionSession():null);
            protocolWarning(authority,logger,warned);
        });
        boolean definitions=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(OutfitRegistrySnapshotPayload.TYPE,(payload,context) -> {
            var connection=context.client().getConnection(); var result=sync.snapshot(connection,payload.snapshot());
            if (result==ClientOutfitRegistry.Result.CONFLICT) {
                textures.deactivate(connection); if (!warned[1]) { warned[1]=true; logger.warn("同连接收到冲突的装束 Registry，已停用装束资产同步，重连后重新建立。"); } return;
            }
            requestAndRegister(context.client(),registry,store,sync,textures,logger);
            logger.debug("装束 Registry 已接收：状态={}，定义={}，所需内容={}。",registry.state(),payload.snapshot().entries().size(),registry.requiredHashes().size());
        });
        boolean refreshes=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(OutfitRegistryRefreshPayload.TYPE,(payload,context) -> {
            var connection=context.client().getConnection();
            var result=sync.refresh(connection,payload.registryGeneration(),payload.snapshot());
            if (result==ClientOutfitRegistry.Result.CONFLICT) {
                textures.deactivate(connection);
                if (!warned[1]) { warned[1]=true; logger.warn("装束刷新代次发生协议冲突，已停用当前连接装束同步。"); }
                return;
            }
            if (result==ClientOutfitRegistry.Result.APPLIED) textures.retain(connection,registry.requiredHashes());
            if (result==ClientOutfitRegistry.Result.APPLIED || result==ClientOutfitRegistry.Result.IDEMPOTENT)
                requestAndRegister(context.client(),registry,store,sync,textures,logger);
            logger.debug("装束目录刷新已处理：代次={}，结果={}，定义={}。",payload.registryGeneration(),result,registry.entries().size());
        });
        boolean assets=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(OutfitAssetDataPayload.TYPE,(payload,context) -> {
            var connection=context.client().getConnection(); var result=sync.receive(connection,payload);
            if (result==ClientOutfitAssetSync.Receive.STORED) {
                var registration=textures.register(connection,store,payload.sha256(),ClientOutfitTextureManager.minecraft(context.client().getTextureManager()),logger);
                logger.debug("装束内容已验证：hash={}，字节={}，纹理={}，剩余 pending={}。",payload.sha256().substring(0,12),payload.pngBytes().length,registration,sync.pendingCount());
            }
            else if (result==ClientOutfitAssetSync.Receive.NOT_AUTHORIZED || result==ClientOutfitAssetSync.Receive.STALE) logger.debug("已忽略旧视图装束资产：{}。",result);
            else if (!warned[1]) { warned[1]=true; logger.warn("已拒绝当前连接不符合授权或验证要求的装束资产：{}；同类诊断不重复。",result); }
        });
        if (!(snapshots && updates && removes && results && definitions && refreshes && assets)) throw new IllegalStateException("完整时装客户端接收器重复注册。");
        resultReceiverReady=true;
        logger.info("3790's Fashion 新增七种 S2C 接收器已注册，客户端使用聚合权威和独立装束资产。");
    }
    private static void protocolWarning(ClientPlayerFashionRegistry authority, Logger logger, boolean[] warned) {
        if (authority.full().protocolFailed() && !warned[0]) { warned[0]=true; logger.warn("同 revision 的完整权威不一致，已停用当前连接时装权威，重连后恢复。"); }
    }
    private static void capabilities(Minecraft client, ClientPlayerFashionRegistry authority, ClientOutfitRegistry registry, ClientOutfitAssetSync sync,
            ClientOutfitAssetStore store, ClientOutfitTextureManager textures, Logger logger) {
        var connection=client.getConnection(); if (connection==null || !sync.matches(connection)) return;
        // canSend 在客户端只查询服务器 C2S 接收能力；最终 v2 route 由 Full Snapshot 确认。
        if (!ClientPlayNetworking.canSend(SetFullFashionSelectionPayload.TYPE)) authority.full().unsupported(connection);
        if (!ClientPlayNetworking.canSend(OutfitAssetRequestPayload.TYPE)) registry.unsupported(connection);
        else requestAndRegister(client,registry,store,sync,textures,logger);
    }
    private static void requestAndRegister(Minecraft client, ClientOutfitRegistry registry, ClientOutfitAssetStore store, ClientOutfitAssetSync sync,
            ClientOutfitTextureManager textures, Logger logger) {
        var connection=client.getConnection(); if (connection==null || !sync.matches(connection)) return;
        for (String hash:registry.requiredHashes()) textures.register(connection,store,hash,ClientOutfitTextureManager.minecraft(client.getTextureManager()),logger);
        if (!ClientPlayNetworking.canSend(OutfitAssetRequestPayload.TYPE)) return;
        for (var request:sync.missing(connection)) if (sync.markRequested(connection,request)) ClientPlayNetworking.send(request);
    }
}
