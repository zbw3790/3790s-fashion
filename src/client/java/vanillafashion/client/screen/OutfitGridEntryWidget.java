package vanillafashion.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import vanillafashion.outfit.OutfitId;

/** 真实条目始终可聚焦；是否能编辑在激活时重新检查。 */
final class OutfitGridEntryWidget extends AbstractButton {
    private final OutfitId id;
    private final OutfitWardrobeContent content;
    OutfitGridEntryWidget(WardrobeLayout.Bounds bounds,OutfitId id,OutfitWardrobeContent content) {
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),Component.literal(id.value()));
        this.id=id;this.content=content;refresh();
    }
    OutfitId id() { return id; }
    boolean canActivate() { return visible && content.canActivate(id); }
    void refresh() { active=true;setMessage(Component.literal(String.join("；",content.tooltip(id)))); }
    @Override public void onPress(InputWithModifiers input) { if (canActivate()) content.select(id); }
    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        var bounds=new WardrobeLayout.Bounds(getX(),getY(),getWidth(),getHeight());
        WardrobeGuiPainter.slot(graphics::fill,bounds);
        content.source.find(id).ifPresent(entry -> {
            if (content.source.model()==null) return;
            var plan=thumbnail();
            content.source.texture(id).ifPresent(texture -> {
                for (var face:plan.faces()) graphics.blit(RenderPipelines.GUI_TEXTURED,texture,getX()+1+face.x(),getY()+1+face.y(),
                        face.u(),face.v(),face.width(),face.height(),face.width(),face.height(),64,64);
            });

        });
        WardrobeGuiPainter.slotOverlay(graphics::fill,bounds,isHoveredOrFocused(),content.selected(id),dimmed());
        if (content.partialUse(id)) graphics.fill(getX()+2,getY()+2,getX()+5,getY()+5,0xffdfbf70);
        if (content.source.texture(id).isEmpty()) {
            int x=getX()+9,y=getY()+14;
            graphics.fill(x,y,x+2,y+2,0xffdedede);graphics.fill(x+4,y,x+6,y+2,0xffdedede);
        }
    }
    OutfitThumbnail.Plan thumbnail() {
        return OutfitThumbnail.plan(content.source.find(id).map(entry -> entry.parts()).orElse(java.util.Set.of()),
                content.source.model());
    }
    boolean dimmed() { return content.source.availability(id)!=vanillafashion.client.outfit.ClientOutfitTextureResolver.Availability.READY; }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
