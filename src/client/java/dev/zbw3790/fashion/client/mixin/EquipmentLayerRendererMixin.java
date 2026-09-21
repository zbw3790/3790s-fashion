package dev.zbw3790.fashion.client.mixin;

import java.util.function.Function;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.model.Model;
import net.minecraft.resources.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import dev.zbw3790.fashion.client.render.armor.*;

/** 仅改变局部已预检调用的基础纹理；第二个 Function.apply 的饰纹查找不受影响。 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin implements ArmorEquipmentAccess {
    @Shadow @Final private EquipmentAssetManager equipmentAssets;
    @Override public EquipmentClientInfo fashion3790$equipment(ResourceKey<EquipmentAsset> asset) {return equipmentAssets.get(asset);}
    @Redirect(method="renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at=@At(value="INVOKE",target="Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",ordinal=0))
    private Object fashion3790$baseTexture(Function<Object,Object> lookup,Object key,EquipmentClientInfo.LayerType type,
            ResourceKey<EquipmentAsset> asset,Model<?> model,Object state,ItemStack stack,PoseStack pose,SubmitNodeCollector collector,
            int light,Identifier override,int outline,int order) {
        Object original=lookup.apply(key);
        if(!(state instanceof AvatarRenderState) || override==null || !override.getNamespace().equals("fashion_3790") || !override.getPath().startsWith("armor_asset/")) return original;
        var info=equipmentAssets.get(asset);
        if(!ArmorLayerPolicy.supported(info,type)) return original;
        return original.equals(info.getLayers(type).getFirst().getTextureLocation(type))?override:original;
    }
}
