package vanillafashion.fashion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistryKnowledge;

/** 每个服务器存档独占一个实例；查询、修改和 reconcile 均由服务器线程调用。 */
public final class PlayerFashionService {
	private final PlayerFashionSavedData data;
	private final Optional<String> degradedReason;
	private final boolean authoritativeStateKnown;
	private final BiPredicate<UUID, CapeId> selectionPolicy;
	private CapeRegistryKnowledge registry;
	private boolean stopped;

	public PlayerFashionService(PlayerFashionPersistence.LoadResult loaded, CapeRegistryKnowledge registry) {
		this(loaded, registry, (playerId, capeId) -> true);
	}

	public PlayerFashionService(PlayerFashionPersistence.LoadResult loaded, CapeRegistryKnowledge registry,
			BiPredicate<UUID, CapeId> selectionPolicy) {
		this.data = loaded.data();
		this.degradedReason = loaded.degradedReason();
		this.authoritativeStateKnown = loaded.authoritativeStateKnown();
		this.registry = Objects.requireNonNull(registry, "Registry 扫描知识不能为 null。");
		this.selectionPolicy = Objects.requireNonNull(selectionPolicy, "时装选择策略不能为 null。");
	}

	public Optional<CapeId> getStoredSelection(UUID playerId) {
		return data.selection(Objects.requireNonNull(playerId, "玩家 UUID 不能为 null。"));
	}

	public Optional<CapeId> getEffectiveSelection(UUID playerId) {
		return stopped ? Optional.empty() : getStoredSelection(playerId).filter(registry::isValid);
	}

	public Availability availability() {
		if (stopped) {
			return Availability.STOPPED;
		}
		if (degradedReason.isPresent()) {
			return Availability.PERSISTENCE_DEGRADED_READ_ONLY;
		}
		return registry.trustworthy() ? Availability.NORMAL_WRITABLE : Availability.REGISTRY_UNAVAILABLE;
	}

	public Optional<String> degradedReason() {
		return degradedReason;
	}

	/** 完整可读与持久化可写独立；Registry 不可用时 effective 安全回退到 Vanilla。 */
	public boolean canProvideAuthoritativeSnapshot() {
		return !stopped && authoritativeStateKnown;
	}

	public int storedCount() {
		return data.size();
	}

	// 网络调用方从实际 ServerPlayer 解析 sender UUID；默认策略不引入权限框架。
	public boolean canPlayerSelect(UUID playerId, CapeId capeId) {
		Objects.requireNonNull(playerId, "玩家 UUID 不能为 null。");
		Objects.requireNonNull(capeId, "Cape ID 不能为 null。");
		return availability() == Availability.NORMAL_WRITABLE && registry.isValid(capeId)
				&& selectionPolicy.test(playerId, capeId);
	}

	public MutationResult setSelection(UUID playerId, Optional<CapeId> selection) {
		Objects.requireNonNull(playerId, "玩家 UUID 不能为 null。");
		Objects.requireNonNull(selection, "Vanilla 选择必须使用 Optional.empty()。");
		if (availability() != Availability.NORMAL_WRITABLE) {
			return MutationResult.SERVICE_UNAVAILABLE;
		}
		if (selection.isPresent() && !registry.isValid(selection.orElseThrow())) {
			return MutationResult.CAPE_NOT_AVAILABLE;
		}
		if (selection.isPresent() && !canPlayerSelect(playerId, selection.orElseThrow())) {
			return MutationResult.NOT_ALLOWED;
		}
		Optional<CapeId> previous = getStoredSelection(playerId);
		if (previous.equals(selection)) {
			return MutationResult.NO_CHANGE;
		}
		if (previous.isEmpty() && selection.isPresent()
				&& data.size() >= PlayerFashionSavedData.MAX_PERSISTED_PLAYER_FASHION_ENTRIES) {
			return MutationResult.STORAGE_LIMIT;
		}
		data.setSelection(playerId, selection);
		return MutationResult.CHANGED;
	}

	public ReconciliationResult reconcile(CapeRegistryKnowledge updated) {
		registry = Objects.requireNonNull(updated, "Registry 扫描知识不能为 null。");
		int cleared = 0;
		int dormant = 0;
		for (var entry : data.snapshot().entrySet()) {
			if (availability() == Availability.NORMAL_WRITABLE && registry.isDefinitelyAbsent(entry.getValue())) {
				data.setSelection(entry.getKey(), Optional.empty());
				cleared++;
			} else if (getEffectiveSelection(entry.getKey()).isEmpty()) {
				dormant++;
			}
		}
		return new ReconciliationResult(cleared, dormant);
	}

	public void stop() {
		stopped = true;
	}

	public enum Availability {
		NORMAL_WRITABLE,
		PERSISTENCE_DEGRADED_READ_ONLY,
		REGISTRY_UNAVAILABLE,
		STOPPED
	}

	public enum MutationResult {
		CHANGED,
		NO_CHANGE,
		CAPE_NOT_AVAILABLE,
		NOT_ALLOWED,
		SERVICE_UNAVAILABLE,
		STORAGE_LIMIT
	}

	public record ReconciliationResult(int clearedCount, int dormantCount) {
	}
}
