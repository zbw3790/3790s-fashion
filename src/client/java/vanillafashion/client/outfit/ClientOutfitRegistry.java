package vanillafashion.client.outfit;

import java.util.*;
import vanillafashion.outfit.*;

/** 一次连接的可信完整定义；冲突初始化停用至重连。 */
public final class ClientOutfitRegistry {
    public enum State { UNSUPPORTED, UNKNOWN, UNAVAILABLE, KNOWN }
    public enum Result { APPLIED, IDEMPOTENT, STALE, CONFLICT }
    private Object connection;
    private State state=State.UNSUPPORTED;
    private OutfitRegistrySnapshot snapshot;
    private Map<OutfitId,OutfitRegistrySnapshot.Entry> entries=Map.of();
    private boolean failed;
    public void begin(Object connection) { clear(); this.connection=Objects.requireNonNull(connection); state=State.UNKNOWN; }
    public boolean matches(Object connection) { return connection!=null && this.connection==connection; }
    public boolean disconnect(Object connection) { if (!matches(connection)) return false; clear(); return true; }
    public void unsupported(Object connection) { if (matches(connection) && snapshot==null) state=State.UNSUPPORTED; }
    public Result replace(Object connection, OutfitRegistrySnapshot next) {
        if (!matches(connection)) return Result.STALE;
        if (failed) return Result.CONFLICT;
        if (snapshot!=null) {
            if (snapshot.equals(next)) return Result.IDEMPOTENT;
            failed=true; state=State.UNAVAILABLE; entries=Map.of(); return Result.CONFLICT;
        }
        snapshot=next; state=next.available()?State.KNOWN:State.UNAVAILABLE;
        var values=new TreeMap<OutfitId,OutfitRegistrySnapshot.Entry>(); next.entries().forEach(entry -> values.put(entry.id(),entry)); entries=Map.copyOf(values);
        return Result.APPLIED;
    }
    public Optional<OutfitRegistrySnapshot.Entry> find(OutfitId id) { return state==State.KNOWN?Optional.ofNullable(entries.get(id)):Optional.empty(); }
    public Set<String> requiredHashes() { return state==State.KNOWN?snapshot.requiredHashes():Set.of(); }
    /** 上界沿用已验证 Snapshot；明确排序，不依赖 Map.copyOf 的遍历顺序。 */
    public List<OutfitRegistrySnapshot.Entry> entries() {
        return state==State.KNOWN ? snapshot.entries() : List.of();
    }
    public State state() { return state; }
    public void clear() { connection=null; state=State.UNSUPPORTED; snapshot=null; entries=Map.of(); failed=false; }
}
