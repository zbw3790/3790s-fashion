package vanillafashion.fashion;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;
import vanillafashion.cape.*;
import vanillafashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.fashion.FashionTestSupport.*;

class FullFashionPersistenceTest {
    @TempDir Path temp;
    @BeforeAll static void boot(){bootstrap();}
    static PlayerFashionStoredState mixed(){return new PlayerFashionStoredState(Optional.of(FOUNDER),OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.NONE).with(OutfitPart.RIGHT_ARM,OutfitPartSelection.outfit(new OutfitId("dormant_robe"))));}
    static PlayerFashionPersistence.LoadResult load(SavedDataStorage storage,Path directory){return PlayerFashionPersistence.load(storage,directory,NOPLogger.NOP_LOGGER);}
    Path frozen(Path directory) throws IOException {
        Path file=PlayerFashionPersistence.dataFile(directory);Files.createDirectories(file.getParent());
        try(var input=getClass().getResourceAsStream("/vanillafashion/fashion/frozen-v021-player-fashion.dat")){assertNotNull(input);Files.copy(input,file);}
        assertEquals("7ddc07fb644f4768a951fd67d321afe270134b4cc81cefaab07ff3cd66bcec54",CapeAssetHash.sha256(Files.readAllBytes(file)));return file;
    }
    @Test void frozenV1LoadsWithoutDirtyOrRewriteThenActualMutationWritesV2() throws Exception {
        Path directory=temp.resolve("data");Path file=frozen(directory);byte[] before=Files.readAllBytes(file);
        try(var storage=storage(directory)) {var loaded=load(storage,directory);assertTrue(loaded.authoritativeStateKnown());assertFalse(loaded.data().isDirty());assertEquals(OutfitSelections.original(),loaded.data().storedState(FIRST).outfit());assertEquals(Optional.of(FOUNDER),loaded.data().storedState(FIRST).cape());assertEquals(Optional.of(BUILDER),loaded.data().storedState(SECOND).cape());storage.saveAndJoin();}
        assertArrayEquals(before,Files.readAllBytes(file));
        try(var storage=storage(directory)) {var loaded=load(storage,directory);loaded.data().setState(FIRST,mixed());assertTrue(loaded.data().isDirty());storage.saveAndJoin();}
        var root=NbtIo.readCompressed(file,NbtAccounter.create(PlayerFashionPersistence.MAX_NBT_BYTES));assertEquals(2,root.getCompound("data").orElseThrow().getIntOr("schema_version",-1));
        try(var storage=storage(directory)){var loaded=load(storage,directory);assertEquals(mixed(),loaded.data().storedState(FIRST));assertEquals(Optional.of(BUILDER),loaded.data().storedState(SECOND).cape());assertFalse(loaded.data().isDirty());}
    }
    @Test void independentProcessReadsWrittenV2() throws Exception {
        String childFile=System.getProperty("vanillaFashion.s03RestartFile");
        if(childFile!=null){Path directory=Path.of(childFile);try(var storage=storage(directory)){var loaded=load(storage,directory);assertTrue(loaded.authoritativeStateKnown());assertEquals(mixed(),loaded.data().storedState(FIRST));assertFalse(loaded.data().isDirty());storage.saveAndJoin();}return;}
        Path directory=temp.resolve("restart/data");try(var storage=storage(directory)){var loaded=load(storage,directory);loaded.data().setState(FIRST,mixed());storage.saveAndJoin();}
        Path file=PlayerFashionPersistence.dataFile(directory);byte[] before=Files.readAllBytes(file);
        String classpath=System.getProperty("vanillaFashion.testRuntimeClasspath");assertNotNull(classpath);
        Path arguments=temp.resolve("restart.args");String content="-cp\n"+quote(classpath)+"\nvanillafashion.fashion.S03PersistenceSubprocess\n"+quote(directory.toString())+"\n";Files.writeString(arguments,content);
        Path output=temp.resolve("restart-output.txt");var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"@"+arguments).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean done=process.waitFor(60,TimeUnit.SECONDS);if(!done)process.destroyForcibly();assertTrue(done,"独立测试 JVM 超时。");assertEquals(0,process.exitValue(),()->read(output));assertArrayEquals(before,Files.readAllBytes(file));
    }
    static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    static String read(Path path){try{return Files.readString(path);}catch(IOException e){return "无法读取子进程诊断。";}}
    @Test void sparseDeterministicV2KeepsNoneAndDormantWithoutRegistryQueries() {
        var data=new PlayerFashionSavedData();data.setState(SECOND,mixed());data.setState(FIRST,mixed());
        var tag=(CompoundTag)PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow();
        assertEquals(Set.of("schema_version","entries"),tag.keySet());assertFalse(tag.toString().contains("revision"));var entries=tag.getList("entries").orElseThrow();assertEquals(2,entries.size());
        var entry=(CompoundTag)entries.get(0);assertEquals(FIRST.toString(),entry.getString("uuid").orElseThrow());assertEquals(2,entry.getList("outfit_parts").orElseThrow().size());
        var loaded=PlayerFashionSavedData.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();assertEquals(data.storedSnapshot(),loaded.storedSnapshot());assertFalse(loaded.isDirty());
        loaded.setState(FIRST,PlayerFashionStoredState.DEFAULT);assertEquals(1,loaded.size());
    }
    static CompoundTag validRoot(){var data=new PlayerFashionSavedData();data.setState(FIRST,mixed());data.setState(SECOND,PlayerFashionStoredState.DEFAULT.withCape(Optional.of(BUILDER)));var root=new CompoundTag();root.put("data",PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow());root.putInt("DataVersion",0);return root;}
    @ParameterizedTest @ValueSource(strings={"top_field","entry_field","part_field","duplicate_uuid","duplicate_part","part_type","parts_type","part_overflow","bad_part","original_mode","bad_mode","missing_id","none_with_id","bad_id","cape_type","bad_uuid","version","truncated"})
    void everyBadV2EntryProtectsEntireActualFileAndRejectsBothWriteRoutes(String damage) throws Exception {
        CompoundTag root=validRoot();var data=root.getCompound("data").orElseThrow();var entries=data.getList("entries").orElseThrow();var entry=(CompoundTag)entries.get(0);var parts=entry.getList("outfit_parts").orElseThrow();var part=(CompoundTag)parts.get(1);
        switch(damage){
            case "top_field"->data.putInt("extra",1);case "entry_field"->entry.putInt("extra",1);case "part_field"->part.putInt("extra",1);
            case "duplicate_uuid"->entries.add(entry.copy());case "duplicate_part"->parts.add(part.copy());case "part_type"->parts.set(1,StringTag.valueOf("坏部位"));case "parts_type"->entry.putString("outfit_parts","坏列表");
            case "part_overflow"->{while(parts.size()<7)parts.add(part.copy());}case "bad_part"->part.putString("part","tail");case "original_mode"->part.putString("mode","original");case "bad_mode"->part.putString("mode","custom");
            case "missing_id"->part.remove("outfit_id");case "none_with_id"->part.putString("mode","none");case "bad_id"->part.putString("outfit_id","../bad");case "cape_type"->entry.putInt("cape_id",1);case "bad_uuid"->entry.putString("uuid","1-1-1-1-1");case "version"->data.putInt("schema_version",3);case "truncated"->{}
            default->throw new AssertionError("未知坏文件测试。");
        }
        Path directory=temp.resolve("data");Path file=PlayerFashionPersistence.dataFile(directory);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);if(damage.equals("truncated"))Files.write(file,Arrays.copyOf(Files.readAllBytes(file),20));byte[] before=Files.readAllBytes(file);
        try(var storage=storage(directory)){var loaded=load(storage,directory);assertFalse(loaded.authoritativeStateKnown());assertTrue(loaded.degradedReason().isPresent());var service=new PlayerFashionService(loaded,valid(temp));Object connection=new Object();service.join(FIRST,connection,e->{});
            assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE,service.setSelection(FIRST,Optional.of(FOUNDER)));assertEquals(FullFashionSelectionStatus.READ_ONLY_PERSISTENCE,service.apply(FIRST,connection,true,0,mixed()).status());assertFalse(loaded.data().isDirty());storage.saveAndJoin();}
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void maximum16384ShapePassesActual64MiBBoundedLoaderAndOrdinarySave() throws Exception {
        var data=new PlayerFashionSavedData();String longest="a".repeat(64);var outfit=OutfitSelections.original();for(var part:OutfitPart.CANONICAL_ORDER)outfit=outfit.with(part,OutfitPartSelection.outfit(new OutfitId(longest)));
        var state=new PlayerFashionStoredState(Optional.of(new CapeId(longest)),outfit);for(int i=0;i<16384;i++)data.setState(new UUID(0,i),state);
        var root=new CompoundTag();root.put("data",PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow());root.putInt("DataVersion",0);
        Path directory=temp.resolve("maximum/data");Path file=PlayerFashionPersistence.dataFile(directory);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);
        var budget=NbtAccounter.create(PlayerFashionPersistence.MAX_NBT_BYTES);NbtIo.readCompressed(file,budget);assertEquals(62882276,budget.getUsage());assertEquals(67108864,PlayerFashionPersistence.MAX_NBT_BYTES);
        try(var storage=storage(directory)){var loaded=load(storage,directory);assertTrue(loaded.authoritativeStateKnown());assertEquals(16384,loaded.data().size());assertEquals(state,loaded.data().storedState(new UUID(0,16383)));assertFalse(loaded.data().isDirty());storage.saveAndJoin();}
    }
    @Test void oversizedCompressedNbtIsRejectedBeforeDecodeAndOriginalFileSurvives() throws Exception {
        var root=validRoot();root.putByteArray("oversize",new byte[(int)PlayerFashionPersistence.MAX_NBT_BYTES]);Path directory=temp.resolve("large/data");Path file=PlayerFashionPersistence.dataFile(directory);Files.createDirectories(file.getParent());NbtIo.writeCompressed(root,file);byte[] before=Files.readAllBytes(file);
        assertThrows(RuntimeException.class,()->NbtIo.readCompressed(file,NbtAccounter.create(PlayerFashionPersistence.MAX_NBT_BYTES)));
        try(var storage=storage(directory)){var loaded=load(storage,directory);assertFalse(loaded.authoritativeStateKnown());assertTrue(loaded.degradedReason().isPresent());assertFalse(loaded.data().isDirty());storage.saveAndJoin();}assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void productionBoundedReadNeverDelegatesToStorageGet() throws Exception {
        Path directory=temp.resolve("guard/data");frozen(directory);
        try(var storage=new SavedDataStorage(directory,net.minecraft.util.datafix.DataFixers.getDataFixer(),net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty())){
            @Override public <T extends net.minecraft.world.level.saveddata.SavedData>T get(net.minecraft.world.level.saveddata.SavedDataType<T> type){throw new AssertionError("禁止在有界读取后再次调用标准无界加载。");}
        }){assertTrue(load(storage,directory).authoritativeStateKnown());storage.saveAndJoin();}
    }
}
