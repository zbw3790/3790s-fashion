package dev.zbw3790.fashion.client.screen;

import net.minecraft.network.chat.Component;
import dev.zbw3790.fashion.armor.ArmorSlot;

/** 新增盔甲界面的翻译入口；完整名称保留给 Tooltip 与旁白。 */
final class WardrobeArmorText {
    private WardrobeArmorText() { }
    static Component text(String key, Object... arguments) {
        return Component.translatable("gui.fashion_3790.armor." + key, arguments);
    }
    static String string(String key, Object... arguments) { return text(key, arguments).getString(); }
    static String slot(ArmorSlot slot) { return string("slot." + slot.serializedName()); }
}
