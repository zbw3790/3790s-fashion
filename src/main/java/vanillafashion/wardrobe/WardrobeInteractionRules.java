package vanillafashion.wardrobe;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public final class WardrobeInteractionRules {
	private WardrobeInteractionRules() {
	}

	public static boolean isWardrobeCandidate(
			Player player,
			InteractionHand hand,
			Entity entity
	) {
		ArmorStand armorStand = entity instanceof ArmorStand stand ? stand : null;

		return matches(new Input(
				armorStand != null,
				hand == InteractionHand.MAIN_HAND,
				player.getMainHandItem().isEmpty(),
				player.isSpectator(),
				isSlotEmpty(armorStand, EquipmentSlot.HEAD),
				isSlotEmpty(armorStand, EquipmentSlot.CHEST),
				isSlotEmpty(armorStand, EquipmentSlot.LEGS),
				isSlotEmpty(armorStand, EquipmentSlot.FEET),
				isSlotEmpty(armorStand, EquipmentSlot.MAINHAND),
				isSlotEmpty(armorStand, EquipmentSlot.OFFHAND)
		));
	}

	static boolean matches(Input input) {
		return input.armorStand()
				&& input.mainHandInteraction()
				&& input.playerMainHandEmpty()
				&& !input.spectator()
				&& input.headEmpty()
				&& input.chestEmpty()
				&& input.legsEmpty()
				&& input.feetEmpty()
				&& input.mainHandEmpty()
				&& input.offHandEmpty();
	}

	private static boolean isSlotEmpty(ArmorStand armorStand, EquipmentSlot slot) {
		return armorStand != null && armorStand.getItemBySlot(slot).isEmpty();
	}

	record Input(
			boolean armorStand,
			boolean mainHandInteraction,
			boolean playerMainHandEmpty,
			boolean spectator,
			boolean headEmpty,
			boolean chestEmpty,
			boolean legsEmpty,
			boolean feetEmpty,
			boolean mainHandEmpty,
			boolean offHandEmpty
	) {
	}
}
