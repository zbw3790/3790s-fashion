package vanillafashion.network;

import java.util.*;
import org.junit.jupiter.api.Test;
import vanillafashion.outfit.*;
import vanillafashion.testutil.S03TestAssets;
import static org.junit.jupiter.api.Assertions.*;

class OutfitRefreshAssetViewTest {
    static ConnectionOutfitAssetView view(long generation,int color) {
        var asset=OutfitAsset.fromBytes(S03TestAssets.png(64,64,0xff000000|color));var id=new OutfitId("robe");
        var registry=new OutfitRegistry(List.of(new OutfitRegistryEntry(id,new OutfitMetadata(OutfitPart.ALL,Set.of(OutfitModel.WIDE)),Map.of(OutfitModel.WIDE,new OutfitModelResource.Valid(asset)))));
        var snapshot=new OutfitRegistrySnapshot(true,List.of(new OutfitRegistrySnapshot.Entry(id,OutfitPart.ALL,Set.of(OutfitModel.WIDE),Map.of(OutfitModel.WIDE,asset.sha256()))));
        return new ConnectionOutfitAssetView(generation,snapshot,OutfitAssetIndex.from(registry));
    }
    static String hash(ConnectionOutfitAssetView view){return view.snapshot().requiredHashes().iterator().next();}
    static OutfitAssetRequestPayload request(ConnectionOutfitAssetView view){return new OutfitAssetRequestPayload(List.of(hash(view)));}
    @Test void absentCapabilityPinsOldSourceAndPresentCapabilityRefreshesBeforeSending() {
        var channel=new PlayerFashionNetworking.Channels();Object legacy=new Object(),current=new Object(),second=new Object();
        var old=view(0,1);var next=view(1,2);for(Object c:List.of(legacy,current,second))channel.assets.open(c,old);
        var sent=new ArrayList<Object>();var counts=channel.refreshAssets(List.of(legacy,current,second),next,c->c!=legacy,c->{
            assertSame(next,channel.assets.view(current).orElseThrow());assertSame(next,channel.assets.view(second).orElseThrow());sent.add(c);return true;
        });
        assertEquals(2,counts.refreshed());assertEquals(1,counts.pinned());assertEquals(List.of(current,second),sent);
        assertSame(old,channel.assets.view(legacy).orElseThrow());assertEquals(List.of(hash(old)),channel.assets.claim(legacy,request(old)));
        assertArrayEquals(old.assets().find(hash(old)).orElseThrow().bytes(),channel.assets.asset(legacy,hash(old)).orElseThrow().bytes());
        assertTrue(channel.assets.claim(legacy,request(next)).isEmpty());
        assertEquals(List.of(hash(next)),channel.assets.claim(current,request(next)));assertTrue(channel.assets.asset(current,hash(old)).isEmpty());
    }
    @Test void duplicateOpenAndRefreshDoNotResetAttemptsOrUniqueCount() {
        var tracker=new OutfitAssetRequestTracker();Object c=new Object();var old=view(0,1);var next=view(1,2);tracker.open(c,old);
        tracker.claim(c,request(old));tracker.claim(c,request(old));assertTrue(tracker.refreshAuthorized(c,next));tracker.open(c,next);
        assertEquals(2,tracker.attempts(c));assertEquals(1,tracker.sentCount(c));assertEquals(List.of(hash(next)),tracker.claim(c,request(next)));
        assertEquals(3,tracker.attempts(c));assertEquals(2,tracker.sentCount(c));
        assertFalse(tracker.refreshAuthorized(c,old));assertFalse(tracker.refreshAuthorized(c,next));
        assertSame(next,tracker.view(c).orElseThrow());
    }
    @Test void refreshCannotBypassAttemptExhaustion() {
        var tracker=new OutfitAssetRequestTracker();Object c=new Object();var old=view(0,1);var next=view(1,2);tracker.open(c,old);
        var request=new OutfitAssetRequestPayload(Collections.nCopies(64,hash(old)));
        for(int i=0;i<16;i++)tracker.claim(c,request);
        assertEquals(1024,tracker.attempts(c));tracker.refreshAuthorized(c,next);
        assertTrue(tracker.claim(c,request(next)).isEmpty());assertEquals(1024,tracker.attempts(c));assertEquals(1,tracker.sentCount(c));
    }
    @Test void manyGenerationsCannotBypass512LifetimeUniqueLimit() {
        var tracker=new OutfitAssetRequestTracker();Object c=new Object();
        for(int i=0;i<513;i++) {
            var next=view(i,i);if(i==0)tracker.open(c,next);else assertTrue(tracker.refreshAuthorized(c,next));
            assertEquals(i<512?List.of(hash(next)):List.of(),tracker.claim(c,request(next)));
        }
        assertEquals(512,tracker.sentCount(c));assertEquals(513,tracker.attempts(c));
    }
    @Test void disconnectReleasesPinnedViewAndOldIdentityCannotRefreshNewOne() {
        var tracker=new OutfitAssetRequestTracker();Object old=new Object(),next=new Object();tracker.open(old,view(0,1));tracker.close(old);
        assertTrue(tracker.view(old).isEmpty());assertTrue(tracker.asset(old,hash(view(0,1))).isEmpty());
        tracker.open(next,view(1,2));assertFalse(tracker.refreshAuthorized(old,view(2,3)));assertEquals(1,tracker.view(next).orElseThrow().generation());
        tracker.clear();assertEquals(0,tracker.connectionCount());assertTrue(tracker.view(next).isEmpty());
    }
}
