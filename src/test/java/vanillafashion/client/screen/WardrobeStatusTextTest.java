package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import vanillafashion.cape.CapeId;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.network.CapeSelectionReason;
import vanillafashion.network.CapeSelectionResultPayload;

class WardrobeStatusTextTest {
	private static final CapeId FIRST = new CapeId("first");
	private static final CapeId SECOND = new CapeId("second");
	private static final ClientPlayerFashionRegistry.State AVAILABLE = ClientPlayerFashionRegistry.State.AVAILABLE;

	private static WardrobeSelectionSession session(PlayerFashionAuthoritativeState authority) {
		var selection = new WardrobeSelectionSession();
		selection.observe(Optional.of(authority), AVAILABLE);
		return selection;
	}

	private static WardrobeStatusText text(WardrobeSelectionSession selection, CapeWardrobeContent.State state) {
		return WardrobeStatusText.create(selection, AVAILABLE, true, false, id -> true, state, "");
	}

	private static ClientCapeSelectionRequestTracker tracker() {
		var tracker = new ClientCapeSelectionRequestTracker();
		tracker.beginConnection(new Object());
		return tracker;
	}

	@Test
	void normalVanillaKeepsTheSecondLineEmpty() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var text = text(selection, CapeWardrobeContent.State.READY);
		assertEquals(WardrobeStatusText.Priority.NORMAL, text.priority());
		assertEquals("当前选择：原版", text.firstLine());
		assertEquals("", text.secondLine());
		assertEquals(List.of(selection.selectionLabel()), text.fullText());
		assertEquals(selection.selectionLabel(), text.narration());
	}

	@Test
	void draftLabelDoesNotChangeBaselineOrSession() {
		var selection = session(PlayerFashionAuthoritativeState.active(FIRST));
		selection.select(Optional.of(SECOND));
		var text = text(selection, CapeWardrobeContent.State.READY);
		assertEquals("当前选择：second", text.firstLine());
		assertEquals(Optional.of(FIRST), selection.baseline());
		assertEquals(Optional.of(SECOND), selection.draft());
		assertTrue(selection.dirty());
		assertTrue(selection.canFinish(true, false, id -> true));
	}

	@Test
	void emptyRegistryKeepsOriginalAndUsesTheSecondLine() {
		var text = text(session(PlayerFashionAuthoritativeState.vanilla()), CapeWardrobeContent.State.EMPTY);
		assertEquals(WardrobeStatusText.Priority.NORMAL, text.priority());
		assertEquals("当前选择：原版", text.firstLine());
		assertEquals("服务器暂无可用披风", text.secondLine());
	}

	@Test
	void contentLoadingAndErrorOnlySupplementAnEmptySessionStatus() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var loading = WardrobeStatusText.create(selection, AVAILABLE, true, false, id -> true,
				CapeWardrobeContent.State.LOADING, "正在读取披风资源");
		assertEquals(WardrobeStatusText.Priority.LOADING, loading.priority());
		assertEquals("正在读取披风资源", loading.secondLine());
		var error = WardrobeStatusText.create(selection, AVAILABLE, true, false, id -> true,
				CapeWardrobeContent.State.ERROR, "披风资源当前不可用");
		assertEquals(WardrobeStatusText.Priority.ERROR, error.priority());
		assertEquals("披风资源当前不可用", error.secondLine());
		assertFalse(text(selection, CapeWardrobeContent.State.LOADING).secondLine().isEmpty());
		assertFalse(text(selection, CapeWardrobeContent.State.ERROR).secondLine().isEmpty());
	}

	@Test
	void unknownAuthorityAndMissingMetadataKeepSessionSyncText() {
		var unknown = new WardrobeSelectionSession();
		var loading = text(unknown, CapeWardrobeContent.State.EMPTY);
		assertEquals(WardrobeStatusText.Priority.LOADING, loading.priority());
		assertEquals(unknown.status(true, false, id -> true), loading.secondLine());
		var active = session(PlayerFashionAuthoritativeState.active(FIRST));
		Predicate<CapeId> missingMetadata = id -> false;
		var metadata = WardrobeStatusText.create(active, AVAILABLE, true, false, missingMetadata,
				CapeWardrobeContent.State.EMPTY, "");
		assertEquals(WardrobeStatusText.Priority.LOADING, metadata.priority());
		assertEquals(active.status(true, false, missingMetadata), metadata.secondLine());
	}

	@Test
	void unavailableSnapshotIsAnErrorEvenWithoutAuthority() {
		var selection = new WardrobeSelectionSession();
		selection.observe(Optional.empty(), ClientPlayerFashionRegistry.State.UNAVAILABLE);
		var text = WardrobeStatusText.create(selection, ClientPlayerFashionRegistry.State.UNAVAILABLE,
				true, false, id -> true, CapeWardrobeContent.State.LOADING, "列表正在读取");
		assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
		assertEquals("时装状态当前不可用", text.secondLine());
	}

	@Test
	void unsupportedChannelKeepsSessionError() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var text = WardrobeStatusText.create(selection, AVAILABLE, false, false, id -> true,
				CapeWardrobeContent.State.EMPTY, "");
		assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
		assertEquals(selection.status(false, false, id -> true), text.secondLine());
	}

	@Test
	void pendingSurvivesContentLoadingAndKeepsTheTimeoutMessage() {
		var selection = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		selection.select(Optional.of(SECOND));
		var tracker = tracker();
		long requestId = selection.finish(tracker, true, id -> true).request().orElseThrow().requestId();
		var pending = text(selection, CapeWardrobeContent.State.LOADING);
		assertEquals(WardrobeStatusText.Priority.PENDING, pending.priority());
		assertEquals("正在保存…", pending.secondLine());
		for (int tick = 0; tick < 200; tick++) {
			selection.tick();
		}
		var timedOut = text(selection, CapeWardrobeContent.State.EMPTY);
		assertEquals("尚未收到服务器确认，状态以服务器为准", timedOut.secondLine());
		assertEquals(requestId, selection.pendingRequestId());
		assertTrue(tracker.hasOutstanding());
	}

	@Test
	void successfulAcknowledgementClearsPendingTextWithoutClosingTheSession() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		selection.select(Optional.of(FIRST));
		var tracker = tracker();
		long requestId = selection.finish(tracker, true, id -> true).request().orElseThrow().requestId();
		for (int tick = 0; tick < 200; tick++) {
			selection.tick();
		}
		tracker.complete(requestId);
		assertTrue(selection.acceptResult(new CapeSelectionResultPayload(requestId, true,
				PlayerFashionAuthoritativeState.active(FIRST), CapeSelectionReason.APPLIED), AVAILABLE, id -> true));
		var text = text(selection, CapeWardrobeContent.State.READY);
		assertEquals(WardrobeStatusText.Priority.NORMAL, text.priority());
		assertEquals("当前选择：first", text.firstLine());
		assertEquals("", text.secondLine());
		assertFalse(selection.closed());
		assertFalse(selection.canFinish(true, false, id -> true));
		assertTrue(selection.canEdit());
	}

	@Test
	void successfulAcknowledgementNeverHidesTheReturnedDormantState() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		selection.select(Optional.of(FIRST));
		var tracker = tracker();
		long requestId = selection.finish(tracker, true, id -> true).request().orElseThrow().requestId();
		tracker.complete(requestId);
		assertTrue(selection.acceptResult(new CapeSelectionResultPayload(requestId, true,
				PlayerFashionAuthoritativeState.dormant(FIRST), CapeSelectionReason.APPLIED), AVAILABLE, id -> false));
		var text = text(selection, CapeWardrobeContent.State.EMPTY);
		assertEquals(WardrobeStatusText.Priority.DORMANT, text.priority());
		assertEquals("已保存选择：first", text.firstLine());
		assertEquals("该披风当前不可用，暂时使用原版外观", text.secondLine());
		assertEquals(Optional.of(FIRST), selection.baseline());
		assertEquals(Optional.of(FIRST), selection.draft());
		assertTrue(selection.previewSelection().isEmpty());
		assertFalse(selection.closed());
	}

	@Test
	void contentErrorIsVisibleBeforePendingAndKeepsPendingInTheTooltip() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		selection.select(Optional.of(FIRST));
		selection.finish(tracker(), true, id -> true);
		var text = text(selection, CapeWardrobeContent.State.ERROR);
		assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
		assertEquals("披风列表当前不可用", text.secondLine());
		assertEquals("当前选择：first", text.firstLine());
		assertEquals(List.of("当前选择：first", "披风列表当前不可用", "正在保存…"), text.fullText());
		assertTrue(text.narration().contains("正在保存…"));
		assertTrue(selection.pendingRequestId() > 0);
	}

	@Test
	void rejectionKeepsAuthoritativeSavedLabelAndTheFullError() {
		var selection = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		selection.select(Optional.of(SECOND));
		long requestId = selection.finish(tracker(), true, id -> true).request().orElseThrow().requestId();
		selection.acceptResult(new CapeSelectionResultPayload(requestId, false,
				PlayerFashionAuthoritativeState.dormant(FIRST), CapeSelectionReason.NOT_ALLOWED), AVAILABLE, id -> false);
		var text = text(selection, CapeWardrobeContent.State.ERROR);
		assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
		assertEquals("已保存选择：first", text.firstLine());
		assertEquals("你不能选择该披风", text.secondLine());
		assertEquals(List.of("已保存选择：first", "你不能选择该披风", "披风列表当前不可用"), text.fullText());
		assertFalse(selection.closed());
		assertEquals(Optional.of(FIRST), selection.baseline());
	}

	@Test
	void previousOutstandingRequestHasPendingPriority() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var text = WardrobeStatusText.create(selection, AVAILABLE, true, true, id -> true,
				CapeWardrobeContent.State.EMPTY, "");
		assertEquals(WardrobeStatusText.Priority.PENDING, text.priority());
		assertEquals("正在等待之前的选择确认", text.secondLine());
	}

	@Test
	void outstandingIsVisibleBeforeDormantWithoutLosingTheSavedWarning() {
		var selection = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		var text = WardrobeStatusText.create(selection, AVAILABLE, true, true, id -> false,
				CapeWardrobeContent.State.LOADING, "列表正在读取");
		assertEquals(WardrobeStatusText.Priority.PENDING, text.priority());
		assertEquals("已保存选择：first", text.firstLine());
		assertEquals("正在等待之前的选择确认", text.secondLine());
		assertEquals(List.of("已保存选择：first", "正在等待之前的选择确认",
				"该披风当前不可用，暂时使用原版外观"), text.fullText());
		assertEquals(Optional.of(FIRST), selection.baseline());
		assertTrue(selection.previewSelection().isEmpty());
	}

	@Test
	void unavailableSnapshotIsVisibleBeforeAnExistingRequest() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		selection.select(Optional.of(FIRST));
		var tracker = tracker();
		long requestId = selection.finish(tracker, true, id -> true).request().orElseThrow().requestId();
		selection.observe(Optional.empty(), ClientPlayerFashionRegistry.State.UNAVAILABLE);
		var text = WardrobeStatusText.create(selection, ClientPlayerFashionRegistry.State.UNAVAILABLE,
				true, true, id -> true, CapeWardrobeContent.State.READY, "");
		assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
		assertEquals("时装状态当前不可用", text.secondLine());
		assertTrue(text.fullText().contains("正在保存…"));
		assertEquals(requestId, selection.pendingRequestId());
		assertTrue(tracker.hasOutstanding());
	}

	@Test
	void unsupportedChannelIsVisibleBeforeDormant() {
		var selection = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		var text = WardrobeStatusText.create(selection, AVAILABLE, false, false, id -> false,
				CapeWardrobeContent.State.LOADING, "列表正在读取");
		assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
		assertEquals("服务器不支持保存时装选择", text.secondLine());
		assertEquals("已保存选择：first", text.firstLine());
		assertTrue(text.fullText().contains("该披风当前不可用，暂时使用原版外观"));
	}

	@Test
	void contentErrorIsVisibleBeforeDormantOrSync() {
		var dormant = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		var unknown = new WardrobeSelectionSession();
		for (var selection : List.of(dormant, unknown)) {
			var text = WardrobeStatusText.create(selection, AVAILABLE, true, false, id -> false,
					CapeWardrobeContent.State.ERROR, "披风资源当前不可用");
			assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
			assertEquals("披风资源当前不可用", text.secondLine());
			assertEquals(selection.selectionLabel(), text.firstLine());
			assertTrue(text.fullText().contains(selection.status(true, false, id -> false)));
		}
	}

	@Test
	void knownSelfStillShowsSyncUntilTheCompleteSnapshotIsAvailable() {
		var selection = new WardrobeSelectionSession();
		selection.observe(Optional.of(PlayerFashionAuthoritativeState.vanilla()),
				ClientPlayerFashionRegistry.State.UNINITIALIZED);
		selection.select(Optional.of(FIRST));
		var text = WardrobeStatusText.create(selection, ClientPlayerFashionRegistry.State.UNINITIALIZED,
				true, false, id -> true, CapeWardrobeContent.State.READY, "");
		assertEquals(WardrobeStatusText.Priority.LOADING, text.priority());
		assertEquals("时装状态正在同步", text.secondLine());
		assertFalse(selection.canFinish(true, false, id -> true));
	}

	@Test
	void dormantOutranksLoadingAndNeverBecomesVanillaSelection() {
		var selection = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		var text = WardrobeStatusText.create(selection, AVAILABLE, true, false, id -> false,
				CapeWardrobeContent.State.LOADING, "列表正在读取");
		assertEquals(WardrobeStatusText.Priority.DORMANT, text.priority());
		assertEquals("已保存选择：first", text.firstLine());
		assertEquals("该披风当前不可用，暂时使用原版外观", text.secondLine());
		assertEquals(Optional.of(FIRST), selection.draft());
		assertTrue(selection.previewSelection().isEmpty());
	}

	@Test
	void editedDormantDraftHasNormalTextWhenTheNewCapeIsReady() {
		var selection = session(PlayerFashionAuthoritativeState.dormant(FIRST));
		selection.select(Optional.of(SECOND));
		var text = text(selection, CapeWardrobeContent.State.READY);
		assertEquals(WardrobeStatusText.Priority.NORMAL, text.priority());
		assertEquals("当前选择：second", text.firstLine());
		assertEquals("", text.secondLine());
	}

	@Test
	void clippedDisplayPreservesCompleteTooltipAndNarration() {
		var text = new WardrobeStatusText(WardrobeStatusText.Priority.ERROR,
				"当前选择：a_very_long_cape_identifier", "服务器时装数据已达上限");
		var display = text.clip(12, WardrobeStatusTextTest::width);
		assertTrue(width(display.firstLine()) <= 12);
		assertTrue(width(display.secondLine()) <= 12);
		assertTrue(display.firstLine().endsWith("…"));
		assertTrue(display.secondLine().endsWith("…"));
		assertEquals(List.of("当前选择：a_very_long_cape_identifier", "服务器时装数据已达上限"), text.fullText());
		assertEquals("当前选择：a_very_long_cape_identifier；服务器时装数据已达上限", text.narration());
	}

	@Test
	void clippingUsesPixelWidthsInsteadOfCharacterCount() {
		assertEquals("披风a…", WardrobeStatusText.fit("披风abcd", 6, WardrobeStatusTextTest::width));
		assertEquals("披风abcd", WardrobeStatusText.fit("披风abcd", 8, WardrobeStatusTextTest::width));
	}

	@Test
	void clippingNeverSplitsASurrogatePair() {
		assertEquals("a😀…", WardrobeStatusText.fit("a😀bc", 4, WardrobeStatusTextTest::width));
		assertEquals("a…", WardrobeStatusText.fit("a😀bc", 3, WardrobeStatusTextTest::width));
	}

	@Test
	void zeroWidthAndInsufficientEllipsisWidthProduceAnEmptyDisplay() {
		assertEquals("", WardrobeStatusText.fit("披风", 0, WardrobeStatusTextTest::width));
		assertEquals("", WardrobeStatusText.fit("披风", -1, WardrobeStatusTextTest::width));
		assertEquals("", WardrobeStatusText.fit("abcd", 1, text -> text.length() * 2));
		assertEquals("", WardrobeStatusText.fit("", 20, WardrobeStatusTextTest::width));
		assertEquals("…", WardrobeStatusText.fit("披风", 1, WardrobeStatusTextTest::width));
	}

	private static int width(String text) {
		return text.codePoints().map(point -> point < 128 || point == '…' ? 1 : 2).sum();
	}
}
