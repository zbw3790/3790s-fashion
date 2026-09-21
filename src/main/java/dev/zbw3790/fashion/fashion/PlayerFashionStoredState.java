package dev.zbw3790.fashion.fashion;

import java.util.Objects;
import dev.zbw3790.fashion.armor.ArmorSelections;
import java.util.Optional;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.outfit.OutfitSelections;

/** 唯一保存意图；完整六部位不含模型、装备或纹理就绪状态。 */
public record PlayerFashionStoredState(Optional<CapeId> cape, OutfitSelections outfit, ArmorSelections armor) {
    /** 旧数据／显式默认构造；修改已有状态应使用保留字段的方法。 */
    public PlayerFashionStoredState(Optional<CapeId> cape, OutfitSelections outfit) { this(cape, outfit, ArmorSelections.original()); }
    public static final PlayerFashionStoredState DEFAULT = new PlayerFashionStoredState(Optional.empty(), OutfitSelections.original());
    public PlayerFashionStoredState { Objects.requireNonNull(armor, "盔甲选择不能为 null。"); Objects.requireNonNull(cape, "披风选择不能为 null。"); Objects.requireNonNull(outfit, "装束选择不能为 null。"); }
    public PlayerFashionStoredState withCape(Optional<CapeId> value) { return new PlayerFashionStoredState(value, outfit, armor); }
    public PlayerFashionStoredState withOutfit(OutfitSelections value) { return new PlayerFashionStoredState(cape, value, armor); }
    public PlayerFashionStoredState withArmor(ArmorSelections value) { return new PlayerFashionStoredState(cape, outfit, value); }
    public boolean isDefault() { return equals(DEFAULT); }
}
