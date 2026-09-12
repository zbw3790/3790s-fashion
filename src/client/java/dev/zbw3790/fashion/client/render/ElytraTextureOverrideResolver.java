package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class ElytraTextureOverrideResolver {
	private ElytraTextureOverrideResolver() {
	}

	public static ElytraTextureDecision resolve(AvatarRenderState state) {
		Objects.requireNonNull(state, "玩家渲染状态不能为空。");
		return PlayerFashionRenderDecisions.elytraTexture(state);
	}
}
