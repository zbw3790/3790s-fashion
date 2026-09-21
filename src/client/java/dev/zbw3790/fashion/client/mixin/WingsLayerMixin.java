package dev.zbw3790.fashion.client.mixin;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.zbw3790.fashion.client.render.ElytraTextureDecision;
import dev.zbw3790.fashion.client.render.ElytraTextureOverrideResolver;

@Mixin(WingsLayer.class)
abstract class WingsLayerMixin {
    // 只替换本层局部参数；身体及其他层仍持有真实胸甲副本，原版翼逻辑只执行一次。
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at=@At("HEAD"),argsOnly=true)
    private HumanoidRenderState fashion3790$previewWings(HumanoidRenderState state) {
        return dev.zbw3790.fashion.client.render.WardrobePreviewWings.forLayer(state);
    }

	@Inject(
			method = "getPlayerElytraTexture(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)"
					+ "Lnet/minecraft/resources/Identifier;",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void fashion3790$selectElytraTexture(
			HumanoidRenderState state,
			CallbackInfoReturnable<Identifier> callback
	) {
		if (!(state instanceof AvatarRenderState avatarState)) {
			return;
		}

		ElytraTextureDecision decision = ElytraTextureOverrideResolver.resolve(avatarState);

		switch (decision.mode()) {
			case PASS_THROUGH -> {
				// 不取消注入，继续使用 Vanilla 官方 Elytra/Cape 纹理选择。
			}
			case CUSTOM_TEXTURE -> callback.setReturnValue(decision.texture());
			case VANILLA_DEFAULT -> callback.setReturnValue(null);
		}
	}
}
