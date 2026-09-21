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

public record ArmorAssetRequestPayload(List<String> sha256Hashes) implements CustomPacketPayload {
    public static final Type<ArmorAssetRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "armor_asset_request"));
    public static final int MAX_BODY_BYTES = 2049;
    public static final StreamCodec<FriendlyByteBuf, ArmorAssetRequestPayload> CODEC = StreamCodec.ofMember(ArmorAssetRequestPayload::encode,
            buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES, ArmorAssetRequestPayload::read));
    public ArmorAssetRequestPayload { sha256Hashes = List.copyOf(sha256Hashes);
        if (sha256Hashes.isEmpty() || sha256Hashes.size() > 64) throw new IllegalArgumentException("盔甲请求必须包含 1 至 64 个 hash。");
        sha256Hashes.forEach(hash -> dev.zbw3790.fashion.cape.CapeAssetHash.requireValid(hash, "盔甲请求 hash ")); }
    private void encode(FriendlyByteBuf b) { b.writeVarInt(sha256Hashes.size()); sha256Hashes.forEach(hash -> FashionWireCodec.hash(b, hash)); }
    private static ArmorAssetRequestPayload read(FriendlyByteBuf b) { int count = FashionWireCodec.count(b, 1, 64); var hashes = new ArrayList<String>(count);
        for (int i=0; i<count; i++) hashes.add(FashionWireCodec.hash(b)); return new ArmorAssetRequestPayload(hashes); }
    @Override public Type<ArmorAssetRequestPayload> type() { return TYPE; }
}
