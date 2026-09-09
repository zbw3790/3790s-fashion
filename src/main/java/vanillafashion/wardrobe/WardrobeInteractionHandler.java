package vanillafashion.wardrobe;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import vanillafashion.VanillaFashion;
import vanillafashion.network.OpenWardrobePayload;
import vanillafashion.network.ServerPayloadSender;

/** 原版完整交互返回后唯一的衣柜入口；不修改装备，不重跑原版行为。 */
public final class WardrobeInteractionHandler {
    private WardrobeInteractionHandler() { }
    public static InteractionResult afterVanilla(Player player, Entity target, InteractionResult finalResult) {
        boolean candidate=WardrobeInteractionRules.isWardrobeCandidate(player,target,finalResult);
        return fallback(finalResult,candidate,player.level().isClientSide(),VanillaFashion.wardrobeServerAvailability().isAvailable(),
                () -> player instanceof ServerPlayer serverPlayer
                        && ServerPayloadSender.sendIfSupported(serverPlayer.connection,OpenWardrobePayload.INSTANCE));
    }
    static InteractionResult fallback(InteractionResult original, boolean candidate, boolean client, boolean available, BooleanSupplier open) {
        if (!candidate || original!=InteractionResult.PASS) return original;
        // 无挥手、无物品使用语义；客户端仅消费后续分支，界面仍由服务器消息打开。
        return (client?available:open.getAsBoolean()) ? InteractionResult.CONSUME.withoutItem() : original;
    }
}
