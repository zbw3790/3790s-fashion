package dev.zbw3790.fashion.cape;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class CapeAssetHash {
	public static final int SHA_256_LENGTH = 64;
	private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

	private CapeAssetHash() {
	}

	public static String requireValid(String sha256, String description) {
		if (sha256 == null || !SHA_256_PATTERN.matcher(sha256).matches()) {
			throw new IllegalArgumentException(description + "必须是 64 位小写十六进制字符串。");
		}

		return sha256;
	}

	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256。", exception);
		}
	}
}
