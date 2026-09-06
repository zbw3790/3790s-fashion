package vanillafashion.network;

import java.util.Objects;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;
import vanillafashion.cape.CapeAssetHash;
import vanillafashion.cape.CapeAssetLimits;

public final class CapeAssetDataPayload implements CustomPacketPayload {
	public static final Type<CapeAssetDataPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "cape_asset_data")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, CapeAssetDataPayload> CODEC =
			StreamCodec.ofMember(CapeAssetDataPayload::encode, CapeAssetDataPayload::decode);

	private final String sha256;
	private final byte[] pngBytes;

	public CapeAssetDataPayload(String sha256, byte[] pngBytes) {
		this.sha256 = CapeAssetHash.requireValid(sha256, "Cape 资产数据 SHA-256 ");
		Objects.requireNonNull(pngBytes, "Cape 资产数据字节不能为 null。");

		if (pngBytes.length < 1 || pngBytes.length > CapeAssetLimits.MAX_ASSET_BYTES) {
			throw new IllegalArgumentException(
					"Cape 资产数据长度必须为 1 至 " + CapeAssetLimits.MAX_ASSET_BYTES + " 字节。"
			);
		}

		this.pngBytes = pngBytes.clone();
	}

	public String sha256() {
		return sha256;
	}

	public byte[] pngBytes() {
		return pngBytes.clone();
	}

	private void encode(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(sha256, CapeAssetHash.SHA_256_LENGTH);
		buffer.writeByteArray(pngBytes);
	}

	private static CapeAssetDataPayload decode(RegistryFriendlyByteBuf buffer) {
		String sha256 = buffer.readUtf(CapeAssetHash.SHA_256_LENGTH);
		byte[] pngBytes = buffer.readByteArray(CapeAssetLimits.MAX_ASSET_BYTES);

		try {
			return new CapeAssetDataPayload(sha256, pngBytes);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Cape 资产数据内容不合法。", exception);
		}
	}

	@Override
	public Type<CapeAssetDataPayload> type() {
		return TYPE;
	}
}
