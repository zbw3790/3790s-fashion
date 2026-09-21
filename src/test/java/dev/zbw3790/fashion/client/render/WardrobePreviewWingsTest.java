package dev.zbw3790.fashion.client.render;
import java.util.Optional;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.render.armor.ArmorRendering;
class WardrobePreviewWingsTest {
 @BeforeAll static void boot(){WardrobePreviewTestSupport.bootstrap();}
 private AvatarRenderState body(ItemStack chest){var s=new AvatarRenderState();s.chestEquipment=chest;s.elytraRotX=.25F;s.elytraRotZ=-.25F;WardrobePreviewRenderState.attach(s,WardrobePreviewAppearance.serverCosmetic(Optional.of(Identifier.parse("fashion_3790:test/cape")),ElytraTextureDecision.customTexture(Identifier.parse("fashion_3790:test/wings"))));return s;}
 @Test void independentWingInputPreservesChestAndComponents(){
  var chest=new ItemStack(Items.LEATHER_CHESTPLATE);chest.set(DataComponents.DYED_COLOR,new net.minecraft.world.item.component.DyedItemColor(0x3790ff));chest.setDamageValue(19);var before=chest.copy();var b=body(chest);
  WardrobePreviewWings.attach(b,true);var w=(AvatarRenderState)WardrobePreviewWings.forLayer(b);
  assertNotSame(b,w);assertSame(chest,b.chestEquipment);assertTrue(ItemStack.matches(before,chest));assertTrue(w.chestEquipment.is(Items.ELYTRA));assertEquals(.25F,w.elytraRotX);assertEquals(-.25F,w.elytraRotZ);assertTrue(WardrobePreviewRenderState.find(w).isPresent());assertEquals(Identifier.parse("fashion_3790:test/wings"),ElytraTextureOverrideResolver.resolve(w).texture());
  assertFalse(PlayerFashionRenderDecisions.allowVanillaCape(b));assertTrue(PlayerFashionRenderDecisions.capeTexture(b).isEmpty());
 }
 @Test void hiddenChestDoesNotHideTryOnWings(){var b=body(new ItemStack(Items.IRON_CHESTPLATE));ArmorRendering.attach(b,ArmorSelections.original().with(ArmorSlot.CHEST,ArmorSelection.HIDDEN));WardrobePreviewWings.attach(b,true);assertTrue(ArmorRendering.hidden(b,net.minecraft.world.entity.EquipmentSlot.CHEST));assertFalse(ArmorRendering.hidden(WardrobePreviewWings.forLayer(b),net.minecraft.world.entity.EquipmentSlot.CHEST));}
 @Test void emptyChestNeverInventsArmor(){var b=body(ItemStack.EMPTY);WardrobePreviewWings.attach(b,true);assertTrue(b.chestEquipment.isEmpty());assertTrue(WardrobePreviewWings.forLayer(b).chestEquipment.is(Items.ELYTRA));}
 @Test void actualChestWingsStillHaveOneLayerInput(){var b=body(new ItemStack(Items.ELYTRA));WardrobePreviewWings.attach(b,true);var w=WardrobePreviewWings.forLayer(b);assertSame(w,WardrobePreviewWings.forLayer(b));assertSame(w,WardrobePreviewWings.forLayer(w));}
 @Test void noPreviewLeavesWorldAndNonplayerUntouched(){var w=new AvatarRenderState();var n=new HumanoidRenderState();assertSame(w,WardrobePreviewWings.forLayer(w));assertSame(n,WardrobePreviewWings.forLayer(n));assertFalse(WardrobePreviewWings.active(w));}
 @Test void independentFramesDoNotCarryPreviousMode(){var b=body(new ItemStack(Items.IRON_CHESTPLATE));WardrobePreviewWings.attach(b,true);var w=WardrobePreviewWings.forLayer(b);var next=body(ItemStack.EMPTY);WardrobePreviewWings.attach(next,false);assertFalse(WardrobePreviewWings.active(next));assertSame(w,WardrobePreviewWings.forLayer(b));assertTrue(PlayerFashionRenderDecisions.capeTexture(next).isPresent());}
}
