package vanillafashion.wardrobe;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** 不启动世界或网络；只替代环境和装备容器，执行真实 Player／ArmorStand／ItemStack 方法。 */
class WardrobeVanillaInteractionTest {
    ProbePlayer player; ProbeStand stand; ProbeLevel level;
    @BeforeAll static void boot(){vanillafashion.client.render.WardrobePreviewTestSupport.bootstrap();}
    @BeforeEach void prepare() throws Exception {
        level=allocate(ProbeLevel.class);player=allocate(ProbePlayer.class);stand=allocate(ProbeStand.class);
        player.world=level;player.hands=new EnumMap<>(InteractionHand.class);
        stand.world=level;stand.slots=new EnumMap<>(EquipmentSlot.class);
        var type=Entity.class.getDeclaredField("type");type.setAccessible(true);type.set(stand,EntityTypes.ARMOR_STAND);type.set(player,EntityTypes.PLAYER);
    }
    InteractionResult interact(InteractionHand hand) {return player.interactOn(stand,hand,new Vec3(0,1.8,0));}
    void assertFallback(InteractionHand hand) {
        var beforeHands=new EnumMap<>(player.hands);var beforeSlots=new EnumMap<>(stand.slots);
        var original=interact(hand);assertSame(InteractionResult.PASS,original);
        var opens=new AtomicInteger();assertTrue(WardrobeInteractionHandler.fallback(original,
                WardrobeInteractionRules.isWardrobeCandidate(player,stand,original),false,true,()->{opens.incrementAndGet();return true;}).consumesAction());
        assertEquals(1,opens.get());assertEquals(beforeHands,player.hands);assertEquals(beforeSlots,stand.slots);
    }
    @ParameterizedTest @EnumSource(InteractionHand.class)
    void emptyHandOnEmptyStandUsesOneFallback(InteractionHand hand){assertFallback(hand);}
    @ParameterizedTest @EnumSource(InteractionHand.class)
    void ordinaryItemAndInternalFailBecomeFinalPass(InteractionHand hand) {
        player.hands.put(hand,new ItemStack(Items.STICK));
        assertSame(InteractionResult.FAIL,stand.interact(player,hand,Vec3.ZERO));assertFallback(hand);
    }
    @ParameterizedTest @EnumSource(InteractionHand.class)
    void equippedStandWithNoActionItemDoesNotLoseEquipment(InteractionHand hand) {
        stand.slots.put(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));player.hands.put(hand,new ItemStack(Items.STICK));assertFallback(hand);
    }
    @ParameterizedTest @EnumSource(InteractionHand.class)
    void placeArmorIsVanillaOnlyAndMovesItemExactlyOnce(InteractionHand hand) {
        player.hands.put(hand,new ItemStack(Items.IRON_HELMET));var result=interact(hand);assertTrue(result.consumesAction());
        assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET));assertTrue(player.getItemInHand(hand).isEmpty());assertEquals(1,stand.writes);
        assertSame(result,WardrobeInteractionHandler.fallback(result,true,false,true,()->{fail("放装备后不能开窗。");return false;}));
    }
    @ParameterizedTest @EnumSource(InteractionHand.class)
    void takeArmorIsVanillaOnlyAndMovesItemExactlyOnce(InteractionHand hand) {
        stand.slots.put(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));var result=interact(hand);assertTrue(result.consumesAction());
        assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty());assertTrue(player.getItemInHand(hand).is(Items.IRON_HELMET));assertEquals(1,stand.writes);
        assertSame(result,WardrobeInteractionHandler.fallback(result,true,false,true,()->{fail("取装备后不能开窗。");return false;}));
    }
    @Test void equippableItemPathConsumedAfterEntityPassRemainsVanillaOnly() {
        // marker 让实体入口返回 PASS；实际 ItemStack 再执行 Equippable 分支。
        stand.marker=true;
        var held=new ItemStack(Items.STICK);held.set(DataComponents.EQUIPPABLE,Equippable.builder(EquipmentSlot.HEAD).setEquipOnInteract(true).setAllowedEntities(EntityTypes.ARMOR_STAND).build());
        player.hands.put(InteractionHand.MAIN_HAND,held);
        assertSame(InteractionResult.PASS,stand.interact(player,InteractionHand.MAIN_HAND,Vec3.ZERO));
        var result=interact(InteractionHand.MAIN_HAND);assertTrue(result.consumesAction());assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.STICK));
        assertSame(result,WardrobeInteractionHandler.fallback(result,true,false,true,()->{fail("Equippable 已处理。");return false;}));
    }
    @Test void clientVanillaPredictionConsumesBeforeOffhandAndDoesNotOpenLocally() {
        level.client=true;player.hands.put(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        var result=interact(InteractionHand.MAIN_HAND);assertTrue(result.consumesAction());assertEquals(0,stand.writes);
        assertSame(result,WardrobeInteractionHandler.fallback(result,true,true,true,()->{fail();return false;}));
    }
    @Test void actualClientMixinConsumesFinalPassWithoutEquipmentMutation() {
        level.client=true;stand.marker=true;
        var availability=vanillafashion.VanillaFashion.wardrobeServerAvailability();boolean before=availability.isAvailable();
        try {
            availability.markAvailable();
            assertEquals(InteractionResult.CONSUME.withoutItem(),interact(InteractionHand.MAIN_HAND));
            assertEquals(0,stand.writes);assertTrue(player.hands.isEmpty());
            availability.reset();assertSame(InteractionResult.PASS,interact(InteractionHand.MAIN_HAND));
        } finally {if(before)availability.markAvailable();else availability.reset();}
    }
    @Test void commonMixinIsActuallyAppliedByKnotTestLoader() {
        assertTrue(Arrays.stream(Player.class.getDeclaredMethods()).anyMatch(method->method.getName().contains("vanillaFashion$afterInteraction")),"必须验证 Player 返回点注入实际存在。");
    }
    @Test void spectatorPassIsNotWardrobeCandidate(){player.spectator=true;var result=interact(InteractionHand.MAIN_HAND);assertSame(InteractionResult.PASS,result);assertFalse(WardrobeInteractionRules.isWardrobeCandidate(player,stand,result));}
    @SuppressWarnings("removal") private static <T> T allocate(Class<T> type) throws Exception {
        // 测试隔离：不执行实体／世界构造和线程启动，所有被访问的环境接口在下方显式提供。
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    static class ProbeLevel extends ServerLevel {
        boolean client;
        ProbeLevel(){super(null,null,null,null,null,null,false,0,List.of(),false);}
        @Override public boolean isClientSide(){return client;}
        @Override public void gameEvent(Holder<GameEvent> event,Vec3 position,GameEvent.Context context) { }
    }
    static class ProbePlayer extends Player {
        ProbeLevel world;EnumMap<InteractionHand,ItemStack> hands;boolean spectator;
        ProbePlayer(){super(null,new GameProfile(new UUID(0,1),"测试玩家"));}
        @Override public Level level(){return world;}
        @Override public ItemStack getItemInHand(InteractionHand hand){return hands.getOrDefault(hand,ItemStack.EMPTY);}
        @Override public void setItemInHand(InteractionHand hand,ItemStack item){hands.put(hand,item);}
        @Override public boolean isSpectator(){return spectator;}
        @Override public boolean isSecondaryUseActive(){return false;}
        @Override public boolean hasInfiniteMaterials(){return false;}
        @Override public GameType gameMode(){return GameType.SURVIVAL;}
    }
    static class ProbeStand extends ArmorStand {
        ProbeLevel world;EnumMap<EquipmentSlot,ItemStack> slots;int writes;boolean marker;
        ProbeStand(){super(EntityTypes.ARMOR_STAND,null);}
        @Override public Level level(){return world;}
        @Override public EntityType<?> getType(){return EntityTypes.ARMOR_STAND;}
        @Override public ItemStack getItemBySlot(EquipmentSlot slot){return slots.getOrDefault(slot,ItemStack.EMPTY);}
        @Override public void setItemSlot(EquipmentSlot slot,ItemStack item){slots.put(slot,item);writes++;}
        @Override public boolean isMarker(){return marker;}
        @Override public boolean isSmall(){return false;}
        @Override public boolean showArms(){return false;}
        @Override public float getAgeScale(){return 1;}
        @Override public boolean isAlive(){return true;}
        @Override public Vec3 position(){return Vec3.ZERO;}
    }
}
