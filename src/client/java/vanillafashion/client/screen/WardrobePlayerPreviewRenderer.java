package vanillafashion.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vanillafashion.client.render.WardrobePreviewAppearance;
import vanillafashion.client.render.WardrobePreviewRenderState;

final class WardrobePlayerPreviewRenderer {
	private static final float RENDER_STATE_PARTIAL_TICK = 1.0F;
	private static final float VANILLA_FRONT_BODY_ROTATION_DEGREES = 180.0F;
	private static final float MOUSE_ANGLE_DIVISOR = 40.0F;
	private static final float MOUSE_ANGLE_SCALE_DEGREES = 20.0F;
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0D);

	boolean extract(
			GuiGraphicsExtractor graphics,
			WardrobeLayout.Bounds previewBounds,
			int entitySize,
			float offsetY,
			int mouseY,
			float previewYawDegrees,
			LocalPlayer player,
			WardrobePreviewAppearance appearance
	) {
		EntityRenderer<? super LocalPlayer, ?> renderer = Minecraft.getInstance()
				.getEntityRenderDispatcher()
				.getRenderer(player);
		EntityRenderState extractedState = renderer.createRenderState(player, RENDER_STATE_PARTIAL_TICK);

		if (!(extractedState instanceof AvatarRenderState state)) {
			return false;
		}

		state.shadowPieces.clear();
		state.outlineColor = 0;
		WardrobePreviewRenderState.attach(state, appearance);

		float centerY = (previewBounds.y() + previewBounds.bottom()) / 2.0F;
		float verticalMouseAngle = (float) Math.atan((centerY - mouseY) / MOUSE_ANGLE_DIVISOR);
		Quaternionf modelRotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf cameraOrientation = new Quaternionf().rotateX(
				verticalMouseAngle * MOUSE_ANGLE_SCALE_DEGREES * DEGREES_TO_RADIANS
		);
		modelRotation.mul(cameraOrientation);

		// 与 Minecraft 26.2 InventoryScreen 的 GUI 预览变换一致，只把水平朝向交给界面拖动状态。
		state.bodyRot = VANILLA_FRONT_BODY_ROTATION_DEGREES - previewYawDegrees;
		state.yRot = 0.0F;
		state.xRot = state.pose == Pose.FALL_FLYING
				? 0.0F
				: -verticalMouseAngle * MOUSE_ANGLE_SCALE_DEGREES;
		state.boundingBoxWidth /= state.scale;
		state.boundingBoxHeight /= state.scale;
		state.scale = 1.0F;

		Vector3f translation = new Vector3f(
				0.0F,
				state.boundingBoxHeight / 2.0F + offsetY,
				0.0F
		);
		graphics.entity(
				state,
				entitySize,
				translation,
				modelRotation,
				cameraOrientation,
				previewBounds.x() + 1,
				previewBounds.y() + 1,
				previewBounds.right() - 1,
				previewBounds.bottom() - 1
		);
		return true;
	}
}
