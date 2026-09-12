package dev.zbw3790.fashion.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeAssetLimits;

public record CapeAssetRequestPayload(List<String> sha256Hashes) implements CustomPacketPayload {
	public static final Type<CapeAssetRequestPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "cape_asset_request")
	);
	public static final StreamCodec<FriendlyByteBuf, CapeAssetRequestPayload> CODEC =
			StreamCodec.ofMember(CapeAssetRequestPayload::encode, CapeAssetRequestPayload::decode);

	public CapeAssetRequestPayload {
		Objects.requireNonNull(sha256Hashes, "Cape 资产请求 hash 列表不能为 null。");

		if (sha256Hashes.isEmpty() || sha256Hashes.size() > CapeAssetLimits.MAX_REQUEST_HASHES) {
			throw new IllegalArgumentException(
					"Cape 资产请求必须包含 1 至 " + CapeAssetLimits.MAX_REQUEST_HASHES + " 个 hash。"
			);
		}

		Set<String> uniqueHashes = new HashSet<>();

		for (String sha256 : sha256Hashes) {
			CapeAssetHash.requireValid(sha256, "Cape 资产请求 SHA-256 ");

			if (!uniqueHashes.add(sha256)) {
				throw new IllegalArgumentException("同一个 Cape 资产请求不能包含重复 hash。");
			}
		}

		sha256Hashes = List.copyOf(sha256Hashes);
	}

	private void encode(FriendlyByteBuf buffer) {
		ByteBufCodecs.writeCount(buffer, sha256Hashes.size(), CapeAssetLimits.MAX_REQUEST_HASHES);

		for (String sha256 : sha256Hashes) {
			buffer.writeUtf(sha256, CapeAssetHash.SHA_256_LENGTH);
		}
	}

	private static CapeAssetRequestPayload decode(FriendlyByteBuf buffer) {
		int count = ByteBufCodecs.readCount(buffer, CapeAssetLimits.MAX_REQUEST_HASHES);

		if (count < 1) {
			throw new DecoderException("Cape 资产请求至少需要一个 hash。");
		}

		List<String> hashes = new ArrayList<>(count);

		for (int index = 0; index < count; index++) {
			hashes.add(buffer.readUtf(CapeAssetHash.SHA_256_LENGTH));
		}

		try {
			return new CapeAssetRequestPayload(hashes);
		} catch (IllegalArgumentException exception) {
			throw new DecoderException("Cape 资产请求内容不合法。", exception);
		}
	}

	@Override
	public Type<CapeAssetRequestPayload> type() {
		return TYPE;
	}
}
