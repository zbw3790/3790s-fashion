package vanillafashion.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WardrobePreviewEquipmentTest {
	@BeforeAll
	static void bootstrap() {
		WardrobePreviewTestSupport.bootstrap();
	}

	@Test
	void emptyChestDoesNotHaveWings() {
		assertFalse(WardrobePreviewEquipment.hasWings(ItemStack.EMPTY, assets(false)));
	}

	@Test
	void vanillaElytraUsesTheActualWingsLayer() {
		assertTrue(WardrobePreviewEquipment.hasWings(new ItemStack(Items.ELYTRA), assets(false)));
	}

	@Test
	void ordinaryArmorKeepsItsHumanoidLayer() {
		assertFalse(WardrobePreviewEquipment.hasWings(new ItemStack(Items.IRON_CHESTPLATE), assets(false)));
	}

	@Test
	void customWingsLayerIsDetectedWithoutHardCodingTheItem() {
		assertTrue(WardrobePreviewEquipment.hasWings(new ItemStack(Items.IRON_CHESTPLATE), assets(true)));
	}

	@Test
	void rendererRegistrationAfterReloadReplacesOnlyTheResourceReference() {
		var first = assets(false);
		var second = assets(true);
		WardrobePreviewEquipment.bind(first);
		var captured = WardrobePreviewEquipment.assets().orElseThrow();
		WardrobePreviewEquipment.bind(second);
		assertSame(first, captured);
		assertSame(second, WardrobePreviewEquipment.assets().orElseThrow());
		assertFalse(WardrobePreviewEquipment.hasWings(new ItemStack(Items.IRON_CHESTPLATE), captured));
		assertTrue(WardrobePreviewEquipment.hasWings(new ItemStack(Items.IRON_CHESTPLATE), second));
	}

	private static EquipmentAssetManager assets(boolean ironHasWings) {
		var wings = new EquipmentClientInfo(Map.of(EquipmentClientInfo.LayerType.WINGS,
				List.of(new EquipmentClientInfo.Layer(Identifier.withDefaultNamespace("elytra")))));
		return new EquipmentAssetManager() {
			@Override
			public EquipmentClientInfo get(ResourceKey<EquipmentAsset> key) {
				return key.equals(EquipmentAssets.ELYTRA) || ironHasWings && key.equals(EquipmentAssets.IRON)
						? wings : EquipmentAssetManager.MISSING;
			}
		};
	}
}
