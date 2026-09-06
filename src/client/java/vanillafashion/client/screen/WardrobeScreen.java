package vanillafashion.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.client.cape.ClientCapeRegistry;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.network.SetCapeSelectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import vanillafashion.client.cape.ClientCapeTextureManager;
import vanillafashion.client.cape.ClientCapeTextureResolver;
import vanillafashion.client.render.WardrobePreviewAppearance;

public final class WardrobeScreen extends Screen {
	private static final Component TITLE = Component.literal("衣柜");
	private static final Component PLAYER_SECTION_TITLE = Component.literal("玩家");
	private static final Component CAPE_SECTION_TITLE = Component.literal("披风");
	private static final Component EMPTY_REGISTRY_MESSAGE = Component.literal("服务器暂无可用披风");
	private static final Component PLAYER_UNAVAILABLE_MESSAGE = Component.literal("玩家预览不可用");
	private static final Component FINISH_BUTTON = Component.literal("完成");
	private static final Component CANCEL_BUTTON = Component.literal("取消");
	private static final Component PREVIOUS_PAGE_BUTTON = Component.literal("<");
	private static final Component NEXT_PAGE_BUTTON = Component.literal(">");
	private static final float PLAYER_PREVIEW_OFFSET_Y = 0.0625F;
	private static final int PREVIEW_BACKGROUND_COLOR = 0x80000000;
	private static final int PREVIEW_OUTLINE_COLOR = 0xFF808080;

	private final WardrobeCapeCatalog catalog;
	private final ClientCapeTextureManager textureManager;
	private WardrobePreviewAppearanceResolver previewAppearanceResolver;
	private final ClientCapeRegistry capeRegistry;
	private final ClientPlayerFashionRegistry playerFashions;
	private final ClientCapeSelectionRequestTracker requests;
	private final UUID self;
	private final Object connection;
	private final WardrobeSelectionSession selection = new WardrobeSelectionSession();
	private List<CapeCosmeticMetadata> metadataEntries;
	private Button finishButton;
	private final WardrobePreviewRotation previewRotation = new WardrobePreviewRotation();
	private final WardrobePlayerPreviewRenderer previewRenderer = new WardrobePlayerPreviewRenderer();
	private final List<CapeGridEntryWidget> entryWidgets = new ArrayList<>();
	private WardrobeLayout layout;

	public WardrobeScreen(
			ClientCapeRegistry capeRegistry,
			ClientCapeTextureManager textureManager,
			ClientPlayerFashionRegistry playerFashions,
			ClientCapeSelectionRequestTracker requests,
			UUID self,
			Object connection
	) {
		super(TITLE);
		this.capeRegistry = Objects.requireNonNull(capeRegistry);
		this.playerFashions = Objects.requireNonNull(playerFashions);
		this.requests = Objects.requireNonNull(requests);
		this.self = Objects.requireNonNull(self);
		this.connection = Objects.requireNonNull(connection);
		metadataEntries = capeRegistry.entries();
		catalog = new WardrobeCapeCatalog(metadataEntries);
		previewAppearanceResolver = new WardrobePreviewAppearanceResolver(
				metadataEntries,
				new ClientCapeTextureResolver(textureManager)
		);
		this.textureManager = Objects.requireNonNull(textureManager, "衣柜运行时纹理管理器不能为 null。");
		selection.observe(playerFashions.selfAuthority(self), playerFashions.state());
	}

