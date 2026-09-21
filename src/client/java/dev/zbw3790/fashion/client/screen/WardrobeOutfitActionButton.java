package dev.zbw3790.fashion.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** 固定清除动作等待 ACK 时保留亮度，输入仍使用真正的禁用状态。 */
class WardrobeOutfitActionButton extends Button {
    private static final WidgetSprites SPRITES=new WidgetSprites(
            Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"),
            Identifier.withDefaultNamespace("widget/button_highlighted"));
    private boolean pendingAppearance;

    WardrobeOutfitActionButton(WardrobeLayout.Bounds bounds,String label,Runnable action) {
        this(bounds,Component.literal(label),action);
    }

    WardrobeOutfitActionButton(WardrobeLayout.Bounds bounds,Component label,Runnable action) {
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),label,
                button -> action.run(),DEFAULT_NARRATION);
    }

    void refresh(boolean canActivate,boolean waitingWithTrustedAuthority) {
        active=canActivate;
        pendingAppearance=waitingWithTrustedAuthority;
    }

    Identifier backgroundSprite() {
        return SPRITES.get(active || pendingAppearance,isHoveredOrFocused());
    }

    @Override public Component getMessage() {
        return pendingAppearance?message:super.getMessage();
    }

    @Override public void onPress(InputWithModifiers input) {
        if (active && visible) super.onPress(input);
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        // 复用 Vanilla sprite、九宫格缩放和文字布局，不为绘制临时改写 active。
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,backgroundSprite(),getX(),getY(),getWidth(),getHeight(),ARGB.white(alpha));
        extractDefaultLabel(graphics.textRendererForWidget(this,GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}
