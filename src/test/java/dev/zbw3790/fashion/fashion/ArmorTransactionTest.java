package dev.zbw3790.fashion.fashion;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.network.*;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.fashion.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;
class ArmorTransactionTest {
 @TempDir Path temp;
 @BeforeAll static void boot(){bootstrap();}
 @Test void realHandlerAtomicityNoopStaleLegacyAndMembership() throws Exception {
  var service=service(new PlayerFashionSavedData(),valid(temp));Object connection=new Object();service.join(FIRST,connection,e->fail());
  var hidden=PlayerFashionStoredState.DEFAULT.withArmor(new ArmorSelections(15));
  var outcome=FullFashionSelectionHandler.process(FIRST,connection,new SetFullFashionSelectionPayload(1,0,hidden),service,FashionAuthorityRoute.V4,true).orElseThrow();
  assertEquals(FullFashionSelectionStatus.SUCCESS,outcome.result().status());assertEquals(hidden,service.stored(FIRST));assertEquals(1,service.authority(FIRST).orElseThrow().revision());
  assertFalse(service.apply(FIRST,connection,true,1,hidden).changed());assertEquals(1,service.authority(FIRST).orElseThrow().revision());
  assertEquals(FullFashionSelectionStatus.CONFLICT,service.apply(FIRST,connection,true,0,PlayerFashionStoredState.DEFAULT).status());
  var invalid=hidden.withArmor(ArmorSelections.original()).withOutfit(OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.outfit(new OutfitId("bad"))));
  assertNotEquals(FullFashionSelectionStatus.SUCCESS,service.apply(FIRST,connection,true,1,invalid).status());assertEquals(hidden,service.stored(FIRST));
  assertEquals(PlayerFashionService.MutationResult.CHANGED,service.setSelection(FIRST,Optional.of(FOUNDER)));assertEquals(15,service.stored(FIRST).armor().hiddenMask());
  var before=service.stored(FIRST);service.reconcile(valid(temp));assertEquals(before,service.stored(FIRST));
  assertEquals(2,service.join(FIRST,connection,e->fail()).revision());service.leave(FIRST,connection,e->assertEquals(2,e.state().revision()));Object next=new Object();assertEquals(0,service.join(FIRST,next,e->fail()).revision());assertEquals(before,service.stored(FIRST));assertFalse(service.leave(FIRST,connection,e->fail()));
 }
 @Test void armorParticipatesInFieldMergeAndNeverDisappearsInCapeEdit() {
  var state=PlayerFashionStoredState.DEFAULT.withArmor(new ArmorSelections(1));var first=authority(state,0);var draft=new FullFashionDraft(first);
  draft.edit(state.withCape(Optional.of(FOUNDER)));draft.observe(authority(state.withArmor(new ArmorSelections(3)),1));assertEquals(3,draft.draft().armor().hiddenMask());assertEquals(Optional.of(FOUNDER),draft.draft().cape());assertTrue(draft.conflicts().isEmpty());
  draft.edit(draft.draft().withArmor(new ArmorSelections(2)));draft.observe(authority(state.withArmor(new ArmorSelections(2)),2));assertTrue(draft.conflicts().contains(FullFashionDraft.Field.ARMOR_HEAD));assertFalse(draft.canSubmit());
  draft.acceptSuccess(authority(draft.draft(),3),Optional.empty());assertEquals(2,draft.draft().armor().hiddenMask());assertFalse(draft.dirty());
 }
 @Test void observerLeftAndReconnectDoNotRetainHidden() {
  var registry=new ClientPlayerFashionRegistry();Object connection=new Object();registry.beginConnection(connection);var state=authority(PlayerFashionStoredState.DEFAULT.withArmor(new ArmorSelections(15)),4);
  registry.receiveFullSnapshot(connection,new FullPlayerFashionSnapshot(true,List.of(new FullPlayerFashionEntry(FIRST,state))));assertEquals(15,registry.full().find(FIRST).orElseThrow().stored().armor().hiddenMask());
  registry.receiveFullRemove(connection,new FullPlayerFashionRemovePayload(FIRST,4,FullPlayerFashionRemovePayload.Reason.LEFT));assertTrue(registry.full().find(FIRST).isEmpty());registry.receiveFullUpdate(connection,new FullPlayerFashionEntry(FIRST,FullPlayerFashionState.defaults(0)));assertEquals(0,registry.full().find(FIRST).orElseThrow().stored().armor().hiddenMask());
 }
 static FullPlayerFashionState authority(PlayerFashionStoredState s,long revision){return new FullPlayerFashionState(s,new PlayerFashionEffectiveState(s.cape(),s.outfit(),s.armor()),revision);}
}
