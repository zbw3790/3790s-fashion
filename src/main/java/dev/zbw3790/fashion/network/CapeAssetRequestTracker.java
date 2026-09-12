package dev.zbw3790.fashion.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.zbw3790.fashion.cape.CapeAssetLimits;

public final class CapeAssetRequestTracker {
	private final Map<UUID, ConnectionState> connections = new HashMap<>();

	public void open(UUID playerId, Object connection) {
		connections.put(playerId, new ConnectionState(connection));
	}

	public boolean close(UUID playerId, Object connection) {
		ConnectionState state = connections.get(playerId);

		if (state == null || state.connection != connection) {
			return false;
		}

		connections.remove(playerId);
		return true;
	}

	public ClaimResult claim(UUID playerId, Object connection, String sha256) {
		ConnectionState state = connections.get(playerId);

		if (state == null || state.connection != connection) {
			return ClaimResult.STALE_CONNECTION;
		}

		if (state.requestedHashes >= CapeAssetLimits.MAX_REQUESTED_HASHES_PER_CONNECTION) {
			return ClaimResult.LIMIT_REACHED;
		}

		state.requestedHashes++;

		if (!state.claimedHashes.add(sha256)) {
			return ClaimResult.DUPLICATE;
		}

		return ClaimResult.ACCEPTED;
	}

	public void clear() { connections.clear(); }

	public int connectionCount() {
		return connections.size();
	}

	private static final class ConnectionState {
		private final Object connection;
		private int requestedHashes;
		private final Set<String> claimedHashes = new HashSet<>();

		private ConnectionState(Object connection) {
			this.connection = Objects.requireNonNull(connection, "资产请求连接身份不能为 null。");
		}
	}

	public enum ClaimResult {
		ACCEPTED,
		DUPLICATE,
		LIMIT_REACHED,
		STALE_CONNECTION
	}
}
