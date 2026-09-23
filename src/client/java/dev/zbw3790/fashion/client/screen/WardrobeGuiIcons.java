package dev.zbw3790.fashion.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** 用户提供的四份人工 PNG；五处用途共享资源，不生成或修改图像。 */
final class WardrobeGuiIcons {
    static final Identifier CAPE = resource("cape-tag-16.png");
    static final Identifier OUTFIT = resource("outfit-tag-16.png");
    static final Identifier ARMOR = resource("armor-tag-16.png");
    static final Identifier ELYTRA = resource("elytra-switch-button-16.png");

    private WardrobeGuiIcons() { }

    static Identifier tabTexture(WardrobeScreen.SelectedTab tab) {
        return switch (tab) {
            case CAPE -> CAPE;
            case OUTFIT -> OUTFIT;
            case ARMOR -> ARMOR;
        };
    }

    static Identifier previewTexture(WardrobePreviewMode mode) {
        // 保持按钮显示当前模式；披风态与顶部 Cape 页签引用同一 Identifier。
        return switch (mode) {
            case CAPE -> CAPE;
            case ELYTRA -> ELYTRA;
        };
    }

    static void drawTabs(GuiGraphicsExtractor graphics, WardrobeLayout layout) {
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            draw(graphics, tabTexture(tab), layout.tabIconBounds(tab.ordinal()));
        }
    }

    static void draw(GuiGraphicsExtractor graphics, Identifier texture, WardrobeLayout.Bounds bounds) {
        if (bounds.width() != 16 || bounds.height() != 16) {
            throw new IllegalArgumentException("手绘图标必须使用完整十六乘十六画布。");
        }
        // Vanilla 默认白色纹理提交；完整 UV、等比原尺寸，不设置 tint、裁剪或变换。
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, bounds.x(), bounds.y(),
                0, 0, 16, 16, 16, 16, 16, 16);
    }

    private static Identifier resource(String filename) {
        return Identifier.fromNamespaceAndPath("fashion_3790", "textures/gui/icons/" + filename);
    }
}
