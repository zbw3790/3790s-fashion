package vanillafashion.client.screen;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.client.render.WardrobePreviewAppearance;
import vanillafashion.client.cape.ClientCapeRegistry;
import vanillafashion.client.cape.ClientCapeTextureManager;
import vanillafashion.client.cape.ClientCapeTextureResolver;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.network.SetCapeSelectionPayload;

/** 承载唯一 Draft、玩家预览和应用入口；Cape 内容只管理网格与分页。 */
public final class WardrobeScreen extends Screen {
	private static final Component TITLE = Component.literal("衣柜");
	private static final Component CAPE_TAB = Component.literal("披风");
	private static final Component APPLY = Component.literal("应用");
	private static final String TOO_SMALL = "窗口过小，无法显示衣柜";
	private static final String PREVIEW_UNAVAILABLE = "玩家预览不可用";
	private static final float PLAYER_PREVIEW_OFFSET_Y = 0.0625F;

	private final ClientPlayerFashionRegistry playerFashions;
	private final ClientCapeSelectionRequestTracker requests;
	private final UUID self;
	private final Object connection;
	private final Actions actions;
	private final WardrobeSelectionSession selection = new WardrobeSelectionSession();
	private final CapeWardrobeContent capeContent;
	private final ClientCapeTextureResolver textureResolver;
	private final WardrobePreviewRotation previewRotation = new WardrobePreviewRotation();
	private final WardrobePlayerPreviewRenderer previewRenderer = new WardrobePlayerPreviewRenderer();
	private final SelectedTab selectedTab = SelectedTab.CAPE;
	private WardrobePreviewAppearanceResolver previewAppearanceResolver;
	private List<CapeCosmeticMetadata> previewMetadata;
	private WardrobeLayout layout;
	private Button applyButton;

	public WardrobeScreen(ClientCapeRegistry capeRegistry, ClientCapeTextureManager textureManager,
			ClientPlayerFashionRegistry playerFashions, ClientCapeSelectionRequestTracker requests,
			UUID self, Object connection) {
		this(Minecraft.getInstance(), Minecraft.getInstance().font, capeRegistry, textureManager,
				playerFashions, requests, self, connection, runtimeActions(Minecraft.getInstance()));
	}

	/** 将屏幕外部的输入绑定、发送和关闭动作集中在一个边界，便于普通 JVM 验证。 */
	WardrobeScreen(Minecraft minecraft, Font font, ClientCapeRegistry capeRegistry,
			ClientCapeTextureManager textureManager, ClientPlayerFashionRegistry playerFashions,
			ClientCapeSelectionRequestTracker requests, UUID self, Object connection, Actions actions) {
		super(minecraft, Objects.requireNonNull(font), TITLE);
		this.playerFashions = Objects.requireNonNull(playerFashions);
		this.requests = Objects.requireNonNull(requests);
		this.self = Objects.requireNonNull(self);
		this.connection = Objects.requireNonNull(connection);
		this.actions = Objects.requireNonNull(actions);
		capeContent = new CapeWardrobeContent(capeRegistry, textureManager, selection);
		textureResolver = new ClientCapeTextureResolver(textureManager);
		previewMetadata = capeContent.metadataEntries();
		previewAppearanceResolver = new WardrobePreviewAppearanceResolver(previewMetadata, textureResolver);
		observeAuthority();
	}

	private static Actions runtimeActions(Minecraft minecraft) {
		return new Actions(
				() -> minecraft.getConnection() != null
						&& ClientPlayNetworking.canSend(SetCapeSelectionPayload.TYPE),
				event -> minecraft.options.keyInventory.matches(event),
				ClientPlayNetworking::send,
				() -> minecraft.gui.setScreen(null));
	}

