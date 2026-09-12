package dev.zbw3790.fashion.fashion;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** 规划在线集合变化后的消息；不依赖加入者的 Mod 能力。 */
public final class PlayerFashionSyncPlanner {
	private PlayerFashionSyncPlanner() {
	}

	public static Plan join(Collection<UUID> onlineAfter, int previousCount, UUID joining,
			PlayerFashionService service) {
		if (!onlineAfter.contains(joining)) {
			throw new IllegalArgumentException("加入后的在线集合必须包含加入者。");
		}
		var snapshot = PlayerFashionSnapshotBuilder.build(onlineAfter, service);
		boolean all = !snapshot.snapshotAvailable()
				|| previousCount > PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES;
		return new Plan(snapshot, all,
				all ? Optional.empty() : Optional.of(new PlayerFashionEntry(
						joining, PlayerFashionAuthoritativeState.fromService(joining, service))),
				Optional.empty());
	}

	public static Plan leave(Collection<UUID> onlineAfter, int previousCount, UUID leaving,
			PlayerFashionService service) {
		if (onlineAfter.contains(leaving)) {
			throw new IllegalArgumentException("离开后的在线集合不能包含离开者。");
		}
		var snapshot = PlayerFashionSnapshotBuilder.build(onlineAfter, service);
		boolean all = !snapshot.snapshotAvailable()
				|| previousCount > PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES;
		return new Plan(snapshot, all, Optional.empty(), Optional.of(leaving));
	}

	public record Plan(PlayerFashionSnapshot snapshot, boolean snapshotForAll,
			Optional<PlayerFashionEntry> update, Optional<UUID> remove) {
	}
}
