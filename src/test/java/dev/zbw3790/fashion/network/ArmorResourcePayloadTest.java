package dev.zbw3790.fashion.network;

import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import dev.zbw3790.fashion.armor.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.network.FullFashionPayloadTest.*;

class ArmorResourcePayloadTest {
    static Stream<Sample> samples(){
        var entries=IntStream.range(0,128).mapToObj(i->new ArmorRegistrySnapshot.Entry(new ArmorStyleId("%032x".formatted(i)),"甲".repeat(80),Set.copyOf(ArmorSlot.CANONICAL_ORDER),Map.of(ArmorGeometry.OUTER,hash(i*2),ArmorGeometry.INNER,hash(i*2+1)))).toList();
        return Stream.of(new Sample("盔甲 Registry",ArmorRegistryPayload.CODEC,new ArmorRegistryPayload(Long.MAX_VALUE,new ArmorRegistrySnapshot(true,entries)),43659),
            new Sample("盔甲资产",ArmorAssetDataPayload.CODEC,new ArmorAssetDataPayload(hash(1),new byte[16384]),16419),
            new Sample("盔甲请求",ArmorAssetRequestPayload.CODEC,new ArmorAssetRequestPayload(IntStream.range(0,64).mapToObj(FullFashionPayloadTest::hash).toList()),2049));
    }
    @ParameterizedTest @MethodSource("samples") @SuppressWarnings("unchecked") void exactBudgetsRoundtripAndRejectTrailing(Sample sample){var bytes=encode(sample);assertEquals(sample.budget(),bytes.length);var b=buffer();try{b.writeBytes(bytes);assertEquals(sample.value(),sample.codec().decode(b));b.clear();b.writeBytes(bytes);b.writeByte(0);assertThrows(RuntimeException.class,()->sample.codec().decode(b));}finally{b.release();}}
    @Test void invalidRegistryMasksAndCountRejected(){for(int count:new int[]{-1,129,Integer.MAX_VALUE})reject(ArmorRegistryPayload.CODEC,b->{b.writeLong(0);b.writeBoolean(true);b.writeVarInt(count);});for(int slot:new int[]{0,16,255})reject(ArmorRegistryPayload.CODEC,b->{b.writeLong(0);b.writeBoolean(true);b.writeVarInt(1);b.writeUtf("a");b.writeUtf("名称");b.writeByte(slot);b.writeByte(1);});}
    @Test void domainKeysCannotShareArmorWithOutfitAndBudgetPersistsOnRefresh() throws Exception {
        var asset=ArmorAsset.fromBytes(dev.zbw3790.fashion.armor.ArmorTextureFixture.png(0xff3790ff));
        var snapshot=new ArmorRegistrySnapshot(true,List.of(new ArmorRegistrySnapshot.Entry(new ArmorStyleId("same"),"测试",Set.of(ArmorSlot.HEAD),Map.of(ArmorGeometry.OUTER,asset.hash()))));
        var loaded=new ArmorRegistryLoadResult(snapshot,Map.of(asset.hash(),asset),List.of());Object connection=new Object();var armor=new ArmorAssetRequestTracker();var outfit=new OutfitAssetRequestTracker();
        armor.open(connection,0,loaded);assertTrue(outfit.claim(connection,new OutfitAssetRequestPayload(List.of(asset.hash()))).isEmpty());
        assertEquals(List.of(asset.hash()),armor.claim(connection,new ArmorAssetRequestPayload(List.of(asset.hash()))));
        armor.refresh(connection,1,loaded);assertTrue(armor.claim(connection,new ArmorAssetRequestPayload(List.of(asset.hash()))).isEmpty());assertEquals(2,armor.attempts(connection));
        for(int i=0;i<17;i++)armor.claim(connection,new ArmorAssetRequestPayload(Collections.nCopies(64,hash(999))));assertEquals(1024,armor.attempts(connection));armor.close(connection);assertEquals(0,armor.connectionCount());
    }
}
