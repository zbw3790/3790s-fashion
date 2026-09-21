package dev.zbw3790.fashion.client.armor;

import java.util.*;
import dev.zbw3790.fashion.armor.*;

/** 一次连接的可信完整定义；冲突初始化停用至重连。 */
public final class ClientArmorRegistry {
    public enum State { UNSUPPORTED, UNKNOWN, UNAVAILABLE, KNOWN }
    public enum Result { APPLIED, IDEMPOTENT, STALE, CONFLICT }
    private Object connection;
    private State state=State.UNSUPPORTED;
    private ArmorRegistrySnapshot snapshot, initialSnapshot;
    private long generation;
    private Map<ArmorStyleId,ArmorRegistrySnapshot.Entry> entries=Map.of();
    private boolean failed;
    public void begin(Object connection) { clear(); this.connection=Objects.requireNonNull(connection); state=State.UNKNOWN; }
    public boolean matches(Object connection) { return connection!=null && this.connection==connection; }
    public boolean disconnect(Object connection) { if (!matches(connection)) return false; clear(); return true; }
    public void unsupported(Object connection) { if (matches(connection) && snapshot==null) state=State.UNSUPPORTED; }
    public Result receive(Object connection,long generation,ArmorRegistrySnapshot next) {
        if (!matches(connection)) return Result.STALE;
        if (failed) return Result.CONFLICT;
        Objects.requireNonNull(next);
        if (generation<0) return conflict();
        if (snapshot!=null) {
            if(generation<this.generation) return Result.STALE;
            if(generation==this.generation) return snapshot.equals(next)?Result.IDEMPOTENT:conflict();
        }
        this.generation=generation;install(next);return Result.APPLIED;
    }
    public long generation() { return generation; }
    private Result conflict() { failed=true; state=State.UNAVAILABLE; entries=Map.of(); return Result.CONFLICT; }
    private void install(ArmorRegistrySnapshot next) {
        snapshot=next; state=next.available()?State.KNOWN:State.UNAVAILABLE;
        var values=new TreeMap<ArmorStyleId,ArmorRegistrySnapshot.Entry>(); next.entries().forEach(entry -> values.put(entry.id(),entry)); entries=Map.copyOf(values);
    }
    public Optional<ArmorRegistrySnapshot.Entry> find(ArmorStyleId id) { return state==State.KNOWN?Optional.ofNullable(entries.get(id)):Optional.empty(); }
    public Set<String> requiredHashes() { return state==State.KNOWN?snapshot.requiredHashes():Set.of(); }
    /** 上界沿用已验证 Snapshot；明确排序，不依赖 Map.copyOf 的遍历顺序。 */
    public List<ArmorRegistrySnapshot.Entry> entries() {
        return state==State.KNOWN ? snapshot.entries() : List.of();
    }
    public Object connection() {return connection;}
    public State state() { return state; }
    public void clear() { connection=null; state=State.UNSUPPORTED; snapshot=null; initialSnapshot=null; generation=0; entries=Map.of(); failed=false; }
}
