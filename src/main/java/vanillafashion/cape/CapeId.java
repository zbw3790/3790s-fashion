package vanillafashion.cape;

import java.util.regex.Pattern;

public record CapeId(String value) {
	public static final int MAX_LENGTH = 64;
	private static final Pattern VALID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]*");

	public CapeId {
		if (value == null) {
			throw new IllegalArgumentException("Cosmetic ID 不能为 null。");
		}

		if (value.isEmpty() || value.length() > MAX_LENGTH || !VALID_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException(
					"Cosmetic ID 不合法：长度必须为 1～64，首字符必须为小写英文字母或数字，其余字符只能使用小写英文字母、数字、下划线、连字符或点。"
			);
		}
	}

	@Override
	public String toString() {
		return value;
	}
}
