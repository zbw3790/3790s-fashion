package dev.zbw3790.fashion.armor;

import java.util.List;

/** 固定视觉槽顺序；与真实装备的内容和生命周期独立。 */
public enum ArmorSlot {
    HEAD, CHEST, LEGS, FEET;
    public static final List<ArmorSlot> CANONICAL_ORDER = List.of(values());
    public String serializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static ArmorSlot fromName(String value) {
        return CANONICAL_ORDER.stream().filter(slot -> slot.serializedName().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知盔甲视觉槽。"));
    }
}
