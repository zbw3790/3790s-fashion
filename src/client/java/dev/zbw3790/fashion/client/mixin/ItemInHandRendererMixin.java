package dev.zbw3790.fashion.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.zbw3790.fashion.outfit.OutfitPart;
import dev.zbw3790.fashion.client.render.outfit.OutfitRendering;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @WrapOperation(method = "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"),
            require = 1, expect = 1, allow = 1)
    private void fashion3790$hand0(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector collector,
            int light, Identifier skin, boolean sleeve, Operation<Void> original,
            PoseStack outerPose, SubmitNodeCollector outerCollector, int outerLight, float equipProgress, float swingProgress, HumanoidArm arm) {
        if (arm != HumanoidArm.RIGHT) { original.call(renderer, pose, collector, light, skin, sleeve); return; }
        OutfitRendering.hand(renderer, collector, skin, sleeve, OutfitPart.RIGHT_ARM,
                adapted -> original.call(renderer, pose, adapted, light, skin, sleeve));
    }

    @WrapOperation(method = "renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"),
            require = 1, expect = 1, allow = 1)
    private void fashion3790$hand1(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector collector,
            int light, Identifier skin, boolean sleeve, Operation<Void> original,
            PoseStack outerPose, SubmitNodeCollector outerCollector, int outerLight, float equipProgress, float swingProgress, HumanoidArm arm) {
        if (arm != HumanoidArm.LEFT) { original.call(renderer, pose, collector, light, skin, sleeve); return; }
        OutfitRendering.hand(renderer, collector, skin, sleeve, OutfitPart.LEFT_ARM,
                adapted -> original.call(renderer, pose, adapted, light, skin, sleeve));
    }

    @WrapOperation(method = "renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"),
            require = 1, expect = 1, allow = 1)
    private void fashion3790$hand2(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector collector,
            int light, Identifier skin, boolean sleeve, Operation<Void> original,
            PoseStack outerPose, SubmitNodeCollector outerCollector, int outerLight, HumanoidArm arm) {
        if (arm != HumanoidArm.RIGHT) { original.call(renderer, pose, collector, light, skin, sleeve); return; }
        OutfitRendering.hand(renderer, collector, skin, sleeve, OutfitPart.RIGHT_ARM,
                adapted -> original.call(renderer, pose, adapted, light, skin, sleeve));
    }

    @WrapOperation(method = "renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"),
            require = 1, expect = 1, allow = 1)
    private void fashion3790$hand3(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector collector,
            int light, Identifier skin, boolean sleeve, Operation<Void> original,
            PoseStack outerPose, SubmitNodeCollector outerCollector, int outerLight, HumanoidArm arm) {
        if (arm != HumanoidArm.LEFT) { original.call(renderer, pose, collector, light, skin, sleeve); return; }
        OutfitRendering.hand(renderer, collector, skin, sleeve, OutfitPart.LEFT_ARM,
                adapted -> original.call(renderer, pose, adapted, light, skin, sleeve));
    }

}
