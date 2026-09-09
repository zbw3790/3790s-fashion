package vanillafashion.fashion;
import java.util.Objects;
import java.util.UUID;
public record FullPlayerFashionEntry(UUID playerId, FullPlayerFashionState state) {
    public FullPlayerFashionEntry { Objects.requireNonNull(playerId, "玩家身份不能为 null。"); Objects.requireNonNull(state, "权威状态不能为 null。"); }
}
