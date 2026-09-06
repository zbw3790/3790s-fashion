package vanillafashion.client.render;

import java.util.Optional;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** 绘制阶段仅消费两个独立的不可变外观；优先级为 Preview、World、Vanilla。 */
public final class PlayerFashionRenderDecisions {
	private PlayerFashionRenderDecisions() {
	}

	public static boolean allowVanillaCape(AvatarRenderState state) {
		return WardrobePreviewRenderDecisions.allowVanillaCape(
				WardrobePreviewRenderState.find(state), !PlayerFashionRenderState.find(state).suppressesVanillaCape());
	}

	public static Optional<Identifier> capeTexture(AvatarRenderState state) {
		return WardrobePreviewRenderDecisions.capeTexture(
				WardrobePreviewRenderState.find(state), () -> PlayerFashionRenderState.find(state).capeTexture());
	}

	public static ElytraTextureDecision elytraTexture(AvatarRenderState state) {
		return WardrobePreviewRenderDecisions.elytraTexture(
				WardrobePreviewRenderState.find(state), () -> PlayerFashionRenderState.find(state).elytraDecision());
	}
}
