package vanillafashion.cape;

import java.util.Objects;
import java.util.Optional;

public final class CapeAssetReadResult {
	private final Status status;
	private final byte[] bytes;

	private CapeAssetReadResult(Status status, byte[] bytes) {
		this.status = Objects.requireNonNull(status, "资产读取状态不能为 null。");
		this.bytes = bytes == null ? null : bytes.clone();
	}

	public static CapeAssetReadResult success(byte[] bytes) {
		Objects.requireNonNull(bytes, "成功读取的资产字节不能为 null。");
		return new CapeAssetReadResult(Status.SUCCESS, bytes);
	}

	public static CapeAssetReadResult failure(Status status) {
		if (status == Status.SUCCESS) {
			throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态。");
		}

		return new CapeAssetReadResult(status, null);
	}

	public Status status() {
		return status;
	}

	public Optional<byte[]> bytes() {
		return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
	}

	public enum Status {
		SUCCESS,
		NOT_REGULAR_FILE,
		EMPTY,
		TOO_LARGE,
		HASH_MISMATCH,
		IO_ERROR
	}
}
