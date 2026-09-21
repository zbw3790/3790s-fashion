package dev.zbw3790.fashion.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 只供本次衣柜 WingsLayer 消费；不覆盖身体的装备，不宣称真实 BODY 已装备。 */
public final class WardrobePreviewWings {
    private static final RenderStateDataKey<AvatarRenderState> KEY=RenderStateDataKey.create(()->"fashion_3790:wardrobe_try_on_wings");
    private WardrobePreviewWings() { }
    /** 身体配置完成后一次提取；发布后的两个状态不再修改或复用。 */
    public static void attach(AvatarRenderState body, boolean enabled) {
        var target=(FabricRenderState)body;
        if(!enabled){target.setData(KEY,null);return;}
        var appearance=WardrobePreviewRenderState.find(body).orElseThrow();
        var wings=new AvatarRenderState();
        // 26.2 WingsLayer／ElytraModel 只消费以下输入及纹理决策；不伪造实体 id。
        wings.chestEquipment=new ItemStack(Items.ELYTRA);
        wings.skin=body.skin;wings.showCape=body.showCape;wings.isBaby=body.isBaby;
        wings.isCrouching=body.isCrouching;wings.elytraRotX=body.elytraRotX;
        wings.elytraRotY=body.elytraRotY;wings.elytraRotZ=body.elytraRotZ;
        wings.outlineColor=body.outlineColor;
        WardrobePreviewRenderState.attach(wings,appearance);
        target.setData(KEY,wings);
    }
    public static boolean active(AvatarRenderState body) {return ((FabricRenderState)body).getData(KEY)!=null;}
    public static HumanoidRenderState forLayer(HumanoidRenderState body) {
        if(!(body instanceof AvatarRenderState avatar))return body;
        var wings=((FabricRenderState)avatar).getData(KEY);
        return wings==null?body:wings;
    }
}
