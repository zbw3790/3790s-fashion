package vanillafashion.client.fashion;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import vanillafashion.cape.CapeId;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionSnapshot;

/** 连接内状态仅在客户端线程修改；未知玩家与已知 Vanilla 使用不同表达。 */
public final class ClientPlayerFashionRegistry {
	private State state = State.UNINITIALIZED;
	private final Map<UUID, PlayerFashionAuthoritativeState> entries = new HashMap<>();
	private Object connection;
	private Optional<SelfAuthority> resultConfirmedSelf = Optional.empty();

	public void beginConnection(Object connection) {
		clear();
		this.connection = Objects.requireNonNull(connection, "连接身份不能为 null。");
	}

	public boolean disconnect(Object connection) {
		// 旧连接排队的清理不能清除新连接已接收的 Snapshot。
		if (this.connection == connection) {
			clear();
			return true;
		}
		return false;
	}

	public void replace(PlayerFashionSnapshot snapshot) {
		resultConfirmedSelf = Optional.empty();
		entries.clear();
		state = snapshot.snapshotAvailable() ? State.AVAILABLE : State.UNAVAILABLE;
		snapshot.entries().forEach(entry -> entries.put(entry.playerId(), entry.state()));
	}

	public void update(PlayerFashionEntry entry) {
		if (state != State.AVAILABLE) {
			return;
		}
		if (!entries.containsKey(entry.playerId())
				&& entries.size() >= PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES) {
			replace(PlayerFashionSnapshot.unavailable());
			return;
		}
		entries.put(entry.playerId(), entry.state());
	}

	public void remove(UUID playerId) {
		resultConfirmedSelf = resultConfirmedSelf.filter(entry -> !entry.playerId().equals(playerId));
		if (state == State.AVAILABLE) {
			entries.remove(playerId);
		}
	}

	public Optional<PlayerFashionAuthoritativeState> getKnownState(UUID playerId) {
		return state == State.AVAILABLE ? Optional.ofNullable(entries.get(playerId)) : Optional.empty();
	}

	public Optional<PlayerFashionAuthoritativeState> find(UUID playerId) {
		return getKnownState(playerId);
	}

	public Optional<CapeId> getStoredSelection(UUID playerId) {
		return getKnownState(playerId).flatMap(PlayerFashionAuthoritativeState::storedSelection);
	}

	public Optional<CapeId> getEffectiveSelection(UUID playerId) {
		return getKnownState(playerId).flatMap(PlayerFashionAuthoritativeState::effectiveSelection);
	}

	public State state() {
		return state;
	}

	/** Result 只补充自身确认，不把单条权威信息升级为完整世界 Snapshot。 */
	public void confirmSelfResult(UUID playerId, PlayerFashionAuthoritativeState authoritativeState) {
		var entry = new PlayerFashionEntry(playerId, authoritativeState);
		update(entry);
		resultConfirmedSelf = Optional.of(new SelfAuthority(playerId, authoritativeState));
	}

	public Optional<PlayerFashionAuthoritativeState> selfAuthority(UUID playerId) {
		return state == State.AVAILABLE ? getKnownState(playerId)
				: resultConfirmedSelf.filter(entry -> entry.playerId().equals(playerId)).map(SelfAuthority::state);
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
		state = State.UNINITIALIZED;
		connection = null;
		resultConfirmedSelf = Optional.empty();
	}

	public enum State {
		UNINITIALIZED,
		AVAILABLE,
		UNAVAILABLE
	}

	private record SelfAuthority(UUID playerId, PlayerFashionAuthoritativeState state) { }
}
