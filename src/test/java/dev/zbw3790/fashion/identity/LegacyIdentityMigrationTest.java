package dev.zbw3790.fashion.identity;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIdentityMigrationTest {
    @TempDir Path config;
    Path legacy() { return config.resolve("vanilla-fashion"); }
    Path current() { return config.resolve("3790s-fashion"); }
    @Test void absentOldDirectoryDoesNotInventAssets() throws Exception {
        assertEquals(current(), LegacyIdentityMigration.configRoot(config));
        assertFalse(Files.exists(current()));
    }
    @Test void copiesAllUserFilesAndRetainsOriginalWithIdempotentNewPrecedence() throws Exception {
        Path cape=legacy().resolve("capes/private/cape.png"), outfit=legacy().resolve("outfits/mine/slim.png");
        Files.createDirectories(cape.getParent());Files.createDirectories(outfit.getParent());
        Files.write(cape,new byte[]{1,2,3});Files.write(outfit,new byte[]{4,5});
        Files.writeString(legacy().resolve("用户笔记.txt"),"全部保留");
        assertEquals(current(),LegacyIdentityMigration.configRoot(config));
        assertArrayEquals(Files.readAllBytes(cape),Files.readAllBytes(current().resolve("capes/private/cape.png")));
        assertArrayEquals(Files.readAllBytes(outfit),Files.readAllBytes(current().resolve("outfits/mine/slim.png")));
        assertEquals("全部保留",Files.readString(current().resolve("用户笔记.txt")));
        Files.writeString(current().resolve("用户笔记.txt"),"新内容");
        LegacyIdentityMigration.configRoot(config);
        assertEquals("新内容",Files.readString(current().resolve("用户笔记.txt")));
        assertEquals("全部保留",Files.readString(legacy().resolve("用户笔记.txt")));
        try(var paths=Files.list(config)){assertEquals(2,paths.count());}
    }
    @Test void existingEmptyCanonicalIsIntentionalAndDoesNotMergeOldAssets() throws Exception {
        Files.createDirectories(current());Files.createDirectories(legacy());Files.writeString(legacy().resolve("old.txt"),"旧");
        LegacyIdentityMigration.configRoot(config);assertFalse(Files.exists(current().resolve("old.txt")));
    }
    @Test void malformedOldRootDoesNotPublishAnEmptyNewDirectory() throws Exception {
        Files.writeString(legacy(),"不是目录");assertThrows(java.io.IOException.class,()->LegacyIdentityMigration.configRoot(config));
        assertFalse(Files.exists(current()));assertEquals("不是目录",Files.readString(legacy()));
    }
    @Test void malformedCanonicalNeverFallsBackToLegacy() throws Exception {
        Files.createDirectory(legacy());Files.writeString(current(),"不能覆盖");
        assertThrows(java.io.IOException.class,()->LegacyIdentityMigration.configRoot(config));
        assertEquals("不能覆盖",Files.readString(current()));
    }
    @Test void validatedFilePublicationNeverOverwritesTarget() throws Exception {
        Path target=config.resolve("data/current.dat");LegacyIdentityMigration.publishValidatedFile(target,new byte[]{1,2});
        assertThrows(FileAlreadyExistsException.class,()->LegacyIdentityMigration.publishValidatedFile(target,new byte[]{3}));
        assertArrayEquals(new byte[]{1,2},Files.readAllBytes(target));
    }
}
