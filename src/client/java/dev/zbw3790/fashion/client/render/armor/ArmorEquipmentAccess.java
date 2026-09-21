package dev.zbw3790.fashion.client.render.armor;

import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;

/** 只读取原版的真实装备层定义，不替换 equipment asset。 */
public interface ArmorEquipmentAccess {
    EquipmentClientInfo fashion3790$equipment(ResourceKey<EquipmentAsset> asset);
}
