package vanillafashion.client.render.outfit;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import vanillafashion.outfit.OutfitModel;
import vanillafashion.outfit.OutfitPart;

/** 最小消费边界；空结果表示本帧没有输入，必须透传原版。 */
@FunctionalInterface
public interface OutfitAppearanceProvider {
    OutfitAppearanceProvider EMPTY = context -> Optional.empty();
    Optional<OutfitRenderAppearance> resolve(Context context);

    /** connection 只作为身份令牌使用，不向渲染核心提供协议或可写玩家状态。 */
    record Context(Object connection, UUID player, OutfitModel model, OutfitRenderAppearance.Scene scene,
            Map<OutfitPart, Boolean> originalVisibility) {
        public Context {
            Objects.requireNonNull(player, "解析上下文必须属于玩家。");
            Objects.requireNonNull(model, "解析上下文必须有实际模型。");
            Objects.requireNonNull(scene, "解析上下文必须指定场景。");
            originalVisibility = OutfitRenderAppearance.copyVisibility(originalVisibility);
        }
    }
}
