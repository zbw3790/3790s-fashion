package dev.zbw3790.fashion.armor;

public enum ArmorGeometry {
    OUTER, INNER;
    public static ArmorGeometry forSlot(ArmorSlot slot) { return slot==ArmorSlot.LEGS ? INNER : OUTER; }
    public String serializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static ArmorGeometry fromName(String name) {
        return switch(name) { case "outer" -> OUTER; case "inner" -> INNER; default -> throw new IllegalArgumentException("未知盔甲几何层。"); };
    }
}
