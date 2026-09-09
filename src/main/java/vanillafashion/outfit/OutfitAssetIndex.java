package vanillafashion.outfit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import vanillafashion.cape.CapeAssetHash;

public final class OutfitAssetIndex {
	public record Reference(OutfitId id, OutfitModel model) {
		public Reference { Objects.requireNonNull(id); Objects.requireNonNull(model); }
	}
	private final Map<String, OutfitAsset> content;
	private final Map<Reference, String> references;

	private OutfitAssetIndex(Map<String, OutfitAsset> content, Map<Reference, String> references) {
		this.content = Collections.unmodifiableMap(new TreeMap<>(content));
		this.references = Collections.unmodifiableMap(new LinkedHashMap<>(references));
	}
	public static OutfitAssetIndex from(OutfitRegistry registry) {
		Map<String, OutfitAsset> content = new TreeMap<>();
		Map<Reference, String> references = new LinkedHashMap<>();
		for (OutfitRegistryEntry entry : registry.entries()) {
			for (OutfitModel model : entry.metadata().models()) {
				if (entry.model(model) instanceof OutfitModelResource.Valid valid) {
					OutfitAsset asset = valid.asset();
					OutfitAsset previous = content.putIfAbsent(asset.sha256(), asset);
					if (previous != null && !previous.equals(asset)) throw new IllegalStateException("同 SHA 内容发生冲突。");
					references.put(new Reference(entry.id(), model), asset.sha256());
				}
			}
		}
		return new OutfitAssetIndex(content, references);
	}
	public Optional<OutfitAsset> find(String hash) {
		return Optional.ofNullable(content.get(CapeAssetHash.requireValid(hash, "装束内容 hash ")));
	}
	public Map<Reference, String> references() { return references; }
	public int size() { return content.size(); }
}