	@Override
	protected void init() {
		layout = WardrobeLayout.calculate(Math.max(1, width), Math.max(1, height), font.lineHeight);
		previewRotation.endDrag(WardrobePreviewRotation.PRIMARY_MOUSE_BUTTON);
		applyButton = null;
		capeContent.buildWidgets(layout, widget -> addRenderableWidget(widget), this::rebuildWidgets);
		if (layout.fitsScreen()) {
			addRenderableWidget(new CapeTabWidget(layout.tabBounds()));
			var bounds = layout.applyButtonBounds();
			applyButton = addRenderableWidget(Button.builder(APPLY, button -> applySelection())
					.bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
		}
		refreshSelection();
	}

	@Override
	protected void setInitialFocus() {
		if (minecraft != null) {
			super.setInitialFocus();
		}
	}

	@Override
	public void tick() {
		super.tick();
		selection.tick();
		refreshSelection();
	}

	private void observeAuthority() {
		selection.observe(playerFashions.selfAuthority(self), playerFashions.state());
	}

	private boolean canSendSelection() {
		return requests.matchesConnection(connection) && actions.channelSupported().getAsBoolean();
	}

	private void refreshSelection() {
		observeAuthority();
		boolean changed = capeContent.refresh(
				playerFashions.state() == ClientPlayerFashionRegistry.State.UNAVAILABLE);
		// 分页或 resize 可能先刷新内容；预览独立核对自己的元数据快照。
		if (!previewMetadata.equals(capeContent.metadataEntries())) {
			previewMetadata = capeContent.metadataEntries();
			previewAppearanceResolver = new WardrobePreviewAppearanceResolver(previewMetadata, textureResolver);
		}
		if (changed && layout != null) {
			rebuildWidgets();
		}
		if (applyButton != null) {
			applyButton.active = selection.canFinish(canSendSelection(), requests.hasOutstanding(),
					capeContent::hasMetadata);
		}
	}

	void applySelection() {
		refreshSelection();
		var decision = selection.finish(requests, canSendSelection(), capeContent::hasMetadata);
		decision.request().ifPresent(actions.send());
		if (decision.close()) {
			onClose();
		} else {
			refreshSelection();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// 优先关闭，防止 Inventory 被重绑定到 Enter/Space 时误触应用。
		if (event.isEscape() || actions.inventoryKey().test(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		selection.cancel();
		actions.close().run();
	}

	@Override
	public void removed() {
		selection.cancel();
		super.removed();
	}

	public WardrobeSelectionSession selectionSession() {
		return selection;
	}

	WardrobeLayout layout() {
		return layout;
	}

	CapeWardrobeContent capeContent() {
		return capeContent;
	}

	WardrobePreviewRotation previewRotation() {
		return previewRotation;
	}

	SelectedTab selectedTab() {
		return selectedTab;
	}

	WardrobeStatusText statusText() {
		return WardrobeStatusText.create(selection, playerFashions.state(), canSendSelection(),
				requests.hasOutstanding(), capeContent::hasMetadata, capeContent.state(), capeContent.status());
	}

	@Override
	public Component getNarrationMessage() {
		return Component.literal("衣柜，披风。" + statusText().narration());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		if (layout == null || !layout.fitsScreen()) {
			return;
		}
		// Frame 与 selected seam 由同一几何入口计算并绘制。
		WardrobeGuiPainter.frameWithSelectedCapeTab(graphics::fill, layout);
		WardrobeGuiPainter.capeIcon(graphics::fill, layout.tabIconBounds());
		for (int index = 0; index < WardrobeCapeCatalog.PAGE_SIZE; index++) {
			WardrobeGuiPainter.slot(graphics::fill, layout.entryBounds(index));
		}
		extractPlayerPreview(graphics, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (layout == null || !layout.fitsScreen()) {
			String message = WardrobeStatusText.fit(TOO_SMALL, Math.max(1, width - 16), font::width);
			graphics.text(font, message, Math.max(0, (width - font.width(message)) / 2),
					Math.max(0, (height - font.lineHeight) / 2), WardrobeGuiPainter.TEXT_COLOR, false);
			return;
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, title, layout.titleX(), layout.titleY(), WardrobeGuiPainter.TEXT_COLOR, false);
		if (capeContent.paginationVisible()) {
			String page = capeContent.pageNumber() + " / " + capeContent.pageCount();
			graphics.text(font, page, layout.paginationBounds().centerX() - font.width(page) / 2,
					layout.pageLabelY(), WardrobeGuiPainter.TEXT_COLOR, false);
		}
		var bounds = layout.statusBounds();
		var status = statusText();
		var display = status.clip(bounds.width(), font::width);
		graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
		graphics.text(font, display.firstLine(), bounds.x(), bounds.y(), WardrobeGuiPainter.TEXT_COLOR, false);
		graphics.text(font, display.secondLine(), bounds.x(), bounds.y() + 10, WardrobeGuiPainter.TEXT_COLOR, false);
		graphics.disableScissor();
		if (bounds.contains(mouseX, mouseY)) {
			List<Component> lines = status.fullText().stream().map(Component::literal)
					.map(component -> (Component) component).toList();
			graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}

	WardrobePreviewAppearance previewAppearance() {
		return previewAppearanceResolver.resolve(selection.previewSelection());
	}

	private void extractPlayerPreview(GuiGraphicsExtractor graphics, int mouseY) {
		var bounds = layout.previewBounds();
		if (minecraft == null || minecraft.player == null) {
			extractPreviewMessage(graphics, bounds, PREVIEW_UNAVAILABLE);
			return;
		}
		if (!selection.authorityKnown()) {
			extractPreviewMessage(graphics, bounds, "时装状态正在同步");
			return;
		}
		var appearance = previewAppearance();
		if (!previewRenderer.extract(graphics, bounds, layout.previewEntitySize(),
				PLAYER_PREVIEW_OFFSET_Y, mouseY, previewRotation.yawDegrees(), minecraft.player, appearance)) {
			extractPreviewMessage(graphics, bounds, PREVIEW_UNAVAILABLE);
		}
	}

	private void extractPreviewMessage(GuiGraphicsExtractor graphics, WardrobeLayout.Bounds bounds, String message) {
		String fitted = WardrobeStatusText.fit(message, bounds.width(), font::width);
		graphics.text(font, fitted, bounds.centerX() - font.width(fitted) / 2,
				bounds.centerY() - font.lineHeight / 2, WardrobeGuiPainter.TEXT_COLOR, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (layout != null && layout.fitsScreen() && previewRotation.beginDrag(
				event.x(), event.y(), event.button(), layout.previewBounds())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (layout != null && layout.fitsScreen() && previewRotation.drag(event.button(), deltaX)) {
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

	enum SelectedTab { CAPE }

	record Actions(BooleanSupplier channelSupported, Predicate<KeyEvent> inventoryKey,
			Consumer<SetCapeSelectionPayload> send, Runnable close) {
		Actions {
			Objects.requireNonNull(channelSupported);
			Objects.requireNonNull(inventoryKey);
			Objects.requireNonNull(send);
			Objects.requireNonNull(close);
		}
	}

	/** 唯一已选类别仍使用 Vanilla 的焦点、Tooltip 和旁白生命周期。 */
	private static final class CapeTabWidget extends AbstractWidget {
		CapeTabWidget(WardrobeLayout.Bounds bounds) {
			super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), CAPE_TAB);
			setTooltip(Tooltip.create(CAPE_TAB));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			// 像素几何已与主框一并绘制；没有第二套接缝或按钮底图。
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, "披风，已选择");
		}
	}
}
