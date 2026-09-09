package vanillafashion.client.fashion;

import java.util.*;
import vanillafashion.fashion.*;
import vanillafashion.network.FullPlayerFashionRemovePayload;

/** 只在客户端执行队列使用；LEFT 清除 membership，不保留跨成员版本历史。 */
public final class ClientFullPlayerFashionRegistry {
    public enum State { UNSUPPORTED, UNKNOWN, UNAVAILABLE, KNOWN }
    public enum Acceptance { APPLIED, IDEMPOTENT, STALE, IGNORED, PROTOCOL_ERROR }
    private State state=State.UNSUPPORTED;
    private Object connection;
    private boolean protocolFailed;
    private final Map<UUID,FullPlayerFashionState> entries=new HashMap<>();
    public void begin(Object connection) { clear(); this.connection=Objects.requireNonNull(connection); state=State.UNKNOWN; }
    public boolean matches(Object connection) { return connection!=null && this.connection==connection; }
    public boolean disconnect(Object connection) { if (!matches(connection)) return false; clear(); return true; }
    public void unsupported(Object connection) { if (matches(connection) && state==State.UNKNOWN) state=State.UNSUPPORTED; }
    public boolean replace(Object connection, FullPlayerFashionSnapshot snapshot) {
        if (!matches(connection) || protocolFailed) return false;
        entries.clear(); snapshot.entries().forEach(entry -> entries.put(entry.playerId(),entry.state()));
        state=snapshot.available()?State.KNOWN:State.UNAVAILABLE; return true;
    }
    public Acceptance update(Object connection, FullPlayerFashionEntry entry) {
        if (state!=State.KNOWN) return Acceptance.IGNORED;
        return merge(connection,entry);
    }
    public Acceptance result(Object connection, FullPlayerFashionEntry entry) { return merge(connection,entry); }
    private Acceptance merge(Object connection, FullPlayerFashionEntry entry) {
        if (!matches(connection) || protocolFailed) return Acceptance.IGNORED;
        var old=entries.get(entry.playerId()); var next=entry.state();
        if (old!=null) {
            if (next.revision()<old.revision()) return Acceptance.STALE;
            if (next.revision()==old.revision()) return old.equals(next)?Acceptance.IDEMPOTENT:fail();
        } else if (entries.size()>=FullPlayerFashionSnapshot.MAX_PLAYERS) return fail();
        entries.put(entry.playerId(),next); return Acceptance.APPLIED;
    }
    public Acceptance remove(Object connection, FullPlayerFashionRemovePayload payload) {
        if (!matches(connection) || state!=State.KNOWN || protocolFailed) return Acceptance.IGNORED;
        if (payload.reason()==FullPlayerFashionRemovePayload.Reason.DEFAULT) return update(connection,new FullPlayerFashionEntry(payload.playerId(),FullPlayerFashionState.defaults(payload.revision())));
        var old=entries.get(payload.playerId());
        if (old!=null && payload.revision()<old.revision()) return Acceptance.STALE;
        entries.remove(payload.playerId()); return Acceptance.APPLIED;
    }
    private Acceptance fail() { entries.clear(); protocolFailed=true; state=State.UNAVAILABLE; return Acceptance.PROTOCOL_ERROR; }
    public Optional<FullPlayerFashionState> find(UUID id) { return state==State.KNOWN?Optional.ofNullable(entries.get(id)):Optional.empty(); }
    public Optional<FullPlayerFashionState> selfAuthority(UUID id) { return protocolFailed?Optional.empty():Optional.ofNullable(entries.get(id)); }
    public State state() { return state; }
    public int size() { return entries.size(); }
    public boolean protocolFailed() { return protocolFailed; }
    public void clear() { connection=null; entries.clear(); protocolFailed=false; state=State.UNSUPPORTED; }
}
