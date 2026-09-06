package vanillafashion.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistrySnapshot;

public record CapeRegistrySnapshotPayload(CapeRegistrySnapshot snapshot) implements CustomPacketPayload {
	public static final Type<CapeRegistrySnapshotPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "cape_registry_snapshot")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CapeRegistrySnapshotPayload> CODEC =
			StreamCodec.ofMember(CapeRegistrySnapshotPayload::encode, CapeRegistrySnapshotPayload::decode);

	public CapeRegistrySnapshotPayload {
		snapshot = Objects.requireNonNull(snapshot, "Cape Registry Snapshot payload 不能为 null。");
	}

	private void encode(RegistryFriendlyByteBuf buffer) {
		List<CapeCosmeticMetadata> entries = snapshot.entries();
		ByteBufCodecs.writeCount(buffer, entries.size(), CapeRegistrySnapshot.MAX_CAPE_ENTRIES);

		for (CapeCosmeticMetadata entry : entries) {
			buffer.writeUtf(entry.id().value(), CapeId.MAX_LENGTH);
			buffer.writeUtf(entry.capeSha256(), CapeCosmeticMetadata.SHA_256_LENGTH);
			buffer.writeBoolean(entry.hasElytra());

			if (entry.hasElytra()) {
				buffer.writeUtf(entry.elytraSha256().orElseThrow(), CapeCosmeticMetadata.SHA_256_LENGTH);
			}
		}
	}

	private static CapeRegistrySnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
		int count = ByteBufCodecs.readCount(buffer, CapeRegistrySnapshot.MAX_CAPE_ENTRIES);

		if (count < 0) {
			throw new DecoderException("Cape Registry Snapshot 条目数不能为负数。");
		}

		List<CapeCosmeticMetadata> entries = new ArrayList<>(count);

		for (int index = 0; index < count; index++) {
			CapeId id = new CapeId(buffer.readUtf(CapeId.MAX_LENGTH));
			String capeSha256 = buffer.readUtf(CapeCosmeticMetadata.SHA_256_LENGTH);
			Optional<String> elytraSha256 = buffer.readBoolean()
					? Optional.of(buffer.readUtf(CapeCosmeticMetadata.SHA_256_LENGTH))
					: Optional.empty();
			entries.add(new CapeCosmeticMetadata(id, capeSha256, elytraSha256));
		}

		return new CapeRegistrySnapshotPayload(new CapeRegistrySnapshot(entries));
	}

	@Override
	public Type<CapeRegistrySnapshotPayload> type() {
		return TYPE;
	}
}
