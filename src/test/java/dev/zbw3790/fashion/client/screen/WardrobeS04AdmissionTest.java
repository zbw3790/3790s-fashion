package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry.State;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;

@org.junit.jupiter.api.extension.ExtendWith(WardrobeLanguageTestSupport.class)
class WardrobeS04AdmissionTest {
    record Gate(String name,State registry,boolean capeKnown,String edit,boolean allowed) { }
    static Stream<Gate> gates() {
        return Stream.of(
            new Gate("D2-01",State.UNAVAILABLE,true,"cape",true),
            new Gate("D2-02",State.UNAVAILABLE,true,"dormantCape",true),
            new Gate("D2-03",State.UNAVAILABLE,true,"original",true),
            new Gate("D2-04",State.UNAVAILABLE,true,"none",true),
            new Gate("D2-05",State.UNAVAILABLE,true,"outfit",false),
            new Gate("D2-06",State.KNOWN,false,"outfit",true),
            new Gate("D2-07",State.KNOWN,false,"cape",false),
            new Gate("D2-08",State.KNOWN,true,"authority",false),
            new Gate("D2-09",State.UNKNOWN,true,"outfit",false),
            new Gate("D2-10",State.KNOWN,true,"empty",true),
            new Gate("两域故障仅清除",State.UNAVAILABLE,false,"clearBoth",true),
            new Gate("未知定义仍可清除",State.UNKNOWN,false,"none",true),
            new Gate("披风定义故障仍可清除",State.KNOWN,false,"clearCape",true));
    }
    @ParameterizedTest @MethodSource("gates")
    void fieldAdmissionChecksActualScreenAndRequest(Gate gate) {
        var old=OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.outfit(new OutfitId("missing")));
        var stored=new PlayerFashionStoredState(Optional.of(CAPE_A),old);
        var initial=new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),OutfitSelections.original()),0);
        var f=new WardrobeS04Fixture(gate.registry(),gate.edit().equals("empty")?0:10,true,initial);
        if (!gate.capeKnown()) f.capes.replace(new CapeRegistrySnapshot(List.of()));
        var screen=f.open();var session=screen.selectionSession();
        switch(gate.edit()) {
            case "cape","dormantCape" -> session.select(Optional.of(CAPE_B));
            case "original","empty" -> session.clearOutfit(Set.of(OutfitPart.HEAD),OutfitPartSelection.ORIGINAL);
            case "none" -> session.clearOutfit(Set.of(OutfitPart.HEAD),OutfitPartSelection.NONE);
            case "clearCape" -> session.select(Optional.empty());
            case "clearBoth" -> { session.select(Optional.empty());session.clearOutfit(OutfitPart.ALL,OutfitPartSelection.NONE); }
            case "outfit" -> session.selectOutfit(Set.of(OutfitPart.HEAD),LOOK,OutfitPart.ALL);
            case "authority" -> {
                session.select(Optional.of(CAPE_B));
                f.authority.receiveFullSnapshot(f.connection,FullPlayerFashionSnapshot.unavailable());
            }
        }
        screen.tick();assertEquals(gate.allowed(),screen.canApply(),gate.name());
        screen.applySelection();screen.applySelection();
        assertEquals(gate.allowed()?1:0,f.sent.size(),gate.name());
        assertTrue(f.legacySent.isEmpty());
        if (gate.edit().equals("dormantCape")) assertEquals(old,f.sent.getFirst().stored().outfit());
    }
    @Test void knownEmptyAndUnknownRemainDifferentAndBuiltinsNeedNoDefinition() {
        for (State state:List.of(State.UNKNOWN,State.KNOWN)) {
            var f=new WardrobeS04Fixture(state,0,false,FullPlayerFashionState.defaults(0));
            var screen=f.open();var content=screen.outfitContent();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
            assertEquals(state==State.KNOWN?"暂无装束":"加载中",content.resourceStatus());
            press(screen,"无外层");screen.applySelection();assertEquals(1,f.sent.size());
            assertEquals(OutfitSelections.original().allNone().selections(),f.sent.getFirst().stored().outfit());
        }
    }
    @Test void existingIdOnAnotherPartStillNeedsProvidedProofAndActualModelReadiness() {
        var f=new WardrobeS04Fixture();var screen=f.open();
        var partial=new OutfitId("look01");
        screen.selectionSession().selectOutfit(Set.of(OutfitPart.BODY),partial,OutfitPart.ALL);
        screen.applySelection();assertTrue(f.sent.isEmpty());
        screen.selectionSession().clearOutfit(OutfitPart.ALL,OutfitPartSelection.ORIGINAL);
        f.model.set(OutfitModel.SLIM);
        screen.selectionSession().selectOutfit(OutfitPart.ALL,new OutfitId("look02"),OutfitPart.ALL);
        screen.applySelection();assertTrue(f.sent.isEmpty());
        screen.selectionSession().clearOutfit(OutfitPart.ALL,OutfitPartSelection.ORIGINAL);
        screen.selectionSession().selectOutfit(OutfitPart.ALL,new OutfitId("look03"),OutfitPart.ALL);
        screen.applySelection();assertTrue(f.sent.isEmpty());
    }
    @Test void missingSendOrLocalResultReceiverCannotAllocateOrSend() {
        for (boolean missingReceiver:List.of(false,true)) {
            var f=new WardrobeS04Fixture();var screen=f.open();
            screen.selectionSession().clearOutfit(OutfitPart.ALL,OutfitPartSelection.NONE);
            f.canSend=missingReceiver;f.receiverReady=!missingReceiver;
            screen.applySelection();assertFalse(screen.canApply());assertTrue(f.sent.isEmpty());assertFalse(f.authority.fullRequests().hasOutstanding());
        }
    }
    @Test void fullSnapshotUnavailableCannotBeUpgradedByResidualSelfResult() {
        var f=new WardrobeS04Fixture();var screen=f.open();
        screen.selectionSession().select(Optional.of(CAPE_A));screen.applySelection();
        f.authority.receiveFullSnapshot(f.connection,FullPlayerFashionSnapshot.unavailable());
        f.result(screen,1,FullFashionSelectionStatus.SUCCESS,state(1,new PlayerFashionStoredState(Optional.of(CAPE_A),OutfitSelections.original())));
        assertFalse(screen.selectionSession().authorityKnown());
        screen.selectionSession().clearOutfit(OutfitPart.ALL,OutfitPartSelection.NONE);
        screen.applySelection();assertEquals(1,f.sent.size());
    }
}
