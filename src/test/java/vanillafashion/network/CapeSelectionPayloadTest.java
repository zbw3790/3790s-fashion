package vanillafashion.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeId;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;

class CapeSelectionPayloadTest {
	private static final Optional<CapeId> CAPE = Optional.of(new CapeId("founder"));
	private static final CapeId CAPE_ID = CAPE.orElseThrow();

	@ParameterizedTest
	@ValueSource(longs = {1, 17, Long.MAX_VALUE})
	void requestRoundTripsPositiveIdAndBothSelections(long id) {
		for (var selection : java.util.List.of(Optional.<CapeId>empty(), CAPE)) {
			var request = new SetCapeSelectionPayload(id, selection);
			assertEquals(request, roundTrip(SetCapeSelectionPayload.CODEC, request));
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1, Long.MIN_VALUE})
	void constructorsRejectNonPositiveRequestIds(long id) {
		assertThrows(IllegalArgumentException.class, () -> new SetCapeSelectionPayload(id, CAPE));
		assertThrows(IllegalArgumentException.class, () -> new CapeSelectionResultPayload(
				id, true, PlayerFashionAuthoritativeState.active(CAPE_ID), CapeSelectionReason.APPLIED));
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1, Long.MIN_VALUE})
	void decodeRejectsNonPositiveIdBeforeRemainingFields(long id) {
		assertMalformed(SetCapeSelectionPayload.CODEC, buffer -> buffer.writeLong(id));
		assertMalformed(CapeSelectionResultPayload.CODEC, buffer -> buffer.writeLong(id));
	}

	@ParameterizedTest
	@EnumSource(CapeSelectionReason.class)
	void resultRoundTripsEveryReasonAndAuthoritativeState(CapeSelectionReason reason) {
		for (var state : java.util.List.of(
				PlayerFashionAuthoritativeState.vanilla(),
				PlayerFashionAuthoritativeState.active(CAPE_ID),
				PlayerFashionAuthoritativeState.dormant(CAPE_ID))) {
			var result = new CapeSelectionResultPayload(Long.MAX_VALUE, reason.accepted(), state, reason);
			assertEquals(result, roundTrip(CapeSelectionResultPayload.CODEC, result));
		}
	}

	@ParameterizedTest
	@EnumSource(CapeSelectionReason.class)
	void inconsistentAcceptedReasonRejectedByModelAndWire(CapeSelectionReason reason) {
		assertThrows(IllegalArgumentException.class, () -> new CapeSelectionResultPayload(
				1, !reason.accepted(), PlayerFashionAuthoritativeState.active(CAPE_ID), reason));
		assertMalformed(CapeSelectionResultPayload.CODEC, buffer -> {
			buffer.writeLong(1);
			buffer.writeBoolean(!reason.accepted());
			buffer.writeBoolean(false);
			buffer.writeBoolean(false);
			buffer.writeByte(reason.wireId());
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "A", "../cape", "a:b", "a/b", "披风", "a b"})
	void bothCodecsRejectInvalidCapeSyntax(String cape) {
		assertMalformed(SetCapeSelectionPayload.CODEC, buffer -> writeRequest(buffer, cape));
		assertMalformed(CapeSelectionResultPayload.CODEC, buffer -> {
			buffer.writeLong(1);
			buffer.writeBoolean(true);
			buffer.writeBoolean(true);
			buffer.writeUtf(cape);
			buffer.writeBoolean(false);
			buffer.writeByte(0);
		});
	}

	@Test
	void length64RoundTripsBut65AndOversizedWireAreRejected() {
		var selection = Optional.of(new CapeId("a".repeat(64)));
		var request = new SetCapeSelectionPayload(1, selection);
		assertEquals(request, roundTrip(SetCapeSelectionPayload.CODEC, request));
		assertMalformed(SetCapeSelectionPayload.CODEC, buffer -> writeRequest(buffer, "a".repeat(65)));
		assertMalformed(SetCapeSelectionPayload.CODEC, buffer -> {
			buffer.writeLong(1);
			buffer.writeBoolean(true);
			buffer.writeVarInt(Integer.MAX_VALUE);
		});
	}

	@ParameterizedTest
	@ValueSource(ints = {6, 127, 255})
	void unknownReasonHasBoundedRejection(int reason) {
		assertMalformed(CapeSelectionResultPayload.CODEC, buffer -> {
			buffer.writeLong(1);
			buffer.writeBoolean(false);
			buffer.writeBoolean(false);
			buffer.writeBoolean(false);
			buffer.writeByte(reason);
		});
	}

	@Test
	void everyTruncatedPrefixFails() {
		assertTruncated(SetCapeSelectionPayload.CODEC, new SetCapeSelectionPayload(17, CAPE));
		assertTruncated(CapeSelectionResultPayload.CODEC, new CapeSelectionResultPayload(
				17, true, PlayerFashionAuthoritativeState.active(CAPE_ID), CapeSelectionReason.APPLIED));
	}

	@Test
	void fieldsContainNoTargetUuidOrAssetData() {
		assertEquals(java.util.List.of("requestId", "selection"), java.util.Arrays.stream(
				SetCapeSelectionPayload.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
		assertEquals(java.util.List.of("requestId", "accepted", "authoritativeState", "reason"), java.util.Arrays.stream(
				CapeSelectionResultPayload.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
		assertEquals("vanilla_fashion:set_cape_selection", SetCapeSelectionPayload.TYPE.id().toString());
		assertEquals("vanilla_fashion:cape_selection_result", CapeSelectionResultPayload.TYPE.id().toString());
	}

	@Test
	void nullValuesAreNotVanillaOrUnknownReason() {
		assertThrows(NullPointerException.class, () -> new SetCapeSelectionPayload(1, null));
		assertThrows(NullPointerException.class, () -> new CapeSelectionResultPayload(1, true, null, CapeSelectionReason.APPLIED));
		assertThrows(NullPointerException.class, () -> new CapeSelectionResultPayload(
				1, true, PlayerFashionAuthoritativeState.active(CAPE_ID), null));
	}

	@Test
	void acceptedResultKeepsActiveAuthoritativeState() {
		var state = PlayerFashionAuthoritativeState.active(CAPE_ID);
		assertEquals(state, roundTrip(CapeSelectionResultPayload.CODEC,
				new CapeSelectionResultPayload(1, true, state, CapeSelectionReason.APPLIED)).authoritativeState());
	}

	@Test
	void acceptedResultKeepsVanillaAuthoritativeState() {
		var state = PlayerFashionAuthoritativeState.vanilla();
		assertEquals(state, roundTrip(CapeSelectionResultPayload.CODEC,
				new CapeSelectionResultPayload(1, true, state, CapeSelectionReason.NO_CHANGE)).authoritativeState());
	}

	@Test
	void rejectedResultKeepsDormantAuthoritativeState() {
		var state = PlayerFashionAuthoritativeState.dormant(CAPE_ID);
		assertEquals(state, roundTrip(CapeSelectionResultPayload.CODEC,
				new CapeSelectionResultPayload(
						1, false, state, CapeSelectionReason.CAPE_NOT_AVAILABLE)).authoritativeState());
	}

	@Test
	void resultCodecRejectsEffectiveWithoutStored() {
		assertMalformed(CapeSelectionResultPayload.CODEC, buffer -> {
			buffer.writeLong(1);
			buffer.writeBoolean(false);
			buffer.writeBoolean(false);
			buffer.writeBoolean(true);
			buffer.writeUtf(CAPE_ID.value());
		});
	}

	@Test
	void resultCodecRejectsDifferentStoredAndEffective() {
		assertMalformed(CapeSelectionResultPayload.CODEC, buffer -> {
			buffer.writeLong(1);
			buffer.writeBoolean(false);
			buffer.writeBoolean(true);
			buffer.writeUtf(CAPE_ID.value());
			buffer.writeBoolean(true);
			buffer.writeUtf("builder");
		});
	}

	private static void writeRequest(RegistryFriendlyByteBuf buffer, String cape) {
		buffer.writeLong(1);
		buffer.writeBoolean(true);
		buffer.writeUtf(cape);
	}

	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
		var buffer = buffer();
		try {
			codec.encode(buffer, payload);
			var result = codec.decode(buffer);
			assertEquals(0, buffer.readableBytes());
			return result;
		} finally { buffer.release(); }
	}

	private static void assertMalformed(StreamCodec<RegistryFriendlyByteBuf, ?> codec,
			Consumer<RegistryFriendlyByteBuf> writer) {
		var buffer = buffer();
		try {
			writer.accept(buffer);
			assertThrows(DecoderException.class, () -> codec.decode(buffer));
		} finally { buffer.release(); }
	}

	private static <T> void assertTruncated(StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
		var full = buffer();
		try {
			codec.encode(full, payload);
			for (int length = 0; length < full.readableBytes(); length++) {
				var prefix = buffer();
				try {
					prefix.writeBytes(full, 0, length);
					assertThrows(RuntimeException.class, () -> codec.decode(prefix));
				} finally { prefix.release(); }
			}
		} finally { full.release(); }
	}
}
