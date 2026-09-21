package dev.zbw3790.fashion.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.armor.*;

public record ArmorAssetDataPayload(String sha256, byte[] pngBytes) implements CustomPacketPayload {
    public static final Type<ArmorAssetDataPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "armor_asset_data"));
    public static final int MAX_BODY_BYTES = 16419;
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorAssetDataPayload> CODEC = StreamCodec.ofMember(ArmorAssetDataPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, ArmorAssetDataPayload::read));
    public ArmorAssetDataPayload { sha256 = dev.zbw3790.fashion.cape.CapeAssetHash.requireValid(sha256, "盔甲资产 hash "); Objects.requireNonNull(pngBytes);
        if (pngBytes.length < 1 || pngBytes.length > ArmorAsset.MAX_BYTES) throw new IllegalArgumentException("盔甲资产长度必须为 1 至 16384 字节。"); pngBytes = pngBytes.clone(); }
    private void encode(RegistryFriendlyByteBuf b) { FashionWireCodec.hash(b, sha256); b.writeByteArray(pngBytes); }
    private static ArmorAssetDataPayload read(RegistryFriendlyByteBuf b) { String hash = FashionWireCodec.hash(b); int size = FashionWireCodec.count(b, 1, ArmorAsset.MAX_BYTES);
        byte[] bytes = new byte[size]; b.readBytes(bytes); return new ArmorAssetDataPayload(hash, bytes); }
    @Override public Type<ArmorAssetDataPayload> type() { return TYPE; }
    @Override public byte[] pngBytes() { return pngBytes.clone(); }
    @Override public boolean equals(Object other) { return other instanceof ArmorAssetDataPayload value && sha256.equals(value.sha256) && Arrays.equals(pngBytes, value.pngBytes); }
    @Override public int hashCode() { return 31 * sha256.hashCode() + Arrays.hashCode(pngBytes); }
}
