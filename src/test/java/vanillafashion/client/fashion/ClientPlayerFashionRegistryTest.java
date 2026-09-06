package vanillafashion.client.fashion;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vanillafashion.cape.CapeId;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionSnapshot;

class ClientPlayerFashionRegistryTest {
	private static final UUID FIRST = new UUID(0, 1);
	private static final UUID SECOND = new UUID(0, 2);
	private static final CapeId CAPE = new CapeId("founder");
	private final ClientPlayerFashionRegistry registry = new ClientPlayerFashionRegistry();

	@Test
	void startsUninitializedAndUnknown() {
		assertEquals(ClientPlayerFashionRegistry.State.UNINITIALIZED, registry.state());
		assertTrue(registry.find(FIRST).isEmpty());
	}

	@Test
	void availableEmptySnapshotIsReadyButPlayerUnknown() {
		registry.replace(new PlayerFashionSnapshot(true, List.of()));
		assertEquals(ClientPlayerFashionRegistry.State.AVAILABLE, registry.state());
		assertTrue(registry.find(FIRST).isEmpty());
	}

	@Test
	void knownVanillaIsDistinctFromUnknown() {
		replace(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla()));
		assertTrue(registry.find(FIRST).isPresent());
		assertTrue(registry.find(FIRST).orElseThrow().isVanillaStored());
		assertTrue(registry.find(SECOND).isEmpty());
	}

	@Test
	void knownCustomRetainsCapeId() {
		replace(custom(FIRST));
		assertEquals(Optional.of(CAPE), registry.find(FIRST).orElseThrow().effectiveSelection());
	}

	@Test
	void fullReplaceDropsStaleEntries() {
		replace(custom(FIRST));
		replace(custom(SECOND));
		assertTrue(registry.find(FIRST).isEmpty());
		assertTrue(registry.find(SECOND).isPresent());
	}

	@Test
	void updateUpsertsAndCanChangeBackToVanilla() {
		replace(custom(FIRST));
		registry.update(custom(SECOND));
		registry.update(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla()));
		assertEquals(2, registry.size());
		assertTrue(registry.find(FIRST).orElseThrow().isVanillaStored());
		assertEquals(Optional.of(CAPE), registry.find(SECOND).orElseThrow().effectiveSelection());
	}

	@Test
	void removeMakesPlayerUnknownWithoutChangingReadiness() {
		replace(custom(FIRST));
		registry.remove(FIRST);
		assertTrue(registry.find(FIRST).isEmpty());
		assertEquals(ClientPlayerFashionRegistry.State.AVAILABLE, registry.state());
	}

	@Test
	void clearDropsConnectionState() {
		replace(custom(FIRST));
		registry.clear();
		assertEquals(ClientPlayerFashionRegistry.State.UNINITIALIZED, registry.state());
		assertEquals(0, registry.size());
	}

	@Test
	void unavailableSnapshotClearsAllEntries() {
		replace(custom(FIRST));
		registry.replace(PlayerFashionSnapshot.unavailable());
		assertEquals(ClientPlayerFashionRegistry.State.UNAVAILABLE, registry.state());
		assertTrue(registry.find(FIRST).isEmpty());
		assertEquals(0, registry.size());
	}

	@ParameterizedTest
	@EnumSource(value = ClientPlayerFashionRegistry.State.class, names = {"UNINITIALIZED", "UNAVAILABLE"})
	void deltasNeverEstablishReadiness(ClientPlayerFashionRegistry.State state) {
		if (state == ClientPlayerFashionRegistry.State.UNAVAILABLE) {
			registry.replace(PlayerFashionSnapshot.unavailable());
		}
		registry.update(custom(FIRST));
		registry.remove(FIRST);
		assertEquals(state, registry.state());
		assertEquals(0, registry.size());
		assertTrue(registry.find(FIRST).isEmpty());
	}

	@Test
	void newConnectionDropsOldSnapshotAndLateCleanupCannotTouchNewSnapshot() {
		Object old = new Object();
		Object current = new Object();
		registry.beginConnection(old);
		replace(custom(FIRST));
		registry.beginConnection(current);
		assertTrue(registry.find(FIRST).isEmpty());
		replace(custom(SECOND));
		registry.disconnect(old);
		assertTrue(registry.find(SECOND).isPresent());
		registry.disconnect(current);
		assertEquals(ClientPlayerFashionRegistry.State.UNINITIALIZED, registry.state());
		assertEquals(0, registry.size());
	}

	@Test
	void overflowDeltaInvalidatesInsteadOfKeepingPartialReadyRegistry() {
		registry.replace(new PlayerFashionSnapshot(true, IntStream.range(0, 1024)
				.mapToObj(i -> custom(new UUID(0, i))).toList()));
		registry.update(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.vanilla()));
		assertEquals(ClientPlayerFashionRegistry.State.AVAILABLE, registry.state());
		registry.update(custom(new UUID(0, 1024)));
		assertEquals(ClientPlayerFashionRegistry.State.UNAVAILABLE, registry.state());
		assertEquals(0, registry.size());
	}

	@Test
	void fullSnapshotRestoresAfterOverflow() {
		registry.replace(PlayerFashionSnapshot.unavailable());
		registry.update(custom(FIRST));
		replace(custom(SECOND));
		assertEquals(ClientPlayerFashionRegistry.State.AVAILABLE, registry.state());
		assertTrue(registry.find(FIRST).isEmpty());
		assertTrue(registry.find(SECOND).isPresent());
	}

	@Test
	void knownDormantIsDistinctFromKnownVanilla() {
		replace(
				new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.dormant(CAPE)),
				new PlayerFashionEntry(SECOND, PlayerFashionAuthoritativeState.vanilla()));
		assertTrue(registry.getKnownState(FIRST).orElseThrow().isDormant());
		assertTrue(registry.getKnownState(SECOND).orElseThrow().isVanillaStored());
		assertNotEquals(registry.getKnownState(FIRST), registry.getKnownState(SECOND));
	}

	@Test
	void dormantStoredQueryKeepsCapeId() {
		replace(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.dormant(CAPE)));
		assertEquals(Optional.of(CAPE), registry.getStoredSelection(FIRST));
	}

	@Test
	void dormantEffectiveQueryIsEmpty() {
		replace(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.dormant(CAPE)));
		assertTrue(registry.getEffectiveSelection(FIRST).isEmpty());
	}

	@Test
	void selfConfirmedDormantSurvivesWhileGlobalSnapshotUnavailable() {
		registry.replace(PlayerFashionSnapshot.unavailable());
		registry.confirmSelfResult(FIRST, PlayerFashionAuthoritativeState.dormant(CAPE));
		assertEquals(PlayerFashionAuthoritativeState.dormant(CAPE),
				registry.selfAuthority(FIRST).orElseThrow());
		assertTrue(registry.getKnownState(FIRST).isEmpty());
		assertEquals(ClientPlayerFashionRegistry.State.UNAVAILABLE, registry.state());
	}

	@Test
	void dormantUpdateCanBecomeActiveWithoutLosingStoredId() {
		replace(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.dormant(CAPE)));
		registry.update(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.active(CAPE)));
		assertEquals(PlayerFashionAuthoritativeState.active(CAPE), registry.getKnownState(FIRST).orElseThrow());
	}

	private void replace(PlayerFashionEntry... entries) {
		registry.replace(new PlayerFashionSnapshot(true, List.of(entries)));
	}

	private static PlayerFashionEntry custom(UUID id) {
		return new PlayerFashionEntry(id, PlayerFashionAuthoritativeState.active(CAPE));
	}
}
