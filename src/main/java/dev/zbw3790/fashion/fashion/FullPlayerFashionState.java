package dev.zbw3790.fashion.fashion;

import java.util.Objects;
import dev.zbw3790.fashion.outfit.OutfitPart;
import dev.zbw3790.fashion.outfit.OutfitPartSelection;

/** 当前在线 membership 的完整权威；legacy 值只是该对象的 Cape 投影。 */
public record FullPlayerFashionState(PlayerFashionStoredState stored, PlayerFashionEffectiveState effective, long revision) {
    public FullPlayerFashionState {
        Objects.requireNonNull(stored, "保存状态不能为 null。"); Objects.requireNonNull(effective, "有效状态不能为 null。");
        if (revision < 0) throw new IllegalArgumentException("权威版本不能为负数。");
        if (effective.cape().isPresent() && !effective.cape().equals(stored.cape())) throw new IllegalArgumentException("有效披风必须来自同一保存选择。");
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
            var selected = stored.outfit().get(part); var visible = effective.outfit().get(part);
            if (selected instanceof OutfitPartSelection.Outfit) {
                if (!visible.equals(selected) && visible != OutfitPartSelection.ORIGINAL) throw new IllegalArgumentException("有效装束只能激活自身或回退原版。");
            } else if (!selected.equals(visible)) throw new IllegalArgumentException("内建选择的有效状态必须保持。");
        }
    }
    public static FullPlayerFashionState defaults(long revision) {
        return new FullPlayerFashionState(PlayerFashionStoredState.DEFAULT, new PlayerFashionEffectiveState(java.util.Optional.empty(), dev.zbw3790.fashion.outfit.OutfitSelections.original()), revision);
    }
    public PlayerFashionAuthoritativeState capeProjection() { return new PlayerFashionAuthoritativeState(stored.cape(), effective.cape()); }
}
