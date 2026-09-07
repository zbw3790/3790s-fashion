package vanillafashion.client.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeId;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.screen.WardrobeSelectionSession;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionSnapshot;
import vanillafashion.network.*;

class ClientCapeSelectionResultsTest {
	private static final UUID SELF = new UUID(0, 1);
	private static final UUID REMOTE = new UUID(0, 2);
	private static final Optional<CapeId> FIRST = Optional.of(new CapeId("cape1"));
	private static final Optional<CapeId> SECOND = Optional.of(new CapeId("cape2"));
	private final Object connection = new Object();
	private final ClientPlayerFashionRegistry registry = new ClientPlayerFashionRegistry();
	private final ClientCapeSelectionRequestTracker tracker = new ClientCapeSelectionRequestTracker();

	ClientCapeSelectionResultsTest() {
		registry.beginConnection(connection);
		registry.replace(snapshot(FIRST));
		tracker.beginConnection(connection);
	}

	private static PlayerFashionSnapshot snapshot(Optional<CapeId> self) {
		return new PlayerFashionSnapshot(true, List.of(new PlayerFashionEntry(SELF, state(self)),
				new PlayerFashionEntry(REMOTE, PlayerFashionAuthoritativeState.vanilla())));
	}

	private WardrobeSelectionSession screen() {
		var session = new WardrobeSelectionSession();
		session.observe(registry.selfAuthority(SELF), registry.state());
		return session;
	}

	private long submit(WardrobeSelectionSession screen) {
		screen.select(SECOND);
		return screen.finish(tracker, true, id -> true).request().orElseThrow().requestId();
	}

	private static CapeSelectionResultPayload accepted(long id) {
		return new CapeSelectionResultPayload(
				id, true, PlayerFashionAuthoritativeState.active(SECOND.orElseThrow()), CapeSelectionReason.APPLIED);
	}

	private boolean apply(CapeSelectionResultPayload result, WardrobeSelectionSession current) {
		return ClientCapeSelectionResults.apply(connection, SELF, result, registry, tracker, () -> current, id -> true);
	}

	@Test void currentConnectionSenderPassesSourceCheck() {
		var sender = new Object();
		assertTrue(ClientConnectionIdentity.isCurrentSender(sender, sender));
	}

	@Test void missingConnectionSenderCannotPassSourceCheck() {
		assertFalse(ClientConnectionIdentity.isCurrentSender(new Object(), null));
		assertFalse(ClientConnectionIdentity.isCurrentSender(null, new Object()));
		assertFalse(ClientConnectionIdentity.isCurrentSender(null, null));
	}

	@Test void equalButDistinctSendersCannotPassSourceCheck() {
		var oldSender = new String("连接发送器");
		var currentSender = new String("连接发送器");
		assertEquals(oldSender, currentSender);
		assertFalse(ClientConnectionIdentity.isCurrentSender(oldSender, currentSender));
	}

