package dev.zbw3790.fashion.client.armor;

import java.util.*;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import dev.zbw3790.fashion.armor.*;

/** 本连接资源服务；渲染只读取就绪句柄，不执行 IO。 */
public final class ClientArmorResources {
    public record Ready(ArmorStyleId style,ArmorSlot slot,ArmorGeometry geometry,long generation,String hash,Identifier texture) { }
    public final ClientArmorRegistry registry=new ClientArmorRegistry();
    public final ClientArmorAssetStore store=new ClientArmorAssetStore();
    public final ClientArmorTextureManager textures=new ClientArmorTextureManager();
    public final ClientArmorAssetSync sync;
    public ClientArmorResources(Path gameDirectory,Logger logger) {sync=new ClientArmorAssetSync(registry,store,ClientArmorAssetCache.fromGameDirectory(gameDirectory,logger));}
    public Optional<Ready> resolve(ArmorStyleId style,ArmorSlot slot) {
        var entry=registry.find(style).filter(value->value.slots().contains(slot));
        if(entry.isEmpty()) return Optional.empty();
        var geometry=ArmorGeometry.forSlot(slot);String hash=entry.orElseThrow().hashes().get(geometry);
        return textures.find(registry.connection(),hash).map(texture->new Ready(style,slot,geometry,registry.generation(),hash,texture));
    }
}
