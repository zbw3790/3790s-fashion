package dev.zbw3790.fashion.client.render.outfit;

import java.util.Objects;
import java.util.Optional;

/** 不缓存上帧；输入不属于当前玩家、模型或场景时拒绝使用。 */
public final class OutfitAppearanceResolver {
    private final OutfitAppearanceProvider provider;
    public OutfitAppearanceResolver(OutfitAppearanceProvider provider) { this.provider = Objects.requireNonNull(provider); }
    public boolean hasSource() { return provider != OutfitAppearanceProvider.EMPTY; }
    public Optional<OutfitRenderAppearance> resolve(OutfitAppearanceProvider.Context context) {
        if (!hasSource() || context.connection() == null) return Optional.empty();
        return Objects.requireNonNull(provider.resolve(context), "来源必须明确返回当前帧结果。")
                .filter(value -> value.player().equals(context.player()) && value.model() == context.model()
                        && value.scene() == context.scene() && value.originalVisibility().equals(context.originalVisibility()));
    }
}
