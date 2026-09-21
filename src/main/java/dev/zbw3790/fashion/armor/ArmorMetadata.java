package dev.zbw3790.fashion.armor;

import java.nio.charset.StandardCharsets;
import java.util.*;

public record ArmorMetadata(ArmorStyleId id,String name,Set<ArmorSlot> slots,Map<ArmorGeometry,String> textures) {
    public ArmorMetadata {
        Objects.requireNonNull(id); validateName(name); slots=Set.copyOf(slots); textures=Map.copyOf(textures);
        if(slots.isEmpty() || textures.isEmpty()) throw new IllegalArgumentException("盔甲样式缺少槽位或纹理。");
        for(var slot:slots) if(!textures.containsKey(ArmorGeometry.forSlot(slot))) throw new IllegalArgumentException("声明的槽缺少必需纹理。");
        for(var file:textures.values()) if(!file.matches("[a-z0-9][a-z0-9_-]{0,43}\\.png")) throw new IllegalArgumentException("盔甲纹理文件名不安全。");
    }
    public static void validateName(String name) {
        Objects.requireNonNull(name);
        if(name.isBlank() || name.codePointCount(0,name.length())>80 || name.getBytes(StandardCharsets.UTF_8).length>240
                || name.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("盔甲显示名称无效或超长。");
    }
}
