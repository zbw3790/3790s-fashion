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

public record FullPlayerFashionUpdatePayload(FullPlayerFashionEntry entry) implements CustomPacketPayload {
    public static final Type<FullPlayerFashionUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "full_player_fashion_update_v4"));
    public static final int MAX_BODY_BYTES = 621;
    public static final StreamCodec<RegistryFriendlyByteBuf, FullPlayerFashionUpdatePayload> CODEC = StreamCodec.ofMember(FullPlayerFashionUpdatePayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, FullPlayerFashionUpdatePayload::read));
    public FullPlayerFashionUpdatePayload { Objects.requireNonNull(entry); }
    private void encode(RegistryFriendlyByteBuf b) { FashionWireCodec.entry(b, entry); }
    private static FullPlayerFashionUpdatePayload read(RegistryFriendlyByteBuf b) { return new FullPlayerFashionUpdatePayload(FashionWireCodec.entry(b)); }
    @Override public Type<FullPlayerFashionUpdatePayload> type() { return TYPE; }
}
