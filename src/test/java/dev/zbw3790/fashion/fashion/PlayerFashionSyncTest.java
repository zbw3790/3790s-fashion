package dev.zbw3790.fashion.fashion;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.cape.CapeRegistryKnowledge;

class PlayerFashionSyncTest {
	@TempDir Path root;

	private PlayerFashionService service() {
		return FashionTestSupport.service(data(Map.of(FIRST, FOUNDER)), valid(root));
	}

	@Test
	void includesSelfVanillaAndModlessButNotOffline() {
		var service = service();
		service.setSelection(new UUID(0, 9000), Optional.of(BUILDER));
		var snapshot = PlayerFashionSnapshotBuilder.build(List.of(SECOND, FIRST), service);
		assertTrue(snapshot.snapshotAvailable());
		assertEquals(List.of(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.active(FOUNDER)),
				new PlayerFashionEntry(SECOND, PlayerFashionAuthoritativeState.vanilla())), snapshot.entries());
	}

	@Test
	void dormantStoredSelectionIsSentAsDormantWithoutMutation() {
		var service = service();
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of(FOUNDER)));
		var snapshot = PlayerFashionSnapshotBuilder.build(List.of(FIRST), service);
		assertEquals(PlayerFashionAuthoritativeState.dormant(FOUNDER), snapshot.entries().getFirst().state());
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
	}

	@Test
	void orderDoesNotDependOnOnlineIteration() {
		assertEquals(PlayerFashionSnapshotBuilder.build(List.of(FIRST, SECOND), service()),
				PlayerFashionSnapshotBuilder.build(List.of(SECOND, FIRST), service()));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 1024})
	void builderAllowsCompleteBoundary(int count) {
		var snapshot = PlayerFashionSnapshotBuilder.build(players(count), service());
		assertTrue(snapshot.snapshotAvailable());
		assertEquals(count, snapshot.entries().size());
	}

	@ParameterizedTest
	@ValueSource(ints = {1025, 2048})
	void builderFailsClosedWithoutTruncation(int count) {
		assertEquals(PlayerFashionSnapshot.unavailable(), PlayerFashionSnapshotBuilder.build(players(count), service()));
	}

	@Test
	void builderRejectsDuplicateOnlineIds() {
		assertThrows(IllegalArgumentException.class,
				() -> PlayerFashionSnapshotBuilder.build(List.of(FIRST, FIRST), service()));
	}

	@Test
	void trustedReadOnlyCanProvideSnapshot() {
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(
				data(Map.of(FIRST, FOUNDER)), Optional.of("测试可信只读")), valid(root));
		assertNotEquals(PlayerFashionService.Availability.NORMAL_WRITABLE, service.availability());
		assertTrue(service.canProvideAuthoritativeSnapshot());
		assertEquals(PlayerFashionAuthoritativeState.active(FOUNDER),
				PlayerFashionSnapshotBuilder.build(List.of(FIRST), service).entries().getFirst().state());
	}

	@Test
	void unreadablePlaceholderCannotProvideSnapshot() {
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(
				data(Map.of()), Optional.of("测试整体读取失败"), false), valid(root));
		assertFalse(service.canProvideAuthoritativeSnapshot());
		assertEquals(PlayerFashionSnapshot.unavailable(), PlayerFashionSnapshotBuilder.build(List.of(FIRST), service));
	}

	@Test
	void registryFailureReportsDormantStoredAndSafeEffectiveVanilla() {
		var service = service();
		service.reconcile(CapeRegistryKnowledge.unavailable(root));
		var snapshot = PlayerFashionSnapshotBuilder.build(List.of(FIRST), service);
		assertTrue(snapshot.snapshotAvailable());
		assertEquals(PlayerFashionAuthoritativeState.dormant(FOUNDER), snapshot.entries().getFirst().state());
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
	}

	@Test
	void stoppedServiceIsUnavailable() {
		var service = service();
		service.stop();
		assertFalse(PlayerFashionSnapshotBuilder.build(List.of(), service).snapshotAvailable());
	}

	@Test
	void joinPlansSelfSnapshotAndExistingClientUpdate() {
		var plan = PlayerFashionSyncPlanner.join(List.of(FIRST, SECOND), 1, FIRST, service());
		assertFalse(plan.snapshotForAll());
		assertEquals(2, plan.snapshot().entries().size());
		assertEquals(new PlayerFashionEntry(FIRST, PlayerFashionAuthoritativeState.active(FOUNDER)),
				plan.update().orElseThrow());
	}

	@Test
	void modlessNewcomerStillProducesKnownVanillaDelta() {
		var plan = PlayerFashionSyncPlanner.join(List.of(FIRST, SECOND), 1, SECOND, service());
		assertEquals(new PlayerFashionEntry(SECOND, PlayerFashionAuthoritativeState.vanilla()),
				plan.update().orElseThrow());
	}

	@Test
	void rejectedCosmeticRetainsStoredCustomAndEffectiveVanilla() {
		var service = service();
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of(FOUNDER)));
		assertEquals(PlayerFashionAuthoritativeState.dormant(FOUNDER),
				PlayerFashionAuthoritativeState.fromService(FIRST, service));
	}

	@Test
	void definiteDeletionClearsStoredAndEffectiveToVanilla() {
		var service = service();
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of()));
		assertEquals(PlayerFashionAuthoritativeState.vanilla(),
				PlayerFashionAuthoritativeState.fromService(FIRST, service));
	}

	@Test
	void repairReactivatesDormantStoredSelection() {
		var service = service();
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of(FOUNDER)));
		assertTrue(PlayerFashionAuthoritativeState.fromService(FIRST, service).isDormant());
		service.reconcile(valid(root));
		assertEquals(PlayerFashionAuthoritativeState.active(FOUNDER),
				PlayerFashionAuthoritativeState.fromService(FIRST, service));
	}

	@Test
	void deletedThenRecreatedCosmeticRemainsVanillaAfterStoredClear() {
		var service = service();
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of()));
		service.reconcile(valid(root));
		assertEquals(PlayerFashionAuthoritativeState.vanilla(),
				PlayerFashionAuthoritativeState.fromService(FIRST, service));
	}

	@Test
	void joinAt1024StillHasCompleteSnapshotAndDelta() {
		var plan = PlayerFashionSyncPlanner.join(players(1024), 1023, new UUID(0, 1023), service());
		assertTrue(plan.snapshot().snapshotAvailable());
		assertFalse(plan.snapshotForAll());
		assertTrue(plan.update().isPresent());
	}

	@Test
	void join1025InvalidatesEveryExistingSnapshot() {
		var plan = PlayerFashionSyncPlanner.join(players(1025), 1024, new UUID(0, 1024), service());
		assertTrue(plan.snapshotForAll());
		assertEquals(PlayerFashionSnapshot.unavailable(), plan.snapshot());
		assertTrue(plan.update().isEmpty());
	}

	@Test
	void leave1025To1024RecoversEveryClient() {
		var plan = PlayerFashionSyncPlanner.leave(players(1024), 1025, new UUID(0, 1024), service());
		assertTrue(plan.snapshotForAll());
		assertTrue(plan.snapshot().snapshotAvailable());
		assertEquals(1024, plan.snapshot().entries().size());
		assertEquals(Optional.of(new UUID(0, 1024)), plan.remove());
	}

	@Test
	void leaveWhileStillOverflowKeepsUnavailable() {
		var plan = PlayerFashionSyncPlanner.leave(players(1025), 1026, new UUID(0, 1025), service());
		assertTrue(plan.snapshotForAll());
		assertFalse(plan.snapshot().snapshotAvailable());
	}

	@Test
	void modlessLeavePlansRemoveWithoutDeletingSavedData() {
		var service = service();
		var plan = PlayerFashionSyncPlanner.leave(List.of(SECOND), 2, FIRST, service);
		assertFalse(plan.snapshotForAll());
		assertEquals(Optional.of(FIRST), plan.remove());
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
	}

	@Test
	void unreadableServiceInvalidatesExistingClientsOnJoin() {
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(
				data(Map.of()), Optional.of("测试故障"), false), valid(root));
		var plan = PlayerFashionSyncPlanner.join(List.of(FIRST, SECOND), 1, SECOND, service);
		assertTrue(plan.snapshotForAll());
		assertFalse(plan.snapshot().snapshotAvailable());
		assertTrue(plan.update().isEmpty());
	}

	@Test
	void sessionsIncludeJoinImmediatelyAndLeaveBeforePlayerListChanges() {
		var sessions = new PlayerFashionOnlineSessions<Object>();
		Object first = new Object();
		Object second = new Object();
		sessions.join(FIRST, first);
		sessions.join(SECOND, second);
		assertEquals(Set.of(FIRST, SECOND), sessions.snapshot().keySet());
		assertTrue(sessions.leave(FIRST, first));
		assertEquals(Set.of(SECOND), sessions.snapshot().keySet());
	}

	@Test
	void delayedOldConnectionLeaveDoesNotRemoveReconnectedPlayer() {
		var sessions = new PlayerFashionOnlineSessions<Object>();
		Object old = new Object();
		Object current = new Object();
		sessions.join(FIRST, old);
		sessions.join(FIRST, current);
		assertFalse(sessions.leave(FIRST, old));
		assertEquals(1, sessions.size());
		assertSame(current, sessions.snapshot().get(FIRST));
		assertTrue(sessions.leave(FIRST, current));
		assertEquals(0, sessions.size());
	}

	@Test
	void sessionsSnapshotIsImmutableAndIndependent() {
		var sessions = new PlayerFashionOnlineSessions<Object>();
		sessions.join(FIRST, new Object());
		var snapshot = sessions.snapshot();
		sessions.join(SECOND, new Object());
		assertEquals(1, snapshot.size());
		assertThrows(UnsupportedOperationException.class, snapshot::clear);
	}

	private static List<UUID> players(int count) {
		return IntStream.range(0, count).mapToObj(i -> new UUID(0, i)).toList();
	}
}
