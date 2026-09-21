package dev.zbw3790.fashion.fashion;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;

class IdentityPersistenceMigrationTest {
    @TempDir Path directory;
    @BeforeAll static void boot(){bootstrap();}
    Path legacy(){return directory.resolve("vanilla_fashion/player_fashion.dat");}
    Path current(){return PlayerFashionPersistence.dataFile(directory);}
    byte[] writeLegacy(int version) throws Exception {
        Files.createDirectories(legacy().getParent());
        if(version==1){try(var input=getClass().getResourceAsStream("/dev/zbw3790/fashion/fashion/frozen-v021-player-fashion.dat")){assertNotNull(input);Files.copy(input,legacy());}}
        else {var root=FullFashionPersistenceTest.validRoot();root.getCompound("data").orElseThrow().putInt("schema_version",2);NbtIo.writeCompressed(root,legacy());}
        return Files.readAllBytes(legacy());
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void oldWorldIsCopiedOnceAndOnlyCanonicalStateIsRegistered(int version) throws Exception {
        byte[] original=writeLegacy(version);
        Map<UUID,PlayerFashionStoredState> expected;
        try(var storage=storage(directory)){
            var loaded=PlayerFashionPersistence.load(storage,directory,NOPLogger.NOP_LOGGER);
            assertTrue(loaded.authoritativeStateKnown());assertTrue(loaded.degradedReason().isEmpty());
            expected=loaded.data().storedSnapshot();assertEquals(2,expected.size());assertFalse(loaded.data().isDirty());
            if(version==2)assertEquals(FullFashionPersistenceTest.mixed(),loaded.data().storedState(FIRST));
            assertSame(loaded.data(),storage.get(PlayerFashionSavedData.TYPE));storage.saveAndJoin();
        }
        assertArrayEquals(original,Files.readAllBytes(legacy()));assertArrayEquals(original,Files.readAllBytes(current()));
        // 旧副本之后损坏也不影响新位置的唯一权威值。
        Files.write(legacy(),new byte[]{0});
        try(var storage=storage(directory)){
            var loaded=PlayerFashionPersistence.load(storage,directory,NOPLogger.NOP_LOGGER);
            assertEquals(expected,loaded.data().storedSnapshot());assertFalse(loaded.data().isDirty());
            loaded.data().setState(FIRST,PlayerFashionStoredState.DEFAULT);storage.saveAndJoin();
        }
        assertArrayEquals(new byte[]{0},Files.readAllBytes(legacy()));
        var saved=NbtIo.readCompressed(current(),NbtAccounter.create(PlayerFashionPersistence.MAX_NBT_BYTES));
        assertEquals(4,saved.getCompound("data").orElseThrow().getIntOr("schema_version",-1));
        try(var storage=storage(directory)){
            var loaded=PlayerFashionPersistence.load(storage,directory,NOPLogger.NOP_LOGGER);
            assertEquals(PlayerFashionStoredState.DEFAULT,loaded.data().storedState(FIRST));
            assertEquals(expected.get(SECOND),loaded.data().storedState(SECOND));
        }
    }
    @ParameterizedTest @ValueSource(strings={"garbage","schema","duplicate","directory"})
    void invalidLegacyNeverCreatesCanonicalOrBlankAuthority(String damage) throws Exception {
        writeLegacy(2);
        if(damage.equals("directory")){Files.delete(legacy());Files.createDirectory(legacy());}
        else if(damage.equals("garbage"))Files.write(legacy(),new byte[]{0});
        else {
            var root=FullFashionPersistenceTest.validRoot();var data=root.getCompound("data").orElseThrow();
            if(damage.equals("schema"))data.putInt("schema_version",5);
            else {var entries=data.getList("entries").orElseThrow();entries.add(entries.get(0).copy());}
            NbtIo.writeCompressed(root,legacy());
        }
        byte[] before=Files.isRegularFile(legacy())?Files.readAllBytes(legacy()):null;
        try(var storage=storage(directory)){
            var loaded=PlayerFashionPersistence.load(storage,directory,NOPLogger.NOP_LOGGER);
            assertFalse(loaded.authoritativeStateKnown());assertTrue(loaded.degradedReason().isPresent());storage.saveAndJoin();
        }
        assertFalse(Files.exists(current()));if(before!=null)assertArrayEquals(before,Files.readAllBytes(legacy()));
    }
    @Test void badNewFileDoesNotReadGoodLegacyOrOverwriteEither() throws Exception {
        byte[] old=writeLegacy(2);Files.createDirectories(current().getParent());Files.write(current(),new byte[]{0});
        try(var storage=storage(directory)){
            assertFalse(PlayerFashionPersistence.load(storage,directory,NOPLogger.NOP_LOGGER).authoritativeStateKnown());storage.saveAndJoin();
        }
        assertArrayEquals(old,Files.readAllBytes(legacy()));assertArrayEquals(new byte[]{0},Files.readAllBytes(current()));
    }
}
