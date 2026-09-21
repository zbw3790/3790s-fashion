package dev.zbw3790.fashion.network;

import java.util.*;
import dev.zbw3790.fashion.armor.*;

/** 盔甲的连接内容视图；同名 style 与其他资源域没有共享可变状态。 */
public final class ArmorAssetRequestTracker {
    public static final int MAX_UNIQUE=512,MAX_ATTEMPTS=1024;
    private static final class View {
        private long generation;private ArmorRegistryLoadResult loaded;
        private final AssetRequestBudget budget=new AssetRequestBudget(MAX_UNIQUE,MAX_ATTEMPTS);
        private View(long generation,ArmorRegistryLoadResult loaded) {this.generation=generation;this.loaded=loaded;}
    }
    private final Map<Object,View> connections=new IdentityHashMap<>();
    public void open(Object connection,long generation,ArmorRegistryLoadResult loaded) {connections.putIfAbsent(Objects.requireNonNull(connection),new View(generation,loaded));}
    public boolean refresh(Object connection,long generation,ArmorRegistryLoadResult loaded) {
        var view=connections.get(connection);if(view==null || generation<=view.generation) return false;
        view.generation=generation;view.loaded=loaded;return true;
    }
    public List<String> claim(Object connection,ArmorAssetRequestPayload payload) {
        var view=connections.get(connection);return view==null?List.of():view.budget.claim(payload.sha256Hashes(),view.loaded.snapshot().requiredHashes());
    }
    public Optional<ArmorAsset> asset(Object connection,String hash) {var view=connections.get(connection);return view==null?Optional.empty():Optional.ofNullable(view.loaded.assets().get(hash));}
    public int attempts(Object connection) {var view=connections.get(connection);return view==null?0:view.budget.attempts();}
    public void close(Object connection) {connections.remove(connection);}
    public void clear() {connections.clear();}
    public int connectionCount() {return connections.size();}
}
