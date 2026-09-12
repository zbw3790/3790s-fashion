package dev.zbw3790.fashion.outfit;

import java.util.*;
import dev.zbw3790.fashion.cape.CapeAssetHash;

/** 只传递可信定义和已验证的模型内容标识；未知根不能冒充空的可信集合。 */
public record OutfitRegistrySnapshot(boolean available, List<Entry> entries) {
    public static final int MAX_OUTFITS = 256;
    public static final int MAX_ASSETS = 512;
    public OutfitRegistrySnapshot {
        Objects.requireNonNull(entries);
        if (entries.size() > MAX_OUTFITS || (!available && !entries.isEmpty())) throw new IllegalArgumentException("装束定义数量或可用性无效。");
        var sorted = new TreeMap<OutfitId, Entry>();
        for (Entry entry : entries) if (sorted.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("装束定义 ID 重复。");
        entries = List.copyOf(sorted.values());
    }
    public static OutfitRegistrySnapshot unavailable() { return new OutfitRegistrySnapshot(false, List.of()); }
    public static OutfitRegistrySnapshot from(OutfitRegistryLoadResult loaded) {
        if (!loaded.knowledge().trustworthy() || loaded.registry().size() > MAX_OUTFITS) return unavailable();
        return new OutfitRegistrySnapshot(true, loaded.registry().entries().stream().map(entry -> {
            var hashes = new EnumMap<OutfitModel, String>(OutfitModel.class);
            for (OutfitModel model : OutfitModel.CANONICAL_ORDER) {
                if (entry.model(model) instanceof OutfitModelResource.Valid valid) hashes.put(model, valid.asset().sha256());
            }
            return new Entry(entry.id(), entry.metadata().parts(), entry.metadata().models(), hashes);
        }).toList());
    }
    public Set<String> requiredHashes() {
        var hashes = new TreeSet<String>(); entries.forEach(entry -> hashes.addAll(entry.validModels().values()));
        return Collections.unmodifiableSet(hashes);
    }
    public record Entry(OutfitId id, Set<OutfitPart> parts, Set<OutfitModel> declaredModels, Map<OutfitModel, String> validModels) {
        public Entry {
            Objects.requireNonNull(id); parts = Set.copyOf(parts); declaredModels = Set.copyOf(declaredModels);
            if (parts.isEmpty() || declaredModels.isEmpty() || !declaredModels.containsAll(validModels.keySet())) throw new IllegalArgumentException("装束部位或声明模型无效。");
            var hashes = new EnumMap<OutfitModel, String>(OutfitModel.class);
            validModels.forEach((model, hash) -> hashes.put(Objects.requireNonNull(model), CapeAssetHash.requireValid(hash, "装束模型 hash ")));
            validModels = Collections.unmodifiableMap(hashes);
        }
    }
}
