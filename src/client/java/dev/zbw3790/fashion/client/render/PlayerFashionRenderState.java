package dev.zbw3790.fashion.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class PlayerFashionRenderState {
	private static final RenderStateDataKey<PlayerFashionRenderAppearance> APPEARANCE_KEY =
			RenderStateDataKey.create(() -> "fashion_3790:player_fashion_appearance");

	private PlayerFashionRenderState() {
	}

	public static void attach(AvatarRenderState state, PlayerFashionRenderAppearance appearance) {
		((FabricRenderState) state).setData(APPEARANCE_KEY, java.util.Objects.requireNonNull(appearance));
	}

	public static PlayerFashionRenderAppearance find(AvatarRenderState state) {
		PlayerFashionRenderAppearance appearance = ((FabricRenderState) state).getData(APPEARANCE_KEY);
		return appearance == null ? PlayerFashionRenderAppearance.unknown() : appearance;
	}
}
