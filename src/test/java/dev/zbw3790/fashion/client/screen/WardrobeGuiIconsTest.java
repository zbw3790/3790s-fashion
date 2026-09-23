package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WardrobeGuiIconsTest {
    @ParameterizedTest
    @CsvSource({
        "cape-tag-16.png,8408d5c003bec5549ae26bfc3c5d527736c43107edf3e4f3d36646e1358e4d94",
        "outfit-tag-16.png,2ad9ccfa8cd4b20b0ccb7c1c369862e419be3bdc65115af1159bfc1bb73062f2",
        "armor-tag-16.png,0cc2168fd9b47529dde4ed9648aeea5d74510545c00c9df608986accf9f9985c",
        "elytra-switch-button-16.png,d72c39f44c95ba6b32d6fb1bdfa9e800dc3246a92039d9c847e02452bc9e006b"
    })
    void classpathResourcesAreApprovedUnmodifiedRgbaPngs(String name, String sha) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/fashion_3790/textures/gui/icons/" + name)) {
            assertNotNull(stream);
            byte[] data = stream.readAllBytes();
            assertEquals(sha, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
            assertEquals(8, data[24]);
            assertEquals(6, data[25]);
            var image = ImageIO.read(new ByteArrayInputStream(data));
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            var alpha = new java.util.HashSet<Integer>();
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) alpha.add(image.getRGB(x, y) >>> 24);
            assertEquals(Set.of(0, 255), alpha);
        }
    }

    @Test
    void fiveUsesHaveExactlyFourResourcesAndCapeUsesSameIdentifierInstance() {
        assertSame(WardrobeGuiIcons.CAPE, WardrobeGuiIcons.tabTexture(WardrobeScreen.SelectedTab.CAPE));
        assertSame(WardrobeGuiIcons.CAPE, WardrobeGuiIcons.previewTexture(WardrobePreviewMode.CAPE));
        assertSame(WardrobeGuiIcons.OUTFIT, WardrobeGuiIcons.tabTexture(WardrobeScreen.SelectedTab.OUTFIT));
        assertSame(WardrobeGuiIcons.ARMOR, WardrobeGuiIcons.tabTexture(WardrobeScreen.SelectedTab.ARMOR));
        assertSame(WardrobeGuiIcons.ELYTRA, WardrobeGuiIcons.previewTexture(WardrobePreviewMode.ELYTRA));
        assertEquals(4, Set.of(WardrobeGuiIcons.CAPE, WardrobeGuiIcons.OUTFIT, WardrobeGuiIcons.ARMOR, WardrobeGuiIcons.ELYTRA).size());
    }

    @ParameterizedTest
    @CsvSource({"192,214", "200,240", "211,214", "320,240", "640,480"})
    void allAnchorsKeepFullSixteenPixelCanvasWithinControls(int width, int height) {
        var layout = WardrobeLayout.calculate(width, height, 9);
        assertTrue(layout.fitsScreen());
        for (var tab : WardrobeScreen.SelectedTab.values()) {
            var control = layout.tabBounds(tab.ordinal());
            var icon = layout.tabIconBounds(tab.ordinal());
            assertEquals(16, icon.width()); assertEquals(16, icon.height());
            assertEquals(control.x() + 8, icon.x());
            assertTrue(icon.x() >= control.x() && icon.right() <= control.right());
            assertTrue(icon.y() >= control.y() && icon.bottom() <= control.bottom());
        }
        var control = layout.previewModeButtonBounds();
        assertEquals(18, control.width()); assertEquals(18, control.height());
    }

    @Test
    void invalidCanvasIsRejectedBeforeRendering() {
        assertThrows(IllegalArgumentException.class, () -> WardrobeGuiIcons.draw(null, WardrobeGuiIcons.CAPE,
                new WardrobeLayout.Bounds(0, 0, 32, 32)));
        assertThrows(IllegalArgumentException.class, () -> WardrobeGuiIcons.draw(null, WardrobeGuiIcons.ELYTRA,
                new WardrobeLayout.Bounds(0, 0, 15, 16)));
    }

    @Test
    void tabBackgroundContainsNoLegacyIconColorsForAnySelectedTab() {
        var layout = WardrobeLayout.calculate(320, 240, 9);
        var neutral = Set.of(WardrobeGuiPainter.FRAME_COLOR, WardrobeGuiPainter.OUTLINE_COLOR,
                WardrobeGuiPainter.HIGHLIGHT_COLOR, WardrobeGuiPainter.SHADOW_COLOR);
        for (var selected : WardrobeScreen.SelectedTab.values()) {
            WardrobeGuiPainter.frameWithTabs((left, top, right, bottom, color) -> assertTrue(neutral.contains(color)),
                    layout, selected.ordinal());
        }
    }
}
