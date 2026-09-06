package vanillafashion.client.mixin;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vanillafashion.client.render.ElytraTextureDecision;
import vanillafashion.client.render.ElytraTextureOverrideResolver;

@Mixin(WingsLayer.class)
abstract class WingsLayerMixin {
	@Inject(
			method = "getPlayerElytraTexture(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)"
					+ "Lnet/minecraft/resources/Identifier;",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void vanillaFashion$selectElytraTexture(
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
