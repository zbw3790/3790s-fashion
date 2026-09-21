package dev.zbw3790.fashion.armor;

import java.util.Objects;

/** 保存意图与资源可用性分离，CUSTOM 引用缺失时仍保留。 */
public sealed interface ArmorSelection {
    ArmorSelection ORIGINAL = Builtin.ORIGINAL;
    ArmorSelection HIDDEN = Builtin.HIDDEN;
    enum Builtin implements ArmorSelection { ORIGINAL, HIDDEN }
    record Custom(ArmorStyleId id) implements ArmorSelection {
        public Custom { Objects.requireNonNull(id, "自定义盔甲必须携带样式 ID。"); }
    }
    static ArmorSelection custom(ArmorStyleId id) { return new Custom(id); }
}
