package dev.zbw3790.fashion.armor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;

/** schema 4 与完整 v4 协议共用的有界 ASCII 三态编码。 */
public final class ArmorSelectionEncoding {
    public static final int MAX_BYTES = 1 + 4 * (1 + ArmorStyleId.MAX_LENGTH);
    private ArmorSelectionEncoding() { }
    public static byte[] encode(ArmorSelections selections) {
        var out = new ByteArrayOutputStream(MAX_BYTES);
        int mask=0;
        for (var slot : ArmorSlot.CANONICAL_ORDER) {
            var value=selections.get(slot);
            int mode=value instanceof ArmorSelection.Custom ? 2 : value==ArmorSelection.HIDDEN ? 1 : 0;
            mask |= mode << (slot.ordinal()*2);
        }
        out.write(mask);
        for (var value : selections.slots()) if (value instanceof ArmorSelection.Custom custom) {
            byte[] id=custom.id().value().getBytes(StandardCharsets.US_ASCII);
            out.write(id.length); out.writeBytes(id);
        }
        return out.toByteArray();
    }
    public static ArmorSelections decode(byte[] bytes) {
        if (bytes.length<1 || bytes.length>MAX_BYTES) throw new IllegalArgumentException("盔甲编码长度超限。");
        int[] cursor={0};
        var result=read(() -> {
            if (cursor[0]>=bytes.length) throw new IllegalArgumentException("盔甲编码被截断。");
            return Byte.toUnsignedInt(bytes[cursor[0]++]);
        });
        if (cursor[0]!=bytes.length) throw new IllegalArgumentException("盔甲编码含尾随数据。");
        return result;
    }
    public static ArmorSelections read(IntSupplier unsignedByte) {
        int mask=unsignedByte.getAsInt(); var result=ArmorSelections.original();
        for (var slot : ArmorSlot.CANONICAL_ORDER) {
            int mode=(mask >> (slot.ordinal()*2)) & 3;
            if (mode==3) throw new IllegalArgumentException("盔甲模式未定义。");
            if (mode==1) result=result.with(slot,ArmorSelection.HIDDEN);
            else if (mode==2) {
                int length=unsignedByte.getAsInt();
                if (length<1 || length>ArmorStyleId.MAX_LENGTH) throw new IllegalArgumentException("盔甲 ID 长度无效。");
                char[] chars=new char[length];
                for (int i=0;i<length;i++) chars[i]=(char)unsignedByte.getAsInt();
                result=result.with(slot,ArmorSelection.custom(new ArmorStyleId(new String(chars))));
            }
        }
        return result;
    }
}
