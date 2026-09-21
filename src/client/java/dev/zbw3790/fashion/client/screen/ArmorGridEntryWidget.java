package dev.zbw3790.fashion.client.screen;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import dev.zbw3790.fashion.armor.ArmorRegistrySnapshot;

/** 与 Outfit 同尺寸的资源格；元数据负责准入，现有纹理所有者负责就绪状态。 */
final class ArmorGridEntryWidget extends AbstractButton {
    private final ArmorRegistrySnapshot.Entry entry;
    private final ArmorWardrobeContent content;
    private final Font font;
    ArmorGridEntryWidget(WardrobeLayout.Bounds bounds,ArmorRegistrySnapshot.Entry entry,ArmorWardrobeContent content,Font font) {
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),Component.literal(entry.name()));
        this.entry=entry;this.content=content;this.font=font;
    }
    String key() { return "armor:"+content.slot().serializedName()+":style:"+entry.id().value(); }
    List<String> tooltip() { return content.tooltip(entry); }
    boolean canActivate() { return content.canEdit() && content.source().admitted(content.slot(),entry.id()); }
    @Override public void onPress(InputWithModifiers input) { if(canActivate()) content.choose(entry.id()); }
    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float tick) {
        var bounds=new WardrobeLayout.Bounds(getX(),getY(),getWidth(),getHeight());
        WardrobeGuiPainter.slot(graphics::fill,bounds);
        var texture=content.source().texture(content.slot(),entry.id());
        texture.ifPresent(id -> {
            for(var face:ArmorThumbnail.plan(content.slot())) graphics.blit(RenderPipelines.GUI_TEXTURED,id,
                    getX()+1+face.x(),getY()+2+face.y(),face.u()+(face.mirror()?face.width():0),face.v(),
                    face.width()*face.scale(),face.height()*face.scale(),face.mirror()?-face.width():face.width(),face.height(),64,32);
        });
        // 等待确认只锁定编辑，不覆盖黑色遮罩；资源未就绪保留可聚焦的明确占位。
        WardrobeGuiPainter.slotOverlay(graphics::fill,bounds,isHoveredOrFocused(),content.selected(entry),false);
        if(content.selected(entry)) graphics.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+3,WardrobeGuiPainter.BRAND_BLUE);
        if(texture.isEmpty()) {
            String marker=content.source().availability(content.slot(),entry.id())==WardrobeArmorSource.Availability.FAILED?"!":"…";
            graphics.text(font,marker,getX()+(getWidth()-font.width(marker))/2,getY()+9,0xffeeeeee,false);
        }
        graphics.fill(getX()+2,getY()+23,getX()+getWidth()-2,getY()+getHeight()-2,0xb0202020);
        String label=WardrobeStatusText.fit(entry.name(),getWidth()-4,font::width);
        graphics.text(font,label,getX()+(getWidth()-font.width(label))/2,getY()+24,0xffffffff,false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
        output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT,String.join("。",tooltip()));
    }
}
