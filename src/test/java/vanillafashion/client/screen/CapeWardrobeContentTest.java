package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionSnapshot;
import vanillafashion.network.CapeSelectionReason;
import vanillafashion.network.CapeSelectionResultPayload;

class CapeWardrobeContentTest {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("vanillafashion", "test_cape");
	private static final Function<CapeCosmeticMetadata, Optional<Identifier>> READY = ignored -> Optional.of(TEXTURE);
	private static final WardrobeLayout STANDARD = WardrobeLayout.calculate(400, 300, 9);

	@Test
	void emptyRegistryStillHasSelectableOriginalIconWithoutTextureLookup() {
		var selection = session(PlayerFashionAuthoritativeState.active(new CapeId("saved")));
		var content = new CapeWardrobeContent(List::of, metadata -> {
			fail("原版条目不得查询自定义纹理。");
			return Optional.empty();
		}, selection);
		assertEquals(CapeWardrobeContent.State.EMPTY, content.state());
		assertEquals(1, content.slots().size());
		assertEquals(CapeWardrobeContent.Visual.ORIGINAL_ICON, content.slots().getFirst().visual());
		assertEquals("原版", content.slots().getFirst().tooltipText());
		assertTrue(content.activateSlot(0));
		assertTrue(selection.draft().isEmpty());
		assertEquals(Optional.of(new CapeId("saved")), selection.baseline());
		assertFalse(content.paginationVisible());
	}

	@Test
	void catalogKeepsOriginalFirstNaturalOrderAndTwelveSlots() {
		var content = content(() -> List.of(metadata("zeta"), metadata("alpha"), metadata("middle")), READY);
		assertEquals(List.of("原版", "alpha", "middle", "zeta"),
				content.slots().stream().map(model -> model.entry().displayName()).toList());
		var larger = content(() -> entries(24), READY);
		assertEquals(12, larger.slots().size());
		assertEquals(3, larger.pageCount());
		assertEquals(1, larger.pageNumber());
	}

	@Test
	void paginationUsesSmallImageButtonsAndKeepsEndpointsInPlace() {
		var content = content(() -> entries(24), READY);
		var widgets = build(content, STANDARD);
		var arrows = arrows(widgets);
		assertEquals(2, arrows.size());
		assertFalse(arrows.getFirst().visible);
		assertTrue(arrows.getLast().visible);
		var previous = STANDARD.previousPageButtonBounds();
		var next = STANDARD.nextPageButtonBounds();
		assertEquals(previous.x(), arrows.getFirst().getX());
		assertEquals(next.x(), arrows.getLast().getX());
		assertTrue(arrows.stream().allMatch(button -> button.getWidth() == 12 && button.getHeight() == 17));
		assertTrue(content.changePage(true));
		arrows = arrows(build(content, STANDARD));
		assertTrue(arrows.stream().allMatch(button -> button.visible));
		assertTrue(content.changePage(true));
		arrows = arrows(build(content, STANDARD));
		assertTrue(arrows.getFirst().visible);
		assertFalse(arrows.getLast().visible);
		assertEquals(previous.x(), arrows.getFirst().getX());
		assertEquals(next.x(), arrows.getLast().getX());
		assertEquals(1, content.slots().size());
		assertFalse(content.changePage(true));
	}

	@Test
	void singlePageHidesBothArrowWidgets() {
		var content = content(() -> entries(11), READY);
		assertFalse(content.paginationVisible());
		assertTrue(arrows(build(content, STANDARD)).stream().noneMatch(button -> button.visible || button.active));
		assertFalse(content.changePage(false));
		assertFalse(content.changePage(true));
	}

	@Test
	void metadataShrinkClampsPageWithoutChangingDraft() {
		var source = new AtomicReference<>(entries(30));
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var content = new CapeWardrobeContent(source::get, READY, selection);
		assertTrue(content.activateSlot(1));
		var draft = selection.draft();
		content.changePage(true);
		content.changePage(true);
		source.set(entries(2));
		assertTrue(content.refresh(false));
		assertEquals(0, content.pageIndex());
		assertEquals(draft, selection.draft());
		assertEquals(3, content.slots().size());
	}

	@Test
	void textureArrivalRefreshesExistingWidgetsWithoutCatalogRebuild() {
		var texture = new AtomicReference<Optional<Identifier>>(Optional.empty());
		var content = content(() -> entries(1), ignored -> texture.get());
		var widgets = build(content, STANDARD);
		var cape = (CapeGridEntryWidget) widgets.get(1);
		assertFalse(cape.active);
		assertEquals(CapeWardrobeContent.State.LOADING, content.state());
		assertEquals(CapeWardrobeContent.Visual.EMPTY, content.slots().get(1).visual());
		assertTrue(cape.getMessage().getString().contains("纹理正在加载"));
		texture.set(Optional.of(TEXTURE));
		assertFalse(content.refresh(false));
		assertTrue(cape.active);
		assertEquals(CapeWardrobeContent.State.READY, content.state());
		assertEquals(CapeWardrobeContent.Visual.CAPE_THUMBNAIL, content.slots().get(1).visual());
		assertFalse(cape.getMessage().getString().contains("纹理正在加载"));
	}