	@Test void staleSenderCannotUseCurrentConnectionToCorrectOrCompleteReusedRequest() {
		var oldSender = new Object();
		var currentSender = new Object();
		var currentScreen = screen();
		long id = submit(currentScreen);
		// 与接收器一样，来源检查必须先于使用当前连接、玩家身份和 Result correction。
		if (ClientConnectionIdentity.isCurrentSender(oldSender, currentSender)) {
			apply(accepted(id), currentScreen);
			fail("旧连接发送器不得进入当前连接的结果处理。");
		}
		assertEquals(FIRST, registry.find(SELF).orElseThrow().effectiveSelection());
		assertEquals(1, tracker.outstandingCount());
		assertEquals(id, currentScreen.pendingRequestId());
		assertFalse(currentScreen.closed());
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void oldScreenRequest17CannotCloseNewScreenButCorrectsAndReconciles(boolean dirtyNewScreen) {
		for (int number = 1; number < 17; number++) { tracker.complete(tracker.allocate().orElseThrow()); }
		var firstScreen = screen();
		long id = submit(firstScreen);
		assertEquals(17, id);
		firstScreen.cancel();
		var secondScreen = screen();
		if (dirtyNewScreen) { secondScreen.select(Optional.empty()); }
		assertFalse(apply(accepted(id), secondScreen));
		assertEquals(SECOND, registry.selfAuthority(SELF).orElseThrow().effectiveSelection());
		assertEquals(SECOND, registry.find(SELF).orElseThrow().effectiveSelection());
		assertEquals(SECOND, secondScreen.baseline());
		assertEquals(dirtyNewScreen ? Optional.empty() : SECOND, secondScreen.draft());
		assertFalse(secondScreen.closed());
		assertEquals(0, secondScreen.pendingRequestId());
		assertTrue(secondScreen.canEdit());
		assertEquals(dirtyNewScreen, secondScreen.canFinish(true, false, cape -> true));
		assertFalse(tracker.hasOutstanding());
		assertEquals(18, tracker.allocate().orElseThrow());
	}

	@ParameterizedTest @CsvSource({"true,true", "true,false", "false,true", "false,false"})
	void updateAndResultOrderingHaveIdenticalFinalAuthority(boolean updateFirst, boolean accepted) {
		var screen = screen();
		long id = submit(screen);
		var choice = accepted ? SECOND : FIRST;
		var result = new CapeSelectionResultPayload(id, accepted, state(choice),
				accepted ? CapeSelectionReason.APPLIED : CapeSelectionReason.NOT_ALLOWED);
		var update = new PlayerFashionEntry(SELF, state(choice));
		if (updateFirst) {
			registry.update(update);
			screen.observe(registry.selfAuthority(SELF), registry.state());
		}
		assertEquals(accepted, apply(result, screen));
		if (!updateFirst) {
			registry.update(update);
			screen.observe(registry.selfAuthority(SELF), registry.state());
		}
		assertEquals(choice, registry.find(SELF).orElseThrow().effectiveSelection());
		assertEquals(choice, registry.selfAuthority(SELF).orElseThrow().effectiveSelection());
		assertFalse(screen.closed());
		assertTrue(screen.canEdit());
		assertEquals(choice, screen.baseline());
		assertEquals(SECOND, screen.draft());
		assertEquals(!accepted, screen.dirty());
		assertEquals(!accepted, screen.canFinish(true, false, cape -> true));
		assertEquals(0, screen.pendingRequestId());
		assertFalse(tracker.hasOutstanding());
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void sameSessionAcceptsConsecutiveSelectionsInBothMessageOrders(boolean updateFirst) {
		var session = screen();
		long previousRequest = 0;
		for (var choice : List.<Optional<CapeId>>of(SECOND, Optional.empty())) {
			session.select(choice);
			assertTrue(session.canFinish(true, tracker.hasOutstanding(), cape -> true));
			var request = session.finish(tracker, true, cape -> true).request().orElseThrow();
			assertEquals(previousRequest + 1, request.requestId());
			assertEquals(choice, request.selection());
			assertFalse(session.canEdit());
			var update = new PlayerFashionEntry(SELF, state(choice));
			if (updateFirst) {
				registry.update(update);
				session.observe(registry.selfAuthority(SELF), registry.state());
			}
			assertTrue(apply(new CapeSelectionResultPayload(request.requestId(), true,
					state(choice), CapeSelectionReason.APPLIED), session));
			if (!updateFirst) {
				registry.update(update);
				session.observe(registry.selfAuthority(SELF), registry.state());
			}
			assertEquals(state(choice), registry.selfAuthority(SELF).orElseThrow());
			assertEquals(choice, session.baseline());
			assertEquals(choice, session.draft());
			assertEquals(0, session.pendingRequestId());
			assertTrue(session.lastError().isEmpty());
			assertFalse(session.closed());
			assertFalse(session.dirty());
			assertTrue(session.canEdit());
			assertFalse(session.canFinish(true, tracker.hasOutstanding(), cape -> true));
			assertTrue(session.finish(tracker, true, cape -> true).request().isEmpty());
			assertFalse(tracker.hasOutstanding());
			previousRequest = request.requestId();
		}
		assertEquals(3, tracker.allocate().orElseThrow());
	}

	@Test
	void successfulResultThenUpdateDoesNotOverwriteTheNextUnsavedDraft() {
		var session = screen();
		long requestId = submit(session);
		assertTrue(apply(accepted(requestId), session));
		session.select(Optional.empty());
		registry.update(new PlayerFashionEntry(SELF, state(SECOND)));
		session.observe(registry.selfAuthority(SELF), registry.state());
		assertEquals(SECOND, session.baseline());
		assertTrue(session.draft().isEmpty());
		assertTrue(session.dirty());
		assertFalse(session.closed());
		assertTrue(session.canFinish(true, tracker.hasOutstanding(), cape -> true));
		assertEquals(requestId + 1, session.finish(tracker, true, cape -> true).request().orElseThrow().requestId());
	}

	@Test
	void rejectedRequestCanBeAdjustedAndAppliedInTheSameSession() {
		var session = screen();
		long firstRequest = submit(session);
		var dormant = PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow());
		assertFalse(apply(new CapeSelectionResultPayload(firstRequest, false,
				dormant, CapeSelectionReason.SERVICE_UNAVAILABLE), session));
		assertEquals(dormant, registry.selfAuthority(SELF).orElseThrow());
		assertEquals(FIRST, session.baseline());
		assertEquals(SECOND, session.draft());
		assertTrue(session.dormant());
		assertTrue(session.canEdit());
		assertEquals(Optional.of(CapeSelectionReason.SERVICE_UNAVAILABLE), session.lastError());
		assertFalse(tracker.hasOutstanding());
		session.select(Optional.empty());
		long secondRequest = session.finish(tracker, true, cape -> true).request().orElseThrow().requestId();
		assertEquals(firstRequest + 1, secondRequest);
		assertTrue(apply(new CapeSelectionResultPayload(secondRequest, true,
				PlayerFashionAuthoritativeState.vanilla(), CapeSelectionReason.APPLIED), session));
		assertTrue(session.baseline().isEmpty());
		assertTrue(session.draft().isEmpty());
		assertTrue(session.lastError().isEmpty());
		assertEquals(0, session.pendingRequestId());
		assertFalse(session.dormant());
		assertFalse(session.closed());
		assertTrue(session.canEdit());
		assertFalse(session.canFinish(true, false, cape -> true));
		assertFalse(tracker.hasOutstanding());
	}

	@Test
	void closingAfterAnotherDraftKeepsTheLastSuccessfulSelectionForReopen() {
		var session = screen();
		long requestId = submit(session);
		assertTrue(apply(accepted(requestId), session));
		session.select(Optional.empty());
		assertTrue(session.dirty());
		session.cancel();
		var reopened = screen();
		assertEquals(SECOND, registry.selfAuthority(SELF).orElseThrow().storedSelection());
		assertEquals(SECOND, reopened.baseline());
		assertEquals(SECOND, reopened.draft());
		assertFalse(reopened.closed());
		assertFalse(reopened.dirty());
		assertFalse(tracker.hasOutstanding());
		assertEquals(requestId + 1, tracker.allocate().orElseThrow());
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void resultDuringUnavailableCorrectsSelfWithoutEnablingWorldOrFinish(boolean accepted) {
		var screen = screen();
		long id = submit(screen);
		registry.replace(PlayerFashionSnapshot.unavailable());
		var choice = accepted ? SECOND : FIRST;
		apply(new CapeSelectionResultPayload(id, accepted, state(choice),
				accepted ? CapeSelectionReason.APPLIED : CapeSelectionReason.SERVICE_UNAVAILABLE), screen);
		assertEquals(choice, registry.selfAuthority(SELF).orElseThrow().effectiveSelection());
		assertEquals(ClientPlayerFashionRegistry.State.UNAVAILABLE, registry.state());
		assertEquals(0, registry.size());
		assertFalse(screen.closed());
		assertEquals(0, screen.pendingRequestId());
		assertFalse(screen.canFinish(true, false, cape -> true));
		assertTrue(registry.find(SELF).isEmpty());
		assertTrue(registry.find(REMOTE).isEmpty());
		var reopened = screen();
		assertEquals(choice, reopened.baseline());
		assertFalse(reopened.canFinish(true, false, cape -> true));
		assertFalse(tracker.hasOutstanding());
	}

	@Test void uninitializedResultDoesNotInventCompleteSnapshot() {
		registry.beginConnection(connection);
		apply(accepted(9), null);
		assertEquals(ClientPlayerFashionRegistry.State.UNINITIALIZED, registry.state());
		assertEquals(SECOND, registry.selfAuthority(SELF).orElseThrow().effectiveSelection());
		assertTrue(registry.find(SELF).isEmpty());
	}

	@Test void acceptedAndRejectedResultsApplyEvenWhenOriginalScreenIsGone() {
		var screen = screen();
		long id = submit(screen);
		screen.cancel();
		assertFalse(apply(accepted(id), null));
		assertEquals(SECOND, registry.find(SELF).orElseThrow().effectiveSelection());
		assertFalse(apply(new CapeSelectionResultPayload(99, false, state(FIRST),
				CapeSelectionReason.CAPE_NOT_AVAILABLE), null));
		assertEquals(FIRST, registry.find(SELF).orElseThrow().effectiveSelection());
	}

	@Test void authorityCorrectionAndCompletionPrecedeCurrentScreenLookup() {
		var screen = screen();
		long id = submit(screen);
		boolean applied = ClientCapeSelectionResults.apply(connection, SELF, accepted(id), registry, tracker, () -> {
			assertEquals(SECOND, registry.find(SELF).orElseThrow().effectiveSelection());
			assertFalse(tracker.hasOutstanding());
			return screen;
		}, cape -> true);
		assertTrue(applied);
		assertFalse(screen.closed());
		assertTrue(screen.canEdit());
	}

	@Test void oldConnectionCannotCorrectNewRegistryOrCompleteReusedId() {
		var originalScreen = screen();
		submit(originalScreen);
		var newer = new Object();
		registry.beginConnection(newer);
		registry.replace(snapshot(FIRST));
		tracker.beginConnection(newer);
		tracker.allocate();
		assertFalse(ClientCapeSelectionResults.apply(connection, SELF, accepted(1), registry, tracker,
				() -> { fail("旧连接不应触及新 UI。"); return null; }, cape -> true));
		assertEquals(FIRST, registry.find(SELF).orElseThrow().effectiveSelection());
		assertEquals(1, tracker.outstandingCount());
	}

	@Test void unknownResultCorrectsAuthorityWithoutConsumingDifferentOutstanding() {
		var screen = screen();
		submit(screen);
		assertFalse(apply(accepted(999), screen));
		assertEquals(SECOND, registry.find(SELF).orElseThrow().effectiveSelection());
		assertEquals(1, tracker.outstandingCount());
		assertEquals(1, screen.pendingRequestId());
	}

	@Test void completeSnapshotSupersedesSelfOnlyAuthority() {
		registry.replace(PlayerFashionSnapshot.unavailable());
		apply(accepted(17), null);
		registry.replace(snapshot(FIRST));
		assertEquals(FIRST, registry.selfAuthority(SELF).orElseThrow().effectiveSelection());
		registry.replace(PlayerFashionSnapshot.unavailable());
		assertTrue(registry.selfAuthority(SELF).isEmpty());
	}

	@Test
	void oldScreenDormantResultUpdatesRegistryWithStoredSelection() {
		var oldScreen = screen();
		long id = submit(oldScreen);
		oldScreen.cancel();
		assertFalse(apply(dormantRejected(id), null));
		assertEquals(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()),
				registry.getKnownState(SELF).orElseThrow());
	}

	@Test
	void oldScreenDormantResultDoesNotCloseNewScreen() {
		var oldScreen = screen();
		long id = submit(oldScreen);
		oldScreen.cancel();
		var newScreen = screen();
		assertFalse(apply(dormantRejected(id), newScreen));
		assertFalse(newScreen.closed());
	}

	@Test
	void cleanNewScreenAdoptsStoredSelectionFromOldDormantResult() {
		var oldScreen = screen();
		long id = submit(oldScreen);
		oldScreen.cancel();
		var newScreen = screen();
		apply(dormantRejected(id), newScreen);
		assertEquals(FIRST, newScreen.baseline());
		assertEquals(FIRST, newScreen.draft());
		assertTrue(newScreen.dormant());
	}

	@Test void disconnectAndRemoveClearSelfOnlyAuthority() {
		registry.replace(PlayerFashionSnapshot.unavailable());
		apply(accepted(17), null);
		assertTrue(registry.selfAuthority(REMOTE).isEmpty());
		registry.remove(SELF);
		assertTrue(registry.selfAuthority(SELF).isEmpty());
		apply(accepted(18), null);
		assertTrue(registry.disconnect(connection));
		assertTrue(tracker.disconnect(connection));
		assertTrue(registry.selfAuthority(SELF).isEmpty());
		assertFalse(apply(accepted(19), null));
	}

	private static CapeSelectionResultPayload dormantRejected(long id) {
		return new CapeSelectionResultPayload(id, false,
				PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()),
				CapeSelectionReason.CAPE_NOT_AVAILABLE);
	}

	private static PlayerFashionAuthoritativeState state(Optional<CapeId> selection) {
		return selection.map(PlayerFashionAuthoritativeState::active)
				.orElseGet(PlayerFashionAuthoritativeState::vanilla);
	}
}
