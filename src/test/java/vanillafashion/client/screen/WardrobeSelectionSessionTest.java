package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionSnapshot;
import vanillafashion.network.*;

class WardrobeSelectionSessionTest {
	private static final UUID SELF = new UUID(0, 1);
	private static final Optional<CapeId> FIRST = Optional.of(new CapeId("first"));
	private static final Optional<CapeId> SECOND = Optional.of(new CapeId("second"));
	private static final ClientPlayerFashionRegistry.State AVAILABLE = ClientPlayerFashionRegistry.State.AVAILABLE;

	private static WardrobeSelectionSession session(Optional<CapeId> selection) {
		return session(selection.map(PlayerFashionAuthoritativeState::active)
				.orElseGet(PlayerFashionAuthoritativeState::vanilla));
	}

	private static WardrobeSelectionSession session(PlayerFashionAuthoritativeState state) {
		var session = new WardrobeSelectionSession();
		session.observe(Optional.of(state), AVAILABLE);
		return session;
	}

	private static ClientCapeSelectionRequestTracker tracker() {
		var tracker = new ClientCapeSelectionRequestTracker();
		tracker.beginConnection(new Object());
		return tracker;
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void initialDraftMatchesKnownAuthoritativeSelection(boolean custom) {
		var selection = custom ? FIRST : Optional.<CapeId>empty();
		var session = session(selection);
		assertTrue(session.authorityKnown());
		assertEquals(selection, session.baseline());
		assertEquals(selection, session.draft());
		assertFalse(session.dirty());
	}

	@ParameterizedTest @EnumSource(ClientPlayerFashionRegistry.State.class)
	void missingAuthorityNeverBecomesKnownVanilla(ClientPlayerFashionRegistry.State state) {
		var session = new WardrobeSelectionSession();
		session.observe(Optional.empty(), state);
		assertFalse(session.authorityKnown());
		assertFalse(session.canFinish(true, false, id -> true));
		assertFalse(session.canEdit());
		assertTrue(session.finish(tracker(), true, id -> true).request().isEmpty());
		assertFalse(session.closed());
		assertFalse(session.status(true, false, id -> true).isEmpty());
	}

	@Test void metadataMissingPreservesBaselineAndDraftThenResolvesWithoutReopen() {
		var session = session(FIRST);
		assertFalse(session.canFinish(true, false, id -> false));
		assertEquals("当前披风正在同步", session.status(true, false, id -> false));
		assertEquals(FIRST, session.baseline());
		assertEquals(FIRST, session.draft());
		assertTrue(session.canFinish(true, false, id -> true));
		assertTrue(session.finish(tracker(), true, id -> true).close());
	}

	@Test void userSelectionMakesDirtyWithoutChangingBaseline() {
		var session = session(FIRST);
		session.select(SECOND);
		assertTrue(session.dirty());
		assertEquals(FIRST, session.baseline());
		assertEquals(SECOND, session.draft());
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void externalAuthorityFollowsOnlyCleanDraft(boolean dirty) {
		var session = session(FIRST);
		if (dirty) { session.select(Optional.empty()); }
		session.observe(Optional.of(PlayerFashionAuthoritativeState.active(SECOND.orElseThrow())), AVAILABLE);
		assertEquals(SECOND, session.baseline());
		assertEquals(dirty ? Optional.empty() : SECOND, session.draft());
	}

	@Test void unavailableTransitionDoesNotEraseEditedDraft() {
		var session = session(FIRST);
		session.select(SECOND);
		session.observe(Optional.empty(), ClientPlayerFashionRegistry.State.UNAVAILABLE);
		assertFalse(session.canFinish(true, false, id -> true));
		assertEquals(SECOND, session.draft());
		session.observe(Optional.of(PlayerFashionAuthoritativeState.active(FIRST.orElseThrow())), AVAILABLE);
		assertEquals(SECOND, session.draft());
	}

	@Test void equalDraftClosesWithoutAllocationOrPacket() {
		var tracker = tracker();
		var session = session(FIRST);
		var decision = session.finish(tracker, true, id -> true);
		assertTrue(decision.close());
		assertTrue(decision.request().isEmpty());
		assertFalse(tracker.hasOutstanding());
		assertEquals(1, tracker.allocate().orElseThrow());
	}

	@Test void differentDraftStaysOpenAndPendingDisablesDuplicateFinishAndEditing() {
		var tracker = tracker();
		var session = session(FIRST);
		session.select(SECOND);
		var decision = session.finish(tracker, true, id -> true);
		assertFalse(decision.close());
		assertEquals(SECOND, decision.request().orElseThrow().selection());
		assertEquals(1, session.pendingRequestId());
		assertFalse(session.closed());
		assertEquals("正在保存…", session.status(true, true, id -> true));
		session.select(Optional.empty());
		assertEquals(SECOND, session.draft());
		assertTrue(session.finish(tracker, true, id -> true).request().isEmpty());
		assertEquals(1, tracker.outstandingCount());
	}

	@Test void unsupportedChannelDisablesEvenUnchangedFinish() {
		var session = session(FIRST);
		assertFalse(session.canFinish(false, false, id -> true));
		assertFalse(session.finish(tracker(), false, id -> true).close());
		assertEquals("服务器不支持保存时装选择", session.status(false, false, id -> true));
	}

	@Test void missingDraftMetadataDisablesSubmission() {
		var session = session(FIRST);
		session.select(SECOND);
		assertFalse(session.canFinish(true, false, FIRST.orElseThrow()::equals));
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void cancelAndEscPathNeverSendsOrRevokesPending(boolean pending) {
		var session = session(FIRST);
		var tracker = tracker();
		session.select(SECOND);
		if (pending) { session.finish(tracker, true, id -> true); }
		session.cancel();
		assertTrue(session.closed());
		assertEquals(pending, tracker.hasOutstanding());
		assertTrue(session.finish(tracker, true, id -> true).request().isEmpty());
		assertEquals(FIRST, session(FIRST).draft());
	}

	@ParameterizedTest @EnumSource(CapeSelectionReason.class)
	void matchingResultClosesOnlyAcceptedAndRejectPreservesValidDraft(CapeSelectionReason reason) {
		var session = session(FIRST);
		var tracker = tracker();
		session.select(SECOND);
		long id = session.finish(tracker, true, value -> true).request().orElseThrow().requestId();
		var authoritative = reason.accepted() ? SECOND : FIRST;
		var authoritativeState = authoritative.map(PlayerFashionAuthoritativeState::active)
				.orElseGet(PlayerFashionAuthoritativeState::vanilla);
		boolean close = session.acceptResult(new CapeSelectionResultPayload(
				id, reason.accepted(), authoritativeState, reason),
				AVAILABLE, value -> true);
		assertEquals(reason.accepted(), close);
		assertEquals(reason.accepted(), session.closed());
		assertEquals(0, session.pendingRequestId());
		assertEquals(authoritative, session.baseline());
		assertEquals(SECOND, session.draft());
		if (!reason.accepted()) {
			assertEquals(Optional.of(reason), session.lastError());
			assertFalse(session.status(true, false, value -> true).contains(reason.name()));
		}
	}

	@Test void rejectedDisappearedDraftFallsBackToLatestAuthority() {
		var session = session(FIRST);
		session.select(SECOND);
		long id = session.finish(tracker(), true, value -> true).request().orElseThrow().requestId();
		session.acceptResult(new CapeSelectionResultPayload(id, false,
				PlayerFashionAuthoritativeState.active(FIRST.orElseThrow()), CapeSelectionReason.CAPE_NOT_AVAILABLE),
				AVAILABLE, FIRST.orElseThrow()::equals);
		assertEquals(FIRST, session.draft());
		assertFalse(session.closed());
	}

	@Test void nonMatchingResultCannotCompleteCurrentPending() {
		var session = session(FIRST);
		session.select(SECOND);
		session.finish(tracker(), true, id -> true);
		assertFalse(session.acceptResult(new CapeSelectionResultPayload(99, true,
				PlayerFashionAuthoritativeState.active(SECOND.orElseThrow()), CapeSelectionReason.APPLIED),
				AVAILABLE, id -> true));
		assertEquals(1, session.pendingRequestId());
	}

	@Test void pendingTimeoutOnlyChangesMessageAndDoesNotRetryOrCancel() {
		var session = session(FIRST);
		var tracker = tracker();
		session.select(SECOND);
		session.finish(tracker, true, id -> true);
		for (int tick = 0; tick < 250; tick++) { session.tick(); }
		assertEquals("尚未收到服务器确认，状态以服务器为准", session.status(true, true, id -> true));
		assertEquals(1, session.pendingRequestId());
		assertEquals(1, tracker.outstandingCount());
		assertFalse(session.closed());
	}

	@Test void previousScreenOutstandingBlocksFinishButNotPreviewInNewScreen() {
		var tracker = tracker();
		tracker.allocate();
		var session = session(FIRST);
		session.select(SECOND);
		assertEquals(SECOND, session.draft());
		assertFalse(session.canFinish(true, tracker.hasOutstanding(), id -> true));
		assertEquals("正在等待之前的选择确认", session.status(true, true, id -> true));
	}

	@ParameterizedTest @ValueSource(strings = {"cape_only", "split", "shared"})
	void allAssetLayoutsRemainDraftOnlyUntilAuthoritativeUpdate(String layout) {
		var hash = "a".repeat(64);
		var metadata = new CapeCosmeticMetadata(new CapeId(layout), hash,
				layout.equals("cape_only") ? Optional.empty() : Optional.of(layout.equals("shared") ? hash : "b".repeat(64)));
		var registry = new ClientPlayerFashionRegistry();
		registry.replace(new PlayerFashionSnapshot(true, List.of(new PlayerFashionEntry(
				SELF, PlayerFashionAuthoritativeState.active(FIRST.orElseThrow())))));
		var session = session(FIRST);
		session.select(Optional.of(metadata.id()));
		var preview = new WardrobePreviewAppearanceResolver(List.of(metadata), value -> Optional.empty(),
				value -> vanillafashion.client.render.ElytraTextureDecision.vanillaDefault());
		assertNotNull(preview.resolve(session.draft()));
		assertEquals(FIRST, registry.find(SELF).orElseThrow().effectiveSelection());
		session.finish(tracker(), true, id -> true);
		assertEquals(FIRST, registry.find(SELF).orElseThrow().effectiveSelection());
		registry.update(new PlayerFashionEntry(SELF, PlayerFashionAuthoritativeState.active(metadata.id())));
		assertEquals(session.draft(), registry.find(SELF).orElseThrow().effectiveSelection());
	}

	@Test
	void dormantInitialDraftUsesStoredSelectionAndNoVanillaTile() {
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		assertEquals(FIRST, session.baseline());
		assertEquals(FIRST, session.draft());
		assertNotEquals(Optional.<CapeId>empty(), session.draft());
		assertTrue(session.dormant());
	}

	@Test
	void dormantDisplaysSavedSelectionAndPermanentUnavailableWarning() {
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		assertEquals("已保存选择：first", session.selectionLabel());
		assertEquals("该披风当前不可用，暂时使用原版外观",
				session.status(true, false, id -> false));
		assertTrue(session.previewSelection().isEmpty());
	}

	@Test
	void metadataPendingActiveIsNotClassifiedAsDormant() {
		var session = session(PlayerFashionAuthoritativeState.active(FIRST.orElseThrow()));
		assertFalse(session.dormant());
		assertEquals("当前选择：first", session.selectionLabel());
		assertEquals("当前披风正在同步", session.status(true, false, id -> false));
	}

	@Test
	void activeReadyHasNormalSelectionAndNoStatus() {
		var session = session(PlayerFashionAuthoritativeState.active(FIRST.orElseThrow()));
		assertEquals("当前选择：first", session.selectionLabel());
		assertEquals("", session.status(true, false, id -> true));
		assertEquals(FIRST, session.previewSelection());
	}

	@Test
	void trueVanillaHasNormalSelectedLabelAndNoDormantWarning() {
		var session = session(PlayerFashionAuthoritativeState.vanilla());
		assertEquals("当前选择：原版", session.selectionLabel());
		assertEquals("", session.status(true, false, id -> false));
		assertFalse(session.dormant());
	}

	@Test
	void dormantUnchangedFinishClosesWithoutRequestEvenWhenMetadataMissing() {
		var tracker = tracker();
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		assertTrue(session.canFinish(true, false, id -> false));
		var decision = session.finish(tracker, true, id -> false);
		assertTrue(decision.close());
		assertTrue(decision.request().isEmpty());
		assertFalse(tracker.hasOutstanding());
	}

	@Test
	void dormantToVanillaSendsExplicitClear() {
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		session.select(Optional.empty());
		var decision = session.finish(tracker(), true, id -> false);
		assertFalse(decision.close());
		assertTrue(decision.request().orElseThrow().selection().isEmpty());
	}

	@Test
	void dormantToValidCustomSendsReplacementAndPreviewsIt() {
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		session.select(SECOND);
		assertEquals(SECOND, session.previewSelection());
		assertEquals(SECOND, session.finish(tracker(), true, SECOND.orElseThrow()::equals)
				.request().orElseThrow().selection());
	}

	@Test
	void dormantCancelKeepsStoredBaselineWithoutAllocatingRequest() {
		var tracker = tracker();
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		session.cancel();
		assertEquals(FIRST, session.baseline());
		assertEquals(FIRST, session.draft());
		assertFalse(tracker.hasOutstanding());
	}

	@Test
	void cleanDormantSessionFollowsRepairToActive() {
		var session = session(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow()));
		session.observe(Optional.of(PlayerFashionAuthoritativeState.active(FIRST.orElseThrow())), AVAILABLE);
		assertFalse(session.dormant());
		assertEquals(FIRST, session.draft());
		assertEquals(FIRST, session.previewSelection());
	}

	@Test
	void cleanActiveSessionBecomesDormantWithoutLosingStoredDraft() {
		var session = session(PlayerFashionAuthoritativeState.active(FIRST.orElseThrow()));
		session.observe(Optional.of(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow())), AVAILABLE);
		assertTrue(session.dormant());
		assertEquals(FIRST, session.baseline());
		assertEquals(FIRST, session.draft());
		assertTrue(session.previewSelection().isEmpty());
	}

	@Test
	void dirtySessionPreservesUserDraftAcrossDormantAuthorityUpdate() {
		var session = session(PlayerFashionAuthoritativeState.active(FIRST.orElseThrow()));
		session.select(SECOND);
		session.observe(Optional.of(PlayerFashionAuthoritativeState.dormant(FIRST.orElseThrow())), AVAILABLE);
		assertEquals(FIRST, session.baseline());
		assertEquals(SECOND, session.draft());
		assertEquals(SECOND, session.previewSelection());
	}

	@ParameterizedTest @ValueSource(ints = {320, 427, 854})
	void singleApplyStaysSeparateFromBottomStatus(int width) {
		var layout = WardrobeLayout.calculate(width, 240, 9);
		assertTrue(layout.finishButtonBounds().isWithin(width, 240));
		assertEquals(56, layout.applyButtonBounds().width());
		assertFalse(layout.applyButtonBounds().overlaps(layout.statusBounds()));
		assertEquals(20, layout.applyButtonBounds().height());
	}
}
