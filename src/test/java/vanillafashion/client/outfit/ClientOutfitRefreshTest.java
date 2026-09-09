package vanillafashion.client.outfit;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import vanillafashion.cape.CapeAssetHash;
import vanillafashion.network.*;
import vanillafashion.outfit.*;
import vanillafashion.testutil.S03TestAssets;
import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.client.outfit.ClientOutfitPipelineTest.snapshot;

class ClientOutfitRefreshTest {
    @TempDir Path temp;
    final Object connection=new Object(); final ClientOutfitRegistry registry=new ClientOutfitRegistry();
    @Test void generationOrderingAndOldInitializationRemainSeparate() {
        var a=snapshot("a".repeat(64));var b=snapshot("b".repeat(64));registry.begin(connection);registry.replace(connection,a);
        assertEquals(ClientOutfitRegistry.Result.APPLIED,registry.refresh(connection,1,b));assertEquals(1,registry.generation());
        assertEquals(ClientOutfitRegistry.Result.IDEMPOTENT,registry.refresh(connection,1,b));
        assertEquals(ClientOutfitRegistry.Result.STALE,registry.refresh(connection,0,a));
        assertEquals(ClientOutfitRegistry.Result.IDEMPOTENT,registry.replace(connection,a));assertEquals(b.entries(),registry.entries());
        assertEquals(ClientOutfitRegistry.Result.CONFLICT,registry.replace(connection,b));assertTrue(registry.entries().isEmpty());
    }
    @Test void sameGenerationDifferentDataDeactivatesUntilReconnect() {
        registry.begin(connection);registry.replace(connection,snapshot("a".repeat(64)));
        assertEquals(ClientOutfitRegistry.Result.CONFLICT,registry.refresh(connection,0,snapshot("b".repeat(64))));
        assertEquals(ClientOutfitRegistry.Result.CONFLICT,registry.refresh(connection,1,snapshot("b".repeat(64))));
        assertEquals(ClientOutfitRegistry.State.UNAVAILABLE,registry.state());
    }
    @Test void newConnectionRejectsOldRefreshAndDoesNotReuseGeneration() {
        registry.begin(connection);registry.replace(connection,snapshot("a".repeat(64)));registry.refresh(connection,9,snapshot("b".repeat(64)));
        Object next=new Object();registry.begin(next);registry.replace(next,snapshot("c".repeat(64)));
        assertEquals(ClientOutfitRegistry.Result.STALE,registry.refresh(connection,10,snapshot("d".repeat(64))));
        assertEquals(0,registry.generation());assertEquals(snapshot("c".repeat(64)).entries(),registry.entries());
    }
    @Test void oldServerInitialSnapshotWorksWithoutAnyRefresh() {
        registry.begin(connection);assertEquals(ClientOutfitRegistry.Result.APPLIED,registry.replace(connection,snapshot("a".repeat(64))));
        assertEquals(ClientOutfitRegistry.State.KNOWN,registry.state());assertEquals(0,registry.generation());
    }
    @Test void joiningAlreadyReloadedServerEstablishesCurrentGenerationWithoutDifferentInitialization() {
        registry.begin(connection);var current=snapshot("a".repeat(64));registry.replace(connection,current);
        assertEquals(ClientOutfitRegistry.Result.APPLIED,registry.refresh(connection,7,current));assertEquals(7,registry.generation());
        assertEquals(ClientOutfitRegistry.Result.IDEMPOTENT,registry.replace(connection,current));
    }
    @Test void refreshBeforeInitializationIsProtocolError() {
        registry.begin(connection);assertEquals(ClientOutfitRegistry.Result.CONFLICT,registry.refresh(connection,1,snapshot("a".repeat(64))));
    }
    @Test void delayedOldAssetCannotActivateNewMappingAndNewHashBecomesReady() {
        byte[] old=S03TestAssets.png(),next=S03TestAssets.png(64,64,0xff111111);
        String oldHash=CapeAssetHash.sha256(old),newHash=CapeAssetHash.sha256(next);
        var store=new ClientOutfitAssetStore();var textures=new ClientOutfitTextureManager();
        var sync=new ClientOutfitAssetSync(registry,store,new ClientOutfitAssetCache(temp,NOPLogger.NOP_LOGGER));
        sync.begin(connection);textures.begin(connection);sync.snapshot(connection,snapshot(oldHash));sync.markRequested(connection,sync.missing(connection).getFirst());
        sync.refresh(connection,1,snapshot(newHash));
        assertEquals(ClientOutfitAssetSync.Receive.NOT_AUTHORIZED,sync.receive(connection,new OutfitAssetDataPayload(oldHash,old)));
        assertFalse(store.contains(oldHash));assertEquals(newHash,registry.entries().getFirst().validModels().get(OutfitModel.WIDE));
        sync.markRequested(connection,sync.missing(connection).getFirst());assertEquals(ClientOutfitAssetSync.Receive.STORED,sync.receive(connection,new OutfitAssetDataPayload(newHash,next)));
        textures.register(connection,store,newHash,(id,bytes)->new ClientOutfitPipelineTest.Lease(),NOPLogger.NOP_LOGGER);
        var resolver=new ClientOutfitTextureResolver(registry,store,textures);
        assertEquals(ClientOutfitTextureManager.identifierFor(newHash),resolver.resolve(connection,new OutfitId("robe"),OutfitModel.WIDE).orElseThrow().texture().orElseThrow());
        assertTrue(textures.find(connection,oldHash).isEmpty());
    }
    @Test void refreshReleasesObsoleteTextureAndContentButKeepsCacheAndCurrentTexture() {
        var old=S03TestAssets.png();var next=S03TestAssets.png(64,64,0xffaabbcc);
        String a=CapeAssetHash.sha256(old),b=CapeAssetHash.sha256(next);
        var store=new ClientOutfitAssetStore();var textures=new ClientOutfitTextureManager();
        var cache=new ClientOutfitAssetCache(temp,NOPLogger.NOP_LOGGER);cache.storeValidated(a,old);cache.storeValidated(b,next);
        var sync=new ClientOutfitAssetSync(registry,store,cache);sync.begin(connection);textures.begin(connection);sync.snapshot(connection,snapshot(a));
        var oldLease=new ClientOutfitPipelineTest.Lease();textures.register(connection,store,a,(id,png)->oldLease,NOPLogger.NOP_LOGGER);
        assertEquals(ClientOutfitRegistry.Result.APPLIED,sync.refresh(connection,1,snapshot(b)));textures.retain(connection,registry.requiredHashes());
        assertEquals(1,oldLease.closed);assertFalse(store.contains(a));assertTrue(store.contains(b));assertTrue(cache.findValidated(a).isPresent());
        var currentLease=new ClientOutfitPipelineTest.Lease();textures.register(connection,store,b,(id,png)->currentLease,NOPLogger.NOP_LOGGER);
        textures.retain(new Object(),Set.of());assertEquals(0,currentLease.closed);
        textures.retain(connection,registry.requiredHashes());assertEquals(0,currentLease.closed);assertEquals(1,textures.size());
    }
}
