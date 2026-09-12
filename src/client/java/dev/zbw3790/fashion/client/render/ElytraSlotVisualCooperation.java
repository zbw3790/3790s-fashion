package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** 主 Mod 独立运行时只使用本类，不触及可选附属的 API 类型。 */
final class ElytraSlotVisualCooperation {
    private static Function<UUID, PlayerFashionRenderAppearance> appearances =
        playerId -> PlayerFashionRenderAppearance.unknown();
    private static Predicate<AvatarRenderState> occlusion = state -> false;
    private static boolean connected;

    private ElytraSlotVisualCooperation() { }

    static void bindAppearances(PlayerFashionAppearanceResolver resolver) {
        appearances = Objects.requireNonNull(resolver)::resolve;
    }
    static PlayerFashionRenderAppearance appearance(UUID playerId) {
        return appearances.apply(Objects.requireNonNull(playerId));
    }
    static void connect(Predicate<AvatarRenderState> blocksCape) {
        if (connected) throw new IllegalStateException("Elytra Slot 视觉协作已经连接，不能覆盖装备事实来源。");
        occlusion = Objects.requireNonNull(blocksCape);
        connected = true;
    }
    static boolean blocksCape(AvatarRenderState state) {
        return WardrobePreviewRenderState.find(state).isEmpty() && occlusion.test(state);
    }
}
