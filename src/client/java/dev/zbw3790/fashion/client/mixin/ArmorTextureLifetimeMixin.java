package dev.zbw3790.fashion.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.zbw3790.fashion.client.Fashion3790Client;

/** 世界及 GUI 的延迟提交完成后推进退役，不在资源消息中立即释放旧句柄。 */
@Mixin(Minecraft.class)
public abstract class ArmorTextureLifetimeMixin {
    @Inject(method="runTick",at=@At("RETURN"))
    private void fashion3790$frameCompleted(boolean tick,CallbackInfo callback) {Fashion3790Client.armorResources().textures.frameCompleted();}
}
