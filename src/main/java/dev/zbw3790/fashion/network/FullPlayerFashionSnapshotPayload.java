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

public record FullPlayerFashionSnapshotPayload(FullPlayerFashionSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<FullPlayerFashionSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "full_player_fashion_snapshot"));
    public static final int MAX_BODY_BYTES = 498691;
    public static final StreamCodec<RegistryFriendlyByteBuf, FullPlayerFashionSnapshotPayload> CODEC = StreamCodec.ofMember(FullPlayerFashionSnapshotPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, FullPlayerFashionSnapshotPayload::read));
    public FullPlayerFashionSnapshotPayload { Objects.requireNonNull(snapshot); }
    private void encode(RegistryFriendlyByteBuf b) { b.writeBoolean(snapshot.available()); b.writeVarInt(snapshot.entries().size()); snapshot.entries().forEach(entry -> FashionWireCodec.entry(b, entry)); }
    private static FullPlayerFashionSnapshotPayload read(RegistryFriendlyByteBuf b) { boolean available = FashionWireCodec.bool(b); int count = FashionWireCodec.count(b, 0, FullPlayerFashionSnapshot.MAX_PLAYERS);
        if (!available && count != 0) throw new IllegalArgumentException("不可用集合必须为空。");
        var entries = new ArrayList<FullPlayerFashionEntry>(count);
        for (int i=0; i<count; i++) entries.add(FashionWireCodec.entry(b));
        return new FullPlayerFashionSnapshotPayload(new FullPlayerFashionSnapshot(available, entries)); }
    @Override public Type<FullPlayerFashionSnapshotPayload> type() { return TYPE; }
}
