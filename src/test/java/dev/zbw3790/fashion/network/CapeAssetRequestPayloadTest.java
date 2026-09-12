package dev.zbw3790.fashion.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeAssetLimits;

class CapeAssetRequestPayloadTest {
	@Test
	void acceptsOneHash() {
		assertEquals(List.of(hash(1)), new CapeAssetRequestPayload(List.of(hash(1))).sha256Hashes());
	}

	@Test
	void acceptsSixtyFourHashes() {
		assertEquals(
				CapeAssetLimits.MAX_REQUEST_HASHES,
				new CapeAssetRequestPayload(hashes(CapeAssetLimits.MAX_REQUEST_HASHES)).sha256Hashes().size()
		);
	}

	@Test
	void rejectsSixtyFiveHashes() {
		assertThrows(IllegalArgumentException.class, () -> new CapeAssetRequestPayload(hashes(65)));
	}

	@Test
	void rejectsDuplicateHash() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new CapeAssetRequestPayload(List.of(hash(1), hash(1)))
		);
	}

	@Test
	void rejectsInvalidHashWithoutRepair() {
		assertThrows(IllegalArgumentException.class, () -> new CapeAssetRequestPayload(List.of("A".repeat(64))));
		assertThrows(IllegalArgumentException.class, () -> new CapeAssetRequestPayload(List.of(" " + hash(1))));
	}

	@Test
	void codecRoundTripsBoundedRequest() {
		CapeAssetRequestPayload payload = new CapeAssetRequestPayload(List.of(hash(1), hash(2)));
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

		try {
			CapeAssetRequestPayload.CODEC.encode(buffer, payload);
			CapeAssetRequestPayload decoded = CapeAssetRequestPayload.CODEC.decode(buffer);

			assertEquals(payload.sha256Hashes(), decoded.sha256Hashes());
			assertEquals(0, buffer.readableBytes());
		} finally {
			buffer.release();
		}
	}

	@Test
	void codecRejectsEmptyRequest() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

		try {
			buffer.writeVarInt(0);
			assertThrows(DecoderException.class, () -> CapeAssetRequestPayload.CODEC.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	@Test
	void codecRejectsRequestCountAboveLimitBeforeAllocation() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

		try {
			buffer.writeVarInt(CapeAssetLimits.MAX_REQUEST_HASHES + 1);
			assertThrows(DecoderException.class, () -> CapeAssetRequestPayload.CODEC.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	private static List<String> hashes(int count) {
		List<String> hashes = new ArrayList<>();

		for (int index = 0; index < count; index++) {
			hashes.add(hash(index + 1));
		}

		return hashes;
	}

	private static String hash(int value) {
		return "%064x".formatted(value);
	}
}
