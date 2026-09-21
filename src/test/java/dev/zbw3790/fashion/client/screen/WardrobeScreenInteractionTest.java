package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;
import dev.zbw3790.fashion.client.cape.ClientCapeRegistry;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureManager;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.network.ClientCapeSelectionRequestTracker;
import dev.zbw3790.fashion.client.network.ClientCapeSelectionResults;
import dev.zbw3790.fashion.fashion.PlayerFashionAuthoritativeState;
import dev.zbw3790.fashion.fashion.PlayerFashionEntry;
import dev.zbw3790.fashion.fashion.PlayerFashionSnapshot;
import dev.zbw3790.fashion.network.CapeSelectionReason;
import dev.zbw3790.fashion.network.CapeSelectionResultPayload;
import dev.zbw3790.fashion.network.SetCapeSelectionPayload;
import dev.zbw3790.fashion.network.SetFullFashionSelectionPayload;
import dev.zbw3790.fashion.network.FullFashionSelectionResultPayload;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.network.ClientFullFashionSelectionResults;

class WardrobeScreenInteractionTest {
	private static final UUID SELF = new UUID(0, 1);
	private static final CapeId FIRST = new CapeId("cape00");
	private static final CapeId SECOND = new CapeId("cape01");

	@Test
	void actualOriginalWidgetChangesOnlyDraftAndLeavesWorldAuthority() {
		var fixture = new Fixture();
		var screen = fixture.open();
		var original = screen.children().stream().filter(CapeGridEntryWidget.class::isInstance)
				.map(CapeGridEntryWidget.class::cast).findFirst().orElseThrow();
		original.onPress(new KeyEvent(257, 0, 0));
		assertTrue(screen.selectionSession().draft().isEmpty());
		assertEquals(Optional.of(FIRST), screen.selectionSession().baseline());
		assertEquals(Optional.of(FIRST), fixture.authority().effectiveSelection());
		assertTrue(fixture.sent.isEmpty());
	}

	@Test
	void unchangedApplyIsDisabledAndProgrammaticEntryDoesNotCloseOrAllocateRequest() {
		var fixture = new Fixture();
		var screen = fixture.open();
		var apply = applyButton(screen);
		assertEquals("应用", apply.getMessage().getString());
		assertFalse(apply.active);
		apply.onPress(new KeyEvent(257, 0, 0));
		screen.applySelection();
		assertFalse(screen.selectionSession().closed());
		assertEquals(0, fixture.closed.get());
		assertTrue(fixture.sent.isEmpty());
		assertFalse(fixture.requests.hasOutstanding());
		assertEquals(1, fixture.requests.allocate().orElseThrow());
	}

