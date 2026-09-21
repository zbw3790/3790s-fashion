package dev.zbw3790.fashion.client.screen;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.ArmorWardrobeFixture.*;

class ArmorWardrobeInteractionTest {
    @TempDir Path directory;
    ArmorWardrobeFixture f;
    @BeforeEach void setup() throws Exception {f=new ArmorWardrobeFixture(directory);}
    @Test void thirdTabUsesSameSessionCanonicalFrontAndReopensCurrentAuthority() {
        var screen=f.open();assertEquals(WardrobeScreen.SelectedTab.CAPE,screen.selectedTab());assertEquals(180,screen.previewRotation().yawDegrees());
        var session=screen.selectionSession();tab(screen,WardrobeScreen.SelectedTab.ARMOR);assertSame(session,screen.selectionSession());assertEquals(0,screen.previewRotation().yawDegrees());
        press(screen,"style:armor00");screen.applySelection();f.success(screen);screen.onClose();
        var reopened=f.open();assertEquals(stored(screen),stored(reopened));assertFalse(reopened.canApply());
    }
    @Test void slotFirstBuiltinsAndTexturesNeverChangeOtherSlots() {
        var screen=f.armor();assertEquals(ArmorSlot.HEAD,screen.armorContent().slot());
        press(screen,"style:armor02");slot(screen,ArmorSlot.CHEST);press(screen,"style:armor00");
        assertEquals(ArmorSelection.custom(ORANGE),armor(screen).get(ArmorSlot.HEAD));
        assertEquals(ArmorSelection.custom(BLUE),armor(screen).get(ArmorSlot.CHEST));
        press(screen,"hidden");assertEquals(2,armor(screen).hiddenMask());press(screen,"original");
        assertEquals(ArmorSelection.custom(ORANGE),armor(screen).get(ArmorSlot.HEAD));
        slot(screen,ArmorSlot.HEAD);press(screen,"original");assertFalse(screen.canApply());
    }
    @ParameterizedTest @EnumSource(ArmorSlot.class)
    void detailedChangesOnlyTargetAndFiltersUnsupportedStyles(ArmorSlot slot) {
        var screen=f.armor();slot(screen,slot);
        assertEquals(slot==ArmorSlot.HEAD,screen.armorContent().filteredEntries().stream().anyMatch(entry -> entry.id().equals(HEAD_ONLY)));
        press(screen,"style:armor00");
        for(var actual:ArmorSlot.CANONICAL_ORDER) assertEquals(actual==slot?ArmorSelection.custom(BLUE):ArmorSelection.ORIGINAL,armor(screen).get(actual));
        press(screen,"hidden");assertEquals(1<<slot.ordinal(),armor(screen).hiddenMask());press(screen,"original");assertFalse(screen.canApply());
    }
    @Test void reopeningSlotPanelRetainsExplicitTargetAndAllDrafts() {
        var screen=f.armor();
        for(var slot:ArmorSlot.CANONICAL_ORDER) {
            slot(screen,slot);press(screen,"style:armor00");press(screen,"scope");screen.tick();
            assertEquals(ArmorWardrobeContent.Panel.SLOTS,screen.armorContent().panel());
            assertEquals(slot,screen.armorContent().slot());press(screen,"scope");
            assertEquals(ArmorWardrobeContent.Panel.GRID,screen.armorContent().panel());
        }
        for(var slot:ArmorSlot.CANONICAL_ORDER) assertEquals(ArmorSelection.custom(BLUE),armor(screen).get(slot));
    }
    @Test void repeatedIdenticalChoiceIsNoOpAfterSuccess() {
        var screen=f.armor();press(screen,"style:armor00");screen.applySelection();f.success(screen);
        press(screen,"style:armor00");assertFalse(screen.canApply());screen.applySelection();assertEquals(1,f.base.sent.size());
    }
    @ParameterizedTest @ValueSource(ints={200,320})
    void scrollingTargetsStableIdsAndKeepsModeSlotAcrossResize(int width) {
        var screen=f.armor();screen.resize(width,240);slot(screen,ArmorSlot.CHEST);
        var bounds=screen.layout().gridBounds();assertTrue(screen.mouseScrolled(bounds.centerX(),bounds.centerY(),0,-1));
        assertEquals(8,screen.armorContent().offset());assertEquals(new ArmorStyleId("armor09"),screen.armorContent().visibleEntries().getFirst().id());
        press(screen,"style:armor09");var before=stored(screen);
        for(int next:List.of(200,320,1,200,320)) {screen.resize(next,240);assertEquals(ArmorSlot.CHEST,screen.armorContent().slot());assertEquals(8,screen.armorContent().offset());assertEquals(before,stored(screen));}
        tab(screen,WardrobeScreen.SelectedTab.OUTFIT);tab(screen,WardrobeScreen.SelectedTab.ARMOR);assertEquals(8,screen.armorContent().offset());
    }
    @Test void threeDomainsOneApplyStayOpenAndCancelDoesNotBroadcast() {
        var screen=f.armor();press(screen,"style:armor00");screen.selectionSession().select(Optional.of(WardrobeS04Fixture.CAPE_A));
        screen.outfitContent().select(WardrobeS04Fixture.LOOK);var draft=stored(screen);assertTrue(f.base.sent.isEmpty());
        screen.applySelection();assertEquals(1,f.base.sent.size());assertEquals(draft,f.base.sent.getFirst().stored());assertTrue(screen.selectionSession().waiting());
        tab(screen,WardrobeScreen.SelectedTab.CAPE);tab(screen,WardrobeScreen.SelectedTab.ARMOR);assertEquals(draft,stored(screen));
        assertFalse(button(screen,"hidden").active);slot(screen,ArmorSlot.HEAD);assertFalse(((ArmorGridEntryWidget)button(screen,"style:armor00")).canActivate());
        f.success(screen);assertFalse(screen.canApply());assertFalse(screen.selectionSession().closed());assertEquals(0,f.base.closeCount);
        press(screen,"hidden");press(screen,"cancel");assertEquals(1,f.base.closeCount);assertEquals(1,f.base.sent.size());assertEquals(draft,stored(f.open()));
    }
    @ParameterizedTest @ValueSource(ints={69,256})
    void closingDiscardsAllThreeDrafts(int key) {
        var screen=f.armor();press(screen,"hidden");screen.selectionSession().select(Optional.of(WardrobeS04Fixture.CAPE_A));screen.outfitContent().select(WardrobeS04Fixture.LOOK);
        assertTrue(screen.keyPressed(new KeyEvent(key,0,0)));assertTrue(screen.selectionSession().closed());assertTrue(f.base.sent.isEmpty());assertFalse(f.open().selectionSession().dirty());
    }
    @Test void disjointExternalMergeKeepsArmorDraftAndSameSlotConflictBlocksWholeRequest() {
        var screen=f.armor();slot(screen,ArmorSlot.HEAD);press(screen,"style:armor00");
        var external=PlayerFashionStoredState.DEFAULT.withCape(Optional.of(WardrobeS04Fixture.CAPE_B)).withArmor(ArmorSelections.original().with(ArmorSlot.FEET,ArmorSelection.HIDDEN));
        f.base.update(state(1,external));screen.tick();assertEquals(external.cape(),stored(screen).cape());assertEquals(ArmorSelection.HIDDEN,armor(screen).get(ArmorSlot.FEET));assertTrue(screen.canApply());
        external=external.withArmor(external.armor().with(ArmorSlot.HEAD,ArmorSelection.custom(ORANGE)));f.base.update(state(2,external));screen.tick();
        assertTrue(screen.selectionSession().armorConflict(ArmorSlot.HEAD));assertFalse(screen.canApply());assertEquals(ArmorSelection.custom(BLUE),armor(screen).get(ArmorSlot.HEAD));
        screen.reloadAuthority();assertEquals(external,stored(screen));assertFalse(screen.selectionSession().dirty());
    }
    @Test void pendingConflictKeepsOutstandingAndDisablesReloadWithoutRetry() {
        var screen=f.armor();press(screen,"style:armor00");screen.applySelection();
        var external=PlayerFashionStoredState.DEFAULT.withArmor(new ArmorSelections(15));f.base.update(state(1,external));screen.tick();
        assertTrue(screen.selectionSession().conflict());assertTrue(screen.selectionSession().waiting());assertEquals(WardrobeStatusText.Priority.ERROR,screen.statusText().priority());
        assertFalse(screen.canApply());assertFalse(button(screen,"reload").active);assertFalse(screen.statusText().supplementalLines().isEmpty());
        screen.reloadAuthority();screen.applySelection();assertEquals(1,f.base.sent.size());assertEquals(1,screen.selectionSession().pendingRequestId());
    }
    @ParameterizedTest @ValueSource(ints={200,320})
    void controlsFitBothLayoutsDoNotOverlapApplyAndKeyboardReachesAllActions(int width) {
        var screen=f.armor();screen.resize(width,240);
        for(int view=0;view<3;view++) {
            if(view==1) press(screen,"scope");if(view==2) press(screen,"slot:legs");
            var controls=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
            for(var widget:controls) {
                var bounds=new WardrobeLayout.Bounds(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight());
                assertTrue(bounds.x()>=0 && bounds.y()>=0 && bounds.right()<=width && bounds.bottom()<=240);
                for(var other:controls) if(other!=widget) assertFalse(bounds.overlaps(new WardrobeLayout.Bounds(other.getX(),other.getY(),other.getWidth(),other.getHeight())));
            }
            var visited=new HashSet<Object>();screen.setFocused(null);
            for(int i=0;i<controls.size()+1;i++) {assertTrue(screen.keyPressed(new KeyEvent(258,0,0)));visited.add(screen.getFocused());}
            assertTrue(visited.contains(button(screen,"cancel")));assertTrue(visited.contains(button(screen,"scope")));
        }
    }
}
