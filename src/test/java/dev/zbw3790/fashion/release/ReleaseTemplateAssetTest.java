package dev.zbw3790.fashion.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.zbw3790.fashion.cape.CapeCosmeticDefinition;
import dev.zbw3790.fashion.cape.CapeCosmeticValidationResult;
import dev.zbw3790.fashion.cape.CapeCosmeticValidator;
import dev.zbw3790.fashion.cape.CapeTextureAsset;

class ReleaseTemplateAssetTest {
    private static final String BLUE_HASH =
            "4f610799b1774c10bb9b19b757786c0ad0c501d2d783e63d5d0ff95f199a98d5";
    private static final String ROSE_HASH =
            "9b34af9ffea7732979d86f83956ba4e2798b5809685512a4834e5d16e4d075f9";

    private final CapeCosmeticValidator validator = new CapeCosmeticValidator();

    @Test
    void blueMigratorIsUnchangedValidSharedTemplate() throws IOException {
        Path directory = template("blue-migrator");
        CapeCosmeticDefinition definition = requireDefinition(directory);
        CapeTextureAsset cape = definition.cape();
        CapeTextureAsset elytra = definition.elytra().orElseThrow();

        assertEquals("blue-migrator", definition.id().value());
        assertEquals(List.of("cape_elytra.png"), directNames(directory));
        assertEquals("cape_elytra.png", cape.source().getFileName().toString());
        assertEquals(cape.source(), elytra.source());
        assertEquals(BLUE_HASH, cape.sha256());
        assertEquals(BLUE_HASH, elytra.sha256());
        assertAsset(cape.source(), 1189);
        assertEquals(64, cape.width());
        assertEquals(32, cape.height());
    }

    @Test
    void roseRedMigratorIsUnchangedValidSplitTemplate() throws IOException {
        Path directory = template("rose-red-migrator");
        CapeCosmeticDefinition definition = requireDefinition(directory);
        CapeTextureAsset cape = definition.cape();
        CapeTextureAsset elytra = definition.elytra().orElseThrow();

        assertEquals("rose-red-migrator", definition.id().value());
        assertEquals(List.of("cape.png", "elytra.png"), directNames(directory));
        assertEquals("cape.png", cape.source().getFileName().toString());
        assertEquals("elytra.png", elytra.source().getFileName().toString());
        assertEquals(ROSE_HASH, cape.sha256());
        assertEquals(ROSE_HASH, elytra.sha256());
        assertAsset(cape.source(), 1206);
        assertAsset(elytra.source(), 1206);
        assertEquals(64, cape.width());
        assertEquals(32, elytra.height());
    }

    private CapeCosmeticDefinition requireDefinition(Path directory) {
        CapeCosmeticValidationResult result = validator.validate(directory);
        assertTrue(result.isSuccess(), () -> "发布模板必须通过正式 Validator：" + result.issues());
        return result.definition().orElseThrow();
    }

    private static void assertAsset(Path path, long expectedSize) throws IOException {
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        assertEquals(expectedSize, Files.size(path));
    }

    private static List<String> directNames(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static Path template(String id) {
        Path project = root();
        Path internalTemplates = project.resolve("dev-assets/capes");
        Path templateRoot = Files.isDirectory(internalTemplates)
                ? internalTemplates
                : project.resolve("templates/capes");
        return templateRoot.resolve(id);
    }

    private static Path root() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath(); path != null;
                path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) {
                return path;
            }
        }
        throw new IllegalStateException("无法从测试工作目录定位项目根目录。");
    }
}
