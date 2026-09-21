package dev.zbw3790.fashion.client.render;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.render.armor.ArmorRendering;
import static org.junit.jupiter.api.Assertions.*;
class ArmorRenderingTest {
 @BeforeAll static void boot(){WardrobePreviewTestSupport.bootstrap();}
 @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
 void allMasksAreSlotLocalAndDoNotTouchSourceEquipment(int mask){
  var state=new AvatarRenderState();var chest=new ItemStack(Items.ELYTRA);chest.setDamageValue(23);state.chestEquipment=chest;ArmorRendering.attach(state,new ArmorSelections(mask));
  var slots=new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
  for(int i=0;i<4;i++)assertEquals((mask&(1<<i))!=0,ArmorRendering.hidden(state,slots[i]));
  assertFalse(ArmorRendering.hidden(state,EquipmentSlot.BODY));assertFalse(ArmorRendering.hidden(state,EquipmentSlot.MAINHAND));assertSame(chest,state.chestEquipment);assertEquals(23,chest.getDamageValue());
 }
 @Test void unknownNonPlayerAndReusedStatePassThrough(){
  var state=new AvatarRenderState();assertFalse(ArmorRendering.hidden(state,EquipmentSlot.HEAD));ArmorRendering.attach(state,new ArmorSelections(15));assertTrue(ArmorRendering.hidden(state,EquipmentSlot.HEAD));ArmorRendering.attach(state,ArmorSelections.original());assertFalse(ArmorRendering.hidden(state,EquipmentSlot.HEAD));assertFalse(ArmorRendering.hidden(new HumanoidRenderState(),EquipmentSlot.HEAD));
 }
 @Test void publishedSelectionsAreNotRecycled(){var first=new AvatarRenderState();var second=new AvatarRenderState();var snapshot=new ArmorSelections(1);ArmorRendering.attach(first,snapshot);ArmorRendering.attach(second,snapshot.with(ArmorSlot.HEAD,ArmorSelection.ORIGINAL));assertTrue(ArmorRendering.hidden(first,EquipmentSlot.HEAD));assertFalse(ArmorRendering.hidden(second,EquipmentSlot.HEAD));}
}
