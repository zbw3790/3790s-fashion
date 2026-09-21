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
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.cape.CapeId;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorCustomPersistenceTest {
    @TempDir Path temp;
    @BeforeAll static void boot(){bootstrap();}
    @ParameterizedTest @ValueSource(ints={0,1,3,5,10,15}) void schemaThreeKeepsOriginalMaskWithoutDirty(int mask) throws Exception {
        var root=FullFashionPersistenceTest.validRoot();var tag=root.getCompound("data").orElseThrow();tag.putInt("schema_version",3);
        ((CompoundTag)tag.getList("entries").orElseThrow().getFirst()).putByte("armor_hidden",(byte)mask);
        var file=PlayerFashionPersistence.dataFile(temp);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);byte[] before=Files.readAllBytes(file);
        try(var storage=storage(temp)){var loaded=PlayerFashionPersistence.load(storage,temp,NOPLogger.NOP_LOGGER);assertTrue(loaded.authoritativeStateKnown());assertEquals(mask,loaded.data().storedState(FIRST).armor().hiddenMask());assertFalse(loaded.data().isDirty());storage.saveAndJoin();}
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void actualMaximumCustomBudgetAndStoredDormantRoundtrip() throws Exception {
        var armor=ArmorSelections.original();for(var slot:ArmorSlot.CANONICAL_ORDER)armor=armor.with(slot,ArmorSelection.custom(new ArmorStyleId("a".repeat(32))));
        var outfit=OutfitSelections.original();for(var part:OutfitPart.CANONICAL_ORDER)outfit=outfit.with(part,OutfitPartSelection.outfit(new OutfitId("b".repeat(64))));
        var state=new PlayerFashionStoredState(Optional.of(new CapeId("c".repeat(64))),outfit,armor);var data=new PlayerFashionSavedData();
        for(int i=0;i<16384;i++)data.setState(new UUID(0,i),state);
        var encoded=PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow();var root=new CompoundTag();root.put("data",encoded);root.putInt("DataVersion",0);
        var file=PlayerFashionPersistence.dataFile(temp);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);
        var budget=NbtAccounter.create(67108864);NbtIo.readCompressed(file,budget);assertTrue(budget.getUsage()<67108864);
        System.out.println("schema 4 最长合法 16384 条记录 NbtAccounter 字节="+budget.getUsage());
        try(var storage=storage(temp)){var loaded=PlayerFashionPersistence.load(storage,temp,NOPLogger.NOP_LOGGER);assertTrue(loaded.authoritativeStateKnown());assertEquals(16384,loaded.data().size());assertEquals(state,loaded.data().storedState(new UUID(0,16383)));assertFalse(loaded.data().isDirty());storage.saveAndJoin();}
    }
    @ParameterizedTest @ValueSource(strings={"mode","truncated","trailing","type","legacy_field","future"}) void malformedV4ProtectsEntireFile(String kind)throws Exception {
        var root=FullFashionPersistenceTest.validRoot();var tag=root.getCompound("data").orElseThrow();var entry=(CompoundTag)tag.getList("entries").orElseThrow().getFirst();
        switch(kind){case "mode"->entry.putByteArray("armor",new byte[]{3});case "truncated"->entry.putByteArray("armor",new byte[]{2,2,'a'});case "trailing"->entry.putByteArray("armor",new byte[]{0,0});case "type"->entry.putString("armor","bad");case "legacy_field"->entry.putByte("armor_hidden",(byte)1);case "future"->tag.putInt("schema_version",5);default->throw new AssertionError();}
        var file=PlayerFashionPersistence.dataFile(temp);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);byte[] before=Files.readAllBytes(file);
        try(var storage=storage(temp)){var loaded=PlayerFashionPersistence.load(storage,temp,NOPLogger.NOP_LOGGER);assertFalse(loaded.authoritativeStateKnown());assertTrue(loaded.degradedReason().isPresent());storage.saveAndJoin();}
        assertArrayEquals(before,Files.readAllBytes(file));
    }
}
