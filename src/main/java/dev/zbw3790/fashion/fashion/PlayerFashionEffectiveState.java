package dev.zbw3790.fashion.fashion;

import java.util.Objects;
import dev.zbw3790.fashion.armor.ArmorSelections;
import java.util.Optional;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.outfit.OutfitSelections;

/** 服务器资源知识的投影；不依赖任何观察端模型或 GPU。 */
public record PlayerFashionEffectiveState(Optional<CapeId> cape, OutfitSelections outfit, ArmorSelections armor) {
    /** 旧数据／显式默认构造；修改已有状态应使用保留字段的方法。 */
    public PlayerFashionEffectiveState(Optional<CapeId> cape, OutfitSelections outfit) { this(cape, outfit, ArmorSelections.original()); }
    public PlayerFashionEffectiveState { Objects.requireNonNull(armor, "盔甲选择不能为 null。"); Objects.requireNonNull(cape, "有效披风不能为 null。"); Objects.requireNonNull(outfit, "有效装束不能为 null。"); }
}
