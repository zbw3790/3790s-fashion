package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.client.screen.WardrobeS04Fixture.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;

/** 普通 JVM 中复现真实 Screen 的事务提示；不启动或操作 Minecraft 窗口。 */
class WardrobePendingConflictStatusTest {
    enum ChangedField { CAPE, HEAD }

    @ParameterizedTest @EnumSource(ChangedField.class)
    void pendingExternalFieldConflictIsPrimaryInBothTabsWithoutUnlockingTheRequest(ChangedField field) {
        var f = new WardrobeS04Fixture(); var screen = f.open();
        edit(screen, field); screen.applySelection();
        var submitted = f.sent.getFirst();
        assertEquals(0, submitted.expectedRevision());
        var external = external(field); f.update(external); screen.tick();
        var session = screen.selectionSession();
        assertEquals(external, session.fullState().orElseThrow());
        assertEquals(submitted.stored(), session.fullSnapshot().orElseThrow());
        assertEquals(field == ChangedField.HEAD, session.conflicts(Set.of(OutfitPart.HEAD)));
        assertFalse(session.conflicts(Set.of(OutfitPart.RIGHT_LEG)));
        // 当前范围不含冲突字段，两个 Tab 仍须显示全局冲突。
        screen.outfitContent().setScope(OutfitScope.RIGHT_LEG);
        assertConflictInBothTabs(screen, "当前应用仍等待服务器确认");
        assertLocked(f, screen, submitted.requestId());
        assertEquals(external, session.fullState().orElseThrow());
        assertEquals(submitted.stored(), session.fullSnapshot().orElseThrow());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ordinaryPendingAndTimeoutRemainWaitingWithoutInventingConflict(boolean timeout) {
        var f = new WardrobeS04Fixture(); var screen = f.open();
        edit(screen, ChangedField.CAPE); screen.applySelection();
        if (timeout) tickTimeout(screen);
        String expected = timeout ? "尚未收到服务器确认，状态以服务器为准" : "正在保存…";
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            screen.selectTab(tab);
            assertEquals(WardrobeStatusText.Priority.PENDING, screen.statusText().priority());
            assertEquals(expected, screen.statusText().secondLine());
            assertFalse(screen.statusText().narration().contains("存在外部修改"));
        }
        assertFalse(screen.selectionSession().conflict());
        assertFalse(screen.selectionSession().reloadAuthority());
        screen.applySelection(); assertEquals(1, f.sent.size());
        assertEquals(1, screen.selectionSession().pendingRequestId());
        assertTrue(f.authority.fullRequests().hasOutstanding());
    }

