package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import vanillafashion.elytraslot.api.client.ElytraVisualCompatibility;

/** 仅由兼容附属的自定义 Fabric 入口延迟加载；主 Mod 单装不解析此类型。 */
public final class ElytraSlotVisualProvider implements ElytraVisualCompatibility {
    private final Function<UUID, PlayerFashionRenderAppearance> appearances;
    private final Consumer<Predicate<AvatarRenderState>> connection;

    public ElytraSlotVisualProvider() {
        this(ElytraSlotVisualCooperation::appearance, ElytraSlotVisualCooperation::connect);
    }
    ElytraSlotVisualProvider(Function<UUID, PlayerFashionRenderAppearance> appearances,
            Consumer<Predicate<AvatarRenderState>> connection) {
        this.appearances = Objects.requireNonNull(appearances);
        this.connection = Objects.requireNonNull(connection);
    }
    @Override public void connect(Predicate<AvatarRenderState> blocksCape) {
        connection.accept(Objects.requireNonNull(blocksCape));
    }
    @Override public boolean isPreview(AvatarRenderState state) {
        return WardrobePreviewRenderState.find(state).isPresent();
    }
    @Override public void applyAppearance(UUID playerId, AvatarRenderState dedicated) {
        // 只附着主 Mod 自有的不可变外观；原版字段、Outfit 与真实装备均不写入。
        PlayerFashionRenderState.attach(dedicated, appearances.apply(Objects.requireNonNull(playerId)));
    }
}
