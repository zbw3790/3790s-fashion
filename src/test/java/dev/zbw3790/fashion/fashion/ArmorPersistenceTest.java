package dev.zbw3790.fashion.fashion;
import java.util.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.armor.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;
class ArmorPersistenceTest {
 @TempDir Path temp;
 @BeforeAll static void boot(){bootstrap();}
 @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
 void actualSaveReloadPreservesAllMasksAndOfflinePlayer(int mask) throws Exception {
  var state=FullFashionPersistenceTest.mixed().withArmor(new ArmorSelections(mask));
  try(var storage=storage(temp)) {var data=PlayerFashionPersistence.load(storage,temp,NOPLogger.NOP_LOGGER).data();data.setState(FIRST,state);data.setState(SECOND,FullFashionPersistenceTest.mixed().withArmor(new ArmorSelections(15)));storage.saveAndJoin();}
  byte[] before=Files.readAllBytes(PlayerFashionPersistence.dataFile(temp));
  var root=NbtIo.readCompressed(PlayerFashionPersistence.dataFile(temp),NbtAccounter.create(PlayerFashionPersistence.MAX_NBT_BYTES));assertEquals(4,root.getCompound("data").orElseThrow().getIntOr("schema_version",-1));
  try(var storage=storage(temp)) {var loaded=PlayerFashionPersistence.load(storage,temp,NOPLogger.NOP_LOGGER);assertTrue(loaded.authoritativeStateKnown());assertEquals(state,loaded.data().storedState(FIRST));assertEquals(15,loaded.data().storedState(SECOND).armor().hiddenMask());assertFalse(loaded.data().isDirty());storage.saveAndJoin();}
  assertArrayEquals(before,Files.readAllBytes(PlayerFashionPersistence.dataFile(temp)));
 }
 @Test void genuineSchemaTwoDefaultsArmorWithoutDirtyAndPreservesDormant() {
  var tag=FullFashionPersistenceTest.validRoot().getCompound("data").orElseThrow();tag.putInt("schema_version",2);
  var data=PlayerFashionSavedData.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();assertEquals(FullFashionPersistenceTest.mixed(),data.storedState(FIRST));assertEquals(0,data.storedState(FIRST).armor().hiddenMask());assertFalse(data.isDirty());
 }
 @ParameterizedTest @ValueSource(strings={"high","negative","type","custom","future","schema2"})
 void badArmorCannotOverwriteFile(String kind) throws Exception {
  var root=FullFashionPersistenceTest.validRoot();var tag=root.getCompound("data").orElseThrow();var e=(CompoundTag)tag.getList("entries").orElseThrow().get(0);
  switch(kind){case "high"->e.putByte("armor_hidden",(byte)16);case "negative"->e.putByte("armor_hidden",(byte)-1);case "type"->e.putInt("armor_hidden",1);case "custom"->e.putString("armor_hidden","custom");case "future"->tag.putInt("schema_version",5);case "schema2"->{tag.putInt("schema_version",2);e.putByte("armor_hidden",(byte)1);}default->throw new AssertionError();}
  var file=PlayerFashionPersistence.dataFile(temp);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);byte[] before=Files.readAllBytes(file);
  try(var storage=storage(temp)){var loaded=PlayerFashionPersistence.load(storage,temp,NOPLogger.NOP_LOGGER);assertFalse(loaded.authoritativeStateKnown());assertTrue(loaded.degradedReason().isPresent());assertNull(storage.get(PlayerFashionSavedData.TYPE));storage.saveAndJoin();}
  assertArrayEquals(before,Files.readAllBytes(file));
 }
 @Test void allHiddenWithoutEquipmentStillPersistsAndOriginalRemovesDefaultEntry() {
  var data=new PlayerFashionSavedData();assertTrue(data.setState(FIRST,PlayerFashionStoredState.DEFAULT.withArmor(new ArmorSelections(15))));assertEquals(1,data.size());assertTrue(data.setState(FIRST,PlayerFashionStoredState.DEFAULT));assertEquals(0,data.size());
 }
}
