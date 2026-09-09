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

public record FullPlayerFashionRemovePayload(UUID playerId, long revision, Reason reason) implements CustomPacketPayload {
    public static final Type<FullPlayerFashionRemovePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "full_player_fashion_remove"));
    public static final int MAX_BODY_BYTES = 25;
    public static final StreamCodec<RegistryFriendlyByteBuf, FullPlayerFashionRemovePayload> CODEC = StreamCodec.ofMember(FullPlayerFashionRemovePayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, FullPlayerFashionRemovePayload::read));
    public FullPlayerFashionRemovePayload { Objects.requireNonNull(playerId); Objects.requireNonNull(reason); if (revision < 0) throw new IllegalArgumentException("版本不能为负数。"); }
    private void encode(RegistryFriendlyByteBuf b) { b.writeUUID(playerId); b.writeLong(revision); b.writeByte(reason.wireId()); }
    private static FullPlayerFashionRemovePayload read(RegistryFriendlyByteBuf b) { return new FullPlayerFashionRemovePayload(b.readUUID(), FashionWireCodec.nonnegative(b), Reason.fromWire(b.readUnsignedByte())); }
    @Override public Type<FullPlayerFashionRemovePayload> type() { return TYPE; }
    public enum Reason {
        DEFAULT(0), LEFT(1);
        private final int wireId;
        Reason(int wireId) { this.wireId = wireId; }
        public int wireId() { return wireId; }
        public static Reason fromWire(int value) { return switch(value) { case 0 -> DEFAULT; case 1 -> LEFT; default -> throw new IllegalArgumentException("未知移除原因。"); }; }
    }
}
