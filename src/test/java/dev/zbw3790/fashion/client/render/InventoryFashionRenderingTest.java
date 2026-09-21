package dev.zbw3790.fashion.client.render;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import com.mojang.authlib.GameProfile;
class InventoryFashionRenderingTest {
 ProbeLevel level;ProbePlayer player;Object connection=new Object();
 @BeforeAll static void boot(){WardrobePreviewTestSupport.bootstrap();}
 @BeforeEach void init() throws Exception{level=allocate(ProbeLevel.class);player=allocate(ProbePlayer.class);player.world=level;player.uuid=UUID.randomUUID();level.entity=player;}
 @Test void actualEntityAndCurrentConnectionIdentifyInventoryOwner(){assertEquals(Optional.of(player.uuid),InventoryFashionRendering.trustedPlayer(player,level,connection,connection));}
 @Test void oldConnectionCannotReadNewAuthority(){assertTrue(InventoryFashionRendering.trustedPlayer(player,level,connection,new Object()).isEmpty());}
 @Test void disconnectedIsUnknown(){assertTrue(InventoryFashionRendering.trustedPlayer(player,level,null,null).isEmpty());}
 @Test void absentLevelIsUnknown(){assertTrue(InventoryFashionRendering.trustedPlayer(player,null,connection,connection).isEmpty());}
 @Test void sameIdReplacementIsNotSource() throws Exception{level.entity=allocate(ProbePlayer.class);assertTrue(InventoryFashionRendering.trustedPlayer(player,level,connection,connection).isEmpty());}
 @Test void oldWorldPlayerIsNotCurrentSource() throws Exception{var other=allocate(ProbeLevel.class);other.entity=player;assertTrue(InventoryFashionRendering.trustedPlayer(player,other,connection,connection).isEmpty());}
 @SuppressWarnings("removal") private static <T>T allocate(Class<T> c)throws Exception{var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return c.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(c));}
 static class ProbeLevel extends ServerLevel{Entity entity;ProbeLevel(){super(null,null,null,null,null,null,false,0,List.of(),false);}@Override public Entity getEntity(int id){return entity;}}
 static class ProbePlayer extends Player{Level world;UUID uuid;ProbePlayer(){super(null,new GameProfile(new UUID(0,1),"测试"));}@Override public Level level(){return world;}@Override public int getId(){return 17;}@Override public UUID getUUID(){return uuid;}@Override public net.minecraft.world.level.GameType gameMode(){return net.minecraft.world.level.GameType.SURVIVAL;}@Override public boolean isSpectator(){return false;}}
}
