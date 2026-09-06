package vanillafashion.cape;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CapeRejectedEntry(
		Path directory,
		List<CapeValidationIssue> issues
) {
	public CapeRejectedEntry {
		Objects.requireNonNull(directory, "被拒绝条目的目录不能为 null。");
		issues = List.copyOf(Objects.requireNonNull(issues, "被拒绝条目的问题列表不能为 null。"));

		if (issues.isEmpty()) {
			throw new IllegalArgumentException("被拒绝条目必须包含至少一个校验问题。");
		}
	}
}
