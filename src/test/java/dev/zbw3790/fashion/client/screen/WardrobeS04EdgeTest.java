package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry;
import dev.zbw3790.fashion.client.outfit.ClientOutfitTextureResolver.Availability;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;

class WardrobeS04EdgeTest {
    @Test void ordinaryServiceRejectCanBeManuallyRetriedButReadOnlySurvivesReload() {
        for (var status:List.of(FullFashionSelectionStatus.SERVICE_UNAVAILABLE,FullFashionSelectionStatus.READ_ONLY_PERSISTENCE)) {
            var f=new WardrobeS04Fixture();var screen=f.open();
            screen.outfitContent().select(LOOK);screen.applySelection();
            f.result(screen,1,status,FullPlayerFashionState.defaults(0));
            if(status==FullFashionSelectionStatus.SERVICE_UNAVAILABLE) {
                assertTrue(screen.canApply());assertEquals(1,f.sent.size());screen.applySelection();assertEquals(2,f.sent.size());
            } else {
                assertFalse(screen.canApply());screen.reloadAuthority();screen.outfitContent().clear(OutfitPartSelection.NONE);screen.applySelection();
                assertEquals(1,f.sent.size());assertTrue(screen.statusText().secondLine().contains("只读"));
            }
        }
    }
    @Test void pendingNeverDarkensAnOtherwiseReadyOutfitAndAllViewActionsRemainAvailable() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        var entry=screen.children().stream().filter(OutfitGridEntryWidget.class::isInstance).map(OutfitGridEntryWidget.class::cast).findFirst().orElseThrow();
        assertFalse(entry.dimmed());entry.onPress(new KeyEvent(257,0,0));screen.applySelection();
        assertFalse(entry.dimmed());assertFalse(entry.canActivate());assertTrue(entry.active);
        assertEquals(WardrobeStatusText.Priority.PENDING,screen.statusText().priority());
        var before=screen.selectionSession().fullSnapshot();press(screen,"无外层");assertEquals(before,screen.selectionSession().fullSnapshot());
        press(screen,"整套");assertEquals(OutfitWardrobeContent.Panel.ROOT,screen.outfitContent().panel());
        press(screen,"详细…");assertEquals(OutfitWardrobeContent.Panel.DETAIL,screen.outfitContent().panel());
        press(screen,"右袖");assertEquals(OutfitScope.RIGHT_ARM,screen.outfitContent().scope());
        screen.selectTab(WardrobeScreen.SelectedTab.CAPE);screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);assertEquals(before,screen.selectionSession().fullSnapshot());
    }
    @ParameterizedTest @ValueSource(ints={200,320})
    void builtinButtonsKeepVanillaAppearanceButRejectInputAcrossTwoApplies(int width) {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.resize(width,240);
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.selectionSession().select(Optional.of(CAPE_A));screen.outfitContent().select(LOOK);
        for (int request=1;request<=2;request++) {
            if (request==2) press(screen,"无外层");
            var draft=screen.selectionSession().fullSnapshot();
            var labels=List.of("原版","无外层");
            var originalMessages=labels.stream().map(label -> button(screen,label).getMessage()).toList();
            screen.applySelection();assertEquals(request,f.sent.size());
            assertFalse(button(screen,"应用").active);
            for (int i=0;i<labels.size();i++) {
                var action=assertInstanceOf(WardrobeOutfitActionButton.class,button(screen,labels.get(i)));
                assertFalse(action.active);assertEquals(originalMessages.get(i),action.getMessage());
                action.setFocused(false);
                assertEquals(Identifier.withDefaultNamespace("widget/button"),action.backgroundSprite());
                action.setFocused(true);
                assertEquals(Identifier.withDefaultNamespace("widget/button_highlighted"),action.backgroundSprite());
                var click=new MouseButtonEvent(action.getX()+1,action.getY()+1,new MouseButtonInfo(0,0));
                assertFalse(action.mouseClicked(click,false));
                action.onClick(click,false);action.onPress(new KeyEvent(257,0,0));
                screen.setFocused(action);
                screen.keyPressed(new KeyEvent(257,0,0));screen.keyPressed(new KeyEvent(32,0,0));
                assertFalse(action.active);assertEquals(draft,screen.selectionSession().fullSnapshot());
                action.setFocused(false);
            }
            screen.applySelection();assertEquals(request,f.sent.size());
            f.result(screen,request,FullFashionSelectionStatus.SUCCESS,state(request,f.sent.getLast().stored()));
            for (String label:labels) {
                var action=(WardrobeOutfitActionButton)button(screen,label);
                assertTrue(action.active);assertEquals(Identifier.withDefaultNamespace("widget/button"),action.backgroundSprite());
            }
            assertFalse(screen.selectionSession().dirty());assertFalse(screen.canApply());
            assertEquals(0,f.closeCount);
        }
        press(screen,"原版");
        assertEquals(OutfitSelections.original(),screen.selectionSession().fullSnapshot().orElseThrow().outfit());
        assertEquals(Optional.of(CAPE_A),screen.selectionSession().draft());
    }
    @ParameterizedTest @ValueSource(ints={200,320})
    void reopenedPendingKeepsButtonBrightnessButUntrustedAuthorityStillDisablesVisually(int width) {
        var f=new WardrobeS04Fixture();var first=f.open();first.outfitContent().select(LOOK);first.applySelection();
        first.onClose();var screen=f.open();screen.resize(width,240);screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        assertTrue(screen.selectionSession().waiting());assertEquals(0,screen.selectionSession().pendingRequestId());
        var draft=screen.selectionSession().fullSnapshot();
        for (String label:List.of("原版","无外层")) {
            var action=(WardrobeOutfitActionButton)button(screen,label);
            assertFalse(action.active);assertNull(action.getMessage().getStyle().getColor());
            assertEquals(Identifier.withDefaultNamespace("widget/button"),action.backgroundSprite());
            action.onPress(new KeyEvent(257,0,0));
        }
        assertEquals(draft,screen.selectionSession().fullSnapshot());assertEquals(1,f.sent.size());
        f.authority.receiveFullSnapshot(f.connection,FullPlayerFashionSnapshot.unavailable());screen.tick();
        for (String label:List.of("原版","无外层")) {
            var action=(WardrobeOutfitActionButton)button(screen,label);
            assertFalse(action.active);assertNotNull(action.getMessage().getStyle().getColor());
            assertEquals(Identifier.withDefaultNamespace("widget/button_disabled"),action.backgroundSprite());
            action.onPress(new KeyEvent(32,0,0));
        }
        assertEquals(draft,screen.selectionSession().fullSnapshot());assertFalse(screen.canApply());assertEquals(1,f.sent.size());
    }
    @Test void rejectedApplyRestoresBuiltinActionsWithoutResending() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.outfitContent().select(LOOK);screen.applySelection();
        f.result(screen,1,FullFashionSelectionStatus.SERVICE_UNAVAILABLE,FullPlayerFashionState.defaults(0));
        for (String label:List.of("原版","无外层")) {
            var action=(WardrobeOutfitActionButton)button(screen,label);
            assertTrue(action.active);assertNull(action.getMessage().getStyle().getColor());
            assertEquals(Identifier.withDefaultNamespace("widget/button"),action.backgroundSprite());
        }
        assertTrue(screen.selectionSession().dirty());assertEquals(1,f.sent.size());
    }
    @Test void modelUnknownMismatchInvalidAndLoadingAreSeparateTruthfulConditions() {
        var f=new WardrobeS04Fixture(ClientOutfitRegistry.State.KNOWN,10,false,FullPlayerFashionState.defaults(0));
        assertEquals(Availability.LOADING,f.source.availability(LOOK));
        assertEquals(Availability.MODEL_INVALID,f.source.availability(new OutfitId("look03")));
        f.model.set(OutfitModel.SLIM);assertEquals(Availability.MODEL_MISMATCH,f.source.availability(new OutfitId("look02")));
        f.model.set(null);assertEquals(Availability.MODEL_UNKNOWN,f.source.availability(LOOK));assertFalse(f.source.admitted(OutfitPart.HEAD,LOOK));
        f.model.set(OutfitModel.WIDE);f.ready();assertEquals(Availability.READY,f.source.availability(LOOK));
    }
    @Test void scopeChangesOnlyAdmissionAndSelectionWhileFrontProjectionStaysFixed() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        var entry=screen.children().stream().filter(OutfitGridEntryWidget.class::isInstance).map(OutfitGridEntryWidget.class::cast)
                .filter(widget -> widget.id().value().equals("look01")).findFirst().orElseThrow();
        var plan=entry.thumbnail();assertEquals(1,plan.faces().size());assertEquals(OutfitPart.HEAD,plan.faces().getFirst().part());
        var draft=screen.selectionSession().fullSnapshot();
        for(var scope:OutfitScope.values()) {
            screen.outfitContent().setScope(scope);screen.tick();
            assertEquals(plan,entry.thumbnail());assertFalse(entry.dimmed());
            assertEquals(scope.targets.contains(OutfitPart.HEAD),entry.canActivate());
            assertTrue(screen.outfitContent().tooltip(entry.id()).contains("提供：头部"));
            assertFalse(String.join("",screen.outfitContent().tooltip(entry.id())).contains("样片"));
        }
        assertEquals(draft,screen.selectionSession().fullSnapshot());
        assertFalse(f.source.transparent(LOOK));
        assertThrows(IllegalArgumentException.class,() -> f.textures.areaTransparent(f.connection,f.hash,63,0,2,1));
        assertTrue(f.textures.areaTransparent(new Object(),f.hash,0,0,1,1).isEmpty());
    }
    @Test void loadingAndWrongModelNeverBorrowTextureAndReadyDoesNotEditDraft() {
        var f=new WardrobeS04Fixture(ClientOutfitRegistry.State.KNOWN,10,false,FullPlayerFashionState.defaults(0));
        var screen=f.open();var draft=screen.selectionSession().fullSnapshot();
        assertTrue(f.source.texture(LOOK).isEmpty());
        assertTrue(screen.outfitContent().tooltip(LOOK).contains("加载中"));
        f.ready();screen.tick();assertTrue(f.source.texture(LOOK).isPresent());
        assertEquals(draft,screen.selectionSession().fullSnapshot());
        f.model.set(OutfitModel.SLIM);screen.tick();
        assertTrue(f.source.texture(new OutfitId("look02")).isEmpty());
        assertEquals(draft,screen.selectionSession().fullSnapshot());
    }
    @Test void scopeSwitchAndTabReturnRestoreSemanticContentFocus() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        var entry=screen.children().stream().filter(OutfitGridEntryWidget.class::isInstance).map(OutfitGridEntryWidget.class::cast).skip(4).findFirst().orElseThrow();
        screen.setFocused(entry);screen.selectTab(WardrobeScreen.SelectedTab.CAPE);screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        assertEquals(entry.id(),((OutfitGridEntryWidget)screen.getFocused()).id());
        screen.setFocused(button(screen,"整套"));screen.keyPressed(new KeyEvent(257,0,0));
        screen.setFocused(button(screen,"腿部"));screen.keyPressed(new KeyEvent(257,0,0));
        assertEquals("腿部",((net.minecraft.client.gui.components.AbstractWidget)screen.getFocused()).getMessage().getString());
    }
    @Test void mouseDelayInputDismissalAndKeyboardTargetChangeAreDeterministic() {
        var tooltip=new WardrobeTooltipState();
        assertFalse(tooltip.visible("a",false,0));assertFalse(tooltip.visible("a",false,299_000_000));
        assertTrue(tooltip.visible("a",false,300_000_000));tooltip.dismiss();assertFalse(tooltip.visible("a",true,400_000_000));
        assertTrue(tooltip.visible("b",true,400_000_000));assertFalse(tooltip.visible(null,false,500_000_000));
        assertFalse(tooltip.visible("b",false,600_000_000));
    }
    @ParameterizedTest @ValueSource(ints={200,320})
    void tooltipRemainsInsideReservedAreaEvenAtExtremeAnchorAndLongestText(int width) {
        var layout=WardrobeLayout.calculate(width,240,9);var area=layout.tooltipBounds();
        for(int x:List.of(-999,0,width,9999)) for(int y:List.of(-999,0,240,9999)) {
            var placed=layout.tooltipPlacement(x,y,area.width()-8,92);
            assertTrue(placed.x()-4>=area.x() && placed.right()+4<=area.right());
            assertTrue(placed.y()-4>=area.y() && placed.bottom()+4<=area.bottom());
            assertFalse(placed.overlaps(layout.applyButtonBounds()));assertFalse(placed.overlaps(layout.paginationBounds()));
        }
    }
    @Test void legacyOutfitTabStaysInspectableWithoutFakeFullAuthorityAndCapeStillSendsLegacy() {
        var f=new WardrobeS04Fixture();
        f.authority.beginConnection(f.connection);
        f.authority.replace(new PlayerFashionSnapshot(true,List.of(new PlayerFashionEntry(SELF,PlayerFashionAuthoritativeState.vanilla()))));
        var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        assertEquals("服务器不支持装束",screen.outfitContent().summary());assertTrue(screen.selectionSession().fullSnapshot().isEmpty());
        screen.outfitContent().select(LOOK);screen.outfitContent().clear(OutfitPartSelection.NONE);assertTrue(screen.selectionSession().fullSnapshot().isEmpty());
        screen.selectionSession().select(Optional.of(CAPE_A));screen.applySelection();
        assertEquals(1,f.legacySent.size());assertTrue(f.sent.isEmpty());
    }
    @Test void trustedFullRouteArrivingAfterOpenBuildsOutfitControlsWithoutInventedBaseline() {
        var f=new WardrobeS04Fixture();f.authority.beginConnection(f.connection);var screen=f.open();
        screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);assertTrue(screen.selectionSession().fullSnapshot().isEmpty());
        f.authority.receiveFullSnapshot(f.connection,new FullPlayerFashionSnapshot(true,List.of(new FullPlayerFashionEntry(SELF,FullPlayerFashionState.defaults(0)))));
        screen.tick();assertNotNull(button(screen,"原版"));assertNotNull(button(screen,"无外层"));
        assertTrue(screen.selectionSession().authorityKnown());assertFalse(screen.selectionSession().dirty());
    }
}
