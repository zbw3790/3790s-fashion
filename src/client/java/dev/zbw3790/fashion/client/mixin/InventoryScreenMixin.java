package dev.zbw3790.fashion.client.mixin;

import dev.zbw3790.fashion.client.render.InventoryFashionRendering;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.2 生存／创造自身物品栏共用此提取入口；不按全局 Screen 猜场景，不改实体或 state.id。 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",at=@At("RETURN"))
    private static void fashion3790$inventoryAppearance(LivingEntity source, CallbackInfoReturnable<EntityRenderState> callback) {
        InventoryFashionRendering.extracted(source,callback.getReturnValue());
    }
}
