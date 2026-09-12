package dev.zbw3790.fashion.cape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CapeRegistry {
	private static final CapeRegistry EMPTY = new CapeRegistry(List.of());

	private final Map<CapeId, CapeCosmeticDefinition> definitionsById;
	private final List<CapeCosmeticDefinition> definitions;

	public CapeRegistry(Collection<CapeCosmeticDefinition> definitions) {
		Objects.requireNonNull(definitions, "Cape Registry 定义集合不能为 null。");

		List<CapeCosmeticDefinition> sortedDefinitions = new ArrayList<>(definitions);
		sortedDefinitions.sort(Comparator.comparing(definition -> definition.id().value()));

		Map<CapeId, CapeCosmeticDefinition> definitionsById = new LinkedHashMap<>();

		for (CapeCosmeticDefinition definition : sortedDefinitions) {
			Objects.requireNonNull(definition, "Cape Registry 定义不能为 null。");

			if (definitionsById.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalArgumentException("Cape Registry 不能包含重复的 Cosmetic ID：" + definition.id() + "。");
			}
		}

		this.definitionsById = Collections.unmodifiableMap(definitionsById);
		this.definitions = List.copyOf(definitionsById.values());
	}

	public static CapeRegistry empty() {
		return EMPTY;
	}

	public Optional<CapeCosmeticDefinition> find(CapeId id) {
		return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(id, "查询 ID 不能为 null。")));
	}

	public List<CapeCosmeticDefinition> definitions() {
		return definitions;
	}

	public int size() {
		return definitions.size();
	}

	public boolean isEmpty() {
		return definitions.isEmpty();
	}
}
