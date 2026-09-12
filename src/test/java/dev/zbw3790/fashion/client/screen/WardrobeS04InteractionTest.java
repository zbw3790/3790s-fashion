package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry;

class WardrobeS04InteractionTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothTabEditOrdersSendOneFullPayloadAndSecondApplyStaysSameWindow(boolean capeFirst) {
        var f=new WardrobeS04Fixture();var screen=f.open();var session=screen.selectionSession();
        if (capeFirst) session.select(Optional.of(CAPE_A));
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.outfitContent().select(LOOK);
        screen.outfitContent().changePage(true);
        screen.outfitContent().setScope(OutfitScope.LEFT_ARM);
        if (!capeFirst) { screen.selectTab(WardrobeScreen.SelectedTab.CAPE);session.select(Optional.of(CAPE_A)); }
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.applySelection();screen.applySelection();
        assertEquals(1,f.sent.size());var payload=f.sent.getFirst();
        assertEquals(Optional.of(CAPE_A),payload.stored().cape());
        assertEquals(OutfitSelections.original().applyOutfit(OutfitPart.ALL,LOOK,OutfitPart.ALL).selections(),payload.stored().outfit());
        assertEquals(0,payload.expectedRevision());assertFalse(session.canEdit());
        var confirmed=state(1,payload.stored());
        f.update(confirmed);screen.tick();f.result(screen,1,FullFashionSelectionStatus.SUCCESS,confirmed);
        assertSame(session,screen.selectionSession());assertEquals(0,f.closeCount);assertFalse(session.closed());
        assertFalse(session.dirty());assertFalse(screen.canApply());assertEquals(1,screen.outfitContent().pageIndex());
        assertEquals(OutfitScope.LEFT_ARM,screen.outfitContent().scope());assertEquals(WardrobeScreen.SelectedTab.OUTFIT,screen.selectedTab());
        screen.outfitContent().clear(OutfitPartSelection.NONE);screen.applySelection();
        assertEquals(2,f.sent.size());assertEquals(1,f.sent.getLast().expectedRevision());
        assertEquals(OutfitPartSelection.NONE,f.sent.getLast().stored().outfit().get(OutfitPart.LEFT_ARM));
    }
    @ParameterizedTest @ValueSource(ints={256,69})
    void closeReopenPendingLocksBothDomainsButAllowsViewAndLateAck(int key) {
        var f=new WardrobeS04Fixture();var screen=f.open();
        screen.selectionSession().select(Optional.of(CAPE_A));screen.outfitContent().select(LOOK);screen.applySelection();
        screen.keyPressed(new KeyEvent(key,0,0));var reopened=f.open();
        var baseline=reopened.selectionSession().fullSnapshot().orElseThrow();
        assertFalse(reopened.selectionSession().canEdit());
        reopened.selectionSession().select(Optional.of(CAPE_B));
        reopened.selectTab(WardrobeScreen.SelectedTab.OUTFIT);press(reopened,"无外层");
        reopened.outfitContent().select(LOOK);reopened.outfitContent().openPanel();
        assertFalse(reopened.selectionSession().reloadAuthority());reopened.applySelection();
        assertEquals(baseline,reopened.selectionSession().fullSnapshot().orElseThrow());
        assertEquals(OutfitWardrobeContent.Panel.ROOT,reopened.outfitContent().panel());assertEquals(1,f.sent.size());
        f.result(reopened,1,FullFashionSelectionStatus.SUCCESS,state(1,f.sent.getFirst().stored()));
        assertTrue(reopened.selectionSession().canEdit());assertEquals(f.sent.getFirst().stored(),reopened.selectionSession().fullSnapshot().orElseThrow());
        reopened.selectionSession().select(Optional.of(CAPE_B));reopened.keyPressed(new KeyEvent(key,0,0));
        assertEquals(Optional.of(CAPE_A),f.open().selectionSession().draft());
    }
    @Test void crossTabConflictAndExplicitWholeReloadKeepViewAndDoNotSend() {
        var f=new WardrobeS04Fixture();var screen=f.open();
        screen.selectionSession().select(Optional.of(CAPE_A));screen.outfitContent().select(LOOK);
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);screen.outfitContent().changePage(true);
        screen.outfitContent().setScope(OutfitScope.UPPER);
        var external=state(1,new PlayerFashionStoredState(Optional.of(CAPE_A),OutfitSelections.original()));
        f.update(external);screen.tick();
        assertTrue(screen.selectionSession().conflict());assertTrue(screen.statusText().secondLine().contains("存在外部修改"));
        screen.applySelection();assertTrue(f.sent.isEmpty());
        screen.selectTab(WardrobeScreen.SelectedTab.CAPE);assertTrue(screen.statusText().secondLine().contains("存在外部修改"));
        press(screen,"重新加载");
        assertEquals(external.stored(),screen.selectionSession().fullSnapshot().orElseThrow());assertFalse(screen.selectionSession().hasError());
        assertEquals(OutfitScope.UPPER,screen.outfitContent().scope());assertEquals(1,screen.outfitContent().pageIndex());assertTrue(f.sent.isEmpty());
    }
    @Test void invalidResultRetainsDraftAndPreventsBlindRetryUntilMeaningfulEditOrReload() {
        var f=new WardrobeS04Fixture();var screen=f.open();
        screen.outfitContent().select(LOOK);screen.applySelection();
        f.result(screen,1,FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION,FullPlayerFashionState.defaults(0));
        assertTrue(screen.selectionSession().dirty());assertTrue(screen.selectionSession().hasError());
        screen.applySelection();screen.outfitContent().select(LOOK);screen.applySelection();assertEquals(1,f.sent.size());
        screen.outfitContent().clear(OutfitPartSelection.NONE);screen.applySelection();assertEquals(2,f.sent.size());
    }
    @Test void higherRevisionBeforeResultWinsWithoutResettingScopeAndPanel() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.outfitContent().select(LOOK);screen.applySelection();
        var confirmed=state(1,f.sent.getFirst().stored());
        var later=state(2,confirmed.stored().withCape(Optional.of(CAPE_B)));
        f.update(later);screen.outfitContent().openDetail();screen.tick();
        f.result(screen,1,FullFashionSelectionStatus.SUCCESS,confirmed);
        assertEquals(later,screen.selectionSession().fullState().orElseThrow());
        assertEquals(later.stored(),screen.selectionSession().fullSnapshot().orElseThrow());
        assertEquals(OutfitWardrobeContent.Panel.DETAIL,screen.outfitContent().panel());
        assertFalse(screen.selectionSession().dirty());assertEquals(1,f.sent.size());
    }
    @Test void scopesAndPanelsPreservePagesAndFullDraftAcrossActualResizeAndEmergency() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.outfitContent().select(LOOK);
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);screen.outfitContent().changePage(true);
        screen.outfitContent().setScope(OutfitScope.RIGHT_LEG);screen.outfitContent().openDetail();
        var session=screen.selectionSession();var stored=session.fullSnapshot();
        var drag=screen.layout().previewDragBounds();screen.previewRotation().beginDrag(drag.centerX(),drag.centerY(),0,drag);screen.previewRotation().drag(0,42);
        for (int width:List.of(200,1,320)) {
            screen.resize(width,240);assertSame(session,screen.selectionSession());assertEquals(stored,session.fullSnapshot());
            assertEquals(1,screen.outfitContent().pageIndex());assertEquals(OutfitScope.RIGHT_LEG,screen.outfitContent().scope());
            assertEquals(OutfitWardrobeContent.Panel.DETAIL,screen.outfitContent().panel());assertEquals(WardrobePreviewRotation.FRONT_FACING_YAW_DEGREES+42F,screen.previewRotation().yawDegrees());
        }
        assertTrue(f.sent.isEmpty());
    }
    @Test void inspectableMismatchAndEmptyIntersectionRemainFocusableButDoNotEdit() {
        var f=new WardrobeS04Fixture();f.model.set(OutfitModel.SLIM);var screen=f.open();
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        var widgets=screen.children().stream().filter(OutfitGridEntryWidget.class::isInstance).map(OutfitGridEntryWidget.class::cast).toList();
        var mismatch=widgets.stream().filter(w -> w.id().value().equals("look02")).findFirst().orElseThrow();
        assertTrue(mismatch.active);assertFalse(mismatch.canActivate());
        screen.setFocused(mismatch);assertSame(mismatch,screen.getFocused());
        mismatch.onPress(new KeyEvent(257,0,0));assertFalse(screen.selectionSession().dirty());
        screen.outfitContent().setScope(OutfitScope.BODY);screen.resize(200,240);
        var partial=screen.children().stream().filter(OutfitGridEntryWidget.class::isInstance).map(OutfitGridEntryWidget.class::cast)
                .filter(w -> w.id().value().equals("look01")).findFirst().orElseThrow();
        assertTrue(partial.active);assertFalse(partial.canActivate());partial.onPress(new KeyEvent(32,0,0));
        assertFalse(screen.selectionSession().dirty());assertTrue(partial.getMessage().getString().contains("不提供当前范围"));
    }
    @Test void lateAssetChangesAvailabilityWithoutDraftOrAuthorityOrRequest() {
        var f=new WardrobeS04Fixture(ClientOutfitRegistry.State.KNOWN,10,false,FullPlayerFashionState.defaults(0));
        var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        assertFalse(screen.outfitContent().canActivate(LOOK));var before=screen.selectionSession().fullSnapshot();
        f.ready();screen.tick();assertTrue(screen.outfitContent().canActivate(LOOK));
        assertEquals(before,screen.selectionSession().fullSnapshot());assertTrue(f.sent.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={320,200})
    void keyboardVisitsOrderedControlsBothDirectionsAndArrowsStayInGrid(int width) {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.resize(width,240);screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.setFocused(null);var sequence=new ArrayList<String>();
        for (int i=0;i<6;i++) { assertTrue(screen.keyPressed(new KeyEvent(258,0,0)));sequence.add(((AbstractWidget)screen.getFocused()).getMessage().getString()); }
        assertEquals(List.of("披风","装束","当前预览：披风；点击查看鞘翅","整套","原版","无外层"),sequence);
        screen.keyPressed(new KeyEvent(258,0,1));assertEquals("原版",((AbstractWidget)screen.getFocused()).getMessage().getString());
        var first=screen.children().stream().filter(OutfitGridEntryWidget.class::isInstance).map(OutfitGridEntryWidget.class::cast).findFirst().orElseThrow();
        screen.setFocused(first);screen.keyPressed(new KeyEvent(264,0,0));
        assertEquals("look04",((OutfitGridEntryWidget)screen.getFocused()).id().value());
        int page=screen.outfitContent().pageIndex();screen.keyPressed(new KeyEvent(262,0,0));assertEquals(page,screen.outfitContent().pageIndex());
        screen.resize(width==320?200:320,240);assertEquals("look05",((OutfitGridEntryWidget)screen.getFocused()).id().value());
    }
    @ParameterizedTest @ValueSource(ints={257,32})
    void actualFocusedActivationOpensPanelsChangesOnlyTargetsAndReturnsToGrid(int key) {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.setFocused(button(screen,"整套"));screen.keyPressed(new KeyEvent(key,0,0));
        assertEquals(OutfitWardrobeContent.Panel.ROOT,screen.outfitContent().panel());
        screen.setFocused(button(screen,"详细…"));screen.keyPressed(new KeyEvent(key,0,0));
        assertEquals(OutfitWardrobeContent.Panel.DETAIL,screen.outfitContent().panel());
        screen.setFocused(button(screen,"左袖"));screen.keyPressed(new KeyEvent(key,0,0));
        assertEquals(OutfitScope.LEFT_ARM,screen.outfitContent().scope());assertEquals(OutfitWardrobeContent.Panel.GRID,screen.outfitContent().panel());
        screen.setFocused(button(screen,"无外层"));screen.keyPressed(new KeyEvent(key,0,0));
        assertEquals(OutfitSelections.original().with(OutfitPart.LEFT_ARM,OutfitPartSelection.NONE),screen.selectionSession().fullSnapshot().orElseThrow().outfit());
        assertTrue(f.sent.isEmpty());
    }
}