	@Test
	void unavailableRegistrySkipsTextureLookupAndRemovesCustomVisual() {
		var lookups = new AtomicInteger();
		var content = content(() -> entries(1), metadata -> {
			lookups.incrementAndGet();
			return Optional.of(TEXTURE);
		});
		int before = lookups.get();
		content.refresh(true);
		assertEquals(before, lookups.get());
		assertEquals(CapeWardrobeContent.State.ERROR, content.state());
		var custom = content.slots().get(1);
		assertEquals(CapeWardrobeContent.Visual.EMPTY, custom.visual());
		assertTrue(custom.texture().isEmpty());
		assertFalse(content.activateSlot(1));
		assertTrue(content.activateSlot(0));
		assertTrue(custom.tooltipText().contains("当前不可用"));
	}

	@Test
	void missingTextureNeverBecomesMissingTextureIdentifierOrSelectableCape() {
		var content = content(() -> entries(1), ignored -> Optional.empty());
		assertEquals(CapeWardrobeContent.Availability.LOADING, content.slots().get(1).availability());
		assertTrue(content.slots().get(1).texture().isEmpty());
		assertFalse(content.activateSlot(1));
		assertTrue(content.activateSlot(0));
	}

	@Test
	void metadataFailureKeepsPageAndRecoversWithoutLosingCatalog() {
		var fail = new AtomicBoolean();
		var content = content(() -> {
			if (fail.get()) {
				throw new IllegalStateException("测试元数据读取失败。");
			}
			return entries(24);
		}, READY);
		content.changePage(true);
		fail.set(true);
		assertDoesNotThrow(() -> content.refresh(false));
		assertEquals(CapeWardrobeContent.State.ERROR, content.state());
		assertEquals(1, content.pageIndex());
		assertTrue(content.slots().stream().allMatch(model -> model.texture().isEmpty()));
		fail.set(false);
		assertFalse(content.refresh(false));
		assertEquals(CapeWardrobeContent.State.READY, content.state());
		assertEquals(1, content.pageIndex());
	}

	@Test
	void textureLookupFailureIsContainedAndOriginalRemainsAvailable() {
		var content = content(() -> entries(1), metadata -> {
			throw new IllegalStateException("测试纹理查询失败。");
		});
		assertEquals(CapeWardrobeContent.State.ERROR, content.state());
		assertEquals(CapeWardrobeContent.Visual.EMPTY, content.slots().get(1).visual());
		assertTrue(content.slots().getFirst().selectable());
		assertTrue(content.activateSlot(0));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void tooltipPreservesFullIdAndExplicitCustomElytraPresence(boolean hasElytra) {
		String id = "a".repeat(64);
		var metadata = new CapeCosmeticMetadata(new CapeId(id), "a".repeat(64),
				hasElytra ? Optional.of("b".repeat(64)) : Optional.empty());
		var content = content(() -> List.of(metadata), READY);
		var model = content.slots().get(1);
		assertTrue(model.tooltipText().contains(id));
		assertTrue(model.tooltipText().contains("自定义鞘翅：" + (hasElytra ? "有" : "无")));
		assertEquals(CapeWardrobeContent.Visual.CAPE_THUMBNAIL, model.visual());
		assertEquals(CapeWardrobeContent.Visual.ORIGINAL_ICON, content.slots().getFirst().visual());
	}

	@Test
	void unavailableVisualDropsEvenSuppliedTexture() {
		var model = new CapeWardrobeContent.SlotModel(WardrobeCapeCatalog.Entry.cape(metadata("cape")),
				Optional.of(TEXTURE), CapeWardrobeContent.Availability.UNAVAILABLE);
		assertTrue(model.texture().isEmpty());
		assertEquals(CapeWardrobeContent.Visual.EMPTY, model.visual());
	}

	@Test
	void clickingTileOnlyChangesDraftAndPendingBlocksFurtherActivation() {
		var player = new UUID(0, 1);
		var authority = PlayerFashionAuthoritativeState.vanilla();
		var registry = new ClientPlayerFashionRegistry();
		registry.replace(new PlayerFashionSnapshot(true, List.of(new PlayerFashionEntry(player, authority))));
		var selection = session(authority);
		var content = new CapeWardrobeContent(() -> entries(2), READY, selection);
		assertTrue(content.activateSlot(1));
		assertEquals(authority, registry.find(player).orElseThrow());
		assertTrue(selection.baseline().isEmpty());
		var tracker = new ClientCapeSelectionRequestTracker();
		tracker.beginConnection(new Object());
		var request = selection.finish(tracker, true, content::hasMetadata).request().orElseThrow();
		assertEquals(selection.draft(), request.selection());
		var widgets = build(content, STANDARD);
		assertTrue(widgets.stream().filter(CapeGridEntryWidget.class::isInstance).noneMatch(widget -> widget.active));
		assertFalse(content.activateSlot(0));
		assertFalse(content.activateSlot(2));
		assertTrue(selection.finish(tracker, true, content::hasMetadata).request().isEmpty());
		assertEquals(1, tracker.outstandingCount());
		assertEquals(authority, registry.find(player).orElseThrow());
	}

	@Test
	void directPressHonorsVisibilityAndActiveGate() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var content = new CapeWardrobeContent(() -> entries(1), READY, selection);
		var widget = (CapeGridEntryWidget) build(content, STANDARD).get(1);
		widget.visible = false;
		widget.onPress(null);
		assertTrue(selection.draft().isEmpty());
		widget.visible = true;
		widget.active = false;
		widget.onPress(null);
		assertTrue(selection.draft().isEmpty());
		content.refresh(false);
		widget.onPress(null);
		assertEquals(Optional.of(new CapeId("cape_0001")), selection.draft());
	}

