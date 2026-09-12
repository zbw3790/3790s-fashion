package dev.zbw3790.fashion.network;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"rawtypes","unchecked"})
class FullFashionPayloadTest {
    record Sample(String name, StreamCodec codec, Object value, int budget) { @Override public String toString(){return name;} }
    static String hash(int i) { return "%064x".formatted(i); }
    static FullPlayerFashionState authority(boolean maximum) {
        var parts=OutfitSelections.original(); String name=maximum?"a".repeat(64):"robe";
        for (var part:OutfitPart.CANONICAL_ORDER) parts=parts.with(part,OutfitPartSelection.outfit(new OutfitId(name)));
        var stored=new PlayerFashionStoredState(Optional.of(new CapeId(name)),parts);
        return new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),parts),maximum?Long.MAX_VALUE:7);
    }
    static List<Sample> samples(boolean maximum) {
        var state=authority(maximum); var entry=new FullPlayerFashionEntry(new UUID(0,1),state);
        var entries=IntStream.range(0,maximum?1024:2).mapToObj(i -> new FullPlayerFashionEntry(new UUID(0,i),state)).toList();
        var definitions=IntStream.range(0,maximum?256:1).mapToObj(i -> new OutfitRegistrySnapshot.Entry(new OutfitId(hash(i)),OutfitPart.ALL,
                Set.of(OutfitModel.WIDE,OutfitModel.SLIM),Map.of(OutfitModel.WIDE,hash(i*2),OutfitModel.SLIM,hash(i*2+1)))).toList();
        return List.of(
            new Sample("Snapshot",FullPlayerFashionSnapshotPayload.CODEC,new FullPlayerFashionSnapshotPayload(new FullPlayerFashionSnapshot(true,entries)),498691),
            new Sample("Update",FullPlayerFashionUpdatePayload.CODEC,new FullPlayerFashionUpdatePayload(entry),487),
            new Sample("Remove",FullPlayerFashionRemovePayload.CODEC,new FullPlayerFashionRemovePayload(entry.playerId(),state.revision(),FullPlayerFashionRemovePayload.Reason.LEFT),25),
            new Sample("Result",FullFashionSelectionResultPayload.CODEC,new FullFashionSelectionResultPayload(3,FullFashionSelectionStatus.SUCCESS,Optional.of(state)),481),
            new Sample("Registry",OutfitRegistrySnapshotPayload.CODEC,new OutfitRegistrySnapshotPayload(new OutfitRegistrySnapshot(true,definitions)),33795),
            new Sample("RegistryRefresh",OutfitRegistryRefreshPayload.CODEC,new OutfitRegistryRefreshPayload(maximum?Long.MAX_VALUE:1,new OutfitRegistrySnapshot(true,definitions)),33803),
            new Sample("Asset",OutfitAssetDataPayload.CODEC,new OutfitAssetDataPayload(hash(1),new byte[maximum?65536:8]),65571),
            new Sample("AssetRequest",OutfitAssetRequestPayload.CODEC,new OutfitAssetRequestPayload(IntStream.range(0,maximum?64:1).mapToObj(FullFashionPayloadTest::hash).toList()),2049),
            new Sample("Apply",SetFullFashionSelectionPayload.CODEC,new SetFullFashionSelectionPayload(2,state.revision(),state.stored()),478));
    }
    static Stream<Sample> normal(){return samples(false).stream();}
    static Stream<Sample> maximum(){return samples(true).stream();}
    static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);}
    static byte[] encode(Sample sample){var b=buffer();try{sample.codec.encode(b,sample.value);byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return bytes;}finally{b.release();}}
    @ParameterizedTest @MethodSource("normal") void allFamiliesRoundtrip(Sample sample){var b=buffer();try{sample.codec.encode(b,sample.value);assertEquals(sample.value,sample.codec.decode(b));assertFalse(b.isReadable());}finally{b.release();}}
    @ParameterizedTest @MethodSource("maximum") void actualProductionCodecMatchesFrozenWorstCaseBudget(Sample sample){var bytes=encode(sample);assertEquals(sample.budget,bytes.length);var b=buffer();try{b.writeBytes(bytes);assertEquals(sample.value,sample.codec.decode(b));}finally{b.release();}}
    @ParameterizedTest @MethodSource("normal") void everyFamilyRejectsTrailingData(Sample sample){var b=buffer();try{b.writeBytes(encode(sample));b.writeByte(0);assertThrows(DecoderException.class,()->sample.codec.decode(b));}finally{b.release();}}
    @ParameterizedTest @MethodSource("normal") void everyFamilyRejectsTruncatedData(Sample sample){byte[] bytes=encode(sample);for(int size:new int[]{0,1,bytes.length/2,bytes.length-1}){var b=buffer();try{b.writeBytes(bytes,0,size);assertThrows(RuntimeException.class,()->sample.codec.decode(b));}finally{b.release();}}}
    @ParameterizedTest @MethodSource("maximum") void everyFamilyRejectsOverBudgetBeforeAllocation(Sample sample){var b=buffer();try{b.writeZero(sample.budget+1);assertThrows(DecoderException.class,()->sample.codec.decode(b));}finally{b.release();}}
    static void reject(StreamCodec codec, Consumer<RegistryFriendlyByteBuf> write){var b=buffer();try{write.accept(b);assertThrows(DecoderException.class,()->codec.decode(b));}finally{b.release();}}
    @ParameterizedTest @ValueSource(ints={-1,1025,Integer.MAX_VALUE}) void snapshotCountsAreBounded(int count){reject(FullPlayerFashionSnapshotPayload.CODEC,b->{b.writeBoolean(true);b.writeVarInt(count);});}
    @ParameterizedTest @ValueSource(ints={-1,257,Integer.MAX_VALUE}) void registryCountsAreBounded(int count){reject(OutfitRegistrySnapshotPayload.CODEC,b->{b.writeBoolean(true);b.writeVarInt(count);});}
    @ParameterizedTest @ValueSource(ints={-1,0,65,Integer.MAX_VALUE}) void requestCountsAreBounded(int count){reject(OutfitAssetRequestPayload.CODEC,b->b.writeVarInt(count));}
    @ParameterizedTest @ValueSource(ints={-1,0,65537,Integer.MAX_VALUE}) void assetLengthsAreBounded(int count){reject(OutfitAssetDataPayload.CODEC,b->{FashionWireCodec.hash(b,hash(0));b.writeVarInt(count);});}
    @ParameterizedTest @ValueSource(ints={1,65536}) void assetByteArraysHaveNoMutableLeaks(int size){byte[] bytes=new byte[size];var payload=new OutfitAssetDataPayload(hash(0),bytes);bytes[0]=1;assertEquals(0,payload.pngBytes()[0]);payload.pngBytes()[0]=2;assertEquals(0,payload.pngBytes()[0]);}
    @Test void unavailableCollectionsCannotCarryEntries(){reject(FullPlayerFashionSnapshotPayload.CODEC,b->{b.writeBoolean(false);b.writeVarInt(1);});reject(OutfitRegistrySnapshotPayload.CODEC,b->{b.writeBoolean(false);b.writeVarInt(1);});}
    @Test void duplicateUuidsAreRejected(){reject(FullPlayerFashionSnapshotPayload.CODEC,b->{b.writeBoolean(true);b.writeVarInt(2);var e=new FullPlayerFashionEntry(new UUID(0,1),FullPlayerFashionState.defaults(0));FashionWireCodec.entry(b,e);FashionWireCodec.entry(b,e);});}
    @Test void duplicateOutfitIdsAreRejected(){reject(OutfitRegistrySnapshotPayload.CODEC,b->{b.writeBoolean(true);b.writeVarInt(2);for(int i=0;i<2;i++){b.writeUtf("same");b.writeByte(1);b.writeByte(1);b.writeByte(0);}});}
    @Test void duplicateRequestHashesRemainAttempts(){var payload=new OutfitAssetRequestPayload(List.of(hash(1),hash(1)));var b=buffer();try{OutfitAssetRequestPayload.CODEC.encode(b,payload);assertEquals(payload,OutfitAssetRequestPayload.CODEC.decode(b));}finally{b.release();}}
    @ParameterizedTest @ValueSource(ints={3,127,255}) void unknownSelectionKindsRejected(int kind){reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(1);b.writeLong(0);b.writeBoolean(false);b.writeByte(kind);});}
    @ParameterizedTest @ValueSource(ints={9,127,255}) void unknownResultStatusesRejected(int value){reject(FullFashionSelectionResultPayload.CODEC,b->{b.writeLong(1);b.writeByte(value);b.writeBoolean(false);});}
    @ParameterizedTest @ValueSource(ints={2,127,255}) void unknownRemoveReasonsRejected(int value){reject(FullPlayerFashionRemovePayload.CODEC,b->{b.writeUUID(new UUID(0,1));b.writeLong(0);b.writeByte(value);});}
    @ParameterizedTest @ValueSource(ints={-1,0}) void positiveRequestIdsRequired(long value){reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(value);b.writeLong(0);FashionWireCodec.stored(b,PlayerFashionStoredState.DEFAULT);});reject(FullFashionSelectionResultPayload.CODEC,b->{b.writeLong(value);b.writeByte(4);b.writeBoolean(false);});}
    @Test void negativeRegistryGenerationRejected() {
        reject(OutfitRegistryRefreshPayload.CODEC,b->{b.writeLong(-1); b.writeBoolean(true);b.writeVarInt(0);});
    }
    @Test void negativeRevisionsRejected(){reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(1);b.writeLong(-1);FashionWireCodec.stored(b,PlayerFashionStoredState.DEFAULT);});reject(FullPlayerFashionUpdatePayload.CODEC,b->{b.writeUUID(new UUID(0,1));b.writeLong(-1);});}
    @ParameterizedTest @ValueSource(ints={1,2,4,8,16,32,64,128,255}) void defaultsCannotCarryActiveOrReservedBits(int mask){reject(FullPlayerFashionUpdatePayload.CODEC,b->{b.writeUUID(new UUID(0,1));b.writeLong(0);FashionWireCodec.stored(b,PlayerFashionStoredState.DEFAULT);b.writeByte(mask);});}
    @ParameterizedTest @CsvSource({"0,1,0","64,1,0","1,0,0","1,4,0","1,1,2","63,3,4"}) void invalidRegistryMasksRejected(int parts,int declared,int valid){reject(OutfitRegistrySnapshotPayload.CODEC,b->{b.writeBoolean(true);b.writeVarInt(1);b.writeUtf("valid");b.writeByte(parts);b.writeByte(declared);b.writeByte(valid);});}
    @ParameterizedTest @ValueSource(strings={"","../bad","UPPER","a:b","a/b","中文"}) void invalidIdsRejectedInBothStoredRoles(String id){for(boolean cape:new boolean[]{true,false})reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(1);b.writeLong(0);b.writeBoolean(cape);if(!cape)b.writeByte(2);b.writeUtf(id);});}
    @Test void utfAndIdLengthsRejected(){reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(1);b.writeLong(0);b.writeBoolean(true);b.writeUtf("a".repeat(65));});reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(1);b.writeLong(0);b.writeBoolean(true);b.writeVarInt(193);});}
    @Test void invalidUtfCannotBecomeValidId(){reject(SetFullFashionSelectionPayload.CODEC,b->{b.writeLong(1);b.writeLong(0);b.writeBoolean(true);b.writeVarInt(1);b.writeByte(0x80);});}
    @ParameterizedTest @ValueSource(ints={0,1,31,32,33,64}) void binaryHashHasExactly32Bytes(int length){var b=buffer();try{String text=hash(123);FashionWireCodec.hash(b,text);assertEquals(32,b.readableBytes());assertEquals(text,FashionWireCodec.hash(b));if(length!=32){b.clear();b.writeVarInt(1);b.writeZero(length);assertThrows(DecoderException.class,()->OutfitAssetRequestPayload.CODEC.decode(b));}}finally{b.release();}}
    @ParameterizedTest @EnumSource(FullFashionSelectionStatus.class) void allStatusesHaveFixedValuesAndRoundtrip(FullFashionSelectionStatus status){assertEquals(status.ordinal(),status.wireId());var value=new FullFashionSelectionResultPayload(1,status,Optional.of(FullPlayerFashionState.defaults(0)));var b=buffer();try{FullFashionSelectionResultPayload.CODEC.encode(b,value);assertEquals(value,FullFashionSelectionResultPayload.CODEC.decode(b));}finally{b.release();}}
    @Test void successRequiresAuthority(){reject(FullFashionSelectionResultPayload.CODEC,b->{b.writeLong(1);b.writeByte(0);b.writeBoolean(false);});}
    @Test void allDormantAndBuiltinStatesRetainStoredWithoutInventingEffective(){var parts=OutfitSelections.original();for(var p:OutfitPart.CANONICAL_ORDER)parts=parts.with(p,OutfitPartSelection.NONE);parts=parts.with(OutfitPart.HEAD,OutfitPartSelection.outfit(new OutfitId("missing")));var stored=new PlayerFashionStoredState(Optional.of(new CapeId("missing")),parts);var effective=new PlayerFashionEffectiveState(Optional.empty(),parts.with(OutfitPart.HEAD,OutfitPartSelection.ORIGINAL));var state=new FullPlayerFashionState(stored,effective,3);var b=buffer();try{FashionWireCodec.authority(b,state);assertEquals(state,FashionWireCodec.authority(b));}finally{b.release();}}
}
