package dev.zbw3790.fashion.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.model.Model;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.zbw3790.fashion.client.render.armor.ArmorRendering;

/** 在盔甲专有逐槽入口统一抑制基础、染色、饰纹、光效与轮廓子提交。 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void fashion3790$armorVisibility(PoseStack pose, SubmitNodeCollector collector,
            ItemStack equipment, EquipmentSlot slot, int light, HumanoidRenderState state, CallbackInfo callback) {
        if (ArmorRendering.hidden(state, slot)) callback.cancel();
    }
    /** 保留原模型和所有效果调用，只携带已验证的局部基础纹理参数。 */
    @Redirect(method="renderArmorPiece",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private void fashion3790$armorTexture(EquipmentLayerRenderer renderer,EquipmentClientInfo.LayerType type,ResourceKey<EquipmentAsset> asset,
            Model<Object> model,Object state,ItemStack stack,PoseStack pose,SubmitNodeCollector collector,int light,int outline,
            PoseStack outerPose,SubmitNodeCollector outerCollector,ItemStack outerEquipment,EquipmentSlot slot,int outerLight,HumanoidRenderState outerState) {
        var texture=ArmorRendering.replacement(renderer,outerState,slot,type,asset);
        if(texture==null) renderer.renderLayers(type,asset,model,state,stack,pose,collector,light,outline);
        else renderer.renderLayers(type,asset,model,state,stack,pose,collector,light,texture,outline,1);
    }
}
