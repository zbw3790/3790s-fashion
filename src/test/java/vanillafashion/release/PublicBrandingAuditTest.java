package vanillafashion.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

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
    void generatedLogosAndMetadataIconAreValidAndConsistent() throws Exception {
        Path project = root();
        Path logo = project.resolve("branding/logo.png");
        Path mediumLogo = project.resolve("branding/logo-256.png");
        Path smallLogo = project.resolve("branding/logo-128.png");
        Path icon = project.resolve("src/main/resources/assets/vanilla_fashion/icon.png");

        assertDimensions(logo, 512);
        assertDimensions(mediumLogo, 256);
        assertDimensions(smallLogo, 128);
        assertEquals("51d51b580197812431141bea66e82546bd902c608f10a61b7cf8b89923c2647a", sha256(logo));
        assertEquals("b591dac8fb7631fdbf985f9bc9d191f60503f7b319c0808ac8474cad4e558966", sha256(mediumLogo));
        assertEquals("05d31278fd2e98a28fa1f5879e29ea280af846853ed86e7c03ccf7a2e79f2cfd", sha256(smallLogo));
        Path tinyLogo = project.resolve("branding/logo-64.png");
        assertDimensions(tinyLogo, 64);
        assertEquals("59232a4adb2f934d46cd6e28985cf72db29fe04848c1eafdf7ebe873c461ebb5", sha256(tinyLogo));
        assertArrayEquals(Files.readAllBytes(smallLogo), Files.readAllBytes(icon));
        BufferedImage canonical = ImageIO.read(logo.toFile());
        assertEquals(0xFF3790FF, canonical.getRGB(256, 256));
        assertEquals(0xFFFFFFFF, canonical.getRGB(256, 416));
        assertEquals(0xFF26292B, canonical.getRGB(0, 0));
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

    @Test
    void brandingNoticeSeparatesThirdPartyAssetsFromCodeLicense() throws IOException {
        String notice = Files.readString(root().resolve("branding/NOTICE.md"));
        assertTrue(notice.contains("当前 Logo 不再嵌入 Mojang Armor Stand sprite"));
        assertTrue(notice.contains("数字仅由原创七段矩形绘制"));
        assertTrue(notice.contains("#3790FF"));
        assertTrue(notice.contains("历史品牌"));
        assertTrue(notice.contains("Minecraft Java Edition 26.2"));
        assertTrue(notice.contains("assets/minecraft/textures/item/armor_stand.png"));
        assertTrue(notice.contains("assets/minecraft/textures/font/ascii.png"));
        assertTrue(notice.contains("MIT 声明不授予第三方素材的权利"));
        assertTrue(notice.contains("不代表取得新的授权"));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
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
