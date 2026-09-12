package dev.zbw3790.fashion.fashion;
import java.util.*;
/** 完整在线集合；不可用不能用截断列表或默认值冒充。 */
public record FullPlayerFashionSnapshot(boolean available, List<FullPlayerFashionEntry> entries) {
    public static final int MAX_PLAYERS = 1024;
    public FullPlayerFashionSnapshot {
        Objects.requireNonNull(entries, "在线集合不能为 null。");
        if (entries.size() > MAX_PLAYERS || (!available && !entries.isEmpty())) throw new IllegalArgumentException("完整状态数量与可用性不一致。");
        Set<UUID> ids = new HashSet<>();
        for (var entry : entries) if (!ids.add(Objects.requireNonNull(entry, "在线条目不能为 null。").playerId())) throw new IllegalArgumentException("完整状态玩家重复。");
        entries = entries.stream().sorted(Comparator.comparing(FullPlayerFashionEntry::playerId)).toList();
    }
    public static FullPlayerFashionSnapshot unavailable() { return new FullPlayerFashionSnapshot(false, List.of()); }
}
