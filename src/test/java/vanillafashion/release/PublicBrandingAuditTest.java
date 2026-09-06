package vanillafashion.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class PublicBrandingAuditTest {
    @Test
    void repositoryUsesFrozenMitLicense() throws IOException {
        String license = Files.readString(root().resolve("LICENSE"));
        assertTrue(license.startsWith("MIT License\n\nCopyright (c) 2026 3790\n"));
        assertTrue(license.contains("Permission is hereby granted, free of charge"));
        assertTrue(license.endsWith("SOFTWARE.\n"));
    }

    @Test
    void generatedLogosAndMetadataIconAreValidAndConsistent() throws IOException {
        Path project = root();
        Path logo = project.resolve("branding/logo.png");
        Path smallLogo = project.resolve("branding/logo-128.png");
        Path icon = project.resolve("src/main/resources/assets/vanilla_fashion/icon.png");

        assertDimensions(logo, 512);
        assertDimensions(smallLogo, 128);
        assertArrayEquals(Files.readAllBytes(smallLogo), Files.readAllBytes(icon));
    }

    @Test
    void publicMetadataKeepsStableRuntimeIdentifiers() throws IOException {
        Path project = root();
        String metadata = Files.readString(project.resolve("src/main/resources/fabric.mod.json"));
        String initializer = Files.readString(
                project.resolve("src/main/java/vanillafashion/VanillaFashion.java"));
        String savedData = Files.readString(project.resolve(
                "src/main/java/vanillafashion/fashion/PlayerFashionSavedData.java"));
        String lifecycle = Files.readString(project.resolve(
                "src/main/java/vanillafashion/fashion/PlayerFashionLifecycle.java"));

        assertTrue(metadata.contains("3790's Vanilla Style Fashion"));
        assertTrue(metadata.contains("vanilla_fashion"));
        assertTrue(metadata.contains("assets/vanilla_fashion/icon.png"));
        assertTrue(initializer.contains("MOD_ID = \"vanilla_fashion\""));
        assertTrue(savedData.contains("\"vanilla_fashion\", \"player_fashion\""));
        assertTrue(lifecycle.contains("resolve(\"vanilla-fashion/capes\")"));
    }

    private static void assertDimensions(Path path, int expected) throws IOException {
        assertTrue(Files.isRegularFile(path), () -> "缺少生成 Logo：" + path);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, () -> "无法解码生成 Logo：" + path);
        assertEquals(expected, image.getWidth());
        assertEquals(expected, image.getHeight());
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
