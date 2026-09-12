package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class WardrobePreviewEquipment {
	// 只保留原版资源管理器；资源重载时更新，不保存预览模式、实体或装备。
	private static volatile EquipmentAssetManager equipmentAssets;

	private WardrobePreviewEquipment() {
	}

	public static void bind(EquipmentAssetManager assets) {
		equipmentAssets = Objects.requireNonNull(assets, "预览装备资源管理器不能为空。");
	}

	public static Optional<EquipmentAssetManager> assets() {
		return Optional.ofNullable(equipmentAssets);
	}

	public static boolean hasWings(ItemStack equipment, EquipmentAssetManager assets) {
		Objects.requireNonNull(equipment, "预览胸部装备不能为空。");
		Objects.requireNonNull(assets, "预览装备资源管理器不能为空。");
		Equippable equippable = equipment.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.assetId().isPresent()
				&& !assets.get(equippable.assetId().orElseThrow())
						.getLayers(EquipmentClientInfo.LayerType.WINGS).isEmpty();
	}
}
