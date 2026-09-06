package vanillafashion.client.screen;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistrySnapshot;
import vanillafashion.client.cape.ClientCapeRegistry;
import vanillafashion.client.cape.ClientCapeTextureManager;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.client.network.ClientCapeSelectionResults;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionSnapshot;
import vanillafashion.network.CapeSelectionReason;
import vanillafashion.network.CapeSelectionResultPayload;
import vanillafashion.network.SetCapeSelectionPayload;

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
	void unchangedApplyUsesActualButtonClosesWithoutAllocatingRequest() {
		var fixture = new Fixture();
		var screen = fixture.open();
		var apply = applyButton(screen);
		assertEquals("应用", apply.getMessage().getString());
		apply.onPress(new KeyEvent(257, 0, 0));
		assertTrue(screen.selectionSession().closed());
		assertEquals(1, fixture.closed.get());
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

	@Test
	void acceptedResultClosesMatchingScreenAndReopenUsesSavedChoice() {
		var fixture = new Fixture();
		var screen = fixture.open();
		screen.selectionSession().select(Optional.of(SECOND));
		screen.applySelection();
		assertTrue(fixture.result(new CapeSelectionResultPayload(fixture.sent.getFirst().requestId(), true,
				PlayerFashionAuthoritativeState.active(SECOND), CapeSelectionReason.APPLIED), screen));
		assertEquals(1, fixture.closed.get());
		var reopened = fixture.open();
		reopened.selectionSession().select(Optional.of(FIRST));
		reopened.keyPressed(new KeyEvent(69, 0, 0));
		assertEquals(Optional.of(SECOND), fixture.open().selectionSession().draft());
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
	void onlyCapeTabAndOneFormalActionExist() {
		var screen = new Fixture().open();
		assertArrayEquals(new WardrobeScreen.SelectedTab[] {WardrobeScreen.SelectedTab.CAPE},
				WardrobeScreen.SelectedTab.values());
		var actions = screen.children().stream().filter(Button.class::isInstance)
				.filter(widget -> !(widget instanceof ImageButton)).map(Button.class::cast).toList();
		assertEquals(1, actions.size());
		assertEquals("应用", actions.getFirst().getMessage().getString());
		assertFalse(screen.children().stream().filter(AbstractWidget.class::isInstance)
				.map(AbstractWidget.class::cast).map(widget -> widget.getMessage().getString())
				.anyMatch(message -> List.of("取消", "完成", "Outfit", "Armor", "开发中").contains(message)));
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
		assertEquals(vanillafashion.client.render.WardrobePreviewAppearance.Mode.SERVER_COSMETIC,
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
		assertEquals(vanillafashion.client.render.WardrobePreviewAppearance.Mode.VANILLA,
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

	private static Button applyButton(WardrobeScreen screen) {
		return screen.children().stream().filter(Button.class::isInstance)
				.filter(widget -> !(widget instanceof ImageButton)).map(Button.class::cast)
				.findFirst().orElseThrow();
	}

	private static final class Fixture {
		final Object connection = new Object();
		final ClientPlayerFashionRegistry fashions = new ClientPlayerFashionRegistry();
		final ClientCapeRegistry capes = new ClientCapeRegistry();
		final ClientCapeSelectionRequestTracker requests = new ClientCapeSelectionRequestTracker();
		final List<SetCapeSelectionPayload> sent = new ArrayList<>();
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
							closed::incrementAndGet));
			screen.width = 320;
			screen.height = 240;
			screen.init();
			return screen;
		}

		PlayerFashionAuthoritativeState authority() {
			return fashions.selfAuthority(SELF).orElseThrow();
		}

		boolean result(CapeSelectionResultPayload result, WardrobeScreen screen) {
			boolean close = ClientCapeSelectionResults.apply(connection, SELF, result, fashions, requests,
					() -> screen == null ? null : screen.selectionSession(), id -> capes.find(id).isPresent());
			if (close) {
				screen.onClose();
			}
			return close;
		}
	}
}
