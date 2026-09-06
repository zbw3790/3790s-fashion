package vanillafashion.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistrySnapshot;

class CapeRegistrySnapshotPayloadTest {
	@Test
	void codecRoundTripsBoundedSnapshot() {
		CapeRegistrySnapshot snapshot = new CapeRegistrySnapshot(List.of(
				new CapeCosmeticMetadata(
						new CapeId("cape_only"),
						"a".repeat(64),
						Optional.empty()
				),
				new CapeCosmeticMetadata(
						new CapeId("full"),
						"b".repeat(64),
						Optional.of("c".repeat(64))
				)
		));
		CapeRegistrySnapshotPayload payload = new CapeRegistrySnapshotPayload(snapshot);
		RegistryFriendlyByteBuf buffer = buffer();

		try {
			CapeRegistrySnapshotPayload.CODEC.encode(buffer, payload);
			CapeRegistrySnapshotPayload decoded = CapeRegistrySnapshotPayload.CODEC.decode(buffer);

			assertEquals(snapshot.entries(), decoded.snapshot().entries());
			assertEquals(0, buffer.readableBytes());
		} finally {
			buffer.release();
		}
	}

	@Test
	void codecRejectsEntryCountAboveLimit() {
		RegistryFriendlyByteBuf buffer = buffer();

		try {
			buffer.writeVarInt(CapeRegistrySnapshot.MAX_CAPE_ENTRIES + 1);

			assertThrows(
					DecoderException.class,
					() -> CapeRegistrySnapshotPayload.CODEC.decode(buffer)
			);
		} finally {
			buffer.release();
		}
	}

	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}
}
