package dev.zbw3790.fashion.network;

import java.util.ArrayList;
import java.util.Objects;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.fashion.PlayerFashionEntry;
import dev.zbw3790.fashion.fashion.PlayerFashionSnapshot;

public record PlayerFashionSnapshotPayload(PlayerFashionSnapshot snapshot) implements CustomPacketPayload {
	public static final Type<PlayerFashionSnapshotPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "player_fashion_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerFashionSnapshotPayload> CODEC =
			StreamCodec.ofMember(PlayerFashionSnapshotPayload::encode, PlayerFashionSnapshotPayload::decode);

	public PlayerFashionSnapshotPayload {
		Objects.requireNonNull(snapshot, "玩家时装 Snapshot 不能为 null。");
	}

	private void encode(RegistryFriendlyByteBuf buffer) {
		buffer.writeBoolean(snapshot.snapshotAvailable());
		ByteBufCodecs.writeCount(buffer, snapshot.entries().size(), PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES);
		snapshot.entries().forEach(entry -> PlayerFashionWire.writeEntry(buffer, entry));
	}

	private static PlayerFashionSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
		boolean available = buffer.readBoolean();
		int count = ByteBufCodecs.readCount(buffer, PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES);
		if (count < 0 || (!available && count != 0)) {
			throw new DecoderException("玩家时装 Snapshot 数量或可用性字段无效。");
		}
		var entries = new ArrayList<PlayerFashionEntry>(count);
		for (int index = 0; index < count; index++) {
			entries.add(PlayerFashionWire.readEntry(buffer));
		}
		try {
			return new PlayerFashionSnapshotPayload(new PlayerFashionSnapshot(available, entries));
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("玩家时装 Snapshot 条目无效。", exception);
		}
	}

	@Override
	public Type<PlayerFashionSnapshotPayload> type() {
		return TYPE;
	}
}
