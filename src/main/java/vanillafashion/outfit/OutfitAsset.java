package vanillafashion.outfit;

import java.util.Arrays;
import java.util.Objects;
import vanillafashion.cape.CapeAssetHash;

/** 捕获已验证原字节，不在未来发送时重新打开管理员路径。 */
public final class OutfitAsset {
	private final byte[] bytes;
	private final String sha256;
	private OutfitAsset(byte[] bytes) {
		this.bytes = bytes;
		this.sha256 = CapeAssetHash.sha256(bytes);
	}
	public static OutfitAsset fromBytes(byte[] bytes) {
		byte[] copy = Objects.requireNonNull(bytes, "资产字节不能为 null。").clone();
		if (OutfitPngValidator.validate(copy) != OutfitPngValidator.Result.VALID) {
			throw new IllegalArgumentException("装束资产必须是合法且有界的 64×64 PNG。");
		}
		return new OutfitAsset(copy);
	}
	public byte[] bytes() { return bytes.clone(); }
	public int size() { return bytes.length; }
	public String sha256() { return sha256; }
	@Override public boolean equals(Object other) {
		return other instanceof OutfitAsset asset && Arrays.equals(bytes, asset.bytes);
	}
	@Override public int hashCode() { return sha256.hashCode(); }
}
