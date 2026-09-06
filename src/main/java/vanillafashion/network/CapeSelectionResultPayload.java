package vanillafashion.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;

public record CapeSelectionResultPayload(long requestId, boolean accepted,
		PlayerFashionAuthoritativeState authoritativeState, CapeSelectionReason reason) implements CustomPacketPayload {
	public static final Type<CapeSelectionResultPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "cape_selection_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CapeSelectionResultPayload> CODEC = StreamCodec.of(
			(buffer, payload) -> {
				buffer.writeLong(payload.requestId());
				buffer.writeBoolean(payload.accepted());
				CapeSelectionWire.writeAuthoritativeState(buffer, payload.authoritativeState());
				buffer.writeByte(payload.reason().wireId());
			}, buffer -> {
				try {
					long id = CapeSelectionWire.positive(buffer.readLong());
					boolean accepted = buffer.readBoolean();
					var state = CapeSelectionWire.readAuthoritativeState(buffer);
					return new CapeSelectionResultPayload(id, accepted, state,
							CapeSelectionReason.fromWire(buffer.readUnsignedByte()));
				} catch (IllegalArgumentException exception) {
					throw CapeSelectionWire.malformed(exception);
				}
			});

	public CapeSelectionResultPayload {
		CapeSelectionWire.positive(requestId);
		Objects.requireNonNull(authoritativeState, "权威玩家时装状态不能为 null。");
		Objects.requireNonNull(reason, "选择结果原因不能为 null。");
		if (accepted != reason.accepted()) {
			throw new IllegalArgumentException("选择结果与接受标志不一致。");
		}
	}

	@Override
	public Type<CapeSelectionResultPayload> type() { return TYPE; }
}
