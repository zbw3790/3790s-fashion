package vanillafashion.fashion;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerFashionSnapshot(boolean snapshotAvailable, List<PlayerFashionEntry> entries) {
	public static final int MAX_PLAYER_FASHION_ENTRIES = 1024;

	public PlayerFashionSnapshot {
		Objects.requireNonNull(entries, "玩家时装条目不能为 null。");
		if (entries.size() > MAX_PLAYER_FASHION_ENTRIES || (!snapshotAvailable && !entries.isEmpty())) {
			throw new IllegalArgumentException("玩家时装 Snapshot 超限或不可用状态携带条目。");
		}
		var seen = new HashSet<UUID>();
		for (var entry : entries) {
			if (!seen.add(Objects.requireNonNull(entry, "玩家时装条目不能为 null。").playerId())) {
				throw new IllegalArgumentException("玩家时装 Snapshot 包含重复 UUID。");
			}
		}
		entries = entries.stream().sorted(Comparator.comparing(entry -> entry.playerId().toString())).toList();
	}

	public static PlayerFashionSnapshot unavailable() {
		return new PlayerFashionSnapshot(false, List.of());
	}
}
