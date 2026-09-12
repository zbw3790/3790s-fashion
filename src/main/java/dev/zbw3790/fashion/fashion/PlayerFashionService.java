package dev.zbw3790.fashion.fashion;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistryKnowledge;
import dev.zbw3790.fashion.outfit.*;

/** 每个存档唯一的聚合服务；所有调用在服务器线程，运行期版本只随在线 membership 存在。 */
public final class PlayerFashionService {
    private final PlayerFashionSavedData data;
    private final Optional<String> degradedReason;
    private final boolean authoritativeStateKnown;
    private final BiPredicate<UUID, CapeId> selectionPolicy;
    private final Map<UUID, Membership> online = new LinkedHashMap<>();
    private CapeRegistryKnowledge registry;
    private Optional<OutfitRegistryLoadResult> outfits = Optional.empty();
    private boolean stopped;
    private long registryGeneration;

    public PlayerFashionService(PlayerFashionPersistence.LoadResult loaded, CapeRegistryKnowledge registry) {
        this(loaded, registry, (playerId, capeId) -> true);
    }
    public PlayerFashionService(PlayerFashionPersistence.LoadResult loaded, CapeRegistryKnowledge registry, BiPredicate<UUID, CapeId> selectionPolicy) {
        this.data = loaded.data(); this.degradedReason = loaded.degradedReason();
        this.authoritativeStateKnown = loaded.authoritativeStateKnown(); this.registry = Objects.requireNonNull(registry);
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy);
    }
    public PlayerFashionStoredState stored(UUID playerId) { return data.storedState(Objects.requireNonNull(playerId)); }
    public Optional<CapeId> getStoredSelection(UUID playerId) { return stored(playerId).cape(); }
    public Optional<CapeId> getEffectiveSelection(UUID playerId) {
        if (stopped) return Optional.empty();
        Membership member = online.get(playerId);
        return member == null ? effective(stored(playerId)).cape() : member.state.effective().cape();
    }
    public Availability availability() {
        if (stopped) return Availability.STOPPED;
        if (degradedReason.isPresent()) return Availability.PERSISTENCE_DEGRADED_READ_ONLY;
        return registry.trustworthy() ? Availability.NORMAL_WRITABLE : Availability.REGISTRY_UNAVAILABLE;
    }
    public Optional<String> degradedReason() { return degradedReason; }
    public boolean canProvideAuthoritativeSnapshot() { return !stopped && authoritativeStateKnown; }
    private boolean writable() { return canProvideAuthoritativeSnapshot() && degradedReason.isEmpty(); }
    public int storedCount() { return data.size(); }
    public int onlineCount() { return online.size(); }
    public Optional<OutfitRegistryLoadResult> outfits() { return outfits; }
    public Map<UUID, Object> connections() {
        var result = new LinkedHashMap<UUID, Object>(); online.forEach((id, membership) -> result.put(id, membership.connection));
        return Collections.unmodifiableMap(result);
    }
    public boolean isCurrent(UUID id, Object connection) { var member = online.get(id); return member != null && member.connection == connection; }
    public Optional<FullPlayerFashionState> authority(UUID id) {
        var member = online.get(id); return canProvideAuthoritativeSnapshot() && member != null ? Optional.of(member.state) : Optional.empty();
    }
    /** 替换连接必须先对观察者发旧 LEFT，才释放旧版本并建立新 membership。 */
    public FullPlayerFashionState join(UUID id, Object connection, Consumer<FullPlayerFashionEntry> beforeLeave) {
        Objects.requireNonNull(id); Objects.requireNonNull(connection); Objects.requireNonNull(beforeLeave);
        if (stopped) throw new IllegalStateException("已停止的服务不能建立 membership。");
        var old = online.get(id);
        if (old != null && old.connection == connection) return old.state;
        if (old != null) leave(id, old.connection, beforeLeave);
        var state = state(stored(id), 0); online.put(id, new Membership(connection, state)); return state;
    }
    public boolean leave(UUID id, Object connection, Consumer<FullPlayerFashionEntry> beforeLeave) {
        if (!isCurrent(id, connection)) return false;
        var member = online.get(id); beforeLeave.accept(new FullPlayerFashionEntry(id, member.state));
        online.remove(id); return true;
    }
    public FullPlayerFashionSnapshot fullSnapshot() {
        if (!canProvideAuthoritativeSnapshot() || online.size() > FullPlayerFashionSnapshot.MAX_PLAYERS) return FullPlayerFashionSnapshot.unavailable();
        return new FullPlayerFashionSnapshot(true, online.entrySet().stream().map(e -> new FullPlayerFashionEntry(e.getKey(), e.getValue().state)).toList());
    }
    public boolean canPlayerSelect(UUID id, CapeId cape) {
        return availability() == Availability.NORMAL_WRITABLE && registry.isValid(cape) && selectionPolicy.test(id, cape);
    }
    /** legacy 只改 Cape，Outfit 保留；它的旧 Registry 门禁不被放宽。 */
    public MutationResult setSelection(UUID id, Optional<CapeId> selection) {
        Objects.requireNonNull(id); Objects.requireNonNull(selection);
        if (availability() != Availability.NORMAL_WRITABLE || online.size() > FullPlayerFashionSnapshot.MAX_PLAYERS) return MutationResult.SERVICE_UNAVAILABLE;
        if (selection.isPresent() && !registry.isValid(selection.orElseThrow())) return MutationResult.CAPE_NOT_AVAILABLE;
        if (selection.isPresent() && !canPlayerSelect(id, selection.orElseThrow())) return MutationResult.NOT_ALLOWED;
        var previous = stored(id); var next = previous.withCape(selection);
        if (previous.equals(next)) return MutationResult.NO_CHANGE;
        if (exhausted(id)) return MutationResult.SERVICE_UNAVAILABLE;
        if (exceedsStorage(previous, next)) return MutationResult.STORAGE_LIMIT;
        commit(id, next); return MutationResult.CHANGED;
    }
    public ApplyOutcome apply(UUID id, Object connection, boolean v2AndResultSupported, long expectedRevision, PlayerFashionStoredState requested) {
        Objects.requireNonNull(requested);
        if (!isCurrent(id, connection) || !v2AndResultSupported) return new ApplyOutcome(FullFashionSelectionStatus.PROTOCOL_REJECT, Optional.empty(), false);
        if (degradedReason.isPresent()) return outcome(id, FullFashionSelectionStatus.READ_ONLY_PERSISTENCE, false);
        if (!writable() || online.size() > FullPlayerFashionSnapshot.MAX_PLAYERS) return outcome(id, FullFashionSelectionStatus.SERVICE_UNAVAILABLE, false);
        var current = online.get(id).state;
        if (expectedRevision != current.revision()) return outcome(id, FullFashionSelectionStatus.CONFLICT, false);
        var old = current.stored();
        if (requested.equals(old)) return outcome(id, FullFashionSelectionStatus.SUCCESS, false);
        if (exhausted(id)) return outcome(id, FullFashionSelectionStatus.SERVICE_UNAVAILABLE, false);
        if (!requested.cape().equals(old.cape()) && requested.cape().isPresent()) {
            if (!registry.trustworthy()) return outcome(id, FullFashionSelectionStatus.SERVICE_UNAVAILABLE, false);
            if (!registry.isValid(requested.cape().orElseThrow())) return outcome(id, FullFashionSelectionStatus.INVALID_CAPE, false);
        }
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
            var proposed = requested.outfit().get(part);
            if (proposed.equals(old.outfit().get(part)) || !(proposed instanceof OutfitPartSelection.Outfit outfit)) continue;
            if (outfits.isEmpty() || !outfits.orElseThrow().knowledge().trustworthy()) return outcome(id, FullFashionSelectionStatus.SERVICE_UNAVAILABLE, false);
            if (!active(outfit.id(), part)) return outcome(id, FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION, false);
        }
        if (exceedsStorage(old, requested)) return outcome(id, FullFashionSelectionStatus.STORAGE_LIMIT, false);
        if (!requested.cape().equals(old.cape()) && requested.cape().isPresent() && !selectionPolicy.test(id, requested.cape().orElseThrow())) return outcome(id, FullFashionSelectionStatus.NOT_ALLOWED, false);
        commit(id, requested); return outcome(id, FullFashionSelectionStatus.SUCCESS, true);
    }
    private ApplyOutcome outcome(UUID id, FullFashionSelectionStatus status, boolean changed) { return new ApplyOutcome(status, authority(id), changed); }
    private boolean exhausted(UUID id) { var member = online.get(id); return member != null && member.state.revision() == Long.MAX_VALUE; }
    private boolean exceedsStorage(PlayerFashionStoredState old, PlayerFashionStoredState next) {
        return old.isDefault() && !next.isDefault() && data.size() >= PlayerFashionSavedData.MAX_PERSISTED_PLAYER_FASHION_ENTRIES;
    }
    private void commit(UUID id, PlayerFashionStoredState next) {
        var member = online.get(id);
        var updated = member == null ? null : state(next, Math.addExact(member.state.revision(), 1));
        data.setState(id, next);
        if (member != null) member.state = updated;
    }
    private boolean active(OutfitId id, OutfitPart part) { return active(outfits, id, part); }
    private static boolean active(Optional<OutfitRegistryLoadResult> source, OutfitId id, OutfitPart part) {
        if (source.isEmpty()) return false; var knowledge = source.orElseThrow().knowledge();
        return knowledge.trustworthy() && knowledge.metadataTrusted(id) && knowledge.partDeclared(id, part)
                && OutfitModel.CANONICAL_ORDER.stream().anyMatch(model -> knowledge.modelDeclared(id, model) && knowledge.modelValid(id, model));
    }
    private PlayerFashionEffectiveState effective(PlayerFashionStoredState stored) {
        var result = stored.outfit();
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) if (result.get(part) instanceof OutfitPartSelection.Outfit outfit && !active(outfit.id(), part)) result = result.with(part, OutfitPartSelection.ORIGINAL);
        return new PlayerFashionEffectiveState(stored.cape().filter(registry::isValid), result);
    }
    private FullPlayerFashionState state(PlayerFashionStoredState stored, long revision) { return new FullPlayerFashionState(stored, effective(stored), revision); }
    public ReconciliationResult reconcile(CapeRegistryKnowledge updated) { return reconcile(updated, outfits, entry -> { }); }
    public ReconciliationResult reconcile(CapeRegistryKnowledge updated, OutfitRegistryLoadResult outfitKnowledge, Consumer<FullPlayerFashionEntry> changed) {
        return reconcile(updated, Optional.of(outfitKnowledge), changed);
    }
    private ReconciliationResult reconcile(CapeRegistryKnowledge updated, Optional<OutfitRegistryLoadResult> outfitKnowledge, Consumer<FullPlayerFashionEntry> changed) {
        registry = Objects.requireNonNull(updated); outfits = Objects.requireNonNull(outfitKnowledge);
        int cleared = 0, dormant = 0;
        var ids = new HashSet<>(data.storedSnapshot().keySet()); ids.addAll(online.keySet());
        for (UUID id : ids) {
            var old = stored(id); var next = old; var selection = old.outfit();
            boolean canChange = writable() && !exhausted(id) && online.size() <= FullPlayerFashionSnapshot.MAX_PLAYERS;
            if (canChange) {
                if (old.cape().isPresent() && registry.isDefinitelyAbsent(old.cape().orElseThrow())) next = next.withCape(Optional.empty());
                for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
                    if (selection.get(part) instanceof OutfitPartSelection.Outfit outfit && outfits.map(o -> o.knowledge().isDefinitelyAbsent(outfit.id())).orElse(false)) selection = selection.with(part, OutfitPartSelection.ORIGINAL);
                }
                next = new PlayerFashionStoredState(next.cape(), selection);
            }
            var resolved = effective(next); var member = online.get(id);
            boolean storedChanged = !next.equals(old);
            boolean authorityChanged = member != null && (!member.state.stored().equals(next) || !member.state.effective().equals(resolved));
            if (storedChanged) { data.setState(id, next); cleared++; }
            if (authorityChanged && !exhausted(id) && online.size() <= FullPlayerFashionSnapshot.MAX_PLAYERS) {
                member.state = new FullPlayerFashionState(next, resolved, member.state.revision()+1); changed.accept(new FullPlayerFashionEntry(id, member.state));
            }
            if (!next.cape().equals(resolved.cape()) || !next.outfit().equals(resolved.outfit())) dormant++;
        }
        return new ReconciliationResult(cleared, dormant);
    }
    public long registryGeneration() { return registryGeneration; }
    /** 先计算全部变更再提交；不扫描、删除或重新派生 Cape 领域。 */
    public OutfitReloadCommit reloadOutfits(OutfitRegistryLoadResult candidate) {
        Objects.requireNonNull(candidate);
        if (!candidate.knowledge().trustworthy() || !writable()) return new OutfitReloadCommit(ReloadStatus.UNAVAILABLE, List.of(), 0);
        if (outfits.isPresent() && OutfitRegistrySnapshot.from(outfits.orElseThrow()).equals(OutfitRegistrySnapshot.from(candidate))
                && outfits.orElseThrow().knowledge().knownExistingIds().equals(candidate.knowledge().knownExistingIds()))
            return new OutfitReloadCommit(ReloadStatus.NO_CHANGE, List.of(), 0);
        if (registryGeneration==Long.MAX_VALUE || online.size()>FullPlayerFashionSnapshot.MAX_PLAYERS)
            return new OutfitReloadCommit(ReloadStatus.UNAVAILABLE, List.of(), 0);
        var storedChanges=new LinkedHashMap<UUID,PlayerFashionStoredState>();
        var authorities=new ArrayList<FullPlayerFashionEntry>();
        var ids=new TreeSet<>(data.storedSnapshot().keySet()); ids.addAll(online.keySet());
        for (UUID id:ids) {
            var old=stored(id); var selected=old.outfit();
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER)
                if (selected.get(part) instanceof OutfitPartSelection.Outfit value && candidate.knowledge().isDefinitelyAbsent(value.id()))
                    selected=selected.with(part,OutfitPartSelection.ORIGINAL);
            var next=new PlayerFashionStoredState(old.cape(),selected);
            if (!next.equals(old)) storedChanges.put(id,next);
            var member=online.get(id);
            if (member==null) continue;
            var effectiveOutfit=selected;
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER)
                if (selected.get(part) instanceof OutfitPartSelection.Outfit value && !active(Optional.of(candidate),value.id(),part))
                    effectiveOutfit=effectiveOutfit.with(part,OutfitPartSelection.ORIGINAL);
            var resolved=new PlayerFashionEffectiveState(member.state.effective().cape(),effectiveOutfit);
            if (!member.state.stored().equals(next) || !member.state.effective().equals(resolved)) {
                if (exhausted(id)) return new OutfitReloadCommit(ReloadStatus.UNAVAILABLE,List.of(),0);
                authorities.add(new FullPlayerFashionEntry(id,new FullPlayerFashionState(next,resolved,member.state.revision()+1)));
            }
        }
        // 此处之后没有扫描或用户回调；由同一服务器线程完成可见状态替换。
        outfits=Optional.of(candidate); registryGeneration++;
        storedChanges.forEach(data::setState);
        authorities.forEach(entry -> online.get(entry.playerId()).state=entry.state());
        return new OutfitReloadCommit(ReloadStatus.COMMITTED,List.copyOf(authorities),storedChanges.size());
    }
    public enum ReloadStatus { COMMITTED, NO_CHANGE, UNAVAILABLE }
    public record OutfitReloadCommit(ReloadStatus status, List<FullPlayerFashionEntry> authorities, int storedChanges) { }
    public void stop() { stopped = true; online.clear(); outfits = Optional.empty(); }
    private static final class Membership {
        private final Object connection; private FullPlayerFashionState state;
        private Membership(Object connection, FullPlayerFashionState state) { this.connection = connection; this.state = state; }
    }
    public enum Availability { NORMAL_WRITABLE, PERSISTENCE_DEGRADED_READ_ONLY, REGISTRY_UNAVAILABLE, STOPPED }
    public enum MutationResult { CHANGED, NO_CHANGE, CAPE_NOT_AVAILABLE, NOT_ALLOWED, SERVICE_UNAVAILABLE, STORAGE_LIMIT }
    public record ReconciliationResult(int clearedCount, int dormantCount) { }
    public record ApplyOutcome(FullFashionSelectionStatus status, Optional<FullPlayerFashionState> authority, boolean changed) { }
}
