package dev.zbw3790.fashion.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;

public record OutfitRegistrySnapshotPayload(OutfitRegistrySnapshot snapshot) implements CustomPacketPayload {
    public static final Type<OutfitRegistrySnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "outfit_registry_snapshot"));
    public static final int MAX_BODY_BYTES = 33795;
    public static final StreamCodec<RegistryFriendlyByteBuf, OutfitRegistrySnapshotPayload> CODEC = StreamCodec.ofMember(OutfitRegistrySnapshotPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, OutfitRegistrySnapshotPayload::read));
    public OutfitRegistrySnapshotPayload { Objects.requireNonNull(snapshot); }
    void encode(RegistryFriendlyByteBuf b) { b.writeBoolean(snapshot.available()); b.writeVarInt(snapshot.entries().size());
        for (var entry : snapshot.entries()) {
            b.writeUtf(entry.id().value(), 64);
            int parts=0, declared=0, valid=0;
            for (int i=0; i<6; i++) if (entry.parts().contains(OutfitPart.CANONICAL_ORDER.get(i))) parts |= 1 << i;
            for (int i=0; i<2; i++) {
                var model = OutfitModel.CANONICAL_ORDER.get(i);
                if (entry.declaredModels().contains(model)) declared |= 1 << i;
                if (entry.validModels().containsKey(model)) valid |= 1 << i;
            }
            b.writeByte(parts); b.writeByte(declared); b.writeByte(valid);
            for (OutfitModel model : OutfitModel.CANONICAL_ORDER) if (entry.validModels().containsKey(model)) FashionWireCodec.hash(b, entry.validModels().get(model));
        } }
    static OutfitRegistrySnapshotPayload read(RegistryFriendlyByteBuf b) { boolean available = FashionWireCodec.bool(b); int count = FashionWireCodec.count(b, 0, OutfitRegistrySnapshot.MAX_OUTFITS);
        if (!available && count != 0) throw new IllegalArgumentException("不可用 Registry 必须为空。");
        var entries = new ArrayList<OutfitRegistrySnapshot.Entry>(count);
        for (int index=0; index<count; index++) {
            var id = new OutfitId(b.readUtf(64)); int parts = b.readUnsignedByte(), declared = b.readUnsignedByte(), valid = b.readUnsignedByte();
            if (parts == 0 || (parts & ~63) != 0 || declared == 0 || (declared & ~3) != 0 || (valid & ~declared) != 0) throw new IllegalArgumentException("装束定义掩码无效。");
            var partSet = EnumSet.noneOf(OutfitPart.class); var models = EnumSet.noneOf(OutfitModel.class); var hashes = new EnumMap<OutfitModel, String>(OutfitModel.class);
            for (int i=0; i<6; i++) if ((parts & (1 << i)) != 0) partSet.add(OutfitPart.CANONICAL_ORDER.get(i));
            for (int i=0; i<2; i++) {
                var model = OutfitModel.CANONICAL_ORDER.get(i);
                if ((declared & (1 << i)) != 0) models.add(model);
                if ((valid & (1 << i)) != 0) hashes.put(model, FashionWireCodec.hash(b));
            }
            entries.add(new OutfitRegistrySnapshot.Entry(id, partSet, models, hashes));
        }
        return new OutfitRegistrySnapshotPayload(new OutfitRegistrySnapshot(available, entries)); }
    @Override public Type<OutfitRegistrySnapshotPayload> type() { return TYPE; }
}
