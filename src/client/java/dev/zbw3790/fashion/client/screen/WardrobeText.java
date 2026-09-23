package dev.zbw3790.fashion.client.screen;

import net.minecraft.network.chat.Component;

/** 当前客户端的原版翻译入口；参数始终是字面数据，不缓存已解析的语言。 */
final class WardrobeText {
    private WardrobeText() { }
    static Component text(String key, Object... arguments) {
        return Component.translatable("gui.fashion_3790." + key, arguments);
    }
    static String string(String key, Object... arguments) { return text(key, arguments).getString(); }
}