	@Override
	protected void init() {
		entryWidgets.clear();
		layout = WardrobeLayout.calculate(width, height, font.lineHeight);

		List<WardrobeCapeCatalog.Entry> pageEntries = catalog.pageEntries();
		for (int index = 0; index < pageEntries.size(); index++) {
			WardrobeCapeCatalog.Entry entry = pageEntries.get(index);
			WardrobeLayout.Bounds entryBounds = layout.entryBounds(index);
			var widget = new CapeGridEntryWidget(
					entryBounds.x(),
					entryBounds.y(),
					entryBounds.width(),
					entryBounds.height(),
					font,
					entry,
					textureLookup(entry),
					() -> selection.authorityKnown() && entry.capeId().equals(selection.draft()),
					() -> selection.select(entry.capeId())
			);
			entryWidgets.add(addRenderableWidget(widget));
		}

		WardrobeLayout.Bounds previousPageBounds = layout.previousPageButtonBounds();
		Button previousPageButton = addRenderableWidget(Button.builder(
				PREVIOUS_PAGE_BUTTON,
				button -> changePage(false)
		).bounds(
				previousPageBounds.x(),
				previousPageBounds.y(),
				previousPageBounds.width(),
				previousPageBounds.height()
		).build());
		previousPageButton.active = catalog.canGoToPreviousPage();

		WardrobeLayout.Bounds nextPageBounds = layout.nextPageButtonBounds();
		Button nextPageButton = addRenderableWidget(Button.builder(
				NEXT_PAGE_BUTTON,
				button -> changePage(true)
		).bounds(
				nextPageBounds.x(),
				nextPageBounds.y(),
				nextPageBounds.width(),
				nextPageBounds.height()
		).build());
		nextPageButton.active = catalog.canGoToNextPage();

		var finishBounds = layout.finishButtonBounds();
		finishButton = addRenderableWidget(Button.builder(FINISH_BUTTON, button -> finish())
				.bounds(finishBounds.x(), finishBounds.y(), finishBounds.width(), finishBounds.height()).build());
		WardrobeLayout.Bounds closeButtonBounds = layout.cancelButtonBounds();
		addRenderableWidget(Button.builder(CANCEL_BUTTON, button -> onClose())
				.bounds(
						closeButtonBounds.x(),
						closeButtonBounds.y(),
						closeButtonBounds.width(),
						closeButtonBounds.height()
				)
				.build());
		refreshSelection();
	}

	@Override
	public void tick() {
		super.tick();
		var currentMetadata = capeRegistry.entries();
		if (!metadataEntries.equals(currentMetadata)) {
			metadataEntries = currentMetadata;
			catalog.replace(metadataEntries);
			previewAppearanceResolver = new WardrobePreviewAppearanceResolver(
					metadataEntries, new ClientCapeTextureResolver(textureManager));
			rebuildWidgets();
		}
		selection.tick();
		refreshSelection();
	}

	public WardrobeSelectionSession selectionSession() { return selection; }

	private boolean canSendSelection() {
		return requests.matchesConnection(connection) && minecraft != null && minecraft.getConnection() != null
				&& ClientPlayNetworking.canSend(SetCapeSelectionPayload.TYPE);
	}

	private void refreshSelection() {
		selection.observe(playerFashions.selfAuthority(self), playerFashions.state());
		if (finishButton != null) {
			finishButton.active = selection.canFinish(canSendSelection(), requests.hasOutstanding(),
					id -> capeRegistry.find(id).isPresent());
		}
		entryWidgets.forEach(widget -> {
			widget.refreshAvailability();
			widget.active &= selection.canEdit();
		});
	}

	private void finish() {
		refreshSelection();
		var decision = selection.finish(requests, canSendSelection(), id -> capeRegistry.find(id).isPresent());
		decision.request().ifPresent(ClientPlayNetworking::send);
		if (decision.close()) {
			onClose();
		} else {
			refreshSelection();
		}
	}

	@Override
	public void onClose() {
		selection.cancel();
		super.onClose();
	}

	@Override
	public void removed() {
		selection.cancel();
		super.removed();
	}

	private Component selectionLabel() {
		return Component.literal(selection.selectionLabel());
	}

	private Component statusLabel() {
		return Component.literal(selection.status(canSendSelection(), requests.hasOutstanding(),
				id -> capeRegistry.find(id).isPresent()));
	}