    @ParameterizedTest @EnumSource(ChangedField.class)
    void ordinaryConflictKeepsReloadAvailableAndHasNoPendingFact(ChangedField field) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); edit(screen, field);
        var external = external(field); f.update(external); screen.tick();
        assertConflictInBothTabs(screen, null);
        assertFalse(screen.canApply()); assertTrue(button(screen, "重新加载").active);
        assertFalse(screen.selectionSession().waiting());
        press(screen, "重新加载");
        assertEquals(external.stored(), screen.selectionSession().fullSnapshot().orElseThrow());
        assertFalse(screen.selectionSession().conflict()); assertTrue(f.sent.isEmpty());
    }

    @ParameterizedTest @EnumSource(ChangedField.class)
    void timeoutSupplementsConflictWithoutClearingPendingOrRetrying(ChangedField field) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); edit(screen, field); screen.applySelection();
        f.update(external(field)); screen.tick(); tickTimeout(screen);
        assertConflictInBothTabs(screen, "尚未收到服务器确认，状态以服务器为准");
        assertLocked(f, screen, 1);
    }

    @ParameterizedTest @CsvSource({"CAPE, CONFLICT", "HEAD, CONFLICT", "CAPE, SUCCESS", "HEAD, SUCCESS"})
    void matchingResultSettlesPendingUsingTheExistingMergeAndSuccessRules(ChangedField field, FullFashionSelectionStatus result) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); edit(screen, field); screen.applySelection();
        var submitted = f.sent.getFirst().stored(); var external = external(field);
        f.update(external); screen.tick(); assertConflictInBothTabs(screen, "当前应用仍等待服务器确认");
        // SUCCESS 为显示层回归的合成路径，不改变服务端 CAS 或真实事务语义。
        var returned = result == FullFashionSelectionStatus.SUCCESS ? state(2, submitted) : external;
        f.result(screen, 1, result, returned);
        assertEquals(returned, screen.selectionSession().fullState().orElseThrow());
        assertEquals(0, screen.selectionSession().pendingRequestId());
        assertFalse(screen.selectionSession().waiting()); assertFalse(f.authority.fullRequests().hasOutstanding());
        assertEquals(1, f.sent.size()); assertEquals(0, f.closeCount);
        if (result == FullFashionSelectionStatus.CONFLICT) {
            assertEquals(submitted, screen.selectionSession().fullSnapshot().orElseThrow());
            assertConflictInBothTabs(screen, null);
            assertTrue(button(screen, "重新加载").active); assertFalse(screen.canApply());
            press(screen, "重新加载");
            assertEquals(external.stored(), screen.selectionSession().fullSnapshot().orElseThrow());
        } else {
            assertEquals(submitted, screen.selectionSession().fullSnapshot().orElseThrow());
            assertFalse(screen.selectionSession().dirty()); assertFalse(screen.canApply());
        }
        assertFalse(screen.selectionSession().hasError());
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            screen.selectTab(tab); assertNoTransactionFacts(screen.statusText());
        }
        assertEquals(1, f.sent.size()); assertTrue(screen.selectionSession().canEdit());
        screen.selectionSession().select(Optional.empty()); screen.applySelection();
        if (field == ChangedField.CAPE) assertEquals(2, f.sent.size());
        else assertEquals(1, f.sent.size());
    }

    @ParameterizedTest @EnumSource(ChangedField.class)
    void ownUpdateCannotGuessSuccessAndMatchingResultClearsTemporaryConflict(ChangedField field) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); edit(screen, field); screen.applySelection();
        var confirmed = state(1, f.sent.getFirst().stored());
        f.update(confirmed); screen.tick();
        // 冻结合并规则连 D=N 也可暂记字段冲突；仅匹配 Result 才确认自己的事务。
        assertConflictInBothTabs(screen, "当前应用仍等待服务器确认");
        assertLocked(f, screen, 1);
        f.result(screen, 1, FullFashionSelectionStatus.SUCCESS, confirmed);
        assertFalse(screen.selectionSession().conflict()); assertFalse(screen.selectionSession().waiting());
        assertFalse(screen.selectionSession().dirty()); assertFalse(screen.canApply());
        assertEquals(confirmed.stored(), screen.selectionSession().fullSnapshot().orElseThrow());
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            screen.selectTab(tab); assertNoTransactionFacts(screen.statusText());
        }
        assertEquals(1, f.sent.size());
    }

    @Test
    void confirmedRejectionIsBelowBlockingConflictAndAboveResourceStatus() {
        var f = new WardrobeS04Fixture(); var screen = f.open(); edit(screen, ChangedField.HEAD); screen.applySelection();
        f.result(screen, 1, FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION, FullPlayerFashionState.defaults(0));
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            screen.selectTab(tab); assertEquals("装束选择已失效，请重新选择", screen.statusText().secondLine());
            assertEquals(WardrobeStatusText.Priority.ERROR, screen.statusText().priority());
        }
        f.update(external(ChangedField.HEAD)); screen.tick();
        assertConflictInBothTabs(screen, null); assertEquals(1, f.sent.size());
    }

    @ParameterizedTest @ValueSource(ints = {69, 256})
    void pendingConflictAllowsViewControlsAndCloseWithoutChangingTransaction(int closeKey) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); edit(screen, ChangedField.HEAD); screen.applySelection();
        f.update(external(ChangedField.HEAD)); screen.tick();
        var draft = screen.selectionSession().fullSnapshot(); var authority = screen.selectionSession().fullState();
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT); screen.outfitContent().changePage(true);
        assertEquals(1, screen.outfitContent().pageIndex());
        press(screen, "头部 *"); press(screen, "详细…"); press(screen, "右裤腿");
        assertEquals(OutfitScope.RIGHT_LEG, screen.outfitContent().scope());
        var bounds = screen.layout().previewDragBounds(); var rotation = screen.previewRotation();
        assertTrue(rotation.beginDrag(bounds.centerX(), bounds.centerY(), 0, bounds));
        assertTrue(rotation.drag(0, 47)); assertTrue(rotation.endDrag(0));
        assertEquals(47F, rotation.yawDegrees());
        var mode = screen.children().stream().filter(WardrobePreviewModeButton.class::isInstance)
                .map(WardrobePreviewModeButton.class::cast).findFirst().orElseThrow();
        mode.onPress(new KeyEvent(257, 0, 0)); assertEquals(WardrobePreviewMode.ELYTRA, screen.previewMode());
        assertEquals(47F, rotation.yawDegrees()); assertLocked(f, screen, 1);
        assertEquals(draft, screen.selectionSession().fullSnapshot()); assertEquals(authority, screen.selectionSession().fullState());
        assertTrue(screen.keyPressed(new KeyEvent(closeKey, 0, 0))); assertEquals(1, f.closeCount);
        assertTrue(screen.selectionSession().closed()); assertTrue(f.authority.fullRequests().hasOutstanding());
        assertEquals(1, f.sent.size());
    }

    private static void edit(WardrobeScreen screen, ChangedField field) {
        if (field == ChangedField.CAPE) screen.selectionSession().select(Optional.of(CAPE_A));
        else { screen.outfitContent().setScope(OutfitScope.HEAD); screen.outfitContent().select(LOOK); }
    }

    private static FullPlayerFashionState external(ChangedField field) {
        return state(1, field == ChangedField.CAPE
                ? PlayerFashionStoredState.DEFAULT.withCape(Optional.of(CAPE_B))
                : new PlayerFashionStoredState(Optional.empty(), OutfitSelections.original()
                        .with(OutfitPart.HEAD, OutfitPartSelection.outfit(new OutfitId("look01")))));
    }

    private static void tickTimeout(WardrobeScreen screen) {
        for (int i = 0; i < 205; i++) screen.tick();
    }

    private static void assertConflictInBothTabs(WardrobeScreen screen, String pendingFact) {
        assertTrue(screen.selectionSession().conflict());
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            screen.selectTab(tab); var text = screen.statusText();
            assertEquals(WardrobeStatusText.Priority.ERROR, text.priority());
            assertEquals("存在外部修改", text.secondLine());
            assertEquals("存在外部修改", text.fullText().get(1));
            if (pendingFact != null) {
                assertEquals(pendingFact, text.fullText().get(2));
                assertEquals(1, text.fullText().stream().filter(pendingFact::equals).count());
            } else assertFalse(text.narration().contains("确认"));
        }
    }

    private static void assertLocked(WardrobeS04Fixture f, WardrobeScreen screen, long requestId) {
        var draft = screen.selectionSession().fullSnapshot();
        assertTrue(screen.selectionSession().waiting()); assertFalse(screen.selectionSession().canEdit());
        assertEquals(requestId, screen.selectionSession().pendingRequestId());
        assertTrue(f.authority.fullRequests().hasOutstanding());
        assertFalse(screen.canApply()); assertFalse(button(screen, "应用").active);
        assertFalse(button(screen, "重新加载").active);
        press(screen, "重新加载"); assertFalse(screen.selectionSession().reloadAuthority());
        screen.selectionSession().select(Optional.empty());
        screen.outfitContent().clear(OutfitPartSelection.NONE); screen.outfitContent().select(LOOK);
        screen.applySelection();
        assertEquals(draft, screen.selectionSession().fullSnapshot()); assertEquals(1, f.sent.size());
        assertTrue(f.legacySent.isEmpty());
    }

    private static void assertNoTransactionFacts(WardrobeStatusText text) {
        for (String phrase : List.of("存在外部修改", "确认", "保存…")) assertFalse(text.narration().contains(phrase));
    }
}
