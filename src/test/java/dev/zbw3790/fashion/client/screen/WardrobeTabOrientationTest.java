package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.cape.*;
import dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;

/** 普通 JVM 驱动真实 Screen 事件；不启动或操作 Minecraft 窗口。 */
class WardrobeTabOrientationTest {
    @Test
    void newAndReopenedScreensStartWithCapeAndTheAlreadyVerifiedBackYaw() {
        var f = new WardrobeS04Fixture(); var screen = f.open();
        assertEquals(WardrobeScreen.SelectedTab.CAPE, screen.selectedTab());
        assertEquals(180.0F, screen.previewRotation().yawDegrees());
        assertEquals(180.0F, WardrobePreviewRotation.BACK_FACING_YAW_DEGREES);
        assertEquals(0.0F, WardrobePreviewRotation.FRONT_FACING_YAW_DEGREES);
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT); dragTo(screen, 47.0F); screen.onClose();
        var reopened = f.open();
        assertEquals(WardrobeScreen.SelectedTab.CAPE, reopened.selectedTab());
        assertEquals(180.0F, reopened.previewRotation().yawDegrees());
    }

    @ParameterizedTest @ValueSource(ints = {257, 32})
    void actualTabControlsChooseCanonicalFacingAndAllowManualRotationAfterEachChange(int key) {
        var f = fixtureWithCapePages(); var screen = f.open();
        screen.selectionSession().select(Optional.of(CAPE_A)); screen.outfitContent().select(LOOK);
        screen.capeContent().changePage(true); screen.outfitContent().changePage(true);
        screen.outfitContent().setScope(OutfitScope.LEFT_ARM); modeButton(screen).onPress(new KeyEvent(key, 0, 0));
        var before = unchangedState(f, screen);
        activateTab(screen, WardrobeScreen.SelectedTab.OUTFIT, key);
        assertEquals(0.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        dragTo(screen, 47.0F); assertEquals(before, unchangedState(f, screen));
        activateTab(screen, WardrobeScreen.SelectedTab.CAPE, key);
        assertEquals(180.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        dragTo(screen, 233.0F);
        activateTab(screen, WardrobeScreen.SelectedTab.OUTFIT, key);
        assertEquals(0.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        dragTo(screen, 71.0F); screen.tick(); assertEquals(71.0F, screen.previewRotation().yawDegrees());
    }

    @ParameterizedTest @EnumSource(WardrobeScreen.SelectedTab.class)
    void repeatingTheActiveTabByPointerOrKeyboardDoesNotChangeManualYaw(WardrobeScreen.SelectedTab tab) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); screen.selectTab(tab); dragTo(screen, 47.0F);
        var before = unchangedState(f, screen); var active = button(screen, tab.label);
        // 直接使用控件 onClick，绕开普通 JVM 中不存在的游戏 SoundManager。
        active.onClick(mouse(active.getX() + 8, active.getY() + 8), false);
        assertEquals(47.0F, screen.previewRotation().yawDegrees());
        for (int key : new int[]{257, 32}) {
            activateTab(screen, tab, key); assertEquals(47.0F, screen.previewRotation().yawDegrees());
            assertEquals(before, unchangedState(f, screen));
        }
    }

    @ParameterizedTest @EnumSource(WardrobeScreen.SelectedTab.class)
    void realResizeRoundTripAndEmergencyRebuildKeepManualYawAndAllOtherViewState(WardrobeScreen.SelectedTab tab) {
        var f = fixtureWithCapePages(); var screen = f.open();
        screen.selectionSession().select(Optional.of(CAPE_A)); screen.outfitContent().select(LOOK);
        screen.capeContent().changePage(true); screen.outfitContent().changePage(true);
        screen.selectTab(tab); screen.outfitContent().setScope(OutfitScope.RIGHT_LEG); screen.outfitContent().openDetail();
        modeButton(screen).onPress(new KeyEvent(257, 0, 0)); dragTo(screen, 47.0F);
        var before = unchangedState(f, screen); var session = screen.selectionSession();
        for (int width : new int[]{200, 320, 1, 200, 320}) {
            screen.resize(width, 240);
            assertEquals(47.0F, screen.previewRotation().yawDegrees()); assertEquals(tab, screen.selectedTab());
            assertEquals(before, unchangedState(f, screen)); assertSame(session, screen.selectionSession());
            assertEquals(OutfitWardrobeContent.Panel.DETAIL, screen.outfitContent().panel());
            assertEquals(width == 1 ? WardrobeLayout.Mode.TOO_SMALL : width == 200 ? WardrobeLayout.Mode.COMPACT : WardrobeLayout.Mode.STANDARD,
                    screen.layout().mode());
        }
    }

    @ParameterizedTest @EnumSource(WardrobeScreen.SelectedTab.class)
    void applyPendingSuccessAndRejectDoNotResetTheFacing(WardrobeScreen.SelectedTab tab) {
        for (var result : List.of(FullFashionSelectionStatus.SUCCESS, FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION,
                FullFashionSelectionStatus.SERVICE_UNAVAILABLE, FullFashionSelectionStatus.CONFLICT)) {
            var f = new WardrobeS04Fixture(); var screen = f.open(); screen.selectTab(tab);
            screen.selectionSession().select(Optional.of(CAPE_A)); screen.outfitContent().select(LOOK); dragTo(screen, 47.0F);
            screen.applySelection(); assertEquals(1, f.sent.size()); assertTrue(screen.selectionSession().waiting());
            assertEquals(47.0F, screen.previewRotation().yawDegrees());
            screen.tick(); screen.applySelection(); assertEquals(1, f.sent.size());
            assertEquals(47.0F, screen.previewRotation().yawDegrees());
            var authority = result == FullFashionSelectionStatus.SUCCESS ? state(1, f.sent.getFirst().stored()) : FullPlayerFashionState.defaults(0);
            f.result(screen, 1, result, authority);
            assertEquals(47.0F, screen.previewRotation().yawDegrees()); assertEquals(tab, screen.selectedTab());
            assertFalse(screen.selectionSession().waiting()); assertEquals(0, f.closeCount);
            assertEquals(result != FullFashionSelectionStatus.SUCCESS, screen.selectionSession().hasError());
        }
    }

    @Test
    void switchingTabsWhilePendingChangesFacingButKeepsTheFullRequestAndDraft() {
        var f = fixtureWithCapePages(); var screen = f.open();
        screen.selectionSession().select(Optional.of(CAPE_A)); screen.outfitContent().select(LOOK);
        screen.capeContent().changePage(true); screen.outfitContent().changePage(true);
        screen.outfitContent().setScope(OutfitScope.UPPER); modeButton(screen).onPress(new KeyEvent(257, 0, 0));
        screen.applySelection(); assertTrue(screen.selectionSession().waiting());
        var before = unchangedState(f, screen);
        activateTab(screen, WardrobeScreen.SelectedTab.OUTFIT, 257);
        assertEquals(0.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        dragTo(screen, 47.0F); activateTab(screen, WardrobeScreen.SelectedTab.OUTFIT, 32);
        assertEquals(47.0F, screen.previewRotation().yawDegrees());
        activateTab(screen, WardrobeScreen.SelectedTab.CAPE, 32);
        assertEquals(180.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        screen.applySelection(); assertEquals(1, f.sent.size());
    }

    @Test
    void externalAuthorityConflictTabSwitchAndExplicitReloadHaveIndependentYawRules() {
        var f = fixtureWithCapePages(); var screen = f.open(); screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.selectionSession().select(Optional.of(CAPE_A)); screen.outfitContent().select(LOOK);
        screen.capeContent().changePage(true); screen.outfitContent().changePage(true); screen.outfitContent().setScope(OutfitScope.UPPER);
        dragTo(screen, 47.0F);
        var external = state(1, new PlayerFashionStoredState(Optional.of(CAPE_A), OutfitSelections.original()));
        f.update(external); screen.tick();
        assertTrue(screen.selectionSession().conflict()); assertEquals(47.0F, screen.previewRotation().yawDegrees());
        var before = unchangedState(f, screen);
        activateTab(screen, WardrobeScreen.SelectedTab.CAPE, 257);
        assertEquals(180.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        activateTab(screen, WardrobeScreen.SelectedTab.OUTFIT, 257);
        assertEquals(0.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        dragTo(screen, 47.0F); press(screen, "重新加载");
        assertEquals(47.0F, screen.previewRotation().yawDegrees()); assertFalse(screen.selectionSession().conflict());
        assertEquals(external.stored(), screen.selectionSession().fullSnapshot().orElseThrow());
        assertEquals(1, screen.capeContent().pageIndex()); assertEquals(1, screen.outfitContent().pageIndex());
        assertEquals(OutfitScope.UPPER, screen.outfitContent().scope()); assertTrue(f.sent.isEmpty());
    }

    @Test
    void scopePanelsBothPagesAndAllSelectionActionsPreserveTheManualAngle() {
        var f = fixtureWithCapePages(); var screen = f.open(); screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT); dragTo(screen, 47.0F);
        for (String label : List.of("整套", "详细…", "左袖", "左袖", "腿部", "下一页", "上一页")) {
            press(screen, label); assertEquals(47.0F, screen.previewRotation().yawDegrees(), label);
        }
        screen.outfitContent().select(LOOK); screen.tick(); assertEquals(47.0F, screen.previewRotation().yawDegrees());
        for (String label : List.of("无外层", "原版")) { press(screen, label); assertEquals(47.0F, screen.previewRotation().yawDegrees()); }
        screen.selectionSession().select(Optional.of(CAPE_A)); screen.tick(); assertEquals(47.0F, screen.previewRotation().yawDegrees());
        screen.selectTab(WardrobeScreen.SelectedTab.CAPE); dragTo(screen, 47.0F);
        for (String label : List.of("下一页", "上一页")) { press(screen, label); assertEquals(47.0F, screen.previewRotation().yawDegrees()); }
        assertTrue(screen.capeContent().activateSlot(0)); screen.tick();
        assertEquals(Optional.empty(), screen.selectionSession().draft()); assertEquals(47.0F, screen.previewRotation().yawDegrees());
    }

    @ParameterizedTest @EnumSource(WardrobeScreen.SelectedTab.class)
    void capeElytraPreviewModesNeverChooseFacing(WardrobeScreen.SelectedTab tab) {
        var f = new WardrobeS04Fixture(); var screen = f.open(); screen.selectTab(tab); dragTo(screen, 47.0F);
        var draft = screen.selectionSession().fullSnapshot(); var authority = screen.selectionSession().fullState();
        for (var expected : List.of(WardrobePreviewMode.ELYTRA, WardrobePreviewMode.CAPE)) {
            modeButton(screen).onPress(new KeyEvent(257, 0, 0)); screen.tick();
            assertEquals(expected, screen.previewMode()); assertEquals(47.0F, screen.previewRotation().yawDegrees());
            assertEquals(draft, screen.selectionSession().fullSnapshot()); assertEquals(authority, screen.selectionSession().fullState());
        }
    }

    @Test
    void textureReadinessAndRegistryRebuildDoNotReapplyActiveTabOrientation() {
        var f = new WardrobeS04Fixture(ClientOutfitRegistry.State.KNOWN, 10, false, FullPlayerFashionState.defaults(0));
        var screen = f.open(); screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT); dragTo(screen, 47.0F);
        var before = unchangedState(f, screen); assertTrue(f.source.texture(LOOK).isEmpty());
        f.ready(); screen.tick(); assertTrue(f.source.texture(LOOK).isPresent());
        assertEquals(47.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
        var entries = new ArrayList<>(f.outfits.entries());
        entries.add(new OutfitRegistrySnapshot.Entry(new OutfitId("new-entry"), OutfitPart.ALL, Set.of(OutfitModel.WIDE), Map.of(OutfitModel.WIDE, f.hash)));
        f.outfits.replace(f.connection, new OutfitRegistrySnapshot(true, entries)); screen.tick();
        assertEquals(47.0F, screen.previewRotation().yawDegrees()); assertEquals(before, unchangedState(f, screen));
    }

    @Test
    void ordinaryAuthorityUpdateDoesNotRotateAnUnmodifiedScreen() {
        var f = new WardrobeS04Fixture(); var screen = f.open(); screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT); dragTo(screen, 47.0F);
        var external = state(1, new PlayerFashionStoredState(Optional.of(CAPE_B), OutfitSelections.original()));
        f.update(external); screen.tick();
        assertEquals(external, screen.selectionSession().fullState().orElseThrow());
        assertEquals(47.0F, screen.previewRotation().yawDegrees()); assertFalse(screen.selectionSession().dirty());
    }

    private static List<Object> unchangedState(WardrobeS04Fixture f, WardrobeScreen screen) {
        var session = screen.selectionSession();
        return List.of(session.fullSnapshot(), session.fullState(), session.dirty(), session.waiting(), session.pendingRequestId(),
                session.conflict(), session.hasError(), session.canEdit(), f.authority.fullRequests().hasOutstanding(),
                List.copyOf(f.sent), List.copyOf(f.legacySent), screen.capeContent().pageIndex(), screen.outfitContent().pageIndex(),
                screen.outfitContent().scope(), screen.previewMode(),
                OutfitPart.CANONICAL_ORDER.stream().map(part -> session.conflicts(Set.of(part))).toList());
    }
    private static WardrobeS04Fixture fixtureWithCapePages() {
        var f = new WardrobeS04Fixture(); var capes = new ArrayList<>(f.capes.entries());
        for (int i = 0; i < 24; i++) capes.add(new CapeCosmeticMetadata(new CapeId("page%02d".formatted(i)), "a".repeat(64), Optional.empty()));
        f.capes.replace(new CapeRegistrySnapshot(capes)); return f;
    }
    private static void activateTab(WardrobeScreen screen, WardrobeScreen.SelectedTab tab, int key) {
        screen.setFocused(button(screen, tab.label)); assertTrue(screen.keyPressed(new KeyEvent(key, 0, 0)));
        assertEquals(tab, screen.selectedTab());
    }
    private static WardrobePreviewModeButton modeButton(WardrobeScreen screen) {
        return screen.children().stream().filter(WardrobePreviewModeButton.class::isInstance)
                .map(WardrobePreviewModeButton.class::cast).findFirst().orElseThrow();
    }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)); }
    private static void dragTo(WardrobeScreen screen, float yaw) {
        var bounds = screen.layout().previewDragBounds(); var event = mouse(bounds.centerX(), bounds.centerY());
        assertTrue(screen.mouseClicked(event, false));
        assertTrue(screen.mouseDragged(event, yaw - screen.previewRotation().yawDegrees(), 0));
        assertTrue(screen.mouseReleased(event)); assertFalse(screen.previewRotation().isDragging());
        assertEquals(yaw, screen.previewRotation().yawDegrees());
    }
}
