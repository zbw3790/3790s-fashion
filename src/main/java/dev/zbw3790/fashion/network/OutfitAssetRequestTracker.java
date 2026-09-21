package dev.zbw3790.fashion.network;

import java.util.*;

/** 单连接预算；所有语法合法条目先计 attempts，再查授权与去重。 */
public final class OutfitAssetRequestTracker {
    public static final int MAX_UNIQUE = 512, MAX_ATTEMPTS = 1024;
    private final Map<Object, Budget> connections = new IdentityHashMap<>();
    public void open(Object connection, Set<String> authorized) {
        Objects.requireNonNull(connection);
        if (authorized.size() > MAX_UNIQUE) throw new IllegalArgumentException("装束授权内容超过上限。");
        connections.putIfAbsent(connection, new Budget(Set.copyOf(authorized)));
    }
    public void open(Object connection, ConnectionOutfitAssetView view) {
        open(connection, view.snapshot().requiredHashes());
        Budget budget = connections.get(connection);
        if (budget.view == null) budget.view = view;
    }
    public boolean refreshAuthorized(Object connection, ConnectionOutfitAssetView view) {
        Budget budget = connections.get(connection);
        if (budget == null || budget.view == null || view.generation() <= budget.view.generation()) return false;
        budget.authorized = Set.copyOf(view.snapshot().requiredHashes());
        budget.view = view;
        return true;
    }
    public Optional<ConnectionOutfitAssetView> view(Object connection) {
        Budget budget = connections.get(connection);
        return budget == null ? Optional.empty() : Optional.ofNullable(budget.view);
    }
    public Optional<dev.zbw3790.fashion.outfit.OutfitAsset> asset(Object connection, String hash) {
        return view(connection).filter(view -> view.snapshot().requiredHashes().contains(hash)).flatMap(view -> view.assets().find(hash));
    }
    public void close(Object connection) { connections.remove(connection); }
    public void clear() { connections.clear(); }
    public List<String> claim(Object connection, OutfitAssetRequestPayload payload) {
        Budget budget = connections.get(connection);
        return budget==null ? List.of() : budget.requests.claim(payload.sha256Hashes(),budget.authorized);
    }
    public int connectionCount() { return connections.size(); }
    public int attempts(Object connection) { var budget=connections.get(connection); return budget == null ? 0 : budget.requests.attempts(); }
    public int sentCount(Object connection) { var budget=connections.get(connection); return budget == null ? 0 : budget.requests.sentCount(); }
    private static final class Budget {
        private ConnectionOutfitAssetView view; private Set<String> authorized; private final AssetRequestBudget requests=new AssetRequestBudget(MAX_UNIQUE,MAX_ATTEMPTS);
        private Budget(Set<String> authorized) { this.authorized=authorized; }
    }
}
