package dev.zbw3790.fashion.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;

public record PlayerFashionRemovePayload(UUID playerId) implements CustomPacketPayload {
	public static final Type<PlayerFashionRemovePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "player_fashion_remove"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerFashionRemovePayload> CODEC =
			StreamCodec.of((buffer, payload) -> buffer.writeUUID(payload.playerId()),
					buffer -> new PlayerFashionRemovePayload(buffer.readUUID()));

	public PlayerFashionRemovePayload {
		Objects.requireNonNull(playerId, "玩家 UUID 不能为 null。");
	}

	@Override
	public Type<PlayerFashionRemovePayload> type() {
		return TYPE;
	}
}
