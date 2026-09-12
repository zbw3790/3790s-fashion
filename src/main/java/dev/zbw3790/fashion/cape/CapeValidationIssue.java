package dev.zbw3790.fashion.cape;

import java.nio.file.Path;
import java.util.Objects;

public record CapeValidationIssue(
		CapeValidationErrorCode code,
		Path path,
		String message
) {
	public CapeValidationIssue {
		Objects.requireNonNull(code, "校验错误码不能为 null。");
		Objects.requireNonNull(path, "校验问题路径不能为 null。");

		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("校验问题必须包含中文原因。");
		}
	}
}
