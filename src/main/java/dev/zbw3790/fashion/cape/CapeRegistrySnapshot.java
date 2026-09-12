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

public final class CapeRegistrySnapshot {
	public static final int MAX_CAPE_ENTRIES = 1024;
	private static final CapeRegistrySnapshot EMPTY = new CapeRegistrySnapshot(List.of());

	private final Map<CapeId, CapeCosmeticMetadata> entriesById;
	private final List<CapeCosmeticMetadata> entries;

	public CapeRegistrySnapshot(Collection<CapeCosmeticMetadata> entries) {
		Objects.requireNonNull(entries, "Cape Registry Snapshot 条目集合不能为 null。");

		if (entries.size() > MAX_CAPE_ENTRIES) {
			throw new IllegalArgumentException(
					"Cape Registry Snapshot 条目数不能超过 " + MAX_CAPE_ENTRIES + "。"
			);
		}

		List<CapeCosmeticMetadata> sortedEntries = new ArrayList<>(entries.size());

		for (CapeCosmeticMetadata entry : entries) {
			sortedEntries.add(Objects.requireNonNull(entry, "Cape Registry Snapshot 条目不能为 null。"));
		}

		sortedEntries.sort(Comparator.comparing(entry -> entry.id().value()));

		Map<CapeId, CapeCosmeticMetadata> entriesById = new LinkedHashMap<>();

		for (CapeCosmeticMetadata entry : sortedEntries) {
			if (entriesById.putIfAbsent(entry.id(), entry) != null) {
				throw new IllegalArgumentException(
						"Cape Registry Snapshot 不能包含重复的 CapeId：" + entry.id() + "。"
				);
			}
		}

		this.entriesById = Collections.unmodifiableMap(entriesById);
		this.entries = List.copyOf(entriesById.values());
	}

	public static CapeRegistrySnapshot empty() {
		return EMPTY;
	}

	public static CapeRegistrySnapshot from(CapeRegistry registry) {
		Objects.requireNonNull(registry, "服务器 Cape Registry 不能为 null。");

		List<CapeCosmeticMetadata> metadata = registry.definitions().stream()
				.map(definition -> new CapeCosmeticMetadata(
						definition.id(),
						definition.cape().sha256(),
						definition.elytra().map(CapeTextureAsset::sha256)
				))
				.toList();

		return new CapeRegistrySnapshot(metadata);
	}

	public List<CapeCosmeticMetadata> entries() {
		return entries;
	}

	public Optional<CapeCosmeticMetadata> find(CapeId id) {
		return Optional.ofNullable(entriesById.get(Objects.requireNonNull(id, "查询 CapeId 不能为 null。")));
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}
}
