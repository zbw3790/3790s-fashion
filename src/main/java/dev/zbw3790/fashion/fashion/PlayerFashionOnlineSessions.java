package dev.zbw3790.fashion.fashion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 仅在服务器线程使用；以连接身份处理重连，不依赖 PlayerList 删除时机。 */
public final class PlayerFashionOnlineSessions<T> {
	private final Map<UUID, T> sessions = new LinkedHashMap<>();

	public void join(UUID playerId, T session) {
		sessions.put(Objects.requireNonNull(playerId), Objects.requireNonNull(session));
	}

	public boolean leave(UUID playerId, T session) {
		Objects.requireNonNull(session, "连接身份不能为 null。");
		if (sessions.get(playerId) != session) {
			return false;
		}
		sessions.remove(playerId);
		return true;
	}

	public Map<UUID, T> snapshot() {
		return Map.copyOf(sessions);
	}

	public int size() {
		return sessions.size();
	}
}
