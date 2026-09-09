package vanillafashion.network;

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
    public Optional<vanillafashion.outfit.OutfitAsset> asset(Object connection, String hash) {
        return view(connection).filter(view -> view.snapshot().requiredHashes().contains(hash)).flatMap(view -> view.assets().find(hash));
    }
    public void close(Object connection) { connections.remove(connection); }
    public void clear() { connections.clear(); }
    public List<String> claim(Object connection, OutfitAssetRequestPayload payload) {
        Budget budget = connections.get(connection);
        if (budget == null || budget.exhausted) return List.of();
        if (payload.sha256Hashes().size() > MAX_ATTEMPTS-budget.attempts) { budget.attempts=MAX_ATTEMPTS; budget.exhausted=true; return List.of(); }
        budget.attempts += payload.sha256Hashes().size();
        var accepted = new LinkedHashSet<String>();
        for (String hash : payload.sha256Hashes()) if (budget.authorized.contains(hash) && !budget.sent.contains(hash)) accepted.add(hash);
        if (accepted.size() > MAX_UNIQUE-budget.sent.size()) { budget.exhausted=true; return List.of(); }
        budget.sent.addAll(accepted); return List.copyOf(accepted);
    }
    public int connectionCount() { return connections.size(); }
    public int attempts(Object connection) { var budget=connections.get(connection); return budget == null ? 0 : budget.attempts; }
    public int sentCount(Object connection) { var budget=connections.get(connection); return budget == null ? 0 : budget.sent.size(); }
    private static final class Budget {
        private ConnectionOutfitAssetView view; private Set<String> authorized; private final Set<String> sent=new HashSet<>(); private int attempts; private boolean exhausted;
        private Budget(Set<String> authorized) { this.authorized=authorized; }
    }
}
