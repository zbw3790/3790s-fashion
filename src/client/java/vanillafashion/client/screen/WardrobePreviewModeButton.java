package vanillafashion.client.screen;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** 仅控制当前 Screen 的预览；输入、焦点、按钮底图和旁白沿用 Vanilla。 */
final class WardrobePreviewModeButton extends AbstractButton {
	private final Supplier<WardrobePreviewMode> mode;
	private final Runnable toggle;

	WardrobePreviewModeButton(WardrobeLayout.Bounds bounds, Supplier<WardrobePreviewMode> mode,
			Runnable toggle) {
		super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty());
		this.mode = Objects.requireNonNull(mode);
		this.toggle = Objects.requireNonNull(toggle);
		refreshDescription();
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (active && visible) {
			toggle.run();
			refreshDescription();
		}
	}

	private void refreshDescription() {
		Component description = Component.literal(mode.get() == WardrobePreviewMode.CAPE
				? "当前预览：披风；点击查看鞘翅" : "当前预览：鞘翅；点击查看披风");
		setMessage(description);
		setTooltip(Tooltip.create(description));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(graphics);
		var icon = new WardrobeLayout.Bounds(getX() + 1, getY() + 1, 16, 16);
		if (mode.get() == WardrobePreviewMode.CAPE) {
			WardrobeGuiPainter.capeIcon(graphics::fill, icon);
		} else {
			WardrobeGuiPainter.elytraIcon(graphics::fill, icon);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
