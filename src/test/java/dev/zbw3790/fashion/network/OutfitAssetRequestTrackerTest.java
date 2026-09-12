package dev.zbw3790.fashion.network;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OutfitAssetRequestTrackerTest {
    static String hash(int i){return "%064x".formatted(i);}
    @Test void fullColdCacheUses512AttemptsAndOneSendPerHash(){var t=new OutfitAssetRequestTracker();var c=new Object();var hashes=IntStream.range(0,512).mapToObj(OutfitAssetRequestTrackerTest::hash).toList();t.open(c,Set.copyOf(hashes));for(int i=0;i<512;i+=64)assertEquals(hashes.subList(i,i+64),t.claim(c,new OutfitAssetRequestPayload(hashes.subList(i,i+64))));assertEquals(512,t.attempts(c));assertEquals(512,t.sentCount(c));assertTrue(t.claim(c,new OutfitAssetRequestPayload(hashes.subList(0,64))).isEmpty());assertEquals(576,t.attempts(c));}
    @Test void duplicatesAndUnauthorizedHashesConsumeBudgetBeforeDedup(){var t=new OutfitAssetRequestTracker();var c=new Object();t.open(c,Set.of(hash(0)));assertEquals(List.of(hash(0)),t.claim(c,new OutfitAssetRequestPayload(List.of(hash(0),hash(0),hash(1)))));assertEquals(3,t.attempts(c));assertEquals(1,t.sentCount(c));}
    @Test void batchCrossingAttemptLimitIsEntirelyRejectedAndPermanentlyExhausted(){var t=new OutfitAssetRequestTracker();var c=new Object();t.open(c,Set.of(hash(0)));for(int i=0;i<1023;i++)t.claim(c,new OutfitAssetRequestPayload(List.of(hash(1))));assertTrue(t.claim(c,new OutfitAssetRequestPayload(List.of(hash(0),hash(1)))).isEmpty());assertEquals(1024,t.attempts(c));assertEquals(0,t.sentCount(c));assertTrue(t.claim(c,new OutfitAssetRequestPayload(List.of(hash(0)))).isEmpty());}
    @Test void exactLimitLastAuthorizedAttemptStillSucceedsThenLateOldConnectionIsIgnored(){var t=new OutfitAssetRequestTracker();var old=new Object();t.open(old,Set.of(hash(0)));for(int i=0;i<1023;i++)t.claim(old,new OutfitAssetRequestPayload(List.of(hash(1))));assertEquals(List.of(hash(0)),t.claim(old,new OutfitAssetRequestPayload(List.of(hash(0)))));t.close(old);var next=new Object();t.open(next,Set.of(hash(0)));assertTrue(t.claim(old,new OutfitAssetRequestPayload(List.of(hash(0)))).isEmpty());assertEquals(List.of(hash(0)),t.claim(next,new OutfitAssetRequestPayload(List.of(hash(0)))));}
}
