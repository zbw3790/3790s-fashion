package dev.zbw3790.fashion.client.screen;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

final class CapeGridEntryWidget extends AbstractButton {
	static final int TEXTURE_WIDTH = 64;
	static final int TEXTURE_HEIGHT = 32;
	static final int CAPE_NORTH_X = 1;
	static final int CAPE_NORTH_Y = 1;
	static final int CAPE_NORTH_WIDTH = 10;
	static final int CAPE_NORTH_HEIGHT = 16;

	private final Supplier<CapeWardrobeContent.SlotModel> modelLookup;
	private final BooleanSupplier selected;
	private final BooleanSupplier editable;
	private final BooleanSupplier awaitingConfirmation;
	private final Runnable onSelect;
	private CapeWardrobeContent.SlotModel model;
	private String tooltipText;
	private boolean dimmed;
    private boolean screenTooltip;
    void useScreenTooltip() { screenTooltip=true;setTooltip(null); }
    String tooltipText() { return tooltipText; }

	CapeGridEntryWidget(WardrobeLayout.Bounds bounds,
			Supplier<CapeWardrobeContent.SlotModel> modelLookup,
			BooleanSupplier selected, BooleanSupplier editable,
			BooleanSupplier awaitingConfirmation, Runnable onSelect) {
		super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty());
		this.modelLookup = Objects.requireNonNull(modelLookup);
		this.selected = Objects.requireNonNull(selected);
		this.editable = Objects.requireNonNull(editable);
		this.awaitingConfirmation = Objects.requireNonNull(awaitingConfirmation);
		this.onSelect = Objects.requireNonNull(onSelect);
		refreshAvailability();
	}

	void refreshAvailability() {
		model = Objects.requireNonNull(modelLookup.get());
		active = model.selectable() && editable.getAsBoolean();
		// 与 active 同步刷新，避免 ACK 已到而控件尚未 tick 时短暂套回禁用遮罩。
		dimmed = !active && !(model.selectable() && awaitingConfirmation.getAsBoolean());
		String currentTooltip = model.tooltipText();
		if (!currentTooltip.equals(tooltipText)) {
			tooltipText = currentTooltip;
			setMessage(Component.literal(currentTooltip));
			if (!screenTooltip) setTooltip(Tooltip.create(Component.literal(currentTooltip)));
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (active && visible) {
			onSelect.run();
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		var bounds = new WardrobeLayout.Bounds(getX(), getY(), getWidth(), getHeight());
		WardrobeGuiPainter.slot(graphics::fill, bounds);
		if (model.visual() == CapeWardrobeContent.Visual.ORIGINAL_ICON) {
			WardrobeGuiPainter.originalIcon(graphics::fill,
					new WardrobeLayout.Bounds(getX() + 3, getY() + 9, 16, 16));
		} else {
			model.texture().ifPresent(texture -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
					getX() + 1, getY() + 1, CAPE_NORTH_X, CAPE_NORTH_Y, 20, 32,
					CAPE_NORTH_WIDTH, CAPE_NORTH_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT));
		}
		extractOverlay(graphics::fill);
	}

	void extractOverlay(WardrobeGuiPainter.RectangleSink sink) {
		var bounds = new WardrobeLayout.Bounds(getX(), getY(), getWidth(), getHeight());
		WardrobeGuiPainter.slotOverlay(sink, bounds,
				active && isHoveredOrFocused(), selected.getAsBoolean(), dimmed);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
