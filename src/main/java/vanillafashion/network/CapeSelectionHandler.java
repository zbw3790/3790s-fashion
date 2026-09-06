package vanillafashion.network;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionService;

/** 在服务器线程使用；业务验证由正式 Service 完成，不能回 ACK 时不修改状态。 */
public final class CapeSelectionHandler {
	private CapeSelectionHandler() { }

	public static Optional<Outcome> process(UUID sender, SetCapeSelectionPayload request,
			Optional<PlayerFashionService> service, boolean resultSupported, boolean synchronizationAvailable) {
		if (!resultSupported) {
			return Optional.empty();
		}
		var mutation = synchronizationAvailable && service.isPresent()
				? service.orElseThrow().setSelection(sender, request.selection())
				: PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE;
		var authoritative = service
				.map(value -> PlayerFashionAuthoritativeState.fromService(sender, value))
				.orElseGet(PlayerFashionAuthoritativeState::vanilla);
		var reason = switch (mutation) {
			case CHANGED -> CapeSelectionReason.APPLIED;
			case NO_CHANGE -> CapeSelectionReason.NO_CHANGE;
			case CAPE_NOT_AVAILABLE -> CapeSelectionReason.CAPE_NOT_AVAILABLE;
			case NOT_ALLOWED -> CapeSelectionReason.NOT_ALLOWED;
			case SERVICE_UNAVAILABLE -> CapeSelectionReason.SERVICE_UNAVAILABLE;
			case STORAGE_LIMIT -> CapeSelectionReason.STORAGE_LIMIT;
		};
		var result = new CapeSelectionResultPayload(request.requestId(), reason.accepted(), authoritative, reason);
		var update = mutation == PlayerFashionService.MutationResult.CHANGED
				? Optional.of(new PlayerFashionUpdatePayload(new PlayerFashionEntry(sender, authoritative)))
				: Optional.<PlayerFashionUpdatePayload>empty();
		return Optional.of(new Outcome(result, update));
	}

	/** 接收者使用当前在线连接全集，逐连接过滤 Update 能力，包含请求者。 */
	public static <T> void deliver(Outcome outcome, Iterable<T> receivers, Predicate<T> supportsUpdate,
			BiConsumer<T, PlayerFashionUpdatePayload> sendUpdate, Consumer<CapeSelectionResultPayload> sendResult) {
		outcome.update().ifPresent(update -> {
			for (T receiver : receivers) {
				if (supportsUpdate.test(receiver)) {
					sendUpdate.accept(receiver, update);
				}
			}
		});
		sendResult.accept(outcome.result());
	}

	public record Outcome(CapeSelectionResultPayload result, Optional<PlayerFashionUpdatePayload> update) { }
}
