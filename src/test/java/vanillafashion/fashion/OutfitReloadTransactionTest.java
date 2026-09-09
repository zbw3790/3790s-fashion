package vanillafashion.fashion;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vanillafashion.outfit.*;
import vanillafashion.testutil.S03TestAssets;
import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.fashion.FashionTestSupport.*;

class OutfitReloadTransactionTest {
    @TempDir Path temp;
    final Object connection=new Object(); PlayerFashionSavedData data;PlayerFashionService service;Path root,robe;
    @BeforeAll static void boot(){bootstrap();}
    @BeforeEach void prepare() throws Exception {
        root=temp.resolve("outfits");robe=S03TestAssets.outfit(root,"robe",OutfitPart.ALL,true,false);
        S03TestAssets.outfit(root,"other",OutfitPart.ALL,true,false);
        data=new PlayerFashionSavedData();service=service(data,valid(temp));
        service.reconcile(valid(temp),new OutfitRegistryLoader(4096).load(root),e->{});
        service.join(FIRST,connection,e->{});service.apply(FIRST,connection,true,0,costume("robe"));
        service.join(SECOND,new Object(),e->{});data.setDirty(false);
    }
    PlayerFashionStoredState costume(String id){var p=OutfitSelections.original();for(var part:OutfitPart.values())p=p.with(part,OutfitPartSelection.outfit(new OutfitId(id)));return new PlayerFashionStoredState(Optional.of(FOUNDER),p);}
    OutfitRegistryReloadService.Result reload(List<OutfitRegistryReloadService.Publication> publications) {
        return new OutfitRegistryReloadService(service,root,()->{}).reload(p->{publications.add(p);return new OutfitRegistryReloadService.Clients(1,1);});
    }
    @Test void hashOnlyChangeCommitsGenerationButNoAuthorityOrSavedDataChange() throws Exception {
        var previous=service.authority(FIRST);Files.write(robe.resolve("wide.png"),S03TestAssets.png(64,64,0xff123456));
        var events=new ArrayList<OutfitRegistryReloadService.Publication>();var result=reload(events);
        assertTrue(result.changed());assertEquals(1,service.registryGeneration());assertEquals(previous,service.authority(FIRST));assertFalse(data.isDirty());
        assertTrue(events.getFirst().commit().authorities().isEmpty());assertEquals(1,result.refreshed());assertEquals(1,result.pinned());
    }
    @Test void effectiveOnlyInvalidMetadataAdvancesRevisionOnceKeepsStoredAndCape() throws Exception {
        Files.writeString(robe.resolve("outfit.json"),"{}");var events=new ArrayList<OutfitRegistryReloadService.Publication>();assertTrue(reload(events).success());
        assertEquals(costume("robe"),service.stored(FIRST));assertEquals(2,service.authority(FIRST).orElseThrow().revision());
        assertEquals(OutfitSelections.original(),service.authority(FIRST).orElseThrow().effective().outfit());
        assertEquals(Optional.of(FOUNDER),service.authority(FIRST).orElseThrow().effective().cape());assertFalse(data.isDirty());assertEquals(1,events.getFirst().commit().authorities().size());
    }
    @Test void invalidModelAndProvidedRemovalRemainDormantThenRecover() throws Exception {
        Files.writeString(robe.resolve("wide.png"),"坏 PNG");reload(new ArrayList<>());assertEquals(2,service.authority(FIRST).orElseThrow().revision());
        S03TestAssets.outfit(root,"robe",Set.of(OutfitPart.HEAD),true,false);reload(new ArrayList<>());
        assertEquals(3,service.authority(FIRST).orElseThrow().revision());assertEquals(costume("robe"),service.stored(FIRST));
        assertEquals(costume("robe").outfit().get(OutfitPart.HEAD),service.authority(FIRST).orElseThrow().effective().outfit().get(OutfitPart.HEAD));
        assertEquals(OutfitPartSelection.ORIGINAL,service.authority(FIRST).orElseThrow().effective().outfit().get(OutfitPart.BODY));
        assertFalse(data.isDirty());
    }
    @Test void trustedDeletionClearsSixPartsOnceAndOfflineWithoutRevisionWhileCapeNeverReconciles() throws Exception {
        UUID offline=new UUID(0,3);data.setState(offline,costume("robe"));data.setDirty(false);
        var secondConnection=service.connections().get(SECOND);service.apply(SECOND,secondConnection,true,0,costume("other"));
        var unaffected=service.authority(SECOND);
        Files.move(robe,temp.resolve("archived"));var events=new ArrayList<OutfitRegistryReloadService.Publication>();reload(events);
        assertEquals(2,service.authority(FIRST).orElseThrow().revision());assertEquals(OutfitSelections.original(),service.stored(FIRST).outfit());
        assertEquals(Optional.of(FOUNDER),service.stored(FIRST).cape());assertEquals(Optional.of(FOUNDER),service.stored(offline).cape());
        assertEquals(OutfitSelections.original(),service.stored(offline).outfit());assertTrue(service.authority(offline).isEmpty());
        assertEquals(unaffected,service.authority(SECOND));assertEquals(1,events.getFirst().commit().authorities().size());assertEquals(2,events.getFirst().commit().storedChanges());
        assertEquals(0,service.join(offline,new Object(),e->{}).revision());
    }
    @Test void sameContentIsNoOpAndDoesNotPublishOrDirty() {
        var old=service.outfits().orElseThrow();var result=new OutfitRegistryReloadService(service,root,()->{}).reload(p->{fail("无变化不发布。");return null;});
        assertTrue(result.success());assertFalse(result.changed());assertEquals(0,service.registryGeneration());assertSame(old,service.outfits().orElseThrow());assertFalse(data.isDirty());
    }
    @Test void missingRootFailsWithoutCreatingEmptyRootOrTouchingLive() throws Exception {
        var before=service.outfits();var state=service.authority(FIRST);Files.move(root,temp.resolve("unmounted"));
        assertFalse(reload(new ArrayList<>()).success());assertFalse(Files.exists(root));assertEquals(before,service.outfits());assertEquals(state,service.authority(FIRST));assertFalse(data.isDirty());
    }
    @Test void fileRootFailureAndThreadRejectionLeaveStateUntouched() throws Exception {
        Files.move(root,temp.resolve("archive"));Files.writeString(root,"不是目录");assertFalse(reload(new ArrayList<>()).success());
        assertThrows(IllegalStateException.class,()->new OutfitRegistryReloadService(service,root,()->{throw new IllegalStateException("非服务器线程");}).reload(p->null));
        assertEquals(0,service.registryGeneration());assertEquals(costume("robe"),service.stored(FIRST));assertFalse(data.isDirty());
    }
    @Test void generationExhaustionRefusesCandidateWithoutPartialCommit() throws Exception {
        var field=PlayerFashionService.class.getDeclaredField("registryGeneration");field.setAccessible(true);field.setLong(service,Long.MAX_VALUE);
        Files.move(robe,temp.resolve("archived"));var before=service.outfits();assertFalse(reload(new ArrayList<>()).success());
        assertEquals(before,service.outfits());assertEquals(costume("robe"),service.stored(FIRST));assertFalse(data.isDirty());assertEquals(Long.MAX_VALUE,service.registryGeneration());
    }
    @Test void oneExhaustedOnlineAuthorityBlocksWholeReloadBeforeAnyMutation() throws Exception {
        var field=PlayerFashionService.class.getDeclaredField("online");field.setAccessible(true);var member=((Map<?,?>)field.get(service)).get(FIRST);
        var stateField=member.getClass().getDeclaredField("state");stateField.setAccessible(true);var state=service.authority(FIRST).orElseThrow();
        stateField.set(member,new FullPlayerFashionState(state.stored(),state.effective(),Long.MAX_VALUE));
        UUID offline=new UUID(0,3);data.setState(offline,costume("robe"));data.setDirty(false);
        Files.move(robe,temp.resolve("archived"));assertFalse(reload(new ArrayList<>()).success());
        assertEquals(costume("robe"),service.stored(offline));assertEquals(costume("robe"),service.stored(FIRST));assertEquals(0,service.registryGeneration());assertFalse(data.isDirty());
    }
    @Test void publicationObservesCompleteAuthorityAndGeneration() throws Exception {
        Files.move(robe,temp.resolve("archived"));
        var result=new OutfitRegistryReloadService(service,root,()->{}).reload(p->{
            assertSame(p.candidate(),service.outfits().orElseThrow());assertEquals(p.generation(),service.registryGeneration());
            for(var entry:p.commit().authorities())assertEquals(entry.state(),service.authority(entry.playerId()).orElseThrow());
            return new OutfitRegistryReloadService.Clients(0,0);
        });assertTrue(result.success());
    }
    @Test void addingOutfitRefreshesRegistryWithoutChangingExistingAuthority() throws Exception {
        var before=service.authority(FIRST);S03TestAssets.outfit(root,"added",Set.of(OutfitPart.LEFT_ARM),true,false);
        assertTrue(reload(new ArrayList<>()).changed());assertTrue(service.outfits().orElseThrow().registry().find(new OutfitId("added")).isPresent());
        assertEquals(before,service.authority(FIRST));assertFalse(data.isDirty());
    }
    @Test void partialDeletionKeepsOtherOutfitNoneAndDormantCape() throws Exception {
        var missingCape=new vanillafashion.cape.CapeId("missing_cape");
        var partial=new PlayerFashionStoredState(Optional.of(missingCape),OutfitSelections.original()
                .with(OutfitPart.HEAD,OutfitPartSelection.outfit(new OutfitId("robe")))
                .with(OutfitPart.BODY,OutfitPartSelection.outfit(new OutfitId("other")))
                .with(OutfitPart.LEFT_ARM,OutfitPartSelection.NONE));
        // 新 membership 直接从已有保存记录建立；缺失 Cape 只能由 Cape 域处理。
        service.leave(FIRST,connection,e->{});data.setState(FIRST,partial);service.join(FIRST,connection,e->{});data.setDirty(false);
        Files.move(robe,temp.resolve("archived"));reload(new ArrayList<>());
        assertEquals(new PlayerFashionStoredState(partial.cape(),partial.outfit().with(OutfitPart.HEAD,OutfitPartSelection.ORIGINAL)),service.stored(FIRST));
        assertEquals(1,service.authority(FIRST).orElseThrow().revision());assertEquals(Optional.empty(),service.authority(FIRST).orElseThrow().effective().cape());
    }
    @Test void stoppedOrReadOnlyServicesNeverSwapCandidate() throws Exception {
        Files.write(robe.resolve("wide.png"),S03TestAssets.png(64,64,0xff765432));service.stop();assertFalse(reload(new ArrayList<>()).success());
        var readOnly=new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data,Optional.of("测试只读")),valid(temp));
        assertEquals(PlayerFashionService.ReloadStatus.UNAVAILABLE,readOnly.reloadOutfits(new OutfitRegistryLoader(4096).loadExisting(root)).status());
        assertEquals(0,readOnly.registryGeneration());assertTrue(readOnly.outfits().isEmpty());
    }
}
