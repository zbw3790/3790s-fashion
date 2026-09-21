package dev.zbw3790.fashion.armor;

import java.util.*;
import dev.zbw3790.fashion.cape.CapeAssetHash;

/** 不含本地路径的服务器资源事实；不可用与可信空集合不同。 */
public record ArmorRegistrySnapshot(boolean available,List<Entry> entries) {
    public static final int MAX_STYLES=128, MAX_ASSETS=256, MAX_TOTAL_BYTES=4*1024*1024;
    public ArmorRegistrySnapshot {
        entries=entries.stream().sorted(Comparator.comparing(Entry::id)).toList();
        if(entries.size()>MAX_STYLES || (!available && !entries.isEmpty())) throw new IllegalArgumentException("盔甲 Registry 数量或可用状态无效。");
        var ids=new HashSet<ArmorStyleId>(); var hashes=new HashSet<String>();
        for(var entry:entries) { if(!ids.add(entry.id())) throw new IllegalArgumentException("盔甲样式 ID 重复。"); hashes.addAll(entry.hashes().values()); }
        if(hashes.size()>MAX_ASSETS) throw new IllegalArgumentException("盔甲资源数量超限。");
    }
    public record Entry(ArmorStyleId id,String name,Set<ArmorSlot> slots,Map<ArmorGeometry,String> hashes) {
        public Entry {
            Objects.requireNonNull(id); ArmorMetadata.validateName(name);slots=Set.copyOf(slots);hashes=Map.copyOf(hashes);
            if(slots.isEmpty() || hashes.isEmpty()) throw new IllegalArgumentException("盔甲定义为空。");
            hashes.values().forEach(hash->CapeAssetHash.requireValid(hash,"盔甲 hash "));
            for(var slot:slots) if(!hashes.containsKey(ArmorGeometry.forSlot(slot))) throw new IllegalArgumentException("盔甲定义缺层。");
        }
    }
    public Optional<Entry> find(ArmorStyleId id) { return entries.stream().filter(e->e.id().equals(id)).findFirst(); }
    public boolean supports(ArmorStyleId id,ArmorSlot slot) { return available && find(id).filter(e->e.slots().contains(slot)).isPresent(); }
    public Set<String> requiredHashes() { var hashes=new HashSet<String>();entries.forEach(e->hashes.addAll(e.hashes().values()));return Set.copyOf(hashes); }
    public ArmorSelections effective(ArmorSelections stored) {
        var result=stored;
        for(var slot:ArmorSlot.CANONICAL_ORDER) if(stored.get(slot) instanceof ArmorSelection.Custom custom && !supports(custom.id(),slot)) result=result.with(slot,ArmorSelection.ORIGINAL);
        return result;
    }
    public static ArmorRegistrySnapshot unavailable() { return new ArmorRegistrySnapshot(false,List.of()); }
    public static ArmorRegistrySnapshot empty() { return new ArmorRegistrySnapshot(true,List.of()); }
}
