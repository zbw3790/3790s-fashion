package vanillafashion.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import vanillafashion.cape.CapeId;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionSnapshot;

class PlayerFashionPayloadTest {
	private static final UUID FIRST = new UUID(0, 1);
	private static final CapeId CAPE = new CapeId("founder");
	private static final CapeId SECOND_CAPE = new CapeId("builder");

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 1024})
	void roundTripsAvailableMixedSnapshot(int count) {
		var entries = IntStream.range(0, count).mapToObj(index -> new PlayerFashionEntry(
				new UUID(0, index), index % 2 == 0
						? PlayerFashionAuthoritativeState.vanilla()
						: PlayerFashionAuthoritativeState.active(CAPE))).toList();
		var payload = new PlayerFashionSnapshotPayload(new PlayerFashionSnapshot(true, entries));
		assertEquals(payload, roundTrip(PlayerFashionSnapshotPayload.CODEC, payload));
	}

	@Test
	void roundTripsUnavailable() {
		var payload = new PlayerFashionSnapshotPayload(PlayerFashionSnapshot.unavailable());
		assertEquals(payload, roundTrip(PlayerFashionSnapshotPayload.CODEC, payload));
	}

	@ParameterizedTest
	@MethodSource("authoritativeStates")
	void roundTripsEveryAuthoritativeUpdate(PlayerFashionAuthoritativeState state) {
		var payload = new PlayerFashionUpdatePayload(new PlayerFashionEntry(FIRST, state));
		assertEquals(payload, roundTrip(PlayerFashionUpdatePayload.CODEC, payload));
	}

	@Test
	void roundTripsRemove() {
		var payload = new PlayerFashionRemovePayload(FIRST);
		assertEquals(payload, roundTrip(PlayerFashionRemovePayload.CODEC, payload));
	}

	@Test
	void modelRejectsOverLimitRatherThanTruncating() {
		var entries = IntStream.range(0, 1025)
				.mapToObj(i -> new PlayerFashionEntry(
						new UUID(0, i), PlayerFashionAuthoritativeState.vanilla())).toList();
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionSnapshot(true, entries));
	}

	@Test
	void modelRejectsDuplicates() {
		var entry = new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla());
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionSnapshot(true, List.of(entry, entry)));
	}

	@Test
	void modelRejectsUnavailableEntries() {
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionSnapshot(false,
				List.of(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla()))));
	}

	@Test
	void defensivelyCopiesAndSorts() {
		var entries = new ArrayList<PlayerFashionEntry>();
		entries.add(new PlayerFashionEntry(new UUID(0, 2), PlayerFashionAuthoritativeState.active(CAPE)));
		entries.add(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla()));
		var snapshot = new PlayerFashionSnapshot(true, entries);
		entries.clear();
		assertEquals(2, snapshot.entries().size());
		assertEquals(FIRST, snapshot.entries().getFirst().playerId());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 1025, Integer.MAX_VALUE})
	void rejectsUnboundedCountBeforeAllocation(int count) {
		withBuffer(buffer -> {
			buffer.writeBoolean(true);
			buffer.writeVarInt(count);
			assertThrows(DecoderException.class, () -> PlayerFashionSnapshotPayload.CODEC.decode(buffer));
		});
	}

	@Test
	void rejectsUnavailableNonemptyBeforeReadingEntries() {
		withBuffer(buffer -> {
			buffer.writeBoolean(false);
			buffer.writeVarInt(1);
			assertThrows(DecoderException.class, () -> PlayerFashionSnapshotPayload.CODEC.decode(buffer));
		});
	}

	@Test
	void rejectsDuplicateWireUuids() {
		withBuffer(buffer -> {
			buffer.writeBoolean(true);
			buffer.writeVarInt(2);
			PlayerFashionWire.writeEntry(buffer,
					new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla()));
			PlayerFashionWire.writeEntry(buffer,
					new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.active(CAPE)));
			assertThrows(DecoderException.class, () -> PlayerFashionSnapshotPayload.CODEC.decode(buffer));
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "../cape", "Upper", "a/b", "a:b"})
	void rejectsInvalidCapeInBothPayloads(String cape) {
		withBuffer(buffer -> {
			buffer.writeUUID(FIRST);
			buffer.writeBoolean(true);
			buffer.writeUtf(cape);
			assertThrows(DecoderException.class, () -> PlayerFashionUpdatePayload.CODEC.decode(buffer));
		});
		withBuffer(buffer -> {
			buffer.writeBoolean(true);
			buffer.writeVarInt(1);
			buffer.writeUUID(FIRST);
			buffer.writeBoolean(true);
			buffer.writeUtf(cape);
			assertThrows(DecoderException.class, () -> PlayerFashionSnapshotPayload.CODEC.decode(buffer));
		});
	}

	@ParameterizedTest
	@MethodSource("authoritativeStates")
	void snapshotEntryRoundTripsEveryAuthoritativeState(PlayerFashionAuthoritativeState state) {
		var payload = new PlayerFashionSnapshotPayload(new PlayerFashionSnapshot(
				true, List.of(new PlayerFashionEntry(FIRST, state))));
		assertEquals(payload, roundTrip(PlayerFashionSnapshotPayload.CODEC, payload));
	}

	@Test
	void mixedSnapshotKeepsVanillaActiveAndDormantDistinct() {
		var payload = new PlayerFashionSnapshotPayload(new PlayerFashionSnapshot(true, List.of(
				new PlayerFashionEntry(new UUID(0, 1), PlayerFashionAuthoritativeState.vanilla()),
				new PlayerFashionEntry(new UUID(0, 2), PlayerFashionAuthoritativeState.active(CAPE)),
				new PlayerFashionEntry(new UUID(0, 3), PlayerFashionAuthoritativeState.dormant(SECOND_CAPE)))));
		assertEquals(payload, roundTrip(PlayerFashionSnapshotPayload.CODEC, payload));
	}

	@Test
	void updateRejectsEffectiveWithoutStoredOnWire() {
		withBuffer(buffer -> {
			buffer.writeUUID(FIRST);
			buffer.writeBoolean(false);
			buffer.writeBoolean(true);
			buffer.writeUtf(CAPE.value());
			assertThrows(DecoderException.class, () -> PlayerFashionUpdatePayload.CODEC.decode(buffer));
		});
	}

	@Test
	void updateRejectsDifferentStoredAndEffectiveOnWire() {
		withBuffer(buffer -> {
			buffer.writeUUID(FIRST);
			buffer.writeBoolean(true);
			buffer.writeUtf(CAPE.value());
			buffer.writeBoolean(true);
			buffer.writeUtf(SECOND_CAPE.value());
			assertThrows(DecoderException.class, () -> PlayerFashionUpdatePayload.CODEC.decode(buffer));
		});
	}

	@Test
	void rejectsOverlongCape() {
		withBuffer(buffer -> {
			buffer.writeUUID(FIRST);
			buffer.writeBoolean(true);
			buffer.writeUtf("a".repeat(CapeId.MAX_LENGTH + 1));
			assertThrows(DecoderException.class, () -> PlayerFashionUpdatePayload.CODEC.decode(buffer));
		});
	}

	@Test
	void rejectsTruncatedPacket() {
		withBuffer(buffer -> {
			buffer.writeBoolean(true);
			buffer.writeVarInt(1);
			assertThrows(IndexOutOfBoundsException.class, () -> PlayerFashionSnapshotPayload.CODEC.decode(buffer));
		});
	}

	@Test
	void typesHaveOnlyIntendedIdentifiers() {
		assertEquals("vanilla_fashion:player_fashion_snapshot", PlayerFashionSnapshotPayload.TYPE.id().toString());
		assertEquals("vanilla_fashion:player_fashion_update", PlayerFashionUpdatePayload.TYPE.id().toString());
		assertEquals("vanilla_fashion:player_fashion_remove", PlayerFashionRemovePayload.TYPE.id().toString());
	}

	private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			codec.encode(buffer, value);
			T decoded = codec.decode(buffer);
			assertEquals(0, buffer.readableBytes());
			return decoded;
		} finally {
			buffer.release();
		}
	}

	private static Stream<PlayerFashionAuthoritativeState> authoritativeStates() {
		return Stream.of(
				PlayerFashionAuthoritativeState.vanilla(),
				PlayerFashionAuthoritativeState.active(CAPE),
				PlayerFashionAuthoritativeState.dormant(CAPE));
	}

	private static void withBuffer(Consumer<RegistryFriendlyByteBuf> test) {
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			test.accept(buffer);
		} finally {
			buffer.release();
		}
	}
}
