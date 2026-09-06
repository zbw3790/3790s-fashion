package vanillafashion.client.screen;

import java.util.Optional;
import java.util.Objects;
import java.util.function.Predicate;
import vanillafashion.cape.CapeId;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.network.ClientCapeSelectionRequestTracker;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.network.CapeSelectionReason;
import vanillafashion.network.CapeSelectionResultPayload;
import vanillafashion.network.SetCapeSelectionPayload;

/** Screen 的纯选择状态；不保存玩家、控件、网络连接或世界状态。 */
public final class WardrobeSelectionSession {
	private Optional<CapeId> baseline = Optional.empty();
	private Optional<CapeId> draft = Optional.empty();
	private Optional<PlayerFashionAuthoritativeState> authority = Optional.empty();
	private boolean authorityKnown;
	private ClientPlayerFashionRegistry.State snapshotState = ClientPlayerFashionRegistry.State.UNINITIALIZED;
	private long pendingRequestId;
	private int pendingTicks;
	private Optional<CapeSelectionReason> lastError = Optional.empty();
	private boolean closed;

	public void observe(Optional<PlayerFashionAuthoritativeState> authority,
			ClientPlayerFashionRegistry.State snapshotState) {
		this.snapshotState = Objects.requireNonNull(snapshotState);
		if (closed) {
			return;
		}
		boolean wasClean = draft.equals(baseline);
		if (authority.isPresent()) {
			var nextState = authority.orElseThrow();
			var next = nextState.storedSelection();
			// 初次收到权威值或未编辑时跟随；暂时失去 ready 不丢弃已有 draft。
			if (wasClean) {
				draft = next;
			}
			baseline = next;
			this.authority = Optional.of(nextState);
			authorityKnown = true;
		} else {
			this.authority = Optional.empty();
			authorityKnown = false;
		}
	}

	public void select(Optional<CapeId> selection) {
		Objects.requireNonNull(selection);
		if (canEdit()) {
			draft = selection;
			lastError = Optional.empty();
		}
	}

	public boolean canEdit() { return !closed && authorityKnown && pendingRequestId == 0; }
	public boolean authorityKnown() { return authorityKnown; }
	public Optional<CapeId> baseline() { return baseline; }
	public Optional<CapeId> draft() { return draft; }
	public Optional<CapeId> previewSelection() {
		return isDormant() && !dirty() ? authority.orElseThrow().effectiveSelection() : draft;
	}
	public boolean dirty() { return !draft.equals(baseline); }
	public boolean dormant() { return isDormant(); }
	public long pendingRequestId() { return pendingRequestId; }
	public Optional<CapeSelectionReason> lastError() { return lastError; }
	public boolean closed() { return closed; }

	public boolean canFinish(boolean channelSupported, boolean outstanding, Predicate<CapeId> metadataPresent) {
		return !closed && authorityKnown && snapshotState == ClientPlayerFashionRegistry.State.AVAILABLE
				&& pendingRequestId == 0 && !outstanding && channelSupported
				&& finishMetadataSafe(metadataPresent);
	}

	private boolean finishMetadataSafe(Predicate<CapeId> metadataPresent) {
		if (dirty()) {
			return draft.map(metadataPresent::test).orElse(true);
		}
		return isDormant() || baseline.map(metadataPresent::test).orElse(true);
	}

	public FinishDecision finish(ClientCapeSelectionRequestTracker tracker, boolean channelSupported,
			Predicate<CapeId> metadataPresent) {
		if (!canFinish(channelSupported, tracker.hasOutstanding(), metadataPresent)) {
			return new FinishDecision(false, Optional.empty());
		}
		if (!dirty()) {
			closed = true;
			return new FinishDecision(true, Optional.empty());
		}
		var allocated = tracker.allocate();
		if (allocated.isEmpty()) {
			return new FinishDecision(false, Optional.empty());
		}
		pendingRequestId = allocated.getAsLong();
		pendingTicks = 0;
		lastError = Optional.empty();
		return new FinishDecision(false, Optional.of(new SetCapeSelectionPayload(pendingRequestId, draft)));
	}

	public boolean owns(long requestId) { return !closed && pendingRequestId != 0 && pendingRequestId == requestId; }

	public boolean acceptResult(CapeSelectionResultPayload result, ClientPlayerFashionRegistry.State state,
			Predicate<CapeId> metadataPresent) {
		if (!owns(result.requestId())) {
			return false;
		}
		pendingRequestId = 0;
		pendingTicks = 0;
		authorityKnown = true;
		snapshotState = state;
		authority = Optional.of(result.authoritativeState());
		baseline = result.authoritativeState().storedSelection();
		if (result.accepted()) {
			lastError = Optional.empty();
			closed = true;
			return true;
		}
		lastError = Optional.of(result.reason());
		if (draft.isPresent() && !metadataPresent.test(draft.orElseThrow())) {
			draft = baseline;
		}
		return false;
	}

	/** Cancel、ESC 与 Screen 被替换只关闭本会话，不撤销连接级 outstanding。 */
	public void cancel() { closed = true; }

	public void tick() {
		if (pendingRequestId != 0 && pendingTicks < 200) {
			pendingTicks++;
		}
	}

	public String status(boolean channelSupported, boolean outstanding, Predicate<CapeId> metadataPresent) {
		if (pendingRequestId != 0) {
			return pendingTicks >= 200 ? "尚未收到服务器确认，状态以服务器为准" : "正在保存…";
		}
		if (lastError.isPresent()) {
			return switch (lastError.orElseThrow()) {
				case CAPE_NOT_AVAILABLE -> "该披风当前不可用";
				case NOT_ALLOWED -> "你不能选择该披风";
				case SERVICE_UNAVAILABLE -> "时装服务当前不可用";
				case STORAGE_LIMIT -> "服务器时装数据已达上限";
				case APPLIED, NO_CHANGE -> "";
			};
		}
		if (snapshotState == ClientPlayerFashionRegistry.State.UNAVAILABLE) {
			return "时装状态当前不可用";
		}
		if (!authorityKnown) {
			return "时装状态正在同步";
		}
		if (isDormant() && !dirty()) {
			return "该披风当前不可用，暂时使用原版外观";
		}
		if (draft.isPresent() && !metadataPresent.test(draft.orElseThrow())) {
			return "当前披风正在同步";
		}
		if (!channelSupported) {
			return "服务器不支持保存时装选择";
		}
		return outstanding ? "正在等待之前的选择确认" : "";
	}

	public String selectionLabel() {
		if (isDormant() && !dirty()) {
			return "已保存选择：" + baseline.orElseThrow().value();
		}
		return "当前选择：" + draft.map(CapeId::value).orElse("原版");
	}

	private boolean isDormant() {
		return authority.map(PlayerFashionAuthoritativeState::isDormant).orElse(false);
	}

	public record FinishDecision(boolean close, Optional<SetCapeSelectionPayload> request) { }
}
