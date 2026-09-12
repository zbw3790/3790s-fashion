package dev.zbw3790.fashion.client.fashion;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.fashion.FashionAuthorityRoute;
import dev.zbw3790.fashion.fashion.FullPlayerFashionState;
import dev.zbw3790.fashion.fashion.FullPlayerFashionSnapshot;
import dev.zbw3790.fashion.fashion.FullPlayerFashionEntry;
import dev.zbw3790.fashion.network.FullPlayerFashionRemovePayload;
import dev.zbw3790.fashion.client.network.ClientFullFashionRequestTracker;
import dev.zbw3790.fashion.fashion.PlayerFashionAuthoritativeState;
import dev.zbw3790.fashion.fashion.PlayerFashionEntry;
import dev.zbw3790.fashion.fashion.PlayerFashionSnapshot;

/** 连接内状态仅在客户端线程修改；未知玩家与已知 Vanilla 使用不同表达。 */
public final class ClientPlayerFashionRegistry {
	private State state = State.UNINITIALIZED;
    private FashionAuthorityRoute route = FashionAuthorityRoute.UNDECIDED;
    private final ClientFullPlayerFashionRegistry full = new ClientFullPlayerFashionRegistry();
    private final ClientFullFashionRequestTracker fullRequests = new ClientFullFashionRequestTracker();
    public FashionAuthorityRoute route() { return route; }
    public ClientFullPlayerFashionRegistry full() { return full; }
    public ClientFullFashionRequestTracker fullRequests() { return fullRequests; }
    public Object connectionIdentity() { return connection; }
    public Optional<FullPlayerFashionState> fullAuthority(UUID id) { return route==FashionAuthorityRoute.V2?full.find(id):Optional.empty(); }
    public Optional<FullPlayerFashionState> fullSelfAuthority(UUID id) { return route==FashionAuthorityRoute.V2?full.selfAuthority(id):Optional.empty(); }
    public void beginConnection(Object connection, Object requestConnection) { beginConnection(connection); fullRequests.begin(requestConnection); }
    public void receiveFullSnapshot(Object connection, FullPlayerFashionSnapshot snapshot) {
        if (this.connection!=connection || route==FashionAuthorityRoute.LEGACY) return;
        route=FashionAuthorityRoute.V2; entries.clear(); resultConfirmedSelf=Optional.empty(); full.replace(connection,snapshot);
    }
    public ClientFullPlayerFashionRegistry.Acceptance receiveFullUpdate(Object connection, FullPlayerFashionEntry entry) {
        return route==FashionAuthorityRoute.V2?full.update(connection,entry):ClientFullPlayerFashionRegistry.Acceptance.IGNORED;
    }
    public ClientFullPlayerFashionRegistry.Acceptance receiveFullRemove(Object connection, FullPlayerFashionRemovePayload payload) {
        return route==FashionAuthorityRoute.V2?full.remove(connection,payload):ClientFullPlayerFashionRegistry.Acceptance.IGNORED;
    }

	private final Map<UUID, PlayerFashionAuthoritativeState> entries = new HashMap<>();
	private Object connection;
	private Optional<SelfAuthority> resultConfirmedSelf = Optional.empty();

	public void beginConnection(Object connection) {
		clear();
		this.connection = Objects.requireNonNull(connection, "连接身份不能为 null。");
        full.begin(connection); fullRequests.begin(connection);
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
        if (route==FashionAuthorityRoute.V2) return; route=FashionAuthorityRoute.LEGACY; full.unsupported(connection);
		resultConfirmedSelf = Optional.empty();
		entries.clear();
		state = snapshot.snapshotAvailable() ? State.AVAILABLE : State.UNAVAILABLE;
		snapshot.entries().forEach(entry -> entries.put(entry.playerId(), entry.state()));
	}

	public void update(PlayerFashionEntry entry) {
        if (route==FashionAuthorityRoute.V2) return;
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
        if (route==FashionAuthorityRoute.V2) return;
		resultConfirmedSelf = resultConfirmedSelf.filter(entry -> !entry.playerId().equals(playerId));
		if (state == State.AVAILABLE) {
			entries.remove(playerId);
		}
	}

	public Optional<PlayerFashionAuthoritativeState> getKnownState(UUID playerId) {
        if (route==FashionAuthorityRoute.V2) return full.find(playerId).map(FullPlayerFashionState::capeProjection);
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
        if (route==FashionAuthorityRoute.V2) return switch(full.state()) { case KNOWN -> State.AVAILABLE; case UNAVAILABLE -> State.UNAVAILABLE; default -> State.UNINITIALIZED; };
        return state;
	}

	/** Result 只补充自身确认，不把单条权威信息升级为完整世界 Snapshot。 */
	public void confirmSelfResult(UUID playerId, PlayerFashionAuthoritativeState authoritativeState) {
        if (route==FashionAuthorityRoute.V2) return;
		var entry = new PlayerFashionEntry(playerId, authoritativeState);
		update(entry);
		resultConfirmedSelf = Optional.of(new SelfAuthority(playerId, authoritativeState));
	}

	public Optional<PlayerFashionAuthoritativeState> selfAuthority(UUID playerId) {
        if (route==FashionAuthorityRoute.V2) return getKnownState(playerId);
		return state == State.AVAILABLE ? getKnownState(playerId)
				: resultConfirmedSelf.filter(entry -> entry.playerId().equals(playerId)).map(SelfAuthority::state);
	}

	public int size() {
        return route==FashionAuthorityRoute.V2?full.size():entries.size();
	}

	public void clear() {
        route=FashionAuthorityRoute.UNDECIDED; full.clear(); fullRequests.clear();
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
