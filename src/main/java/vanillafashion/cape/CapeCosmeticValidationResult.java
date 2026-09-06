package vanillafashion.cape;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CapeCosmeticValidationResult(
		Optional<CapeCosmeticDefinition> definition,
		List<CapeValidationIssue> issues
) {
	public CapeCosmeticValidationResult {
		definition = Objects.requireNonNull(definition, "定义状态必须使用 Optional 表达。");
		issues = List.copyOf(Objects.requireNonNull(issues, "校验问题列表不能为 null。"));

		if (definition.isPresent() && !issues.isEmpty()) {
			throw new IllegalArgumentException("成功结果不能包含校验问题。");
		}

		if (definition.isEmpty() && issues.isEmpty()) {
			throw new IllegalArgumentException("失败结果必须包含至少一个校验问题。");
		}
	}

	public static CapeCosmeticValidationResult success(CapeCosmeticDefinition definition) {
		return new CapeCosmeticValidationResult(Optional.of(definition), List.of());
	}

	public static CapeCosmeticValidationResult failure(List<CapeValidationIssue> issues) {
		return new CapeCosmeticValidationResult(Optional.empty(), issues);
	}

	public boolean isSuccess() {
		return definition.isPresent();
	}
}
