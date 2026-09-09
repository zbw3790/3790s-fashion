package vanillafashion.fashion;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vanillafashion.outfit.*;
import vanillafashion.cape.*;
import vanillafashion.testutil.S03TestAssets;
import vanillafashion.network.*;
import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.fashion.FashionTestSupport.*;

class FullFashionServiceTest {
    @TempDir Path temp;
    final Object connection=new Object();
    @BeforeAll static void boot(){bootstrap();}
    PlayerFashionService create(PlayerFashionSavedData data) throws Exception {
        var service=service(data,valid(temp));
        S03TestAssets.outfit(temp.resolve("outfits"),"robe",OutfitPart.ALL,true,false);
        service.reconcile(valid(temp),new OutfitRegistryLoader(4096).load(temp.resolve("outfits")),entry->{});
        service.join(FIRST,connection,entry->fail("初次 JOIN 不应产生 LEFT。"));data.setDirty(false);return service;
    }
    PlayerFashionStoredState costume(){var parts=OutfitSelections.original();for(var part:OutfitPart.CANONICAL_ORDER)parts=parts.with(part,OutfitPartSelection.outfit(new OutfitId("robe")));return new PlayerFashionStoredState(Optional.of(FOUNDER),parts);}
    @Test void realTransactionBroadcastProjectionAndResultShareOneCommit() throws Exception {
        var service=create(new PlayerFashionSavedData());
        var outcome=FullFashionSelectionHandler.process(FIRST,connection,new SetFullFashionSelectionPayload(7,0,costume()),service,FashionAuthorityRoute.V2,true).orElseThrow();
        var events=new ArrayList<String>();
        FullFashionSelectionHandler.deliver(outcome,entry->{
            events.add("UPDATE");
            assertEquals(service.authority(FIRST),Optional.of(entry.state()));
            assertEquals(outcome.result().authority(),Optional.of(entry.state()));
            var full=(FullPlayerFashionUpdatePayload)FullFashionSelectionHandler.projection(FashionAuthorityRoute.V2,entry).orElseThrow();
            assertEquals(entry,full.entry());
            var legacy=(PlayerFashionUpdatePayload)FullFashionSelectionHandler.projection(FashionAuthorityRoute.LEGACY,entry).orElseThrow();
            assertEquals(entry.state().capeProjection(),legacy.entry().state());
            assertTrue(FullFashionSelectionHandler.projection(FashionAuthorityRoute.UNDECIDED,entry).isEmpty());
        },result->{events.add("RESULT");assertEquals(7,result.requestId());assertEquals(FullFashionSelectionStatus.SUCCESS,result.status());assertEquals(service.authority(FIRST),result.authority());});
        assertEquals(List.of("UPDATE","RESULT"),events);
        var noop=FullFashionSelectionHandler.process(FIRST,connection,new SetFullFashionSelectionPayload(8,1,costume()),service,FashionAuthorityRoute.V2,true).orElseThrow();
        FullFashionSelectionHandler.deliver(noop,e->fail("无变化不得广播。"),r->assertEquals(1,r.authority().orElseThrow().revision()));
        var conflict=FullFashionSelectionHandler.process(FIRST,connection,new SetFullFashionSelectionPayload(9,0,PlayerFashionStoredState.DEFAULT),service,FashionAuthorityRoute.V2,true).orElseThrow();
        FullFashionSelectionHandler.deliver(conflict,e->fail("CAS 拒绝不得广播。"),r->assertEquals(FullFashionSelectionStatus.CONFLICT,r.status()));
        var cleared=FullFashionSelectionHandler.process(FIRST,connection,new SetFullFashionSelectionPayload(10,1,PlayerFashionStoredState.DEFAULT),service,FashionAuthorityRoute.V2,true).orElseThrow();
        var remove=(FullPlayerFashionRemovePayload)FullFashionSelectionHandler.projection(FashionAuthorityRoute.V2,cleared.update().orElseThrow()).orElseThrow();
        assertEquals(FullPlayerFashionRemovePayload.Reason.DEFAULT,remove.reason());assertEquals(cleared.result().authority().orElseThrow().revision(),remove.revision());
    }
    @Test void missingResultAndOldConnectionCannotMutateThroughActualHandler() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);var request=new SetFullFashionSelectionPayload(1,0,costume());
        assertTrue(FullFashionSelectionHandler.process(FIRST,connection,request,service,FashionAuthorityRoute.V2,false).isEmpty());
        assertTrue(FullFashionSelectionHandler.process(FIRST,new Object(),request,service,FashionAuthorityRoute.V2,true).isEmpty());
        var refused=FullFashionSelectionHandler.process(FIRST,connection,request,service,FashionAuthorityRoute.LEGACY,true).orElseThrow();
        assertEquals(FullFashionSelectionStatus.PROTOCOL_REJECT,refused.result().status());assertTrue(refused.update().isEmpty());
        assertFalse(data.isDirty());assertEquals(0,service.authority(FIRST).orElseThrow().revision());
    }
    @Test void atomicAggregateApplyNoOpAndCas() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);
        var result=service.apply(FIRST,connection,true,0,costume());assertEquals(FullFashionSelectionStatus.SUCCESS,result.status());assertTrue(result.changed());assertTrue(data.isDirty());
        assertEquals(costume(),service.stored(FIRST));assertEquals(1,result.authority().orElseThrow().revision());assertEquals(costume().outfit(),result.authority().orElseThrow().effective().outfit());
        data.setDirty(false);assertEquals(FullFashionSelectionStatus.CONFLICT,service.apply(FIRST,connection,true,0,costume()).status());assertFalse(data.isDirty());
        var noop=service.apply(FIRST,connection,true,1,costume());assertEquals(FullFashionSelectionStatus.SUCCESS,noop.status());assertFalse(noop.changed());assertEquals(1,noop.authority().orElseThrow().revision());assertFalse(data.isDirty());
    }
    @ParameterizedTest @EnumSource(OutfitPart.class) void invalidChangedPartCannotPartiallySaveCapeOrOtherParts(OutfitPart part) throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);var request=costume();request=new PlayerFashionStoredState(request.cape(),request.outfit().with(part,OutfitPartSelection.outfit(new OutfitId("bad"))));
        assertEquals(FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION,service.apply(FIRST,connection,true,0,request).status());assertEquals(PlayerFashionStoredState.DEFAULT,service.stored(FIRST));assertFalse(data.isDirty());assertEquals(0,service.authority(FIRST).orElseThrow().revision());
    }
    @Test void invalidCapeAndPolicyAreAtomic() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);
        assertEquals(FullFashionSelectionStatus.INVALID_CAPE,service.apply(FIRST,connection,true,0,costume().withCape(Optional.of(new CapeId("absent")))).status());assertFalse(data.isDirty());
        var denied=new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data,Optional.empty()),valid(temp),(id,cape)->false);
        denied.join(FIRST,connection,e->{});assertEquals(FullFashionSelectionStatus.NOT_ALLOWED,denied.apply(FIRST,connection,true,0,PlayerFashionStoredState.DEFAULT.withCape(Optional.of(FOUNDER))).status());assertFalse(data.isDirty());
    }
    @Test void unchangedDormantPartCanBeCarriedButNewReferenceCannot() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);service.apply(FIRST,connection,true,0,costume());
        Files.writeString(temp.resolve("outfits/robe/outfit.json"),"{}");var changed=new ArrayList<FullPlayerFashionEntry>();
        service.reconcile(valid(temp),new OutfitRegistryLoader(4096).load(temp.resolve("outfits")),changed::add);
        assertEquals(1,changed.size());assertEquals(2,service.authority(FIRST).orElseThrow().revision());assertEquals(costume().outfit(),service.stored(FIRST).outfit());
        var next=costume().withCape(Optional.of(BUILDER));assertEquals(FullFashionSelectionStatus.SUCCESS,service.apply(FIRST,connection,true,2,next).status());
        var cleared=new PlayerFashionStoredState(next.cape(),next.outfit().with(OutfitPart.HEAD,OutfitPartSelection.NONE));assertTrue(service.apply(FIRST,connection,true,3,cleared).changed());
        assertEquals(FullFashionSelectionStatus.INVALID_OUTFIT_SELECTION,service.apply(FIRST,connection,true,4,next).status());assertEquals(cleared,service.stored(FIRST));
    }
    @Test void rootFailureAllowsClearAndUnchangedReferencesButNeverNewReference() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);service.apply(FIRST,connection,true,0,costume());
        var badRoot=Files.writeString(temp.resolve("root-file"),"故障根");service.reconcile(new CapeRegistryKnowledge(temp,false,Set.of(),Set.of()),new OutfitRegistryLoader(4096).load(badRoot),e->{});
        long revision=service.authority(FIRST).orElseThrow().revision();assertEquals(costume(),service.stored(FIRST));
        var none=new PlayerFashionStoredState(Optional.empty(),costume().outfit().with(OutfitPart.HEAD,OutfitPartSelection.NONE));assertTrue(service.apply(FIRST,connection,true,revision,none).changed());
        assertEquals(FullFashionSelectionStatus.SERVICE_UNAVAILABLE,service.apply(FIRST,connection,true,revision+1,costume()).status());
        assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE,service.setSelection(FIRST,Optional.empty()));
    }
    @Test void legacyCapePreservesSixPartsAndIncrementsExactlyOnce() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);service.apply(FIRST,connection,true,0,costume());
        assertEquals(PlayerFashionService.MutationResult.CHANGED,service.setSelection(FIRST,Optional.of(BUILDER)));assertEquals(costume().outfit(),service.stored(FIRST).outfit());assertEquals(2,service.authority(FIRST).orElseThrow().revision());
        assertEquals(PlayerFashionService.MutationResult.NO_CHANGE,service.setSelection(FIRST,Optional.of(BUILDER)));assertEquals(2,service.authority(FIRST).orElseThrow().revision());
    }
    @Test void defaultKeepsRevisionWhileLeaveReleasesAndRejoinStartsZero() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);service.apply(FIRST,connection,true,0,costume());service.apply(FIRST,connection,true,1,PlayerFashionStoredState.DEFAULT);
        assertEquals(0,service.storedCount());assertEquals(2,service.authority(FIRST).orElseThrow().revision());
        assertEquals(2,service.join(FIRST,connection,e->fail()).revision()); // 同连接实体刷新不建立新 membership。
        var events=new ArrayList<FullPlayerFashionEntry>();assertTrue(service.leave(FIRST,connection,events::add));assertEquals(2,events.getFirst().state().revision());assertEquals(0,service.onlineCount());
        assertEquals(0,service.join(FIRST,new Object(),events::add).revision());
    }
    @Test void sameUuidReplacementEmitsLeftBeforeRevisionReleaseAndLateOldLeaveCannotDeleteNew() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);service.apply(FIRST,connection,true,0,costume());var newer=new Object();var events=new ArrayList<String>();
        var joined=service.join(FIRST,newer,e->{assertTrue(service.isCurrent(FIRST,connection));assertEquals(1,e.state().revision());events.add("LEFT");});events.add("JOIN");
        assertEquals(List.of("LEFT","JOIN"),events);assertEquals(0,joined.revision());assertEquals(costume(),joined.stored());assertFalse(service.leave(FIRST,connection,e->fail()));assertTrue(service.isCurrent(FIRST,newer));
    }
    @Test void connectionAndCapabilityFailuresNeverWrite() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);
        assertEquals(FullFashionSelectionStatus.PROTOCOL_REJECT,service.apply(FIRST,new Object(),true,0,costume()).status());
        assertEquals(FullFashionSelectionStatus.PROTOCOL_REJECT,service.apply(FIRST,connection,false,0,costume()).status());assertFalse(data.isDirty());
    }
    @Test void unavailablePersistenceNeverPublishesDefaultsOrWrites() throws Exception {
        var data=new PlayerFashionSavedData();var service=new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data,Optional.of("损坏文件"),false),valid(temp));service.join(FIRST,connection,e->{});
        assertFalse(service.fullSnapshot().available());assertTrue(service.authority(FIRST).isEmpty());assertEquals(FullFashionSelectionStatus.READ_ONLY_PERSISTENCE,service.apply(FIRST,connection,true,0,costume()).status());assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE,service.setSelection(FIRST,Optional.of(FOUNDER)));assertFalse(data.isDirty());
    }
    @Test void capacityCountsNondefaultAggregateNotCapeOrOnline() throws Exception {
        var data=new PlayerFashionSavedData();var onlyOutfit=new PlayerFashionStoredState(Optional.empty(),OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.NONE));
        for(int i=0;i<16384;i++)data.setState(new UUID(1,i),onlyOutfit);
        var service=create(data);assertEquals(FullFashionSelectionStatus.STORAGE_LIMIT,service.apply(FIRST,connection,true,0,costume()).status());assertFalse(data.isDirty());
        UUID existing=new UUID(1,0);Object token=new Object();service.join(existing,token,e->{});assertTrue(service.apply(existing,token,true,0,onlyOutfit.withCape(Optional.of(FOUNDER))).changed());
        assertTrue(service.apply(existing,token,true,1,PlayerFashionStoredState.DEFAULT).changed());assertEquals(16383,service.storedCount());assertTrue(service.apply(FIRST,connection,true,0,costume()).changed());
    }
    @Test void onlineOverflowRecoversWithoutResettingMembershipRevision() throws Exception {
        var service=create(new PlayerFashionSavedData());service.apply(FIRST,connection,true,0,costume());
        for(int i=0;i<1023;i++)service.join(new UUID(2,i),new Object(),e->{});
        assertEquals(1024,service.fullSnapshot().entries().size());var extra=new Object();UUID extraId=new UUID(3,0);service.join(extraId,extra,e->{});
        assertFalse(service.fullSnapshot().available());assertEquals(FullFashionSelectionStatus.SERVICE_UNAVAILABLE,service.apply(FIRST,connection,true,1,PlayerFashionStoredState.DEFAULT).status());
        service.leave(extraId,extra,e->{});assertTrue(service.fullSnapshot().available());assertEquals(1,service.authority(FIRST).orElseThrow().revision());
    }
    @Test void moreThan17408SequentialVisitorsDoNotAccumulateHistory() throws Exception {
        var service=create(new PlayerFashionSavedData());service.leave(FIRST,connection,e->{});
        for(int i=0;i<18000;i++){UUID id=new UUID(4,i);Object token=new Object();assertEquals(0,service.join(id,token,e->fail()).revision());assertTrue(service.leave(id,token,e->{}));assertEquals(0,service.onlineCount());}
        service.join(FIRST,connection,e->{});assertTrue(service.apply(FIRST,connection,true,0,costume()).changed());
    }
    @Test void longMaximumBlocksFurtherChangesWithoutWrap() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);
        var onlineField=PlayerFashionService.class.getDeclaredField("online");onlineField.setAccessible(true);Object member=((Map<?,?>)onlineField.get(service)).get(FIRST);
        var stateField=member.getClass().getDeclaredField("state");stateField.setAccessible(true);stateField.set(member,FullPlayerFashionState.defaults(Long.MAX_VALUE));
        assertEquals(FullFashionSelectionStatus.SERVICE_UNAVAILABLE,service.apply(FIRST,connection,true,Long.MAX_VALUE,costume()).status());assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE,service.setSelection(FIRST,Optional.of(FOUNDER)));assertEquals(Long.MAX_VALUE,service.authority(FIRST).orElseThrow().revision());assertFalse(data.isDirty());
    }
    @Test void trustedDeletionOnlyClearsReferencedPartsAndOfflineHasNoRevision() throws Exception {
        var data=new PlayerFashionSavedData();var service=create(data);service.apply(FIRST,connection,true,0,costume());data.setState(SECOND,costume());
        Files.move(temp.resolve("outfits/robe"),temp.resolve("archived-robe"));var changed=new ArrayList<FullPlayerFashionEntry>();service.reconcile(valid(temp),new OutfitRegistryLoader(4096).load(temp.resolve("outfits")),changed::add);
        assertEquals(1,changed.size());assertEquals(2,service.authority(FIRST).orElseThrow().revision());assertEquals(Optional.of(FOUNDER),service.stored(FIRST).cape());assertEquals(OutfitSelections.original(),service.stored(SECOND).outfit());assertTrue(service.authority(SECOND).isEmpty());
        assertEquals(0,service.join(SECOND,new Object(),e->{}).revision());
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void serverEffectiveDependsOnAnyValidDeclaredModel(boolean slim) throws Exception {
        var data=new PlayerFashionSavedData();var service=service(data,valid(temp));var root=temp.resolve("models");S03TestAssets.outfit(root,"robe",OutfitPart.ALL,!slim,slim);
        service.reconcile(valid(temp),new OutfitRegistryLoader(4096).load(root),e->{});service.join(FIRST,connection,e->{});var result=service.apply(FIRST,connection,true,0,costume());assertEquals(costume().outfit(),result.authority().orElseThrow().effective().outfit());
    }
}
