package dev.zbw3790.fashion.client.screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 有界名称行与 Vanilla 按钮；禁用输入不引起 pending 全页闪暗。 */
final class WardrobeArmorButton extends WardrobeOutfitActionButton {
    private final String key;
    private final Font font;
    private final Supplier<Component> label;
    private final Supplier<List<String>> tooltip;
    private final BooleanSupplier enabled, waiting, selected;
    WardrobeArmorButton(WardrobeLayout.Bounds bounds, String key, Font font, Supplier<Component> label,
            Supplier<List<String>> tooltip, BooleanSupplier enabled, BooleanSupplier waiting,
            BooleanSupplier selected, Runnable action) {
        super(bounds, label.get(), action);
        this.key = key;
        this.font = font;
        this.label = label;
        this.tooltip = tooltip;
        this.enabled = enabled;
        this.waiting = waiting;
        this.selected = selected;
        refreshState();
    }
    String key() { return key; }
    List<String> tooltip() { return tooltip.get(); }
    void refreshState() { setMessage(label.get()); refresh(enabled.getAsBoolean(), waiting.getAsBoolean()); }
    @Override public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT, String.join("。",tooltip()));
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        // 背景仍使用 Vanilla 九宫格；文字裁切在控件内，完整值由 Tooltip 提供。
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, backgroundSprite(),
                getX(), getY(), getWidth(), getHeight(), net.minecraft.util.ARGB.white(alpha));
        if (selected.getAsBoolean()) graphics.fill(getX()+2, getY()+2, getX()+getWidth()-2, getY()+3, WardrobeGuiPainter.BRAND_BLUE);
        var lines = getMessage().getString().split("\n", 2);
        int firstY = getY() + (getHeight() - lines.length * font.lineHeight) / 2;
        graphics.enableScissor(getX()+3, getY()+1, getX()+getWidth()-3, getY()+getHeight()-1);
        for (int index = 0; index < lines.length; index++) {
            String fitted = WardrobeStatusText.fit(lines[index], getWidth()-6, font::width);
            graphics.text(font, fitted, getX()+(getWidth()-font.width(fitted))/2, firstY+index*font.lineHeight,
                    active || waiting.getAsBoolean() ? 0xFFFFFFFF : 0xFFA0A0A0, false);
        }
        graphics.disableScissor();
    }
}
