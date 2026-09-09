package vanillafashion.network;

import java.util.*;
import java.util.function.Function;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeAssetHash;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;

/** v2 的有限公共编码；每个顶层解码独立检查大小及尾随字节。旧 Codec 不使用此入口。 */
final class FashionWireCodec {
    private FashionWireCodec() { }
    static <B extends FriendlyByteBuf, T> T decode(B buffer, int maximum, Function<B, T> reader) {
        if (buffer.readableBytes() > maximum) throw new DecoderException("时装消息超过对应字节预算。");
        try {
            T value = reader.apply(buffer);
            if (buffer.isReadable()) throw new IllegalArgumentException("消息含有尾随字节。");
            return value;
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new DecoderException("时装消息字段、数量或长度无效。", exception);
        }
    }
    static boolean bool(FriendlyByteBuf b) {
        int value = b.readUnsignedByte(); if (value > 1) throw new IllegalArgumentException("布尔字段无效。"); return value == 1;
    }
    static int count(FriendlyByteBuf b, int min, int max) {
        int value = b.readVarInt(); if (value < min || value > max) throw new IllegalArgumentException("消息数量超限。"); return value;
    }
    static long nonnegative(FriendlyByteBuf b) {
        long value = b.readLong(); if (value < 0) throw new IllegalArgumentException("版本不能为负数。"); return value;
    }
    static long requestId(FriendlyByteBuf b) {
        long value = nonnegative(b); if (value == 0) throw new IllegalArgumentException("请求标识必须为正数。"); return value;
    }
    static void hash(FriendlyByteBuf b, String value) { b.writeBytes(HexFormat.of().parseHex(CapeAssetHash.requireValid(value, "内容 hash "))); }
    static String hash(FriendlyByteBuf b) { byte[] bytes = new byte[32]; b.readBytes(bytes); return HexFormat.of().formatHex(bytes); }
    static void stored(FriendlyByteBuf b, PlayerFashionStoredState state) {
        b.writeBoolean(state.cape().isPresent()); state.cape().ifPresent(id -> b.writeUtf(id.value(), 64));
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
            var selection = state.outfit().get(part);
            if (selection instanceof OutfitPartSelection.Outfit outfit) { b.writeByte(2); b.writeUtf(outfit.id().value(), 64); }
            else b.writeByte(selection == OutfitPartSelection.NONE ? 1 : 0);
        }
    }
    static PlayerFashionStoredState stored(FriendlyByteBuf b) {
        Optional<CapeId> cape = bool(b) ? Optional.of(new CapeId(b.readUtf(64))) : Optional.empty();
        var outfit = OutfitSelections.original();
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
            var selection = switch (b.readUnsignedByte()) {
                case 0 -> OutfitPartSelection.ORIGINAL; case 1 -> OutfitPartSelection.NONE;
                case 2 -> OutfitPartSelection.outfit(new OutfitId(b.readUtf(64)));
                default -> throw new IllegalArgumentException("未知的装束选择类型。");
            };
            outfit = outfit.with(part, selection);
        }
        return new PlayerFashionStoredState(cape, outfit);
    }
    static void authority(FriendlyByteBuf b, FullPlayerFashionState state) {
        b.writeLong(state.revision()); stored(b, state.stored());
        int mask = state.effective().cape().isPresent() ? 1 : 0;
        for (int i=0; i<6; i++) if (state.effective().outfit().get(OutfitPart.CANONICAL_ORDER.get(i)) instanceof OutfitPartSelection.Outfit) mask |= 1 << (i+1);
        b.writeByte(mask);
    }
    static FullPlayerFashionState authority(FriendlyByteBuf b) {
        long revision = nonnegative(b); var stored = stored(b); int mask = b.readUnsignedByte();
        if ((mask & 128) != 0 || (stored.cape().isEmpty() && (mask & 1) != 0)) throw new IllegalArgumentException("权威激活掩码无效。");
        var effective = stored.outfit();
        for (int i=0; i<6; i++) {
            var part = OutfitPart.CANONICAL_ORDER.get(i); var selection = stored.outfit().get(part); boolean active = (mask & (1 << (i+1))) != 0;
            if (selection instanceof OutfitPartSelection.Outfit) { if (!active) effective = effective.with(part, OutfitPartSelection.ORIGINAL); }
            else if (active) throw new IllegalArgumentException("内建部位不能设置激活位。");
        }
        return new FullPlayerFashionState(stored, new PlayerFashionEffectiveState((mask & 1) != 0 ? stored.cape() : Optional.empty(), effective), revision);
    }
    static void entry(FriendlyByteBuf b, FullPlayerFashionEntry entry) { b.writeUUID(entry.playerId()); authority(b, entry.state()); }
    static FullPlayerFashionEntry entry(FriendlyByteBuf b) { return new FullPlayerFashionEntry(b.readUUID(), authority(b)); }
}
