package dev.zbw3790.fashion.wardrobe;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public final class WardrobeInteractionRules {
    private WardrobeInteractionRules() { }
    public static boolean isWardrobeCandidate(Player player, Entity target, InteractionResult finalResult) {
        return !player.isSecondaryUseActive() && matches(target instanceof ArmorStand,player.isSpectator(),finalResult);
    }
    static boolean matches(boolean armorStand, boolean spectator, InteractionResult finalResult) {
        return armorStand && !spectator && finalResult==InteractionResult.PASS;
    }
}