	@Override
	public void extractRenderState(
			GuiGraphicsExtractor graphics,
			int mouseX,
			int mouseY,
			float partialTick
	) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, layout.titleY(), 0xFFFFFFFF);
		graphics.centeredText(
				font,
				PLAYER_SECTION_TITLE,
				layout.previewBounds().centerX(),
				layout.sectionTitleY(),
				0xFFFFFFFF
		);
		graphics.centeredText(
				font,
				CAPE_SECTION_TITLE,
				layout.gridBounds().centerX(),
				layout.sectionTitleY(),
				0xFFFFFFFF
		);
		graphics.centeredText(
				font,
				statusLabel(),
				layout.contentBounds().centerX(),
				layout.statusLabelY(),
				0xFFA0A0A0
		);
		graphics.centeredText(
				font,
				catalog.pageNumber() + " / " + catalog.pageCount(),
				layout.gridBounds().centerX(),
				layout.pageLabelY(),
				0xFFFFFFFF
		);
		graphics.centeredText(
				font,
				selectionLabel(),
				layout.contentBounds().centerX(),
				layout.selectionLabelY(),
				0xFFFFFFFF
		);

		if (catalog.capeCount() == 0) {
			graphics.centeredText(
					font,
					EMPTY_REGISTRY_MESSAGE,
					layout.gridBounds().centerX(),
					layout.gridBounds().centerY() - font.lineHeight / 2,
					0xFFA0A0A0
			);
		}
	}

	@Override
	public void extractBackground(
			GuiGraphicsExtractor graphics,
			int mouseX,
			int mouseY,
			float partialTick
	) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		extractPlayerPreview(graphics, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (layout != null && previewRotation.beginDrag(
				event.x(),
				event.y(),
				event.button(),
				layout.previewBounds()
		)) {
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (previewRotation.drag(event.button(), deltaX)) {
			return true;
		}

		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (previewRotation.endDrag(event.button())) {
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void changePage(boolean next) {
		if (next) {
			catalog.goToNextPage();
		} else {
			catalog.goToPreviousPage();
		}
		rebuildWidgets();
	}

	private Supplier<Optional<Identifier>> textureLookup(WardrobeCapeCatalog.Entry entry) {
		return entry.metadata()
				.<Supplier<Optional<Identifier>>>map(metadata -> () -> textureManager.find(metadata.capeSha256()))
				.orElse(Optional::empty);
	}

	private void extractPlayerPreview(GuiGraphicsExtractor graphics, int mouseY) {
		WardrobeLayout.Bounds previewBounds = layout.previewBounds();
		graphics.fill(
				previewBounds.x(),
				previewBounds.y(),
				previewBounds.right(),
				previewBounds.bottom(),
				PREVIEW_BACKGROUND_COLOR
		);
		graphics.outline(
				previewBounds.x(),
				previewBounds.y(),
				previewBounds.width(),
				previewBounds.height(),
				PREVIEW_OUTLINE_COLOR
		);

		if (minecraft == null || minecraft.player == null) {
			extractPlayerUnavailable(graphics, previewBounds);
			return;
		}

		if (!selection.authorityKnown()) {
			graphics.centeredText(font, Component.literal("时装状态正在同步"),
					previewBounds.centerX(), previewBounds.centerY(), 0xFFA0A0A0);
			return;
		}
		WardrobePreviewAppearance appearance = previewAppearanceResolver.resolve(selection.previewSelection());
		if (!previewRenderer.extract(
				graphics,
				previewBounds,
				layout.previewEntitySize(),
				PLAYER_PREVIEW_OFFSET_Y,
				mouseY,
				previewRotation.yawDegrees(),
				minecraft.player,
				appearance
		)) {
			extractPlayerUnavailable(graphics, previewBounds);
		}
	}

	private void extractPlayerUnavailable(
			GuiGraphicsExtractor graphics,
			WardrobeLayout.Bounds previewBounds
	) {
		graphics.centeredText(
				font,
				PLAYER_UNAVAILABLE_MESSAGE,
				previewBounds.centerX(),
				previewBounds.centerY() - font.lineHeight / 2,
				0xFFA0A0A0
		);
	}
}
