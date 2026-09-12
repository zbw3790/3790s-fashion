package dev.zbw3790.fashion.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.outfit.OutfitRegistrySnapshot;

/** 运行期刷新使用独立消息，初始化 Snapshot 的冲突契约不变。 */
public record OutfitRegistryRefreshPayload(long registryGeneration, OutfitRegistrySnapshot snapshot) implements CustomPacketPayload {
    public static final Type<OutfitRegistryRefreshPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "outfit_registry_refresh"));
    public static final int MAX_BODY_BYTES = Long.BYTES + OutfitRegistrySnapshotPayload.MAX_BODY_BYTES;
    public static final StreamCodec<RegistryFriendlyByteBuf, OutfitRegistryRefreshPayload> CODEC = StreamCodec.ofMember(
            OutfitRegistryRefreshPayload::encode, buffer -> FashionWireCodec.decode(buffer, MAX_BODY_BYTES,
                    b -> new OutfitRegistryRefreshPayload(b.readLong(), OutfitRegistrySnapshotPayload.read(b).snapshot())));
    public OutfitRegistryRefreshPayload {
        if (registryGeneration < 0) throw new IllegalArgumentException("装束目录代次不能为负数。");
        Objects.requireNonNull(snapshot);
    }
    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(registryGeneration);
        new OutfitRegistrySnapshotPayload(snapshot).encode(buffer);
    }
    @Override public Type<OutfitRegistryRefreshPayload> type() { return TYPE; }
}
