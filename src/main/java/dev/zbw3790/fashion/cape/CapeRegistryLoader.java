package dev.zbw3790.fashion.cape;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class CapeRegistryLoader {
	private final CapeCosmeticValidator validator;

	public CapeRegistryLoader() {
		this(new CapeCosmeticValidator());
	}

	CapeRegistryLoader(CapeCosmeticValidator validator) {
		this.validator = Objects.requireNonNull(validator, "Cape Cosmetic 校验器不能为 null。");
	}

	public CapeRegistryLoadResult load(Path capesRoot) throws IOException {
		Objects.requireNonNull(capesRoot, "Cape 资产根目录不能为 null。");
		Files.createDirectories(capesRoot);

		List<Path> children = directChildren(capesRoot);
		Set<CapeId> knownExistingIds = new HashSet<>();
		// 先保留全部实际名称。目录判断失败或被同名普通文件占位时也不能推断删除。
		children.forEach(path -> CapeRegistryLoadResult.addKnownId(knownExistingIds, path.getFileName().toString()));
		List<Path> candidates = children.stream().filter(Files::isDirectory).toList();
		List<CapeCosmeticDefinition> definitions = new ArrayList<>();
		List<CapeRejectedEntry> rejectedEntries = new ArrayList<>();

		for (Path candidate : candidates) {
			CapeCosmeticValidationResult validationResult = validator.validate(candidate);

			if (validationResult.isSuccess()) {
				definitions.add(validationResult.definition().orElseThrow());
			} else {
				rejectedEntries.add(new CapeRejectedEntry(candidate, validationResult.issues()));
			}
		}

		return new CapeRegistryLoadResult(new CapeRegistry(definitions), rejectedEntries, knownExistingIds);
	}

	private static List<Path> directChildren(Path capesRoot) throws IOException {
		try (Stream<Path> children = Files.list(capesRoot)) {
			return children
					.sorted(Comparator.comparing(path -> path.getFileName().toString()))
					.toList();
		} catch (UncheckedIOException exception) {
			throw exception.getCause();
		}
	}
}
