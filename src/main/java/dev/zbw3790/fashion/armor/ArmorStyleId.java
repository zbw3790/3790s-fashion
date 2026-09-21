package dev.zbw3790.fashion.armor;

import java.util.Objects;
import java.util.regex.Pattern;

/** 有界稳定身份，显示名称不参与保存与资源寻址。 */
public record ArmorStyleId(String value) implements Comparable<ArmorStyleId> {
    public static final int MAX_LENGTH = 32;
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,31}");
    public ArmorStyleId {
        Objects.requireNonNull(value, "盔甲样式 ID 不能为空。");
        if (!VALID.matcher(value).matches()) throw new IllegalArgumentException("盔甲样式 ID 不合法。");
    }
    @Override public int compareTo(ArmorStyleId other) { return value.compareTo(other.value); }
}