	@Test
	void rebuildPreservesPageDraftPendingAndError() {
		var selection = session(PlayerFashionAuthoritativeState.vanilla());
		var content = new CapeWardrobeContent(() -> entries(24), READY, selection);
		content.activateSlot(1);
		content.changePage(true);
		var tracker = new ClientCapeSelectionRequestTracker();
		tracker.beginConnection(new Object());
		long requestId = selection.finish(tracker, true, content::hasMetadata).request().orElseThrow().requestId();
		var draft = selection.draft();
		var standard = build(content, STANDARD);
		var compact = build(content, WardrobeLayout.calculate(200, 240, 9));
		assertNotSame(standard.getFirst(), compact.getFirst());
		assertEquals(1, content.pageIndex());
		assertEquals(draft, selection.draft());
		assertEquals(requestId, selection.pendingRequestId());
		selection.acceptResult(new CapeSelectionResultPayload(requestId, false,
				PlayerFashionAuthoritativeState.vanilla(), CapeSelectionReason.NOT_ALLOWED),
				ClientPlayerFashionRegistry.State.AVAILABLE, content::hasMetadata);
		build(content, STANDARD);
		assertEquals(Optional.of(CapeSelectionReason.NOT_ALLOWED), selection.lastError());
		assertEquals(draft, selection.draft());
		assertTrue(selection.baseline().isEmpty());
		assertFalse(selection.closed());
		assertEquals(1, content.pageIndex());
	}

	@Test
	void emergencyBuildCreatesNoWidgetsAndCanReturnToApprovedLayout() {
		var content = content(() -> entries(24), READY);
		content.changePage(true);
		build(content, STANDARD);
		assertTrue(build(content, WardrobeLayout.calculate(100, 80, 9)).isEmpty());
		assertEquals(1, content.pageIndex());
		assertFalse(build(content, STANDARD).isEmpty());
		assertEquals(1, content.pageIndex());
	}

	@Test
	void unknownAuthorityIsLoadingAndCannotEditEvenOriginal() {
		var selection = new WardrobeSelectionSession();
		var content = new CapeWardrobeContent(List::of, READY, selection);
		assertEquals(CapeWardrobeContent.State.LOADING, content.state());
		assertTrue(content.slots().getFirst().selectable());
		assertFalse(content.activateSlot(0));
	}

	@Test
	void contentHasNoNetworkSenderTrackerOrAuthoritativeRegistryField() {
		for (var field : CapeWardrobeContent.class.getDeclaredFields()) {
			assertFalse(field.getType().getName().contains(".network."));
			assertNotEquals(ClientPlayerFashionRegistry.class, field.getType());
		}
	}

	private static CapeWardrobeContent content(Supplier<List<CapeCosmeticMetadata>> source,
			Function<CapeCosmeticMetadata, Optional<Identifier>> textures) {
		return new CapeWardrobeContent(source, textures, session(PlayerFashionAuthoritativeState.vanilla()));
	}

	private static WardrobeSelectionSession session(PlayerFashionAuthoritativeState authority) {
		var session = new WardrobeSelectionSession();
		session.observe(Optional.of(authority), ClientPlayerFashionRegistry.State.AVAILABLE);
		return session;
	}

	private static List<AbstractWidget> build(CapeWardrobeContent content, WardrobeLayout layout) {
		var widgets = new ArrayList<AbstractWidget>();
		content.buildWidgets(layout, widgets::add, () -> { });
		return widgets;
	}

	private static List<ImageButton> arrows(List<AbstractWidget> widgets) {
		return widgets.stream().filter(ImageButton.class::isInstance).map(ImageButton.class::cast).toList();
	}

	private static List<CapeCosmeticMetadata> entries(int count) {
		var entries = new ArrayList<CapeCosmeticMetadata>();
		for (int index = 1; index <= count; index++) {
			entries.add(metadata(String.format(Locale.ROOT, "cape_%04d", index)));
		}
		return List.copyOf(entries);
	}

	private static CapeCosmeticMetadata metadata(String id) {
		return new CapeCosmeticMetadata(new CapeId(id), "a".repeat(64), Optional.empty());
	}
}
