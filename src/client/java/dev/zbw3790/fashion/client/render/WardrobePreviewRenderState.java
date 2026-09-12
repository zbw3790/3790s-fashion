package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class WardrobePreviewRenderState {
	private static final RenderStateDataKey<WardrobePreviewAppearance> APPEARANCE_KEY =
			RenderStateDataKey.create(() -> "fashion_3790:wardrobe_preview_appearance");

	private WardrobePreviewRenderState() {
	}

	public static void attach(AvatarRenderState state, WardrobePreviewAppearance appearance) {
		Objects.requireNonNull(state, "玩家 RenderState 不能为空。");
		Objects.requireNonNull(appearance, "衣柜预览外观不能为空。");
		((FabricRenderState) state).setData(APPEARANCE_KEY, appearance);
	}

	public static Optional<WardrobePreviewAppearance> find(AvatarRenderState state) {
		Objects.requireNonNull(state, "玩家 RenderState 不能为空。");
		return Optional.ofNullable(((FabricRenderState) state).getData(APPEARANCE_KEY));
	}
}
