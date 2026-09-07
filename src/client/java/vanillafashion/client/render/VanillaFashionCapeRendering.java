package vanillafashion.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityTypes;
import org.slf4j.Logger;

public final class VanillaFashionCapeRendering {
	private VanillaFashionCapeRendering() {
	}

	public static void register(Logger logger) {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType != EntityTypes.PLAYER || !(renderer instanceof AvatarRenderer<?> avatarRenderer)) {
				return;
			}

			WardrobePreviewEquipment.bind(context.getEquipmentAssets());
			helper.register(new VanillaFashionCapeLayer(
					avatarRenderer,
					context.getModelSet(),
					context.getEquipmentAssets()
			));
			logger.info("Vanilla Fashion Cape Render Layer 已附加到玩家 Renderer。");
		});

		LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(
				PlayerFashionRenderDecisions::allowVanillaCape
		);

		logger.info("Vanilla Fashion Cape 渲染回调已注册，优先读取衣柜预览，其次读取逐玩家世界外观。");
	}
}
