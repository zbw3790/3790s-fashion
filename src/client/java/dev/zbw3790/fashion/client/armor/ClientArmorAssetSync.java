package dev.zbw3790.fashion.client.armor;

import java.util.*;
import dev.zbw3790.fashion.network.*;
import dev.zbw3790.fashion.armor.ArmorRegistrySnapshot;

/** 同步 I/O 当前就在客户端队列；未来异步结果也必须持令牌回到此入口。 */
public final class ClientArmorAssetSync {
    public enum Receive { STORED, STALE, NOT_REQUESTED, NOT_AUTHORIZED, INVALID }
    private final ClientArmorRegistry registry;
    private final ClientArmorAssetStore store;
    private final ClientArmorAssetCache cache;
    private Object connection;
    private final Set<String> pending=new HashSet<>(), attempted=new HashSet<>();
    private boolean cacheWarning;
    public ClientArmorAssetSync(ClientArmorRegistry registry, ClientArmorAssetStore store, ClientArmorAssetCache cache) { this.registry=registry; this.store=store; this.cache=cache; }
    public void begin(Object connection) { this.connection=Objects.requireNonNull(connection); pending.clear(); attempted.clear(); store.clear(); cacheWarning=false; registry.begin(connection); }
    public boolean matches(Object connection) { return connection!=null && this.connection==connection; }
    public boolean disconnect(Object connection) {
        if (!matches(connection)) return false; this.connection=null; pending.clear(); attempted.clear(); store.clear(); registry.disconnect(connection); return true;
    }
    public ClientArmorRegistry.Result snapshot(Object connection,long generation,ArmorRegistrySnapshot snapshot) {
        if(!matches(connection)) return ClientArmorRegistry.Result.STALE;
        return installed(connection,registry.receive(connection,generation,snapshot));
    }
    private ClientArmorRegistry.Result installed(Object connection, ClientArmorRegistry.Result result) {
        if (result==ClientArmorRegistry.Result.CONFLICT) { store.clear(); pending.clear(); return result; }
        if (result==ClientArmorRegistry.Result.APPLIED) {
            store.retain(registry.requiredHashes());pending.retainAll(registry.requiredHashes());
        }
        if (result==ClientArmorRegistry.Result.APPLIED) for (String hash:registry.requiredHashes()) {
            cache.findValidated(hash).ifPresent(bytes -> { if (matches(connection) && registry.requiredHashes().contains(hash)) store.store(hash,bytes); });
        }
        return result;
    }
    public List<ArmorAssetRequestPayload> missing(Object connection) {
        if (!matches(connection)) return List.of();
        var hashes=registry.requiredHashes().stream().filter(hash -> !store.contains(hash) && !attempted.contains(hash)).sorted().toList();
        var result=new ArrayList<ArmorAssetRequestPayload>();
        int remaining=Math.max(0,ArmorAssetRequestTracker.MAX_UNIQUE-attempted.size());
        hashes=hashes.subList(0,Math.min(remaining,hashes.size()));
        for (int i=0; i<hashes.size(); i+=64) result.add(new ArmorAssetRequestPayload(hashes.subList(i,Math.min(i+64,hashes.size()))));
        return List.copyOf(result);
    }
    public boolean markRequested(Object connection, ArmorAssetRequestPayload request) {
        if (!matches(connection) || !registry.requiredHashes().containsAll(request.sha256Hashes())) return false;
        if(attempted.size()+request.sha256Hashes().stream().filter(hash->!attempted.contains(hash)).count()>ArmorAssetRequestTracker.MAX_UNIQUE) return false;
        for (String hash:request.sha256Hashes()) if (attempted.add(hash)) pending.add(hash); return true;
    }
    public Receive receive(Object connection, ArmorAssetDataPayload payload) {
        if (!matches(connection)) return Receive.STALE;
        String hash=payload.sha256();
        if (!registry.requiredHashes().contains(hash)) return Receive.NOT_AUTHORIZED;
        if (!pending.remove(hash)) return Receive.NOT_REQUESTED;
        if (store.store(hash,payload.pngBytes())!=ClientArmorAssetStore.Result.STORED) return Receive.INVALID;
        if (cache.storeValidated(hash,payload.pngBytes())==ClientArmorAssetCache.StoreResult.WRITE_FAILED) cacheWarning=true;
        return Receive.STORED;
    }
    public boolean failed(Object connection, String hash) {
        return matches(connection) && attempted.contains(hash) && !pending.contains(hash) && !store.contains(hash);
    }
    public int pendingCount() { return pending.size(); }
    public boolean cacheWarning() { return cacheWarning; }
}
