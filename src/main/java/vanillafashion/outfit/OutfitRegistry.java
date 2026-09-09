package vanillafashion.outfit;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class OutfitRegistry {
	private final Map<OutfitId, OutfitRegistryEntry> entries;
	public OutfitRegistry(Collection<OutfitRegistryEntry> definitions) {
		TreeMap<OutfitId, OutfitRegistryEntry> copy = new TreeMap<>();
		for (OutfitRegistryEntry entry : definitions) {
			if (copy.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("装束 ID 重复。");
		}
		entries = Collections.unmodifiableMap(copy);
	}
	public List<OutfitRegistryEntry> entries() { return List.copyOf(entries.values()); }
	public Optional<OutfitRegistryEntry> find(OutfitId id) { return Optional.ofNullable(entries.get(id)); }
	public int size() { return entries.size(); }
}
