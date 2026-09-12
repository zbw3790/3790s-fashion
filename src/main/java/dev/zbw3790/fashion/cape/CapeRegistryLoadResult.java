package dev.zbw3790.fashion.cape;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CapeRegistryLoadResult(
		CapeRegistry registry,
		List<CapeRejectedEntry> rejectedEntries,
		Set<CapeId> knownExistingIds
) {
	public CapeRegistryLoadResult(CapeRegistry registry, List<CapeRejectedEntry> rejectedEntries) {
		this(registry, rejectedEntries, Set.of());
	}

	public CapeRegistryLoadResult {
		Objects.requireNonNull(registry, "Cape Registry 加载结果不能为 null。");
		rejectedEntries = List.copyOf(Objects.requireNonNull(
				rejectedEntries,
				"Cape Registry 被拒绝条目列表不能为 null。"
		));
		Set<CapeId> known = new HashSet<>(knownExistingIds);
		registry.definitions().forEach(definition -> known.add(definition.id()));
		for (CapeRejectedEntry rejected : rejectedEntries) {
			addKnownId(known, rejected.directory().getFileName().toString());
		}
		knownExistingIds = Set.copyOf(known);
	}

	static void addKnownId(Set<CapeId> known, String name) {
		try {
			known.add(new CapeId(name));
		} catch (IllegalArgumentException exception) {
			// 非法目录名不可能对应格式合法的 stored CapeId。
		}
	}
}
