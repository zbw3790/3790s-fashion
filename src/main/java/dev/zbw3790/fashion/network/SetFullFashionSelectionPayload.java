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

public record SetFullFashionSelectionPayload(long requestId, long expectedRevision, PlayerFashionStoredState stored) implements CustomPacketPayload {
    public static final Type<SetFullFashionSelectionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "set_full_fashion_selection"));
    public static final int MAX_BODY_BYTES = 478;
    public static final StreamCodec<FriendlyByteBuf, SetFullFashionSelectionPayload> CODEC = StreamCodec.ofMember(SetFullFashionSelectionPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, SetFullFashionSelectionPayload::read));
    public SetFullFashionSelectionPayload { if (requestId <= 0 || expectedRevision < 0) throw new IllegalArgumentException("请求或期望版本无效。"); Objects.requireNonNull(stored); }
    private void encode(FriendlyByteBuf b) { b.writeLong(requestId); b.writeLong(expectedRevision); FashionWireCodec.stored(b, stored); }
    private static SetFullFashionSelectionPayload read(FriendlyByteBuf b) { return new SetFullFashionSelectionPayload(FashionWireCodec.requestId(b), FashionWireCodec.nonnegative(b), FashionWireCodec.stored(b)); }
    @Override public Type<SetFullFashionSelectionPayload> type() { return TYPE; }
}
