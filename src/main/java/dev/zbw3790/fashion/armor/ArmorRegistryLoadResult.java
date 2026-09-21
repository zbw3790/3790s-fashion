package dev.zbw3790.fashion.armor;

import java.util.*;

/** metadata 与资产字节必须来自同一快照，失败不发布部分根。 */
public record ArmorRegistryLoadResult(ArmorRegistrySnapshot snapshot,Map<String,ArmorAsset> assets,List<String> diagnostics) {
    public ArmorRegistryLoadResult {
        Objects.requireNonNull(snapshot);assets=Map.copyOf(assets);diagnostics=List.copyOf(diagnostics);
        if(!assets.keySet().equals(snapshot.requiredHashes())) throw new IllegalArgumentException("盔甲快照与内容集合不一致。");
        assets.forEach((hash,asset)->{if(!hash.equals(asset.hash())) throw new IllegalArgumentException("盔甲资产键不匹配。");});
        if(assets.values().stream().mapToLong(ArmorAsset::size).sum()>ArmorRegistrySnapshot.MAX_TOTAL_BYTES) throw new IllegalArgumentException("盔甲快照字节超限。");
    }
    public static ArmorRegistryLoadResult unavailable(String reason) {return new ArmorRegistryLoadResult(ArmorRegistrySnapshot.unavailable(),Map.of(),List.of(reason));}
    public static ArmorRegistryLoadResult empty() {return new ArmorRegistryLoadResult(ArmorRegistrySnapshot.empty(),Map.of(),List.of());}
}
