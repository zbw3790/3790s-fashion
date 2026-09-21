package dev.zbw3790.fashion.network;
import java.util.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
class ArmorWireTest {
 @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
 void allMasksRoundtripWithCapeAndOutfit(int mask) {
  var stored=new PlayerFashionStoredState(Optional.of(new CapeId("cape")),OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.NONE),new ArmorSelections(mask));
  var b=new FriendlyByteBuf(Unpooled.buffer());try {
   var request=new SetFullFashionSelectionPayload(5,7,stored);SetFullFashionSelectionPayload.CODEC.encode(b,request);
   int packed=0;for(int i=0;i<4;i++)if((mask & (1<<i))!=0)packed |= 1<<(i*2);assertEquals(packed,b.getUnsignedByte(b.writerIndex()-1));assertEquals(request,SetFullFashionSelectionPayload.CODEC.decode(b));
   var state=new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),stored.outfit(),stored.armor()),7);
   FashionWireCodec.authority(b,state);assertEquals(state,FashionWireCodec.authority(b));assertFalse(b.isReadable());
  } finally {b.release();}
 }
 @ParameterizedTest @ValueSource(ints={3,12,48,192,255,2,8,32,128}) void invalidMaskRejected(int mask) {
  var b=new FriendlyByteBuf(Unpooled.buffer());try {SetFullFashionSelectionPayload.CODEC.encode(b,new SetFullFashionSelectionPayload(1,0,PlayerFashionStoredState.DEFAULT));b.setByte(b.writerIndex()-1,mask);assertThrows(DecoderException.class,()->SetFullFashionSelectionPayload.CODEC.decode(b));}finally{b.release();}
 }
 @Test void missingArmorIsNotOriginalAndOldChannelIsNotCurrent() {
  var b=new FriendlyByteBuf(Unpooled.buffer());try {b.writeLong(1);b.writeLong(0);b.writeBoolean(false);b.writeZero(6);assertThrows(DecoderException.class,()->SetFullFashionSelectionPayload.CODEC.decode(b));}finally{b.release();}
  assertEquals("set_full_fashion_selection_v4",SetFullFashionSelectionPayload.TYPE.id().getPath());
  assertEquals("full_player_fashion_snapshot_v4",FullPlayerFashionSnapshotPayload.TYPE.id().getPath());
 }
 @Test void effectiveCannotInventDifferentArmor() {
  var stored=PlayerFashionStoredState.DEFAULT.withArmor(new ArmorSelections(1));
  assertThrows(IllegalArgumentException.class,()->new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),stored.outfit()),0));
  assertThrows(IllegalArgumentException.class,()->new ArmorSelections(-1));assertThrows(IllegalArgumentException.class,()->new ArmorSelections(16));
 }
}
