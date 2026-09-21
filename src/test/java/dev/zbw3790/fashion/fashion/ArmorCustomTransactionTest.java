package dev.zbw3790.fashion.fashion;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.fashion.FullFashionDraft;
import static dev.zbw3790.fashion.armor.ArmorTextureFixture.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorCustomTransactionTest {
    @TempDir Path temp;
    PlayerFashionSavedData data;PlayerFashionService service;Path armor,blue;Object connection=new Object();
    @BeforeAll static void boot(){bootstrap();}
    @BeforeEach void setup() throws Exception {
        armor=temp.resolve("armor");blue=style(armor,"blue",0xff3790ff);style(armor,"orange",0xffff9900);
        data=new PlayerFashionSavedData();service=service(data,valid(temp));
        service.reconcile(valid(temp),new OutfitRegistryLoader(4096).load(temp.resolve("outfits")),new ArmorRegistryLoader().loadExisting(armor),e->{});
        service.join(FIRST,connection,e->{});
    }
    PlayerFashionStoredState custom(){var selections=ArmorSelections.original();for(var slot:ArmorSlot.CANONICAL_ORDER)selections=selections.with(slot,ArmorSelection.custom(new ArmorStyleId("blue")));return PlayerFashionStoredState.DEFAULT.withArmor(selections);}
    PlayerFashionService.ApplyOutcome apply(PlayerFashionStoredState state){return service.apply(FIRST,connection,true,service.authority(FIRST).orElseThrow().revision(),state);}
    OutfitRegistryReloadService.Result reload(){return new OutfitRegistryReloadService(service,temp.resolve("outfits"),()->{}).reload(p->new OutfitRegistryReloadService.Clients(1,0));}
    @Test void casNoopAndLegacyCapeKeepAllCustom() {
        assertTrue(apply(custom()).changed());assertEquals(1,service.authority(FIRST).orElseThrow().revision());assertFalse(apply(custom()).changed());
        assertEquals(FullFashionSelectionStatus.CONFLICT,service.apply(FIRST,connection,true,0,PlayerFashionStoredState.DEFAULT).status());
        service.setSelection(FIRST,Optional.of(FOUNDER));assertEquals(custom().armor(),service.stored(FIRST).armor());assertEquals(2,service.authority(FIRST).orElseThrow().revision());
    }
    @Test void missingRemainsStoredAndCapeMutationSucceedsThenRestoresAutomatically() throws Exception {
        apply(custom());Files.move(blue,temp.resolve("archived"));data.setDirty(false);
        assertTrue(reload().success());assertEquals(custom(),service.stored(FIRST));assertEquals(ArmorSelections.original(),service.authority(FIRST).orElseThrow().effective().armor());assertEquals(2,service.authority(FIRST).orElseThrow().revision());assertFalse(data.isDirty());
        assertEquals(FullFashionSelectionStatus.SUCCESS,apply(custom().withCape(Optional.of(FOUNDER))).status());
        Files.move(temp.resolve("archived"),blue);assertTrue(reload().success());assertEquals(custom().armor(),service.authority(FIRST).orElseThrow().effective().armor());assertEquals(4,service.authority(FIRST).orElseThrow().revision());
    }
    @Test void newInvalidAndWrongSlotAreRejectedButClearDoesNotNeedRegistry() throws Exception {
        var invalid=custom().withArmor(custom().armor().with(ArmorSlot.HEAD,ArmorSelection.custom(new ArmorStyleId("absent"))));assertEquals(FullFashionSelectionStatus.INVALID_ARMOR_SELECTION,apply(invalid).status());
        Files.writeString(blue.resolve("armor.json"),metadata("blue","[\"head\"]","{\"outer\":\"outer.png\"}"));Files.delete(blue.resolve("inner.png"));reload();
        assertEquals(FullFashionSelectionStatus.INVALID_ARMOR_SELECTION,apply(custom()).status());
        assertTrue(apply(PlayerFashionStoredState.DEFAULT.withArmor(ArmorSelections.original().with(ArmorSlot.HEAD,ArmorSelection.custom(new ArmorStyleId("blue"))))).changed());
        Files.move(blue,temp.resolve("archived"));reload();assertTrue(apply(PlayerFashionStoredState.DEFAULT).changed());
    }
    @Test void hashOnlyMovesGenerationWithoutRevisionAndWholeFailurePreservesBothDomains() throws Exception {
        apply(custom());var authority=service.authority(FIRST);data.setDirty(false);
        Files.write(blue.resolve("outer.png"),png(0xff00ffff));assertTrue(reload().changed());assertEquals(1,service.registryGeneration());assertEquals(authority,service.authority(FIRST));assertFalse(data.isDirty());
        var previous=service.armor();Files.move(armor,temp.resolve("unmounted"));assertFalse(reload().success());assertSame(previous,service.armor());assertEquals(authority,service.authority(FIRST));assertFalse(data.isDirty());
    }
    @Test void offlineCustomSurvivesMissingAndMembershipRejoinResetsOnlyRevision() throws Exception {
        apply(custom());service.leave(FIRST,connection,e->{});Files.move(blue,temp.resolve("archived"));reload();assertEquals(custom(),service.stored(FIRST));
        var joined=service.join(FIRST,new Object(),e->{});assertEquals(0,joined.revision());assertEquals(custom(),joined.stored());assertEquals(ArmorSelections.original(),joined.effective().armor());
    }
    @Test void customFieldsUseValueEqualityAndMergeIndependentHiddenSlot() {
        var initial=custom();var draft=new FullFashionDraft(new FullPlayerFashionState(initial,new PlayerFashionEffectiveState(initial.cape(),initial.outfit(),initial.armor()),1));
        draft.edit(custom().withCape(Optional.of(FOUNDER)));
        var next=initial.withArmor(initial.armor().with(ArmorSlot.FEET,ArmorSelection.HIDDEN));
        draft.observe(new FullPlayerFashionState(next,new PlayerFashionEffectiveState(next.cape(),next.outfit(),next.armor()),2));
        assertTrue(draft.conflicts().isEmpty());assertEquals(ArmorSelection.HIDDEN,draft.draft().armor().get(ArmorSlot.FEET));assertEquals(Optional.of(FOUNDER),draft.draft().cape());
    }
    @Test void independentCustomSlotsMergeAndSameSlotConflictRetainsDraft() {
        var base=custom();var draft=new FullFashionDraft(new FullPlayerFashionState(base,new PlayerFashionEffectiveState(base.cape(),base.outfit(),base.armor()),0));
        var orange=ArmorSelection.custom(new ArmorStyleId("orange"));
        draft.edit(base.withArmor(base.armor().with(ArmorSlot.HEAD,orange)));
        var chest=base.withArmor(base.armor().with(ArmorSlot.CHEST,ArmorSelection.HIDDEN));
        draft.observe(new FullPlayerFashionState(chest,new PlayerFashionEffectiveState(chest.cape(),chest.outfit(),chest.armor()),1));
        assertEquals(orange,draft.draft().armor().get(ArmorSlot.HEAD));assertEquals(ArmorSelection.HIDDEN,draft.draft().armor().get(ArmorSlot.CHEST));assertTrue(draft.conflicts().isEmpty());
        var conflict=chest.withArmor(chest.armor().with(ArmorSlot.HEAD,ArmorSelection.ORIGINAL));
        draft.observe(new FullPlayerFashionState(conflict,new PlayerFashionEffectiveState(conflict.cape(),conflict.outfit(),conflict.armor()),2));
        assertEquals(Set.of(FullFashionDraft.Field.ARMOR_HEAD),draft.conflicts());assertEquals(orange,draft.draft().armor().get(ArmorSlot.HEAD));assertFalse(draft.canSubmit());
    }
}
