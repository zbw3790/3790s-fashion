package dev.zbw3790.fashion.network;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.cape.CapeId;

public record SetCapeSelectionPayload(long requestId, Optional<CapeId> selection) implements CustomPacketPayload {
	public static final Type<SetCapeSelectionPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "set_cape_selection"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SetCapeSelectionPayload> CODEC = StreamCodec.of(
			(buffer, payload) -> {
				buffer.writeLong(payload.requestId());
				CapeSelectionWire.writeSelection(buffer, payload.selection());
			}, buffer -> {
				try {
					long id = CapeSelectionWire.positive(buffer.readLong());
					return new SetCapeSelectionPayload(id, CapeSelectionWire.readSelection(buffer));
				} catch (IllegalArgumentException exception) {
					throw CapeSelectionWire.malformed(exception);
				}
			});

	public SetCapeSelectionPayload {
		CapeSelectionWire.positive(requestId);
		Objects.requireNonNull(selection, "原版选择必须使用 Optional.empty()。");
	}

	@Override
	public Type<SetCapeSelectionPayload> type() { return TYPE; }
}
