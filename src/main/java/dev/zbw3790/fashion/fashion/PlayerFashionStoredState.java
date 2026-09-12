package dev.zbw3790.fashion.fashion;

import java.util.Objects;
import java.util.Optional;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.outfit.OutfitSelections;

/** 唯一保存意图；完整六部位不含模型、装备或纹理就绪状态。 */
public record PlayerFashionStoredState(Optional<CapeId> cape, OutfitSelections outfit) {
    public static final PlayerFashionStoredState DEFAULT = new PlayerFashionStoredState(Optional.empty(), OutfitSelections.original());
    public PlayerFashionStoredState { Objects.requireNonNull(cape, "披风选择不能为 null。"); Objects.requireNonNull(outfit, "装束选择不能为 null。"); }
    public PlayerFashionStoredState withCape(Optional<CapeId> value) { return new PlayerFashionStoredState(value, outfit); }
    public boolean isDefault() { return equals(DEFAULT); }
}
