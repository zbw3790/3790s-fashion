package vanillafashion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class VanillaFashionCapeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final PlayerCapeModel model;
	private final EquipmentAssetManager equipmentAssets;

	public VanillaFashionCapeLayer(
			RenderLayerParent<AvatarRenderState, PlayerModel> renderer,
			EntityModelSet modelSet,
			EquipmentAssetManager equipmentAssets
	) {
		super(renderer);
		this.model = new PlayerCapeModel(modelSet.bakeLayer(ModelLayers.PLAYER_CAPE));
		this.equipmentAssets = equipmentAssets;
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int packedLight,
			AvatarRenderState state,
			float yRot,
			float xRot
	) {
		boolean shouldTakeOverCape = !PlayerFashionRenderDecisions.allowVanillaCape(state)
				&& !state.isInvisible && state.showCape;

		if (!shouldTakeOverCape
				|| hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) {
			return;
		}

		Optional<Identifier> texture = PlayerFashionRenderDecisions.capeTexture(state);

		if (texture.isEmpty()) {
			return;
		}

		poseStack.pushPose();

		try {
			// 该偏移与 Minecraft 26.2 CapeLayer 一致，只处理带 HUMANOID 层的胸部装备间距。
			if (hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
				poseStack.translate(0.0F, -0.053125F, 0.06875F);
			}

			submitNodeCollector.submitModel(
					model,
					state,
					poseStack,
					RenderTypes.entitySolid(texture.orElseThrow()),
					packedLight,
					OverlayTexture.NO_OVERLAY,
					state.outlineColor,
					null
			);
		} finally {
			poseStack.popPose();
		}
	}

	private boolean hasLayer(ItemStack itemStack, EquipmentClientInfo.LayerType layerType) {
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);

		if (equippable == null || equippable.assetId().isEmpty()) {
			return false;
		}

		EquipmentClientInfo equipmentInfo = equipmentAssets.get(equippable.assetId().orElseThrow());
		return !equipmentInfo.getLayers(layerType).isEmpty();
	}
}
