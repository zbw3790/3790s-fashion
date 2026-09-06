package vanillafashion.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;
import vanillafashion.fashion.PlayerFashionEntry;

public record PlayerFashionUpdatePayload(PlayerFashionEntry entry) implements CustomPacketPayload {
	public static final Type<PlayerFashionUpdatePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "player_fashion_update"));
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
