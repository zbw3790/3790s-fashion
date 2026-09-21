package dev.zbw3790.fashion.armor;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** 固定四槽不可变值；旧掩码构造仅用于 schema 3 迁移与内建选择。 */
public record ArmorSelections(List<ArmorSelection> slots) {
    private static final ArmorSelections ORIGINAL = new ArmorSelections(0);
    public ArmorSelections {
        slots = List.copyOf(slots);
        if (slots.size() != 4) throw new IllegalArgumentException("盔甲选择必须恰好四槽。");
    }
    public ArmorSelections(int hiddenMask) { this(fromMask(hiddenMask)); }
    private static List<ArmorSelection> fromMask(int mask) {
        if (mask < 0 || mask > 15) throw new IllegalArgumentException("盔甲显示掩码只允许四个已定义位。");
        var slots = new ArrayList<ArmorSelection>(4);
        for (int i=0; i<4; i++) slots.add((mask & (1 << i)) == 0 ? ArmorSelection.ORIGINAL : ArmorSelection.HIDDEN);
        return slots;
    }
    public static ArmorSelections original() { return ORIGINAL; }
    public ArmorSelection get(ArmorSlot slot) { return slots.get(Objects.requireNonNull(slot).ordinal()); }
    public ArmorSelections with(ArmorSlot slot, ArmorSelection selection) {
        var changed = new ArrayList<>(slots);
        changed.set(Objects.requireNonNull(slot).ordinal(), Objects.requireNonNull(selection));
        return new ArmorSelections(changed);
    }
    /** 仅投影隐藏位；不能用此值保存 CUSTOM。 */
    public int hiddenMask() {
        int mask=0;
        for (var slot : ArmorSlot.CANONICAL_ORDER) if (get(slot)==ArmorSelection.HIDDEN) mask |= 1 << slot.ordinal();
        return mask;
    }
}
