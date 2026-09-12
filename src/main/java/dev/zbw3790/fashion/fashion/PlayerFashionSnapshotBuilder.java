package dev.zbw3790.fashion.fashion;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

public final class PlayerFashionSnapshotBuilder {
	private PlayerFashionSnapshotBuilder() {
	}

	public static PlayerFashionSnapshot build(Collection<UUID> onlinePlayers, PlayerFashionService service) {
		var players = new HashSet<>(onlinePlayers);
		if (players.size() != onlinePlayers.size() || players.contains(null)) {
			throw new IllegalArgumentException("在线玩家集合必须由非重复且非 null 的 UUID 构成。");
		}
		if (players.size() > PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES
				|| !service.canProvideAuthoritativeSnapshot()) {
			return PlayerFashionSnapshot.unavailable();
		}
		return new PlayerFashionSnapshot(true, players.stream()
				.map(id -> new PlayerFashionEntry(id, PlayerFashionAuthoritativeState.fromService(id, service)))
				.toList());
	}
}
