package dev.zbw3790.fashion.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.zbw3790.fashion.client.render.WardrobePreviewAppearance;
import dev.zbw3790.fashion.client.render.WardrobePreviewEquipment;
import dev.zbw3790.fashion.client.render.WardrobePreviewRenderState;

final class WardrobePlayerPreviewRenderer {
	private static final float RENDER_STATE_PARTIAL_TICK = 1.0F;
	private static final float VANILLA_FRONT_BODY_ROTATION_DEGREES = 180.0F;

	boolean extract(
			GuiGraphicsExtractor graphics,
			WardrobeLayout.Bounds modelBounds,
			int entitySize,
			float offsetY,
			int mouseY,
			float previewYawDegrees,
			LocalPlayer player,
			WardrobePreviewAppearance appearance,
			WardrobePreviewMode mode,
            java.util.Optional<WardrobePreviewDraft> draft,
            WardrobeOutfitSource outfits
	) {
		var equipmentAssets = WardrobePreviewEquipment.assets();
		if (equipmentAssets.isEmpty()) {
			return false;
		}
		EntityRenderer<? super LocalPlayer, ?> renderer = Minecraft.getInstance()
				.getEntityRenderDispatcher()
				.getRenderer(player);
		// 26.2 AvatarRenderer 的无参工厂每次 new；实体装备提取也使用副本。
		EntityRenderState extractedState = renderer.createRenderState(player, RENDER_STATE_PARTIAL_TICK);

		if (!(extractedState instanceof AvatarRenderState state)) {
			return false;
		}

		float centerY = (modelBounds.y() + modelBounds.bottom()) / 2.0F;
		var configuration = WardrobePreviewConfiguration.fromMouse(mode, centerY, mouseY);
		configuration.apply(state, equipment -> WardrobePreviewEquipment.hasWings(
				equipment, equipmentAssets.orElseThrow()));
		state.shadowPieces.clear();
		state.outlineColor = 0;
		WardrobePreviewRenderState.attach(state, appearance);
        dev.zbw3790.fashion.client.render.armor.ArmorRendering.attach(state, draft.map(WardrobePreviewDraft::armor).orElse(dev.zbw3790.fashion.armor.ArmorSelections.original()));

		Quaternionf cameraOrientation = configuration.cameraOrientation();
		Quaternionf modelRotation = new Quaternionf().rotateZ((float) Math.PI).mul(cameraOrientation);

		// 水平拖动与默认背面保持原行为，两处垂直变换共用同一个受限角度。
		state.bodyRot = VANILLA_FRONT_BODY_ROTATION_DEGREES - previewYawDegrees;
		state.yRot = 0.0F;
		if (mode == WardrobePreviewMode.ELYTRA) {
			var standingDimensions = player.getDimensions(Pose.STANDING);
			state.boundingBoxWidth = standingDimensions.width();
			state.boundingBoxHeight = standingDimensions.height();
		}
		state.boundingBoxWidth /= state.scale;
		state.boundingBoxHeight /= state.scale;
		state.scale = 1.0F;

		Vector3f translation = new Vector3f(
				0.0F,
				state.boundingBoxHeight / 2.0F + offsetY,
				0.0F
		);
		// GUI record 持有本次状态与新建变换；提交后不再修改或复用。
		if (draft.isPresent()) dev.zbw3790.fashion.client.render.outfit.OutfitRendering.preparePreview(state,player.getUUID(),
                draft.orElseThrow().provider(outfits.connection(),player.getUUID(),outfits.textures));
        else dev.zbw3790.fashion.client.render.outfit.OutfitRendering.preparePreview(state,player.getUUID());
        dev.zbw3790.fashion.client.render.WardrobePreviewWings.attach(state, mode == WardrobePreviewMode.ELYTRA);
		graphics.entity(
				state,
				entitySize,
				translation,
				modelRotation,
				cameraOrientation,
				modelBounds.x(),
				modelBounds.y(),
				modelBounds.right(),
				modelBounds.bottom()
		);
		return true;
	}
}
