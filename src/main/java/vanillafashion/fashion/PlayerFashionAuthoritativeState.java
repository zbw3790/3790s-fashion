package vanillafashion.fashion;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import vanillafashion.cape.CapeId;

/** 服务端确认的玩家时装状态；保存选择与当前实际生效选择具有不同语义。 */
public record PlayerFashionAuthoritativeState(
		Optional<CapeId> storedSelection,
		Optional<CapeId> effectiveSelection
) {
	public PlayerFashionAuthoritativeState {
		Objects.requireNonNull(storedSelection, "保存选择不能为 null。");
		Objects.requireNonNull(effectiveSelection, "生效选择不能为 null。");
		if (effectiveSelection.isPresent() && !effectiveSelection.equals(storedSelection)) {
			throw new IllegalArgumentException("生效选择必须与非空保存选择相同。");
		}
	}

	public static PlayerFashionAuthoritativeState vanilla() {
		return new PlayerFashionAuthoritativeState(Optional.empty(), Optional.empty());
	}

	public static PlayerFashionAuthoritativeState active(CapeId capeId) {
		var selection = Optional.of(Objects.requireNonNull(capeId, "Cape ID 不能为 null。"));
		return new PlayerFashionAuthoritativeState(selection, selection);
	}

	public static PlayerFashionAuthoritativeState dormant(CapeId capeId) {
		return new PlayerFashionAuthoritativeState(
				Optional.of(Objects.requireNonNull(capeId, "Cape ID 不能为 null。")), Optional.empty());
	}

	/** 统一从服务端真相构造，避免 Snapshot、Update 与 Result 各自组合状态。 */
	public static PlayerFashionAuthoritativeState fromService(UUID playerId, PlayerFashionService service) {
		Objects.requireNonNull(playerId, "玩家 UUID 不能为 null。");
		Objects.requireNonNull(service, "玩家时装服务不能为 null。");
		return new PlayerFashionAuthoritativeState(
				service.getStoredSelection(playerId), service.getEffectiveSelection(playerId));
	}

	public boolean isVanillaStored() {
		return storedSelection.isEmpty();
	}

	public boolean isActiveCustom() {
		return effectiveSelection.isPresent();
	}

	public boolean isDormant() {
		return storedSelection.isPresent() && effectiveSelection.isEmpty();
	}
}
