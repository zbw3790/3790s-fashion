package dev.zbw3790.fashion.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.zbw3790.fashion.wardrobe.WardrobeInteractionHandler;

@Mixin(Player.class)
public abstract class PlayerInteractionMixin {
    @Inject(method="interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;", at=@At("RETURN"), cancellable=true)
    private void fashion3790$afterInteraction(Entity target, InteractionHand hand, Vec3 hit, CallbackInfoReturnable<InteractionResult> callback) {
        var original=callback.getReturnValue();
        var result=WardrobeInteractionHandler.afterVanilla((Player)(Object)this,target,original);
        if (result!=original) callback.setReturnValue(result);
    }
}
