package dev.zbw3790.fashion.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.fashion.PlayerFashionEntry;

public record PlayerFashionUpdatePayload(PlayerFashionEntry entry) implements CustomPacketPayload {
	public static final Type<PlayerFashionUpdatePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "player_fashion_update"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerFashionUpdatePayload> CODEC =
			StreamCodec.of((buffer, payload) -> PlayerFashionWire.writeEntry(buffer, payload.entry()),
					buffer -> new PlayerFashionUpdatePayload(PlayerFashionWire.readEntry(buffer)));

	public PlayerFashionUpdatePayload {
		Objects.requireNonNull(entry, "玩家时装更新条目不能为 null。");
	}

	@Override
	public Type<PlayerFashionUpdatePayload> type() {
		return TYPE;
	}
}
