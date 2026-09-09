package vanillafashion.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;

public record FullFashionSelectionResultPayload(long requestId, FullFashionSelectionStatus status, Optional<FullPlayerFashionState> authority) implements CustomPacketPayload {
    public static final Type<FullFashionSelectionResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "full_fashion_selection_result"));
    public static final int MAX_BODY_BYTES = 481;
    public static final StreamCodec<RegistryFriendlyByteBuf, FullFashionSelectionResultPayload> CODEC = StreamCodec.ofMember(FullFashionSelectionResultPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, FullFashionSelectionResultPayload::read));
    public FullFashionSelectionResultPayload { if (requestId <= 0) throw new IllegalArgumentException("请求标识必须为正数。"); Objects.requireNonNull(status); Objects.requireNonNull(authority); if (status == FullFashionSelectionStatus.SUCCESS && authority.isEmpty()) throw new IllegalArgumentException("成功结果必须携带权威。"); }
    private void encode(RegistryFriendlyByteBuf b) { b.writeLong(requestId); b.writeByte(status.wireId()); b.writeBoolean(authority.isPresent()); authority.ifPresent(value -> FashionWireCodec.authority(b, value)); }
    private static FullFashionSelectionResultPayload read(RegistryFriendlyByteBuf b) { long requestId = FashionWireCodec.requestId(b); var status = FullFashionSelectionStatus.fromWire(b.readUnsignedByte());
        return new FullFashionSelectionResultPayload(requestId, status, FashionWireCodec.bool(b) ? Optional.of(FashionWireCodec.authority(b)) : Optional.empty()); }
    @Override public Type<FullFashionSelectionResultPayload> type() { return TYPE; }
}