	@Test
	void changedApplySendsOnceAndDisablesEditingUntilResult() {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.empty());
		screen.applySelection();
		screen.applySelection();
		screen.selectionSession().select(Optional.of(SECOND));
		assertEquals(1, fixture.sent.size());
		assertFalse(applyButton(screen).active);
		assertTrue(screen.selectionSession().draft().isEmpty());
		assertFalse(screen.selectionSession().closed());
		assertEquals(0, fixture.closed.get());
		assertEquals(Optional.of(FIRST), fixture.authority().effectiveSelection());
		assertTrue(screen.children().stream().filter(CapeGridEntryWidget.class::isInstance)
				.map(AbstractWidget.class::cast).noneMatch(widget -> widget.active));
	}

	@ParameterizedTest
	@ValueSource(ints = {256, 69})
	void escapeAndInventoryCloseDraftWithoutSending(int key) {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.of(SECOND));
		assertTrue(screen.keyPressed(new KeyEvent(key, 0, 0)));
		assertTrue(screen.selectionSession().closed());
		assertTrue(fixture.sent.isEmpty());
		assertEquals(Optional.of(FIRST), fixture.open().selectionSession().draft());
	}

	@Test
	void currentInventoryBindingIsUsedAndOldEDoesNotClose() {
		var fixture = new Fixture();
		fixture.inventoryKey.set(82);
		var screen = fixture.open();
		assertFalse(screen.keyPressed(new KeyEvent(69, 0, 0)));
		assertFalse(screen.selectionSession().closed());
		assertTrue(screen.keyPressed(new KeyEvent(82, 0, 0)));
		assertEquals(1, fixture.closed.get());
	}

	@ParameterizedTest
	@ValueSource(ints = {257, 32})
	void inventoryReboundToActivationKeyClosesBeforeFocusedApply(int key) {
		var fixture = new Fixture();
		fixture.inventoryKey.set(key);
		var screen = fixture.open();
		screen.selectionSession().select(Optional.empty());
		screen.setFocused(applyButton(screen));
		assertTrue(screen.keyPressed(new KeyEvent(key, 0, 0)));
		assertTrue(screen.selectionSession().closed());
		assertTrue(fixture.sent.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(ints = {256, 69})
	void pendingManualCloseKeepsOutstandingAndLateResultUpdatesAuthority(int key) {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.of(SECOND));
		screen.applySelection();
		long requestId = fixture.sent.getFirst().requestId();
		screen.keyPressed(new KeyEvent(key, 0, 0));
		assertTrue(fixture.requests.hasOutstanding());
		var result = new CapeSelectionResultPayload(requestId, true,
				PlayerFashionAuthoritativeState.active(SECOND), CapeSelectionReason.APPLIED);
		assertFalse(fixture.result(result, null));
		assertEquals(Optional.of(SECOND), fixture.authority().storedSelection());
		assertFalse(fixture.requests.hasOutstanding());
		assertEquals(1, fixture.sent.size());
	}

	@ParameterizedTest
	@ValueSource(ints = {256, 69})
	void acceptedResultKeepsScreenAndDiscardingNextDraftReopensSavedChoice(int key) {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.of(SECOND));
		screen.applySelection();
		assertTrue(fixture.result(new CapeSelectionResultPayload(fixture.sent.getFirst().requestId(), true,
				PlayerFashionAuthoritativeState.active(SECOND), CapeSelectionReason.APPLIED), screen));
		assertEquals(0, fixture.closed.get());
		assertFalse(screen.selectionSession().closed());
		screen.selectionSession().select(Optional.of(FIRST));
		screen.keyPressed(new KeyEvent(key, 0, 0));
		assertEquals(Optional.of(SECOND), fixture.open().selectionSession().draft());
		assertEquals(1, fixture.sent.size());
	}

	@Test
	void rejectedResultReconcilesAuthorityAndResizePreservesErrorAndDraft() {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.of(SECOND));
		screen.applySelection();
		var session = screen.selectionSession();
		assertFalse(fixture.result(new CapeSelectionResultPayload(fixture.sent.getFirst().requestId(), false,
				PlayerFashionAuthoritativeState.dormant(FIRST), CapeSelectionReason.NOT_ALLOWED), screen));
		screen.resize(200, 240);
		assertSame(session, screen.selectionSession());
		assertEquals(Optional.of(FIRST), session.baseline());
		assertEquals(Optional.of(SECOND), session.draft());
		assertEquals(Optional.of(CapeSelectionReason.NOT_ALLOWED), session.lastError());
		assertEquals("你不能选择该披风", screen.statusText().secondLine());
		assertEquals(0, session.pendingRequestId());
		assertFalse(session.closed());
		assertEquals(0, fixture.closed.get());
		assertTrue(fixture.authority().isDormant());
	}

	@Test
	void actualResizePreservesSessionPendingPageYawAndSelectedTab() {
		var fixture = new Fixture();
		var screen = fixture.open();
		var session = screen.selectionSession();
		screen.capeContent().changePage(true);
		screen.previewRotation().beginDrag(screen.layout().previewBounds().centerX(),
				screen.layout().previewBounds().centerY(), 0, screen.layout().previewBounds());
		screen.previewRotation().drag(0, 57);
		session.select(Optional.of(SECOND));
		screen.applySelection();
		long request = session.pendingRequestId();
		screen.resize(200, 240);
		assertSame(session, screen.selectionSession());
		assertEquals(176, screen.layout().frameBounds().width());
		assertEquals(Optional.of(FIRST), session.baseline());
		assertEquals(Optional.of(SECOND), session.draft());
		assertEquals(request, session.pendingRequestId());
		assertEquals(1, screen.capeContent().pageIndex());
		assertEquals(237.0F, screen.previewRotation().yawDegrees());
		assertEquals(WardrobeScreen.SelectedTab.CAPE, screen.selectedTab());
		assertFalse(applyButton(screen).active);
		assertEquals(1, fixture.sent.size());
	}

	@Test
	void tooSmallScreenRemovesControlsAndRestoresSameSessionWhenEnlarged() {
		var fixture = new Fixture();
		var screen = fixture.open();
		var session = screen.selectionSession();
		session.select(Optional.of(SECOND));
		screen.resize(191, 213);
		assertFalse(screen.layout().fitsScreen());
		assertTrue(screen.children().isEmpty());
		screen.resize(320, 240);
		assertTrue(screen.layout().fitsScreen());
		assertSame(session, screen.selectionSession());
		assertEquals(Optional.of(SECOND), session.draft());
		assertFalse(screen.children().isEmpty());
	}

	@Test
    void threeTabsShareOneFormalApplyAndOneCancel() {
        var screen=new Fixture().open();
        assertArrayEquals(new WardrobeScreen.SelectedTab[] {WardrobeScreen.SelectedTab.CAPE,WardrobeScreen.SelectedTab.OUTFIT,WardrobeScreen.SelectedTab.ARMOR},WardrobeScreen.SelectedTab.values());
        var actions=screen.children().stream().filter(Button.class::isInstance).filter(widget -> !(widget instanceof ImageButton)).map(Button.class::cast).toList();
        assertEquals(2,actions.size());
        assertEquals(1,actions.stream().filter(button -> button.getMessage().getString().equals("应用")).count());
        assertEquals(1,actions.stream().filter(button -> button instanceof WardrobeArmorButton armor && armor.key().equals("cancel")).count());
    }

	@Test
	void capabilityAndConnectionGateStillBlockApplyIncludingNoChange() {
		var fixture = new Fixture();
		var screen = fixture.open();
		fixture.supported = false;
		screen.applySelection();
		assertFalse(screen.selectionSession().closed());
		assertTrue(fixture.sent.isEmpty());
		fixture.supported = true;
		fixture.requests.beginConnection(new Object());
		screen.applySelection();
		assertFalse(screen.selectionSession().closed());
		assertTrue(fixture.sent.isEmpty());
	}


	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void previewMetadataTracksChangesConsumedByResizeOrPageButton(boolean resize) {
		var fixture = new Fixture();
		var screen = fixture.open();
		assertEquals(dev.zbw3790.fashion.client.render.WardrobePreviewAppearance.Mode.SERVER_COSMETIC,
				screen.previewAppearance().mode());
		fixture.capes.replace(new CapeRegistrySnapshot(fixture.capes.entries().stream()
				.filter(metadata -> !metadata.id().equals(FIRST)).toList()));
		if (resize) {
			screen.resize(200, 240);
		} else {
			var next = screen.children().stream().filter(ImageButton.class::isInstance)
					.map(ImageButton.class::cast).filter(button -> button.visible).findFirst().orElseThrow();
			next.onPress(new KeyEvent(257, 0, 0));
		}
		assertFalse(screen.capeContent().hasMetadata(FIRST));
		assertEquals(dev.zbw3790.fashion.client.render.WardrobePreviewAppearance.Mode.VANILLA,
				screen.previewAppearance().mode());
		assertEquals(Optional.of(FIRST), screen.selectionSession().baseline());
	}

	@Test
	void zeroViewportUsesEmergencyWithoutDiscardingDraft() {
		var screen = new Fixture().open();
		screen.selectionSession().select(Optional.of(SECOND));
		screen.resize(0, 0);
		assertFalse(screen.layout().fitsScreen());
		assertTrue(screen.children().isEmpty());
		assertEquals(Optional.of(SECOND), screen.selectionSession().draft());
	}


	@Test
	void tabAndShiftTabUseVanillaWidgetFocusTraversal() {
		var screen = new Fixture().open();
		screen.keyPressed(new KeyEvent(258, 0, 0));
		var first = screen.getFocused();
		assertNotNull(first);
		screen.keyPressed(new KeyEvent(258, 0, 0));
		assertNotSame(first, screen.getFocused());
		screen.keyPressed(new KeyEvent(258, 0, 1));
		assertSame(first, screen.getFocused());
	}


	@ParameterizedTest
	@ValueSource(ints = {257, 32})
	void previewButtonActivationChangesOnlyScreenLocalMode(int key) {
		var fixture = new Fixture();
		var screen = fixture.open();
		var session = screen.selectionSession();
		session.select(Optional.of(SECOND));
		var baseline = session.baseline();
		var draft = session.draft();
		screen.capeContent().changePage(true);
		screen.previewRotation().beginDrag(screen.layout().previewDragBounds().centerX(),
				screen.layout().previewDragBounds().centerY(), 0, screen.layout().previewDragBounds());
		screen.previewRotation().drag(0, 43);
		screen.previewRotation().endDrag(0);
		var button = previewButton(screen);

		assertEquals(WardrobePreviewMode.CAPE, screen.previewMode());
		assertEquals("当前预览：披风；点击查看鞘翅", button.getMessage().getString());
		screen.setFocused(button);
		assertSame(button, screen.getFocused());
		button.onPress(new KeyEvent(key, 0, 0));
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertEquals("当前预览：鞘翅；点击查看披风", button.getMessage().getString());
		assertTrue(screen.getNarrationMessage().getString().contains("当前预览：鞘翅"));
		assertEquals(baseline, session.baseline());
		assertEquals(draft, session.draft());
		assertTrue(session.dirty());
		assertEquals(1, screen.capeContent().pageIndex());
		assertEquals(223.0F, screen.previewRotation().yawDegrees());
		assertEquals(Optional.of(FIRST), fixture.authority().storedSelection());
		assertTrue(fixture.sent.isEmpty());
		assertFalse(fixture.requests.hasOutstanding());

		button.onPress(new KeyEvent(key, 0, 0));
		assertEquals(WardrobePreviewMode.CAPE, screen.previewMode());
		assertEquals(baseline, session.baseline());
		assertEquals(draft, session.draft());
		assertEquals(1, fixture.requests.allocate().orElseThrow());
	}

	@ParameterizedTest
	@ValueSource(ints = {320, 200})
	void previewClickAndDragRegionsDoNotOverlap(int width) {
		var screen = new Fixture().open();
		screen.resize(width, 240);
		var button = previewButton(screen);
		var buttonBounds = screen.layout().previewModeButtonBounds();
		var dragBounds = screen.layout().previewDragBounds();
		assertFalse(buttonBounds.overlaps(dragBounds));
		assertTrue(button.isMouseOver(buttonBounds.centerX(), buttonBounds.centerY()));
		assertFalse(screen.previewRotation().beginDrag(buttonBounds.centerX(), buttonBounds.centerY(),
				0, dragBounds));

		// 直接执行 Vanilla onClick→onPress，避免普通 JVM 访问游戏 SoundManager。
		button.onClick(mouse(buttonBounds.centerX(), buttonBounds.centerY(), 0), false);
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertFalse(screen.previewRotation().isDragging());
		assertFalse(screen.mouseDragged(mouse(buttonBounds.centerX(), buttonBounds.centerY(), 0), 40, 0));
		assertEquals(180.0F, screen.previewRotation().yawDegrees());
		// 26.2 容器会消费命中子控件的右键，但按钮不激活，也不启动拖动。
		assertTrue(screen.mouseClicked(mouse(buttonBounds.centerX(), buttonBounds.centerY(), 1), false));
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertFalse(screen.previewRotation().isDragging());

		assertTrue(screen.mouseClicked(mouse(dragBounds.centerX(), dragBounds.centerY(), 0), false));
		assertTrue(screen.mouseDragged(mouse(dragBounds.centerX(), dragBounds.centerY(), 0), 43, 9));
		assertEquals(223.0F, screen.previewRotation().yawDegrees());
		assertTrue(screen.mouseReleased(mouse(dragBounds.centerX(), dragBounds.centerY(), 0)));
		assertFalse(screen.previewRotation().isDragging());
		assertFalse(screen.mouseClicked(mouse(screen.layout().previewBounds().x(),
				screen.layout().previewBounds().y(), 0), false));
	}

	@Test
	void pendingAllowsPreviewModeAndRotationButNotCapeEditingOrDuplicateApply() {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.of(SECOND));
		screen.applySelection();
		long pending = screen.selectionSession().pendingRequestId();
		var button = previewButton(screen);
		assertTrue(button.active);
		button.onPress(new KeyEvent(32, 0, 0));
		var bounds = screen.layout().previewDragBounds();
		assertTrue(screen.mouseClicked(mouse(bounds.centerX(), bounds.centerY(), 0), false));
		assertTrue(screen.mouseDragged(mouse(bounds.centerX(), bounds.centerY(), 0), 22, 0));
		screen.mouseReleased(mouse(bounds.centerX(), bounds.centerY(), 0));
		screen.selectionSession().select(Optional.empty());
		screen.applySelection();
		assertEquals(Optional.of(SECOND), screen.selectionSession().draft());
		assertEquals(pending, screen.selectionSession().pendingRequestId());
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertEquals(202.0F, screen.previewRotation().yawDegrees());
		assertEquals(1, fixture.sent.size());
		assertFalse(applyButton(screen).active);
	}

	@Test
	void successfulAckKeepsSameScreenPageYawModeAndAllowsSecondApplication() {
		var fixture = new Fixture();
		var screen = fixture.open();
		var session = screen.selectionSession();
		screen.capeContent().changePage(true);
		previewButton(screen).onPress(new KeyEvent(257, 0, 0));
		var bounds = screen.layout().previewDragBounds();
		screen.mouseClicked(mouse(bounds.centerX(), bounds.centerY(), 0), false);
		screen.mouseDragged(mouse(bounds.centerX(), bounds.centerY(), 0), 71, 0);
		screen.mouseReleased(mouse(bounds.centerX(), bounds.centerY(), 0));
		session.select(Optional.of(SECOND));
		screen.applySelection();
		assertTrue(fixture.result(new CapeSelectionResultPayload(fixture.sent.getFirst().requestId(), true,
				PlayerFashionAuthoritativeState.active(SECOND), CapeSelectionReason.APPLIED), screen));
		assertSame(session, screen.selectionSession());
		assertFalse(session.closed());
		assertFalse(session.dirty());
		assertEquals(Optional.of(SECOND), session.baseline());
		assertEquals(session.baseline(), session.draft());
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertEquals(251.0F, screen.previewRotation().yawDegrees());
		assertEquals(1, screen.capeContent().pageIndex());
		assertEquals(WardrobeScreen.SelectedTab.CAPE, screen.selectedTab());
		assertFalse(applyButton(screen).active);
		assertTrue(session.lastError().isEmpty());
		session.select(Optional.of(FIRST));
		screen.applySelection();
		assertEquals(2, fixture.sent.size());
		assertTrue(fixture.result(new CapeSelectionResultPayload(fixture.sent.getLast().requestId(), true,
				PlayerFashionAuthoritativeState.active(FIRST), CapeSelectionReason.APPLIED), screen));
		assertFalse(session.closed());
		assertFalse(session.dirty());
		assertEquals(Optional.of(FIRST), session.draft());
		assertEquals(Optional.of(FIRST), fixture.authority().storedSelection());
		assertEquals(0, fixture.closed.get());
	}

	@Test
	void resizeAndEmergencyPreservePreviewModeButReopenDefaultsToCape() {
		var fixture = new Fixture();
		var screen = fixture.open();
		previewButton(screen).onPress(new KeyEvent(257, 0, 0));
		assertFalse(screen.selectionSession().dirty());
		screen.resize(200, 240);
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertEquals("当前预览：鞘翅；点击查看披风", previewButton(screen).getMessage().getString());
		screen.resize(1, 1);
		assertTrue(screen.children().isEmpty());
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		screen.resize(320, 240);
		assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
		assertFalse(screen.selectionSession().dirty());
		screen.keyPressed(new KeyEvent(69, 0, 0));
		assertEquals(WardrobePreviewMode.CAPE, fixture.open().previewMode());
		assertTrue(fixture.sent.isEmpty());
	}

	@Test
	void vanillaTabTraversalIncludesPreviewButtonInBothDirections() {
		var screen = new Fixture().open();
		var preview = previewButton(screen);
		boolean forward = false;
		boolean backward = false;
		for (int i = 0; i < 30; i++) {
			screen.keyPressed(new KeyEvent(258, 0, 0));
			forward |= screen.getFocused() == preview;
		}
		for (int i = 0; i < 30; i++) {
			screen.keyPressed(new KeyEvent(258, 0, 1));
			backward |= screen.getFocused() == preview;
		}
		assertTrue(forward);
		assertTrue(backward);
	}

    @Test
    void v2ActualScreenUsesFullApplyKeepsOutfitAndContinuesSameWindow() {
        var fixture=new Fixture();fixture.v2=true;
        var outfit=OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.NONE);
        fixture.fashions.beginConnection(fixture.connection);
        fixture.fashions.receiveFullSnapshot(fixture.connection,new FullPlayerFashionSnapshot(true,List.of(new FullPlayerFashionEntry(SELF,fullState(0,FIRST,outfit)))));
        var screen=fixture.open();var session=screen.selectionSession();
        screen.capeContent().changePage(true);previewButton(screen).onPress(new KeyEvent(257,0,0));
        var bounds=screen.layout().previewDragBounds();screen.previewRotation().beginDrag(bounds.centerX(),bounds.centerY(),0,bounds);screen.previewRotation().drag(0,31);screen.previewRotation().endDrag(0);
        session.select(Optional.of(SECOND));screen.applySelection();assertTrue(fixture.sent.isEmpty());assertEquals(1,fixture.fullSent.size());assertEquals(outfit,fixture.fullSent.getFirst().stored().outfit());assertFalse(applyButton(screen).active);
        var confirmed=fullState(1,SECOND,outfit);fixture.fashions.receiveFullUpdate(fixture.connection,new FullPlayerFashionEntry(SELF,confirmed));
        ClientFullFashionSelectionResults.apply(fixture.connection,fixture.connection,SELF,new FullFashionSelectionResultPayload(1,FullFashionSelectionStatus.SUCCESS,Optional.of(confirmed)),fixture.fashions,()->session);
        screen.resize(200,240);assertSame(session,screen.selectionSession());assertFalse(applyButton(screen).active);assertEquals(1,screen.capeContent().pageIndex());assertEquals(WardrobePreviewMode.ELYTRA,screen.previewMode());assertEquals(211.0F,screen.previewRotation().yawDegrees());assertEquals(0,fixture.closed.get());
        session.select(Optional.of(FIRST));screen.applySelection();assertEquals(2,fixture.fullSent.size());assertEquals(1,fixture.fullSent.getLast().expectedRevision());assertEquals(outfit,fixture.fullSent.getLast().stored().outfit());
    }
    @Test
    void v2ActualScreenExposesCapeConflictAndCannotSendLegacyFallback() {
        var fixture=new Fixture();fixture.v2=true;fixture.fashions.beginConnection(fixture.connection);var outfit=OutfitSelections.original();
        fixture.fashions.receiveFullSnapshot(fixture.connection,new FullPlayerFashionSnapshot(true,List.of(new FullPlayerFashionEntry(SELF,fullState(0,FIRST,outfit)))));
        var screen=fixture.open();screen.selectionSession().select(Optional.of(SECOND));fixture.fashions.receiveFullUpdate(fixture.connection,new FullPlayerFashionEntry(SELF,fullState(1,SECOND,outfit)));screen.tick();screen.applySelection();
        assertFalse(applyButton(screen).active);
        assertTrue(screen.selectionSession().conflict());
        assertTrue(screen.statusText().secondLine().contains("存在外部修改"),screen.statusText().toString());
        assertEquals(WardrobeStatusText.Priority.ERROR,screen.statusText().priority());
        assertTrue(fixture.fullSent.isEmpty());assertTrue(fixture.sent.isEmpty());
    }
    private static FullPlayerFashionState fullState(long revision,CapeId cape,OutfitSelections outfit){var stored=new PlayerFashionStoredState(Optional.of(cape),outfit);return new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),outfit),revision);}

	private static MouseButtonEvent mouse(double x, double y, int button) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
	}

	private static WardrobePreviewModeButton previewButton(WardrobeScreen screen) {
		return screen.children().stream().filter(WardrobePreviewModeButton.class::isInstance)
				.map(WardrobePreviewModeButton.class::cast).findFirst().orElseThrow();
	}

	private static Button applyButton(WardrobeScreen screen) {
		return screen.children().stream().filter(Button.class::isInstance)
				.filter(widget -> !(widget instanceof ImageButton)).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("应用"))
				.findFirst().orElseThrow();
	}

	private static final class Fixture {
		final Object connection = new Object();
		final ClientPlayerFashionRegistry fashions = new ClientPlayerFashionRegistry();
		final ClientCapeRegistry capes = new ClientCapeRegistry();
		final ClientCapeSelectionRequestTracker requests = new ClientCapeSelectionRequestTracker();
		final List<SetCapeSelectionPayload> sent = new ArrayList<>();
        final List<SetFullFashionSelectionPayload> fullSent = new ArrayList<>();
        boolean v2;
		final AtomicInteger closed = new AtomicInteger();
		final AtomicInteger inventoryKey = new AtomicInteger(69);
		boolean supported = true;

		Fixture() {
			fashions.beginConnection(connection);
			fashions.replace(new PlayerFashionSnapshot(true, List.of(new PlayerFashionEntry(
					SELF, PlayerFashionAuthoritativeState.active(FIRST)))));
			requests.beginConnection(connection);
			capes.replace(new CapeRegistrySnapshot(IntStream.range(0, 25).mapToObj(index ->
					new CapeCosmeticMetadata(new CapeId("cape%02d".formatted(index)),
							"a".repeat(64), Optional.empty())).toList()));
		}

		WardrobeScreen open() {
			var screen = new WardrobeScreen(null, new Font(null), capes, new ClientCapeTextureManager(),
					fashions, requests, SELF, connection, new WardrobeScreen.Actions(
							() -> supported, event -> event.key() == inventoryKey.get(), sent::add,
							closed::incrementAndGet, () -> supported && v2, fullSent::add));
			screen.width = 320;
			screen.height = 240;
			screen.init();
			return screen;
		}

		PlayerFashionAuthoritativeState authority() {
			return fashions.selfAuthority(SELF).orElseThrow();
		}

		boolean result(CapeSelectionResultPayload result, WardrobeScreen screen) {
			boolean accepted = ClientCapeSelectionResults.apply(connection, SELF, result, fashions, requests,
					() -> screen == null ? null : screen.selectionSession(), id -> capes.find(id).isPresent());
			if (screen != null) {
				screen.tick();
			}
			return accepted;
		}
	}
}
