package dev.zbw3790.fashion.client.screen;

import java.util.Optional;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.Set;
import java.util.function.BiPredicate;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.client.fashion.FullFashionDraft;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.network.ClientCapeSelectionRequestTracker;
import dev.zbw3790.fashion.fashion.PlayerFashionAuthoritativeState;
import dev.zbw3790.fashion.network.CapeSelectionReason;
import dev.zbw3790.fashion.network.CapeSelectionResultPayload;
import dev.zbw3790.fashion.network.SetCapeSelectionPayload;

/** Screen 的纯选择状态；不保存玩家、控件、网络连接或世界状态。 */
public final class WardrobeSelectionSession {
	private dev.zbw3790.fashion.client.fashion.FullFashionDraft fullDraft;
    private boolean v2;
    private boolean persistenceReadOnly;
    private Optional<dev.zbw3790.fashion.fashion.FullFashionSelectionStatus> fullError = Optional.empty();
    public void observeFull(Optional<dev.zbw3790.fashion.fashion.FullPlayerFashionState> current, ClientPlayerFashionRegistry.State state) {
        v2=true; snapshotState=state;
        if (closed) return;
        authorityKnown=state==ClientPlayerFashionRegistry.State.AVAILABLE && current.isPresent();
        if (!authorityKnown) { authority=Optional.empty(); return; }
        if (fullDraft==null) fullDraft=new dev.zbw3790.fashion.client.fashion.FullFashionDraft(current.orElseThrow());
        else fullDraft.observe(current.orElseThrow());
        projectFull();
    }
    private void projectFull() {
        authority=Optional.of(fullDraft.baseline().capeProjection());
    }
    boolean conflicts(Set<OutfitPart> targets) {
        return v2 && fullDraft!=null && targets.stream().anyMatch(part ->
                fullDraft.conflicts().contains(FullFashionDraft.Field.values()[part.ordinal()+1]));
    }
    public boolean conflict() { return v2 && fullDraft!=null && !fullDraft.conflicts().isEmpty(); }
    public Optional<dev.zbw3790.fashion.network.SetFullFashionSelectionPayload> finishFull(
            dev.zbw3790.fashion.client.network.ClientFullFashionRequestTracker tracker, boolean channelSupported, Predicate<CapeId> metadataPresent) {
        return finishFull(tracker,channelSupported,true,metadataPresent,(part,id) -> false);
    }
    public boolean acceptFullResult(dev.zbw3790.fashion.network.FullFashionSelectionResultPayload result,
            Optional<dev.zbw3790.fashion.fashion.FullPlayerFashionState> latest, ClientPlayerFashionRegistry.State state) {
        if (!v2 || !owns(result.requestId())) return false;
        pendingRequestId=0; pendingTicks=0; snapshotState=state; authorityKnown=state==ClientPlayerFashionRegistry.State.AVAILABLE && latest.isPresent();
        boolean success=result.status()==dev.zbw3790.fashion.fashion.FullFashionSelectionStatus.SUCCESS;
        if (result.status()==FullFashionSelectionStatus.READ_ONLY_PERSISTENCE) persistenceReadOnly=true;
        if (success) {
            fullDraft.acceptSuccess(result.authority().orElseThrow(),latest); fullError=Optional.empty();
        } else { latest.ifPresent(fullDraft::observe); fullError=Optional.of(result.status()); }
        projectFull();
        if (!authorityKnown) authority=Optional.empty();
        return success;
    }
    private final LegacyCapeDraft legacy = new LegacyCapeDraft();
    private boolean connectionOutstanding;
    private static final class LegacyCapeDraft { Optional<CapeId> baseline=Optional.empty(), draft=Optional.empty(); }
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
		boolean wasClean = legacy.draft.equals(legacy.baseline);
		if (authority.isPresent()) {
			var nextState = authority.orElseThrow();
			var next = nextState.storedSelection();
			// 初次收到权威值或未编辑时跟随；暂时失去 ready 不丢弃已有 legacy.draft。
			if (wasClean) {
				legacy.draft = next;
			}
			legacy.baseline = next;
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
            if (selection.equals(draft())) return;
            if (v2) fullDraft.edit(fullDraft.draft().withCape(selection));
            else legacy.draft = selection;
            fullError=Optional.empty();
            lastError=Optional.empty();
		}
	}

	public boolean canEdit() { return !closed && authorityKnown && pendingRequestId == 0 && !connectionOutstanding; }
	public boolean authorityKnown() { return authorityKnown; }
	public Optional<CapeId> baseline() { return v2 ? fullState().map(value -> value.stored().cape()).orElse(Optional.empty()) : legacy.baseline; }
	public Optional<CapeId> draft() { return v2 ? fullSnapshot().map(PlayerFashionStoredState::cape).orElse(Optional.empty()) : legacy.draft; }
	public Optional<CapeId> previewSelection() {
		return isDormant() && draft().equals(baseline()) ? authority.orElseThrow().effectiveSelection() : draft();
	}
	public boolean dirty() { return v2 ? fullDraft!=null && fullDraft.dirty() : !legacy.draft.equals(legacy.baseline); }
	public boolean dormant() { return isDormant(); }
	public long pendingRequestId() { return pendingRequestId; }
	public Optional<CapeSelectionReason> lastError() { return lastError; }
    public boolean hasError() { return persistenceReadOnly || lastError.isPresent() || fullError.isPresent() || conflict(); }
	public boolean closed() { return closed; }

    public boolean canFinish(boolean channelSupported, boolean outstanding, Predicate<CapeId> capeAdmitted) {
        if (v2) return canFinishFull(channelSupported, true, outstanding, capeAdmitted, (part,id) -> false);
        return canEdit() && dirty() && snapshotState==ClientPlayerFashionRegistry.State.AVAILABLE
                && !outstanding && channelSupported && draft().map(capeAdmitted::test).orElse(true);
    }

    public boolean canFinishFull(boolean canSend, boolean receiverReady, boolean outstanding,
            Predicate<CapeId> capeAdmitted, BiPredicate<OutfitPart,OutfitId> outfitAdmitted) {
        return canFinishFull(canSend,receiverReady,outstanding,capeAdmitted,outfitAdmitted,(slot,id) -> false);
    }
    public boolean canFinishFull(boolean canSend, boolean receiverReady, boolean outstanding,
            Predicate<CapeId> capeAdmitted, BiPredicate<OutfitPart,OutfitId> outfitAdmitted,
            BiPredicate<ArmorSlot,ArmorStyleId> armorAdmitted) {
        return v2 && fullDraft!=null && canEdit() && dirty() && !conflict()
                && !persistenceReadOnly && fullError.filter(value -> value==FullFashionSelectionStatus.INVALID_CAPE
                        || value==FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION
                        || value==FullFashionSelectionStatus.INVALID_ARMOR_SELECTION).isEmpty()
                && !outstanding && canSend && receiverReady
                && WardrobeApplyAdmission.changedFields(fullDraft.baseline().stored(),fullDraft.draft(),capeAdmitted,outfitAdmitted,armorAdmitted);
    }
    public Optional<dev.zbw3790.fashion.network.SetFullFashionSelectionPayload> finishFull(
            dev.zbw3790.fashion.client.network.ClientFullFashionRequestTracker tracker, boolean canSend, boolean receiverReady,
            Predicate<CapeId> capeAdmitted, BiPredicate<OutfitPart,OutfitId> outfitAdmitted) {
        return finishFull(tracker,canSend,receiverReady,capeAdmitted,outfitAdmitted,(slot,id) -> false);
    }
    public Optional<dev.zbw3790.fashion.network.SetFullFashionSelectionPayload> finishFull(
            dev.zbw3790.fashion.client.network.ClientFullFashionRequestTracker tracker, boolean canSend, boolean receiverReady,
            Predicate<CapeId> capeAdmitted, BiPredicate<OutfitPart,OutfitId> outfitAdmitted,
            BiPredicate<ArmorSlot,ArmorStyleId> armorAdmitted) {
        if (!canFinishFull(canSend,receiverReady,tracker.hasOutstanding(),capeAdmitted,outfitAdmitted,armorAdmitted)) return Optional.empty();
        var id=tracker.allocate(); if (id.isEmpty()) return Optional.empty();
        pendingRequestId=id.getAsLong(); pendingTicks=0; fullError=Optional.empty();
        return Optional.of(new dev.zbw3790.fashion.network.SetFullFashionSelectionPayload(pendingRequestId,fullDraft.baseline().revision(),fullDraft.draft()));
    }
    Optional<WardrobePreviewDraft> previewDraft() {
        return fullSnapshot().map(value -> new WardrobePreviewDraft(value,fullDraft.baseline()));
    }
    public boolean v2() { return v2; }
    public void connectionOutstanding(boolean value) { connectionOutstanding=value; }
    public boolean waiting() { return pendingRequestId!=0 || connectionOutstanding; }
    public Optional<PlayerFashionStoredState> fullSnapshot() { return Optional.ofNullable(fullDraft).map(FullFashionDraft::draft); }
    public Optional<FullPlayerFashionState> fullState() { return Optional.ofNullable(fullDraft).map(FullFashionDraft::baseline); }
    public void selectOutfit(Set<OutfitPart> targets, OutfitId id, Set<OutfitPart> provided) {
        if (v2 && canEdit()) editOutfit(fullDraft.draft().outfit().applyOutfit(targets,id,provided).selections());
    }
    public void clearOutfit(Set<OutfitPart> targets, OutfitPartSelection builtin) {
        if (builtin instanceof OutfitPartSelection.Outfit) throw new IllegalArgumentException("清除动作只接受原版或无外层。");
        if (v2 && canEdit()) editOutfit(fullDraft.draft().outfit().set(targets,builtin).selections());
    }
    private void editOutfit(OutfitSelections value) {
        if (value.equals(fullDraft.draft().outfit())) return;
        fullDraft.edit(fullDraft.draft().withOutfit(value));
        fullError=Optional.empty(); lastError=Optional.empty();
    }
    /** 整体 CUSTOM 只作用于声明槽与目标槽的交集，其他草稿字段保持。 */
    public void selectArmor(Set<ArmorSlot> targets, ArmorStyleId id, Set<ArmorSlot> provided) {
        if (!v2 || !canEdit()) return;
        var next=fullDraft.draft().armor();
        for (var slot:targets) if (provided.contains(slot)) next=next.with(slot,ArmorSelection.custom(id));
        editArmor(next);
    }
    public void clearArmor(Set<ArmorSlot> targets, ArmorSelection builtin) {
        if (builtin instanceof ArmorSelection.Custom) throw new IllegalArgumentException("清除只接受原版或隐藏。");
        if (!v2 || !canEdit()) return;
        var next=fullDraft.draft().armor();
        for (var slot:targets) next=next.with(slot,builtin);
        editArmor(next);
    }
    private void editArmor(ArmorSelections value) {
        if (value.equals(fullDraft.draft().armor())) return;
        fullDraft.edit(fullDraft.draft().withArmor(value));
        fullError=Optional.empty();lastError=Optional.empty();
    }
    boolean armorConflict(ArmorSlot slot) {
        return v2 && fullDraft!=null && fullDraft.conflicts().contains(FullFashionDraft.Field.values()[slot.ordinal()+7]);
    }
    public boolean reloadAuthority() {
        if (!v2 || !canEdit() || fullDraft==null) return false;
        fullDraft=new FullFashionDraft(fullDraft.baseline()); fullError=Optional.empty(); lastError=Optional.empty();
        projectFull(); return true;
    }

	public FinishDecision finish(ClientCapeSelectionRequestTracker tracker, boolean channelSupported,
			Predicate<CapeId> metadataPresent) {
		if (v2 || !canFinish(channelSupported, tracker.hasOutstanding(), metadataPresent)) {
			return new FinishDecision(false, Optional.empty());
		}
		var allocated = tracker.allocate();
		if (allocated.isEmpty()) {
			return new FinishDecision(false, Optional.empty());
		}
		pendingRequestId = allocated.getAsLong();
		pendingTicks = 0;
		lastError = Optional.empty();
		return new FinishDecision(false, Optional.of(new SetCapeSelectionPayload(pendingRequestId, legacy.draft)));
	}

	public boolean owns(long requestId) { return !closed && pendingRequestId != 0 && pendingRequestId == requestId; }

	/** 返回当前匹配会话是否收到成功确认；成功后继续编辑，不表示关闭界面。 */
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
		legacy.baseline = result.authoritativeState().storedSelection();
		if (result.accepted()) {
			legacy.draft = legacy.baseline;
			lastError = Optional.empty();
			return true;
		}
		lastError = Optional.of(result.reason());
		if (legacy.draft.isPresent() && !metadataPresent.test(legacy.draft.orElseThrow())) {
			legacy.draft = legacy.baseline;
		}
		return false;
	}

	/** ESC、物品栏键与 Screen 被替换只关闭本会话，不撤销连接级 outstanding。 */
	public void cancel() { closed = true; }

	public void tick() {
		if (pendingRequestId != 0 && pendingTicks < 200) {
			pendingTicks++;
		}
	}

	public String status(boolean channelSupported, boolean outstanding, Predicate<CapeId> metadataPresent) {
        if (conflict()) return "存在外部修改";
        if (persistenceReadOnly) return "服务器时装存档当前只读";
        if (fullError.isPresent()) return switch (fullError.orElseThrow()) {
            case SUCCESS -> ""; case CONFLICT -> "存在外部修改，请检查";
            case INVALID_ARMOR_SELECTION -> "盔甲选择已失效，请重新选择";
            case INVALID_CAPE -> "该披风当前不可用"; case INVALID_OUTFIT_SELECTION -> "装束选择已失效，请重新选择";
            case SERVICE_UNAVAILABLE -> "时装服务当前不可用"; case READ_ONLY_PERSISTENCE -> "服务器时装存档当前只读";
            case PROTOCOL_REJECT -> "当前连接不支持完整时装提交"; case STORAGE_LIMIT -> "服务器时装数据已达上限";
            case NOT_ALLOWED -> "你不能提交该时装选择";
        };
        if (lastError.isPresent()) {
			return switch (lastError.orElseThrow()) {
				case CAPE_NOT_AVAILABLE -> "该披风当前不可用";
				case NOT_ALLOWED -> "你不能选择该披风";
				case SERVICE_UNAVAILABLE -> "时装服务当前不可用";
				case STORAGE_LIMIT -> "服务器时装数据已达上限";
				case APPLIED, NO_CHANGE -> "";
			};
		}
		if (pendingRequestId != 0) return pendingStatus();
		if (snapshotState == ClientPlayerFashionRegistry.State.UNAVAILABLE) {
			return "时装状态当前不可用";
		}
		if (!authorityKnown) {
			return "时装状态正在同步";
		}
		if (isDormant() && draft().equals(baseline())) {
			return "该披风当前不可用，暂时使用原版外观";
		}
		if (draft().isPresent() && !metadataPresent.test(draft().orElseThrow())) {
			return "当前披风正在同步";
		}
		if (!channelSupported) {
			return "服务器不支持保存时装选择";
		}
		return outstanding ? "正在等待之前的选择确认" : "";
	}

    /** 仅投影现有请求状态，不以超时取消、重试或结算事务。 */
    String pendingStatus() {
        if (pendingRequestId == 0) return "正在等待之前的选择确认";
        return pendingTicks >= 200 ? "尚未收到服务器确认，状态以服务器为准" : "正在保存…";
    }

    /** 主状态被冲突或拒绝占用时，三个 Tab 共用的补充事实仍保留尚未确认。 */
    List<String> supplementalStatus() {
        if (!hasError() || !waiting()) return List.of();
        return List.of(pendingRequestId != 0 && pendingTicks < 200
                ? "当前应用仍等待服务器确认" : pendingStatus());
    }

	public String selectionLabel() {
		if (isDormant() && draft().equals(baseline())) {
			return "已保存选择：" + baseline().orElseThrow().value();
		}
		return "当前选择：" + draft().map(CapeId::value).orElse("原版");
	}

	private boolean isDormant() {
		return authority.map(PlayerFashionAuthoritativeState::isDormant).orElse(false);
	}

	public record FinishDecision(boolean close, Optional<SetCapeSelectionPayload> request) { }
}
