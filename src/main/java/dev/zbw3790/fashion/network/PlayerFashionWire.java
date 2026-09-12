package dev.zbw3790.fashion.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import dev.zbw3790.fashion.fashion.PlayerFashionEntry;

final class PlayerFashionWire {
	private PlayerFashionWire() {
	}

	static void writeEntry(RegistryFriendlyByteBuf buffer, PlayerFashionEntry entry) {
		buffer.writeUUID(entry.playerId());
		CapeSelectionWire.writeAuthoritativeState(buffer, entry.state());
	}

	static PlayerFashionEntry readEntry(RegistryFriendlyByteBuf buffer) {
		var id = buffer.readUUID();
		try {
			return new PlayerFashionEntry(id, CapeSelectionWire.readAuthoritativeState(buffer));
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("玩家时装消息中的权威状态无效。", exception);
		}
	}
}
