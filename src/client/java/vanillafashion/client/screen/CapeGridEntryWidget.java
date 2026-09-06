package vanillafashion.client.screen;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class CapeGridEntryWidget extends AbstractButton {
	static final int TEXTURE_WIDTH = 64;
	static final int TEXTURE_HEIGHT = 32;
	static final int CAPE_NORTH_X = 1;
	static final int CAPE_NORTH_Y = 1;
	static final int CAPE_NORTH_WIDTH = 10;
	static final int CAPE_NORTH_HEIGHT = 16;

	private static final Component LOADING_TEXT = Component.literal("加载中");
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int INACTIVE_TEXT_COLOR = 0xFFA0A0A0;
	private static final int SELECTED_OUTLINE_COLOR = 0xFFFFFFFF;

	private final Font font;
	private final WardrobeCapeCatalog.Entry entry;
	private final Supplier<Optional<Identifier>> textureLookup;
	private final BooleanSupplier selected;
	private final Runnable onSelect;
	private Optional<Identifier> texture = Optional.empty();

	CapeGridEntryWidget(
			int x,
			int y,
			int width,
			int height,
			Font font,
			WardrobeCapeCatalog.Entry entry,
			Supplier<Optional<Identifier>> textureLookup,
			BooleanSupplier selected,
			Runnable onSelect
	) {
		super(x, y, width, height, Component.literal(entry.displayName()));
		this.font = Objects.requireNonNull(font, "衣柜条目字体不能为 null。");
		this.entry = Objects.requireNonNull(entry, "衣柜条目不能为 null。");
		this.textureLookup = Objects.requireNonNull(textureLookup, "Cape 纹理查询不能为 null。");
		this.selected = Objects.requireNonNull(selected, "衣柜条目选中状态不能为 null。");
		this.onSelect = Objects.requireNonNull(onSelect, "衣柜条目选择动作不能为 null。");
		setOverrideRenderHighlightedSprite(() -> this.selected.getAsBoolean());
		setTooltip(createTooltip(entry));
		refreshAvailability();
	}

	void refreshAvailability() {
		texture = entry.isVanilla()
				? Optional.empty()
				: Objects.requireNonNull(textureLookup.get(), "Cape 纹理查询结果不能为 null。");
		active = entry.isVanilla() || texture.isPresent();
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onSelect.run();
	}

	@Override
	protected void extractContents(
			GuiGraphicsExtractor graphics,
			int mouseX,
			int mouseY,
			float partialTick
	) {
		if (entry.isVanilla()) {
			graphics.centeredText(
					font,
					getMessage(),
					getX() + getWidth() / 2,
					getY() + (getHeight() - font.lineHeight) / 2,
					TEXT_COLOR
			);
		} else {
			extractCapeContents(graphics);
		}

		if (selected.getAsBoolean()) {
			graphics.outline(getX(), getY(), getWidth(), getHeight(), SELECTED_OUTLINE_COLOR);
		}
	}

	private void extractCapeContents(GuiGraphicsExtractor graphics) {
		int labelY = getBottom() - font.lineHeight - 3;
		int thumbnailTop = getY() + 4;
		int availableThumbnailHeight = Math.max(1, labelY - thumbnailTop - 3);
		int thumbnailHeight = Math.min(32, availableThumbnailHeight);
		int thumbnailWidth = Math.max(1, thumbnailHeight * CAPE_NORTH_WIDTH / CAPE_NORTH_HEIGHT);
		int thumbnailX = getX() + (getWidth() - thumbnailWidth) / 2;

		if (texture.isPresent()) {
			graphics.blit(
					RenderPipelines.GUI_TEXTURED,
					texture.orElseThrow(),
					thumbnailX,
					thumbnailTop,
					CAPE_NORTH_X,
					CAPE_NORTH_Y,
					thumbnailWidth,
					thumbnailHeight,
					CAPE_NORTH_WIDTH,
					CAPE_NORTH_HEIGHT,
					TEXTURE_WIDTH,
					TEXTURE_HEIGHT
			);
		} else {
			graphics.centeredText(
					font,
					LOADING_TEXT,
					getX() + getWidth() / 2,
					thumbnailTop + (thumbnailHeight - font.lineHeight) / 2,
					INACTIVE_TEXT_COLOR
			);
		}

		graphics.centeredText(
				font,
				fitLabel(entry.displayName()),
				getX() + getWidth() / 2,
				labelY,
				active ? TEXT_COLOR : INACTIVE_TEXT_COLOR
		);
	}

	private String fitLabel(String label) {
		int availableWidth = Math.max(1, getWidth() - 6);
		if (font.width(label) <= availableWidth) {
			return label;
		}

		String ellipsis = "…";
		return font.plainSubstrByWidth(label, Math.max(1, availableWidth - font.width(ellipsis))) + ellipsis;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	private static Tooltip createTooltip(WardrobeCapeCatalog.Entry entry) {
		if (entry.isVanilla()) {
			return Tooltip.create(Component.literal("原版"));
		}

		var metadata = entry.metadata().orElseThrow();
		String elytraDescription = metadata.hasElytra() ? "\n包含配套鞘翅纹理" : "";
		return Tooltip.create(Component.literal("Cape ID：\n" + metadata.id().value() + elytraDescription));
	}
}
