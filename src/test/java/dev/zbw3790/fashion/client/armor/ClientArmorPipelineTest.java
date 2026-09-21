package dev.zbw3790.fashion.client.armor;

import java.util.*;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.network.*;
import static dev.zbw3790.fashion.armor.ArmorTextureFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientArmorPipelineTest {
    @TempDir Path temp;
    ArmorAsset first,second;Object connection=new Object();ClientArmorResources resources;
    @BeforeEach void setup() throws Exception {first=ArmorAsset.fromBytes(png(0xff3790ff));second=ArmorAsset.fromBytes(png(0xffcc8800));resources=new ClientArmorResources(temp,NOPLogger.NOP_LOGGER);resources.sync.begin(connection);resources.textures.begin(connection);}
    ArmorRegistrySnapshot snapshot(String hash){return new ArmorRegistrySnapshot(true,List.of(new ArmorRegistrySnapshot.Entry(new ArmorStyleId("same"),"相同名称",Set.of(ArmorSlot.HEAD),Map.of(ArmorGeometry.OUTER,hash))));}
    void install(long generation,ArmorAsset asset){assertEquals(ClientArmorRegistry.Result.APPLIED,resources.sync.snapshot(connection,generation,snapshot(asset.hash())));}
    void receive(ArmorAsset asset){var request=resources.sync.missing(connection).getFirst();assertTrue(resources.sync.markRequested(connection,request));assertEquals(ClientArmorAssetSync.Receive.STORED,resources.sync.receive(connection,new ArmorAssetDataPayload(asset.hash(),asset.bytes())));}
    @Test void coldDownloadSameIdNewHashRejectsLateOldAndNoCrossConnectionState() {
        install(0,first);assertTrue(resources.resolve(new ArmorStyleId("same"),ArmorSlot.HEAD).isEmpty());
        var old=resources.sync.missing(connection).getFirst();resources.sync.markRequested(connection,old);
        install(1,second);assertEquals(ClientArmorAssetSync.Receive.NOT_AUTHORIZED,resources.sync.receive(connection,new ArmorAssetDataPayload(first.hash(),first.bytes())));receive(second);
        assertFalse(resources.store.contains(first.hash()));assertTrue(resources.store.contains(second.hash()));
        Object next=new Object();resources.sync.begin(next);resources.textures.begin(next);
        assertEquals(ClientArmorAssetSync.Receive.STALE,resources.sync.receive(connection,new ArmorAssetDataPayload(second.hash(),second.bytes())));
        assertFalse(resources.sync.disconnect(connection));assertTrue(resources.sync.matches(next));assertEquals(0,resources.store.size());assertEquals(ClientArmorRegistry.State.UNKNOWN,resources.registry.state());
    }
    @Test void contentCacheNeedsCurrentRegistryAndBadBytesAreNotRetried() {
        install(0,first);receive(first);resources.sync.begin(new Object());assertEquals(0,resources.store.size());
        resources.sync.begin(connection);install(0,first);assertTrue(resources.store.contains(first.hash()));assertTrue(resources.sync.missing(connection).isEmpty());
        install(1,second);var request=resources.sync.missing(connection).getFirst();resources.sync.markRequested(connection,request);
        assertEquals(ClientArmorAssetSync.Receive.INVALID,resources.sync.receive(connection,new ArmorAssetDataPayload(second.hash(),new byte[]{1,2,3})));
        assertTrue(resources.sync.failed(connection,second.hash()));assertTrue(resources.sync.missing(connection).isEmpty());
        assertEquals(ClientArmorAssetSync.Receive.NOT_REQUESTED,resources.sync.receive(connection,new ArmorAssetDataPayload(second.hash(),second.bytes())));
    }
    @Test void registryGenerationConflictIsTerminalUntilReconnect() {
        install(3,first);assertEquals(ClientArmorRegistry.Result.STALE,resources.sync.snapshot(connection,2,snapshot(second.hash())));
        assertEquals(ClientArmorRegistry.Result.CONFLICT,resources.sync.snapshot(connection,3,snapshot(second.hash())));assertEquals(ClientArmorRegistry.State.UNAVAILABLE,resources.registry.state());
        assertEquals(ClientArmorRegistry.Result.CONFLICT,resources.sync.snapshot(connection,4,snapshot(first.hash())));assertEquals(0,resources.store.size());
    }
    static class Texture implements ClientArmorTextureManager.OwnedTexture {boolean closed;public boolean ready(){return !closed;}public void close(){assertFalse(closed);closed=true;}}
    @Test void delayedFramesKeepOldTexturesAliveAndNewSameHashOwnershipDistinct() {
        install(0,first);receive(first);var old=new Texture();var next=new Texture();var manager=resources.textures;
        assertEquals(ClientArmorTextureManager.Result.REGISTERED,manager.register(connection,resources.store,first.hash(),(id,bytes)->old,NOPLogger.NOP_LOGGER));var oldId=manager.find(connection,first.hash()).orElseThrow();
        manager.retain(connection,Set.of());assertFalse(old.closed);assertTrue(manager.find(connection,first.hash()).isEmpty());
        assertEquals(ClientArmorTextureManager.Result.REGISTERED,manager.register(connection,resources.store,first.hash(),(id,bytes)->next,NOPLogger.NOP_LOGGER));assertNotEquals(oldId,manager.find(connection,first.hash()).orElseThrow());
        manager.frameCompleted();assertFalse(old.closed);manager.frameCompleted();assertTrue(old.closed);assertFalse(next.closed);assertEquals(0,manager.retiredCount());
        manager.disconnect(connection);assertFalse(next.closed);manager.frameCompleted();manager.frameCompleted();assertTrue(next.closed);
    }
    @Test void decodeFailureDoesNotRetryAndReadinessDoesNotInventState() {
        install(0,first);receive(first);int[] attempts={0};
        ClientArmorTextureManager.Backend failing=(id,bytes)->{attempts[0]++;throw new IOException("测试解码失败");};
        assertEquals(ClientArmorTextureManager.Result.FAILED,resources.textures.register(connection,resources.store,first.hash(),failing,NOPLogger.NOP_LOGGER));
        assertEquals(ClientArmorTextureManager.Result.FAILED,resources.textures.register(connection,resources.store,first.hash(),failing,NOPLogger.NOP_LOGGER));assertEquals(1,attempts[0]);
        assertTrue(resources.resolve(new ArmorStyleId("same"),ArmorSlot.HEAD).isEmpty());assertTrue(resources.registry.find(new ArmorStyleId("same")).isPresent());
    }
    @Test void reloadCannotResetConnectionRequestBudget() {
        for(int generation=0;generation<514;generation++) {
            String hash="%064x".formatted(generation);resources.sync.snapshot(connection,generation,snapshot(hash));
            var requests=resources.sync.missing(connection);
            if(generation<512){assertEquals(1,requests.size());resources.sync.markRequested(connection,requests.getFirst());}else assertTrue(requests.isEmpty());
        }
    }
    @Test void resourceDomainsKeepSeparateCachePathsAndValidation() throws Exception {
        var armor=ClientArmorAssetCache.fromGameDirectory(temp,NOPLogger.NOP_LOGGER);
        var outfit=dev.zbw3790.fashion.client.outfit.ClientOutfitAssetCache.fromGameDirectory(temp,NOPLogger.NOP_LOGGER);
        assertNotEquals(armor.pathFor(first.hash()),outfit.pathFor(first.hash()));
        armor.storeValidated(first.hash(),first.bytes());assertTrue(armor.findValidated(first.hash()).isPresent());
        assertTrue(outfit.findValidated(first.hash()).isEmpty());
        assertEquals(dev.zbw3790.fashion.client.asset.ValidatedAssetCache.StoreResult.REJECTED_INVALID,outfit.storeValidated(first.hash(),first.bytes()));
        byte[] square=png(64,64,0xff77bb44);String squareHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(square));
        outfit.storeValidated(squareHash,square);assertTrue(outfit.findValidated(squareHash).isPresent());
        assertEquals(dev.zbw3790.fashion.client.asset.ValidatedAssetCache.StoreResult.REJECTED_INVALID,armor.storeValidated(squareHash,square));
    }
}
