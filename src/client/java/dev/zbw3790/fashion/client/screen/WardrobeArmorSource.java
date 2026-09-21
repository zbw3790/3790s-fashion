package dev.zbw3790.fashion.client.screen;

import java.util.List;
import java.util.Optional;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.armor.*;

/** 衣柜只读资源视图；消费现有下载与纹理所有权，不建立第二套资源生命周期。 */
final class WardrobeArmorSource {
    enum Availability { READY, LOADING, FAILED, MISSING, UNSUPPORTED_SLOT }
    private final ClientArmorResources resources;
    private final Object connection;
    WardrobeArmorSource(ClientArmorResources resources, Object connection) {
        this.resources = resources;
        this.connection = connection;
    }
    /** 资源域使用 PLAY handler；与 Full authority 的底层 Connection 不混用。 */
    static WardrobeArmorSource current(ClientArmorResources resources, net.minecraft.client.multiplayer.ClientPacketListener handler) {
        return new WardrobeArmorSource(resources,handler);
    }
    static WardrobeArmorSource empty() { return new WardrobeArmorSource(null, null); }
    ClientArmorRegistry.State state() {
        return resources != null && resources.registry.matches(connection)
                ? resources.registry.state() : ClientArmorRegistry.State.UNSUPPORTED;
    }
    long generation() { return state() == ClientArmorRegistry.State.KNOWN ? resources.registry.generation() : -1; }
    List<ArmorRegistrySnapshot.Entry> entries() {
        return state() == ClientArmorRegistry.State.KNOWN ? resources.registry.entries() : List.of();
    }
    Optional<ArmorRegistrySnapshot.Entry> find(ArmorStyleId id) {
        return entries().stream().filter(entry -> entry.id().equals(id)).findFirst();
    }
    boolean admitted(ArmorSlot slot, ArmorStyleId id) {
        // 新引用按可信定义准入；PNG 尚未就绪时 Preview 安全回退，随后自动更新。
        return find(id).filter(entry -> entry.slots().contains(slot)).isPresent();
    }
    Optional<net.minecraft.resources.Identifier> texture(ArmorSlot slot, ArmorStyleId id) {
        return admitted(slot,id)?resources.resolve(id,slot).map(ClientArmorResources.Ready::texture):Optional.empty();
    }
    Availability availability(ArmorSlot slot, ArmorStyleId id) {
        var entry = find(id);
        if (entry.isEmpty()) return Availability.MISSING;
        if (!entry.orElseThrow().slots().contains(slot)) return Availability.UNSUPPORTED_SLOT;
        String hash = entry.orElseThrow().hashes().get(ArmorGeometry.forSlot(slot));
        if (resources.resolve(id, slot).isPresent()) return Availability.READY;
        return resources.sync.failed(connection, hash) || resources.textures.failed(connection, hash)
                ? Availability.FAILED : Availability.LOADING;
    }
}
