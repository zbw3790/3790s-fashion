package dev.zbw3790.fashion.fashion;

import java.util.Objects;
import java.util.UUID;

/** 一个明确已知的玩家状态；条目存在表示权威已知，不表示客户端自行推断。 */
public record PlayerFashionEntry(UUID playerId, PlayerFashionAuthoritativeState state) {
	public PlayerFashionEntry {
		Objects.requireNonNull(playerId, "玩家 UUID 不能为 null。");
		Objects.requireNonNull(state, "玩家权威时装状态不能为 null。");
	}
}
