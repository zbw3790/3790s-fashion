package dev.zbw3790.fashion.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeAssetLimits;

class CapeAssetDataPayloadTest {
	private static final String HASH = "a".repeat(64);

	@Test
	void acceptsBoundedBytesAndDefensivelyCopies() {
		byte[] input = {1, 2, 3};
		CapeAssetDataPayload payload = new CapeAssetDataPayload(HASH, input);
		input[0] = 9;
		byte[] output = payload.pngBytes();
		output[1] = 9;

		assertArrayEquals(new byte[] {1, 2, 3}, payload.pngBytes());
	}

	@Test
	void rejectsEmptyBytes() {
		assertThrows(IllegalArgumentException.class, () -> new CapeAssetDataPayload(HASH, new byte[0]));
	}

	@Test
	void rejectsBytesAboveLimit() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new CapeAssetDataPayload(HASH, new byte[CapeAssetLimits.MAX_ASSET_BYTES + 1])
		);
	}

	@Test
	void codecRoundTripsBoundedData() {
		CapeAssetDataPayload payload = new CapeAssetDataPayload(HASH, new byte[] {1, 2, 3});
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

		try {
			CapeAssetDataPayload.CODEC.encode(buffer, payload);
			CapeAssetDataPayload decoded = CapeAssetDataPayload.CODEC.decode(buffer);

			assertEquals(HASH, decoded.sha256());
			assertArrayEquals(payload.pngBytes(), decoded.pngBytes());
			assertEquals(0, buffer.readableBytes());
		} finally {
			buffer.release();
		}
	}

	@Test
	void codecRejectsByteArrayAboveLimitBeforeAllocation() {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

		try {
			buffer.writeUtf(HASH, 64);
			buffer.writeVarInt(CapeAssetLimits.MAX_ASSET_BYTES + 1);
			assertThrows(DecoderException.class, () -> CapeAssetDataPayload.CODEC.decode(buffer));
		} finally {
			buffer.release();
		}
	}
}
