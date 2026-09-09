package vanillafashion.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vanillafashion.client.render.outfit.OutfitSpectatorSubmission;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            require = 1, expect = 1, allow = 1)
    private void vanillaFashion$spectator(SubmitNodeCollector collector, Model<?> model, Object state,
            PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite,
            int outline, CrumblingOverlay crumbling, Operation<Void> original,
            LivingEntityRenderState outerState, PoseStack outerPose, SubmitNodeCollector outerCollector, CameraRenderState camera) {
        OutfitSpectatorSubmission.submit(this, collector, model, state, pose, type, light, overlay, color,
                sprite, outline, crumbling, original, outerState);
    }
}
