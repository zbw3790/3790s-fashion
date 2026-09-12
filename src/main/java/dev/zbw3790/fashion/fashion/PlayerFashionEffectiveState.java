package dev.zbw3790.fashion.fashion;

import java.util.Objects;
import java.util.Optional;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.outfit.OutfitSelections;

/** 服务器资源知识的投影；不依赖任何观察端模型或 GPU。 */
public record PlayerFashionEffectiveState(Optional<CapeId> cape, OutfitSelections outfit) {
    public PlayerFashionEffectiveState { Objects.requireNonNull(cape, "有效披风不能为 null。"); Objects.requireNonNull(outfit, "有效装束不能为 null。"); }
}
