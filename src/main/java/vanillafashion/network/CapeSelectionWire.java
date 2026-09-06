package vanillafashion.network;

import java.util.Optional;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import vanillafashion.cape.CapeId;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;

final class CapeSelectionWire {
	private CapeSelectionWire() { }

	static long positive(long requestId) {
		if (requestId <= 0) {
			throw new IllegalArgumentException("选择请求编号必须为正数。");
		}
		return requestId;
	}

	static Optional<CapeId> readSelection(RegistryFriendlyByteBuf buffer) {
		return buffer.readBoolean() ? Optional.of(new CapeId(buffer.readUtf(CapeId.MAX_LENGTH))) : Optional.empty();
	}

	static void writeSelection(RegistryFriendlyByteBuf buffer, Optional<CapeId> selection) {
		buffer.writeBoolean(selection.isPresent());
		selection.ifPresent(id -> buffer.writeUtf(id.value(), CapeId.MAX_LENGTH));
	}

	static PlayerFashionAuthoritativeState readAuthoritativeState(RegistryFriendlyByteBuf buffer) {
		return new PlayerFashionAuthoritativeState(readSelection(buffer), readSelection(buffer));
	}

	static void writeAuthoritativeState(RegistryFriendlyByteBuf buffer, PlayerFashionAuthoritativeState state) {
		writeSelection(buffer, state.storedSelection());
		writeSelection(buffer, state.effectiveSelection());
	}

	static DecoderException malformed(IllegalArgumentException cause) {
		return new DecoderException("时装选择消息字段无效。", cause);
	}
}
