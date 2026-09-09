package vanillafashion.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;

public record OutfitAssetDataPayload(String sha256, byte[] pngBytes) implements CustomPacketPayload {
    public static final Type<OutfitAssetDataPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "outfit_asset_data"));
    public static final int MAX_BODY_BYTES = 65571;
    public static final StreamCodec<RegistryFriendlyByteBuf, OutfitAssetDataPayload> CODEC = StreamCodec.ofMember(OutfitAssetDataPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, OutfitAssetDataPayload::read));
    public OutfitAssetDataPayload { sha256 = vanillafashion.cape.CapeAssetHash.requireValid(sha256, "装束资产 hash "); Objects.requireNonNull(pngBytes);
        if (pngBytes.length < 1 || pngBytes.length > OutfitPngValidator.MAX_ASSET_BYTES) throw new IllegalArgumentException("装束资产长度必须为 1 至 65536 字节。"); pngBytes = pngBytes.clone(); }
    private void encode(RegistryFriendlyByteBuf b) { FashionWireCodec.hash(b, sha256); b.writeByteArray(pngBytes); }
    private static OutfitAssetDataPayload read(RegistryFriendlyByteBuf b) { String hash = FashionWireCodec.hash(b); int size = FashionWireCodec.count(b, 1, OutfitPngValidator.MAX_ASSET_BYTES);
        byte[] bytes = new byte[size]; b.readBytes(bytes); return new OutfitAssetDataPayload(hash, bytes); }
    @Override public Type<OutfitAssetDataPayload> type() { return TYPE; }
    @Override public byte[] pngBytes() { return pngBytes.clone(); }
    @Override public boolean equals(Object other) { return other instanceof OutfitAssetDataPayload value && sha256.equals(value.sha256) && Arrays.equals(pngBytes, value.pngBytes); }
    @Override public int hashCode() { return 31 * sha256.hashCode() + Arrays.hashCode(pngBytes); }
}
