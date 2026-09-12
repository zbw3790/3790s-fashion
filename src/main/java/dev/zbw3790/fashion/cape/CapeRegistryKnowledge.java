package dev.zbw3790.fashion.cape;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/** 保存本次扫描可信度；删除判断还须确认具体路径确实不存在。 */
public record CapeRegistryKnowledge(
		Path root, boolean trustworthy, Set<CapeId> validIds, Set<CapeId> knownExistingIds
) {
	public CapeRegistryKnowledge {
		root = root.toAbsolutePath().normalize();
		validIds = Set.copyOf(validIds);
		knownExistingIds = Set.copyOf(knownExistingIds);
	}

	public static CapeRegistryKnowledge loaded(Path root, CapeRegistryLoadResult result) {
		return new CapeRegistryKnowledge(root, true,
				result.registry().definitions().stream().map(CapeCosmeticDefinition::id).collect(Collectors.toSet()),
				result.knownExistingIds());
	}

	public static CapeRegistryKnowledge unavailable(Path root) {
		return new CapeRegistryKnowledge(root, false, Set.of(), Set.of());
	}

	public boolean isValid(CapeId id) {
		return trustworthy && validIds.contains(id);
	}

	public boolean isDefinitelyAbsent(CapeId id) {
		if (!trustworthy || validIds.contains(id) || knownExistingIds.contains(id)) {
			return false;
		}
		try {
			return Files.isDirectory(root) && Files.notExists(root.resolve(id.value()), LinkOption.NOFOLLOW_LINKS);
		} catch (SecurityException exception) {
			return false;
		}
	}
}
