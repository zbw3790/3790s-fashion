package dev.zbw3790.fashion.client.network;

import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import dev.zbw3790.fashion.client.armor.*;
import dev.zbw3790.fashion.network.*;

/** 可靠 PLAY 队列与当前连接 identity 共同守卫 Armor 资源。 */
public final class ClientArmorNetworking {
    private ClientArmorNetworking() { }
    public static void register(ClientArmorResources resources,Logger logger) {
        boolean[] warned={false};
        ClientPlayConnectionEvents.INIT.register((handler,client)->{resources.sync.begin(handler);resources.textures.begin(handler);warned[0]=false;});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{
            if(resources.sync.disconnect(handler)) {resources.textures.disconnect(handler);logger.debug("盔甲连接状态已清理，纹理等待帧尾退役。");}
        }));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->capabilities(client,resources,logger));
        ServerboundPlayChannelEvents.REGISTER.register((handler,sender,client,ids)->client.execute(()->{if(client.getConnection()==handler) capabilities(client,resources,logger);}));
        boolean registry=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(ArmorRegistryPayload.TYPE,(payload,context)->{
            var connection=context.client().getConnection();var result=resources.sync.snapshot(connection,payload.generation(),payload.snapshot());
            if(result==ClientArmorRegistry.Result.CONFLICT) {resources.textures.deactivate(connection);warn(logger,warned,"盔甲 Registry 同代次冲突，当前连接停用。");return;}
            if(result==ClientArmorRegistry.Result.APPLIED) resources.textures.retain(connection,resources.registry.requiredHashes());
            if(result==ClientArmorRegistry.Result.APPLIED || result==ClientArmorRegistry.Result.IDEMPOTENT) request(context.client(),resources,logger);
            logger.debug("盔甲 Registry 已处理：代次={}，状态={}，定义={}，所需内容={}。",payload.generation(),resources.registry.state(),resources.registry.entries().size(),resources.registry.requiredHashes().size());
        });
        boolean assets=Fashion3790ClientNetworking.registerCurrentConnectionReceiver(ArmorAssetDataPayload.TYPE,(payload,context)->{
            var connection=context.client().getConnection();var result=resources.sync.receive(connection,payload);
            if(result==ClientArmorAssetSync.Receive.STORED) {
                var registered=resources.textures.register(connection,resources.store,payload.sha256(),ClientArmorTextureManager.minecraft(context.client().getTextureManager()),logger);
                logger.debug("盔甲内容已验证：hash={}，纹理={}，pending={}。",payload.sha256(),registered,resources.sync.pendingCount());
            } else if(result==ClientArmorAssetSync.Receive.INVALID) warn(logger,warned,"盔甲资产校验失败，当前槽安全回退原版。");
        });
        if(!registry || !assets) throw new IllegalStateException("盔甲客户端接收器重复注册。");
    }
    private static void warn(Logger logger,boolean[] warned,String text) {if(!warned[0]) {warned[0]=true;logger.warn("{}",text);}}
    private static void capabilities(Minecraft client,ClientArmorResources resources,Logger logger) {
        var connection=client.getConnection();if(connection==null || !resources.sync.matches(connection)) return;
        if(!ClientPlayNetworking.canSend(ArmorAssetRequestPayload.TYPE)) resources.registry.unsupported(connection);
        else request(client,resources,logger);
    }
    private static void request(Minecraft client,ClientArmorResources resources,Logger logger) {
        var connection=client.getConnection();if(connection==null || !resources.sync.matches(connection)) return;
        for(String hash:resources.registry.requiredHashes()) resources.textures.register(connection,resources.store,hash,ClientArmorTextureManager.minecraft(client.getTextureManager()),logger);
        if(ClientPlayNetworking.canSend(ArmorAssetRequestPayload.TYPE)) for(var request:resources.sync.missing(connection))
            if(resources.sync.markRequested(connection,request)) ClientPlayNetworking.send(request);
    }
}
