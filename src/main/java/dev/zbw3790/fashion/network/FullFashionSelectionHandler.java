package dev.zbw3790.fashion.network;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import dev.zbw3790.fashion.fashion.*;

/** 服务器线程上的完整事务与发送顺序；所有投影均来自同一次提交的 authority。 */
public final class FullFashionSelectionHandler {
    private FullFashionSelectionHandler() { }

    public static Optional<Outcome> process(UUID player, Object connection, SetFullFashionSelectionPayload request,
            PlayerFashionService service, FashionAuthorityRoute route, boolean resultSupported) {
        if (!resultSupported || !service.isCurrent(player, connection)) return Optional.empty();
        var mutation = service.apply(player, connection, route == FashionAuthorityRoute.V2,
                request.expectedRevision(), request.stored());
        var update = mutation.changed()
                ? mutation.authority().map(state -> new FullPlayerFashionEntry(player, state))
                : Optional.<FullPlayerFashionEntry>empty();
        return Optional.of(new Outcome(new FullFashionSelectionResultPayload(
                request.requestId(), mutation.status(), mutation.authority()), update));
    }

    /** 先发布同一提交的 Update／DEFAULT，再回 Result；无变化或拒绝不广播。 */
    public static void deliver(Outcome outcome, Consumer<FullPlayerFashionEntry> broadcast,
            Consumer<FullFashionSelectionResultPayload> result) {
        outcome.update().ifPresent(broadcast);
        result.accept(outcome.result());
    }

    /** 接收者仅选择一条 authority 路线，实际发送仍由逐 Payload canSend 控制。 */
    public static Optional<CustomPacketPayload> projection(FashionAuthorityRoute route, FullPlayerFashionEntry entry) {
        return switch (route) {
            case UNDECIDED -> Optional.empty();
            case LEGACY -> Optional.of(new PlayerFashionUpdatePayload(
                    new PlayerFashionEntry(entry.playerId(), entry.state().capeProjection())));
            case V2 -> Optional.of(entry.state().stored().isDefault()
                    ? new FullPlayerFashionRemovePayload(entry.playerId(), entry.state().revision(),
                            FullPlayerFashionRemovePayload.Reason.DEFAULT)
                    : new FullPlayerFashionUpdatePayload(entry));
        };
    }

    public record Outcome(FullFashionSelectionResultPayload result, Optional<FullPlayerFashionEntry> update) { }
}
