package dev.zbw3790.fashion.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.armor.*;

/** 初始化和刷新都携带同一个全局资源代次；没有本机路径字段。 */
public record ArmorRegistryPayload(long generation,ArmorRegistrySnapshot snapshot) implements CustomPacketPayload {
    public static final Type<ArmorRegistryPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID,"armor_registry_v1"));
    public static final int MAX_BODY_BYTES=8+1+2+128*(33+242+2+64);
    public static final StreamCodec<RegistryFriendlyByteBuf,ArmorRegistryPayload> CODEC=StreamCodec.ofMember(ArmorRegistryPayload::encode,
        b->FashionWireCodec.decode(b,MAX_BODY_BYTES,ArmorRegistryPayload::read));
    public ArmorRegistryPayload { if(generation<0) throw new IllegalArgumentException("盔甲代次不能为负数。"); Objects.requireNonNull(snapshot); }
    private void encode(RegistryFriendlyByteBuf b) {
        b.writeLong(generation);b.writeBoolean(snapshot.available());b.writeVarInt(snapshot.entries().size());
        for(var entry:snapshot.entries()) {
            b.writeUtf(entry.id().value(),32);b.writeUtf(entry.name(),160);
            int slots=0,layers=0;for(var slot:entry.slots()) slots |= 1 << slot.ordinal();
            for(var layer:entry.hashes().keySet()) layers |= 1 << layer.ordinal();
            b.writeByte(slots);b.writeByte(layers);
            for(var layer:ArmorGeometry.values()) if(entry.hashes().containsKey(layer)) FashionWireCodec.hash(b,entry.hashes().get(layer));
        }
    }
    private static ArmorRegistryPayload read(RegistryFriendlyByteBuf b) {
        long generation=FashionWireCodec.nonnegative(b);boolean available=FashionWireCodec.bool(b);
        int count=FashionWireCodec.count(b,0,ArmorRegistrySnapshot.MAX_STYLES);var entries=new ArrayList<ArmorRegistrySnapshot.Entry>(count);
        if(!available && count!=0) throw new IllegalArgumentException("不可用盔甲 Registry 必须为空。");
        for(int i=0;i<count;i++) {
            var id=new ArmorStyleId(b.readUtf(32));String name=b.readUtf(160);
            int slots=b.readUnsignedByte(),layers=b.readUnsignedByte();
            if(slots==0 || slots>15 || layers==0 || layers>3) throw new IllegalArgumentException("盔甲定义位域无效。");
            var slotSet=EnumSet.noneOf(ArmorSlot.class);var hashes=new EnumMap<ArmorGeometry,String>(ArmorGeometry.class);
            for(var slot:ArmorSlot.CANONICAL_ORDER) if((slots & (1 << slot.ordinal()))!=0) slotSet.add(slot);
            for(var layer:ArmorGeometry.values()) if((layers & (1 << layer.ordinal()))!=0) hashes.put(layer,FashionWireCodec.hash(b));
            entries.add(new ArmorRegistrySnapshot.Entry(id,name,slotSet,hashes));
        }
        return new ArmorRegistryPayload(generation,new ArmorRegistrySnapshot(available,entries));
    }
    @Override public Type<ArmorRegistryPayload> type() { return TYPE; }
}
