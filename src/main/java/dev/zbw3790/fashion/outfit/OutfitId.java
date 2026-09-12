package dev.zbw3790.fashion.outfit;

import java.util.Objects;
import java.util.regex.Pattern;

public record OutfitId(String value) implements Comparable<OutfitId> {
	public static final int MAX_LENGTH = 64;
	private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

	public OutfitId {
		Objects.requireNonNull(value, "装束 ID 不能为 null。");
		if (!VALID.matcher(value).matches() || value.contains("..")) {
			throw new IllegalArgumentException("装束 ID 必须为 1～64 个安全 ASCII 字符，且不能含连续两个点。");
		}
	}

	@Override public int compareTo(OutfitId other) { return value.compareTo(other.value); }
	@Override public String toString() { return value; }
}
