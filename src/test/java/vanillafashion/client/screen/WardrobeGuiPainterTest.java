package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WardrobeGuiPainterTest {
	@Test
	void frameAndTabSubmitOnlyIntegerRectanglesInsideTheirSharedGeometry() {
		for (int width : new int[]{192, 211}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			Commands commands = new Commands();
			WardrobeGuiPainter.frameWithSelectedCapeTab(commands, layout);
			assertFalse(commands.rectangles.isEmpty());
			for (Rectangle rectangle : commands.rectangles) {
				assertTrue(rectangle.inside(layout.frameBounds()) || rectangle.inside(layout.tabBounds()));
				assertEquals(255, rectangle.color() >>> 24);
			}
		}
	}

	@Test
	void selectedJoinRemovesFrameTopLineWithoutHolesOrExtraEdges() {
		WardrobeLayout layout = WardrobeLayout.calculate(211, 214, 9);
		Commands commands = new Commands();
		WardrobeGuiPainter.frameWithSelectedCapeTab(commands, layout);
		int x = layout.tabJoinBounds().x(), y = layout.tabJoinBounds().y();
		for (int row = 0; row < 4; row++) {
			for (int column = 3; column < 29; column++) {
				assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(x + column, y + row));
			}
			assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(x, y + row));
			assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(x + 1, y + row));
		}
		assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(x + 31, y));
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(x + 31, y + 1));
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(x + 31, y + 2));
		assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(x + 31, y + 3));
		assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(x + 32, y));
	}

	@Test
	void stairCornersRemainTransparentAndBevelThicknessIsFixed() {
		WardrobeLayout layout = WardrobeLayout.calculate(211, 214, 9);
		Commands commands = new Commands();
		WardrobeGuiPainter.frameWithSelectedCapeTab(commands, layout);
		var tab = layout.tabBounds();
		var frame = layout.frameBounds();
		assertEquals(0, commands.colorAt(tab.x(), tab.y()));
		assertEquals(0, commands.colorAt(tab.x() + 1, tab.y()));
		assertEquals(0, commands.colorAt(frame.right() - 1, frame.y()));
		assertEquals(0, commands.colorAt(frame.x(), frame.bottom() - 1));
		assertEquals(0, commands.colorAt(frame.right() - 1, frame.bottom() - 1));
		assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(frame.x() + 40, frame.y()));
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(frame.x() + 40, frame.y() + 1));
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(frame.x() + 40, frame.y() + 2));
		assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(frame.x() + 40, frame.y() + 3));
		assertEquals(WardrobeGuiPainter.SHADOW_COLOR, commands.colorAt(frame.x() + 40, frame.bottom() - 3));
		assertEquals(WardrobeGuiPainter.SHADOW_COLOR, commands.colorAt(frame.x() + 40, frame.bottom() - 2));
	}

	@Test
	void slotKeepsOnePixelInsetWithNeutralCrossingCorners() {
		Commands commands = new Commands();
		var slot = new WardrobeLayout.Bounds(10, 20, 22, 34);
		WardrobeGuiPainter.slot(commands, slot);
		assertEquals(WardrobeGuiPainter.SLOT_SHADOW_COLOR, commands.colorAt(10, 20));
		assertEquals(WardrobeGuiPainter.SLOT_SHADOW_COLOR, commands.colorAt(30, 20));
		assertEquals(WardrobeGuiPainter.SLOT_SHADOW_COLOR, commands.colorAt(10, 52));
		assertEquals(WardrobeGuiPainter.SLOT_COLOR, commands.colorAt(31, 20));
		assertEquals(WardrobeGuiPainter.SLOT_COLOR, commands.colorAt(10, 53));
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(31, 53));
		assertEquals(WardrobeGuiPainter.SLOT_COLOR, commands.colorAt(11, 21));
		assertEquals(WardrobeGuiPainter.SLOT_COLOR, commands.colorAt(30, 52));
	}

	@Test
	void previewFrameHasSinglePixelRecessedEdgesAndOpaqueDarkGrayInterior() {
		for (int width : new int[]{192, 211}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			var outer = layout.previewBounds();
			var inner = layout.previewInnerBounds();
			Commands commands = new Commands();
			WardrobeGuiPainter.previewFrame(commands, layout);
			assertFalse(commands.rectangles.isEmpty());
			for (Rectangle rectangle : commands.rectangles) {
				assertTrue(rectangle.inside(outer));
				assertEquals(255, rectangle.color() >>> 24);
			}
			for (int y = inner.y(); y < inner.bottom(); y++) {
				for (int x = inner.x(); x < inner.right(); x++) {
					assertEquals(0xFF202020, commands.colorAt(x, y));
				}
				assertEquals(WardrobeGuiPainter.SLOT_SHADOW_COLOR, commands.colorAt(outer.x(), y));
				assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(outer.right() - 1, y));
			}
			for (int x = inner.x(); x < inner.right(); x++) {
				assertEquals(WardrobeGuiPainter.SLOT_SHADOW_COLOR, commands.colorAt(x, outer.y()));
				assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(x, outer.bottom() - 1));
			}
			assertEquals(WardrobeGuiPainter.SLOT_SHADOW_COLOR, commands.colorAt(outer.x(), outer.y()));
			assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(outer.right() - 1, outer.y()));
			assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(outer.x(), outer.bottom() - 1));
			assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR,
					commands.colorAt(outer.right() - 1, outer.bottom() - 1));
		}
	}

	@Test
	void previewFrameDoesNotDrawOutsideTooSmallViewport() {
		Commands commands = new Commands();
		WardrobeGuiPainter.previewFrame(commands, WardrobeLayout.calculate(1, 1, 9));
		assertTrue(commands.rectangles.isEmpty());
	}

	@Test
	void previewMessageColorHasStrongContrastAgainstOpaqueDarkGray() {
		assertEquals(0xFF202020, WardrobeGuiPainter.PREVIEW_BACKGROUND_COLOR);
		assertEquals(255, WardrobeGuiPainter.PREVIEW_TEXT_COLOR >>> 24);
		double contrast = (luminance(WardrobeGuiPainter.PREVIEW_TEXT_COLOR) + 0.05D)
				/ (luminance(WardrobeGuiPainter.PREVIEW_BACKGROUND_COLOR) + 0.05D);
		assertTrue(contrast >= 7.0D, "深灰底预览提示需要足够的明暗对比。");
	}

	@Test
	void elytraIconFitsFixedCanvasWithMirroredTaperedWings() {
		var icon = new WardrobeLayout.Bounds(20, 30, 16, 16);
		Commands commands = new Commands();
		WardrobeGuiPainter.elytraIcon(commands, icon);
		assertFalse(commands.rectangles.isEmpty());
		for (Rectangle rectangle : commands.rectangles) {
			assertTrue(rectangle.inside(icon));
			assertEquals(255, rectangle.color() >>> 24);
		}
		for (int y = 0; y < icon.height(); y++) {
			for (int x = 0; x < icon.width(); x++) {
				assertEquals(commands.colorAt(icon.x() + x, icon.y() + y),
						commands.colorAt(icon.right() - 1 - x, icon.y() + y));
			}
		}
		assertTrue(commands.colorAt(icon.x() + 2, icon.y() + 3) != 0);
		assertEquals(0, commands.colorAt(icon.x() + 2, icon.y() + 12));
		assertTrue(commands.colorAt(icon.x() + 6, icon.y() + 13) != 0);
		assertEquals(0, commands.colorAt(icon.x() + 7, icon.y() + 13));
		assertEquals(0, commands.colorAt(icon.x(), icon.y()));
	}

	@Test
	void hoverAndDisabledOverlaysNeverCoverSlotEdgeOrNeighbour() {
		var slot = new WardrobeLayout.Bounds(10, 20, 22, 34);
		for (boolean disabled : new boolean[]{false, true}) {
			Commands commands = new Commands();
			WardrobeGuiPainter.slotOverlay(commands, slot, true, false, disabled);
			assertEquals(1, commands.rectangles.size());
			assertEquals(new Rectangle(11, 21, 31, 53,
					disabled ? WardrobeGuiPainter.DISABLED_COLOR : WardrobeGuiPainter.HOVER_COLOR),
					commands.rectangles.getFirst());
		}
	}

	@Test
	void selectedOutlineIsOnePixelInsideSlotAndDrawnAfterOverlay() {
		var slot = new WardrobeLayout.Bounds(10, 20, 22, 34);
		Commands commands = new Commands();
		WardrobeGuiPainter.slotOverlay(commands, slot, true, true, false);
		assertEquals(WardrobeGuiPainter.HOVER_COLOR, commands.rectangles.getFirst().color());
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(11, 21));
		assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(30, 52));
		assertEquals(WardrobeGuiPainter.HOVER_COLOR, commands.colorAt(12, 22));
		assertEquals(0, commands.colorAt(10, 20));
	}

	@Test
	void normalSlotHasNoAdditionalOverlay() {
		Commands commands = new Commands();
		WardrobeGuiPainter.slotOverlay(commands, new WardrobeLayout.Bounds(0, 0, 22, 34), false, false, false);
		assertTrue(commands.rectangles.isEmpty());
	}

	@Test
	void originalCapeIconsKeepApprovedCanvasWithoutTextOrTextureInput() {
		var icon = new WardrobeLayout.Bounds(20, 30, 16, 16);
		Commands cape = new Commands();
		Commands original = new Commands();
		WardrobeGuiPainter.capeIcon(cape, icon);
		WardrobeGuiPainter.originalIcon(original, icon);
		for (Commands commands : List.of(cape, original)) {
			assertFalse(commands.rectangles.isEmpty());
			assertTrue(commands.rectangles.stream().allMatch(rectangle -> rectangle.inside(icon)));
		}
		assertTrue(cape.rectangles.stream().allMatch(rectangle ->
				rectangle.inside(new WardrobeLayout.Bounds(24, 32, 8, 13))));
		assertEquals(0, cape.colorAt(23, 32));
		assertEquals(0, cape.colorAt(32, 32));
		assertEquals(0, original.colorAt(20, 30));
		assertTrue(original.colorAt(20, 35) != 0);
	}

	@Test
	void iconsRejectResizeInsteadOfStretchingTheirPixels() {
		assertThrows(IllegalArgumentException.class, () -> WardrobeGuiPainter.capeIcon(new Commands(),
				new WardrobeLayout.Bounds(0, 0, 32, 32)));
		assertThrows(IllegalArgumentException.class, () -> WardrobeGuiPainter.originalIcon(new Commands(),
				new WardrobeLayout.Bounds(0, 0, 15, 16)));
		assertThrows(IllegalArgumentException.class, () -> WardrobeGuiPainter.elytraIcon(new Commands(),
				new WardrobeLayout.Bounds(0, 0, 18, 18)));
	}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"320,0", "320,1", "200,0", "200,1"})
    void bothTabsAndBothLayoutsHaveOneContinuousOuterBoundaryAndOpenSelectedSeam(int width, int selected) throws Exception {
        var layout = WardrobeLayout.calculate(width, 240, 9);
        var frame = layout.frameBounds(); var tab = layout.tabBounds(selected);
        var inactive = layout.tabBounds(1 - selected); var join = layout.tabJoinBounds(selected);
        Commands commands = new Commands(); WardrobeGuiPainter.frameWithTabs(commands, layout, selected);
        for (int y = layout.tabBounds(0).y() + 4; y < frame.bottom() - 4; y++) {
            assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(frame.x(), y), "左边框不能有缺口");
            assertEquals(selected == 1 && y == frame.y() ? WardrobeGuiPainter.OUTLINE_COLOR : WardrobeGuiPainter.HIGHLIGHT_COLOR,
                    commands.colorAt(frame.x() + 1, y), "除顶边交点外不能形成双黑边");
            assertEquals(0, commands.colorAt(frame.x() - 1, y), "不能突出一像素");
        }
        for (int x = frame.x(); x < frame.right() - 4; x++) {
            if (x < join.x() || x >= join.right()) {
                assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(x, frame.y()));
            }
        }
        for (int y = join.y(); y < join.bottom(); y++) for (int x = join.x() + 3; x < join.right() - 3; x++)
            assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(x, y));
        for (int x = inactive.x() + 3; x < inactive.right() - 1; x++) {
            assertEquals(WardrobeGuiPainter.SHADOW_COLOR, commands.colorAt(x, frame.y() - 2));
            assertEquals(WardrobeGuiPainter.SHADOW_COLOR, commands.colorAt(x, frame.y() - 1));
            assertEquals(WardrobeGuiPainter.OUTLINE_COLOR, commands.colorAt(x, frame.y()));
        }
        if (selected == 1) {
            for (int x = join.x(); x < join.right(); x++)
                assertEquals(WardrobeGuiPainter.FRAME_COLOR, commands.colorAt(x, join.bottom() - 1));
            assertEquals(WardrobeGuiPainter.HIGHLIGHT_COLOR, commands.colorAt(join.x(), join.y() + 1));
        }
        // 所有黑色外轮廓像素必须属于一个八连通分量，包含原版阶梯圆角。
        var black = new java.util.HashSet<List<Integer>>();
        for (int y = tab.y(); y < frame.bottom(); y++) for (int x = frame.x(); x < frame.right(); x++)
            if (commands.colorAt(x, y) == WardrobeGuiPainter.OUTLINE_COLOR) black.add(List.of(x, y));
        var queue = new java.util.ArrayDeque<List<Integer>>(); queue.add(black.iterator().next());
        var visited = new java.util.HashSet<List<Integer>>();
        while (!queue.isEmpty()) {
            var point = queue.removeFirst(); if (!visited.add(point)) continue;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                var next = List.of(point.get(0) + dx, point.get(1) + dy);
                if (black.contains(next) && !visited.contains(next)) queue.add(next);
            }
        }
        assertEquals(black, visited);
        // 仅保存矩形命令的内部位图，不捕获或启动 Minecraft。
        var image = new java.awt.image.BufferedImage(frame.width(), 40, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 40; y++) for (int x = 0; x < frame.width(); x++)
            image.setRGB(x, y, commands.colorAt(frame.x() + x, tab.y() + y));
        var folder = java.nio.file.Path.of("painter-regression"); java.nio.file.Files.createDirectories(folder);
        javax.imageio.ImageIO.write(image, "PNG", folder.resolve("frame-" + width + "-" + selected + ".png").toFile());
    }

    @Test
    void shirtIconIsSymmetricOpaquePixelArtUsingCapePaletteWithoutExtraSymbols() {
        var bounds = new WardrobeLayout.Bounds(0, 0, 16, 16);
        Commands shirt = new Commands(), cape = new Commands();
        WardrobeGuiPainter.outfitIcon(shirt, bounds); WardrobeGuiPainter.capeIcon(cape, bounds);
        var palette = new java.util.HashSet<Integer>(); cape.rectangles.forEach(rect -> palette.add(rect.color()));
        for (var rect : shirt.rectangles) { assertTrue(rect.inside(bounds)); assertTrue(palette.contains(rect.color())); }
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            assertEquals(shirt.colorAt(x, y), shirt.colorAt(15 - x, y));
            int alpha = shirt.colorAt(x, y) >>> 24; assertTrue(alpha == 0 || alpha == 255);
            if (x < 2 || x > 13 || y < 2 || y > 13) assertEquals(0, alpha);
        }
        assertTrue(shirt.colorAt(2, 5) != 0); assertTrue(shirt.colorAt(13, 5) != 0);
        assertEquals(0, shirt.colorAt(2, 9)); assertEquals(0, shirt.colorAt(13, 9));
        assertTrue(shirt.colorAt(4, 12) != 0); assertTrue(shirt.colorAt(11, 12) != 0);
        assertThrows(IllegalArgumentException.class, () -> WardrobeGuiPainter.outfitIcon(new Commands(),
                new WardrobeLayout.Bounds(0, 0, 32, 32)));
        Commands again = new Commands(); WardrobeGuiPainter.outfitIcon(again, bounds);
        assertEquals(shirt.rectangles, again.rectangles);
    }

	private static double luminance(int color) {
		return 0.2126D * linearChannel((color >>> 16) & 0xFF)
				+ 0.7152D * linearChannel((color >>> 8) & 0xFF)
				+ 0.0722D * linearChannel(color & 0xFF);
	}

	private static double linearChannel(int channel) {
		double normalized = channel / 255.0D;
		return normalized <= 0.04045D ? normalized / 12.92D
				: Math.pow((normalized + 0.055D) / 1.055D, 2.4D);
	}

	private record Rectangle(int left, int top, int right, int bottom, int color) {
		boolean inside(WardrobeLayout.Bounds bounds) {
			return left >= bounds.x() && top >= bounds.y() && right <= bounds.right() && bottom <= bounds.bottom();
		}
	}

	/** 只记录 GUI 几何提交，不创建截图或模拟 Runtime Renderer。 */
	private static final class Commands implements WardrobeGuiPainter.RectangleSink {
		private final List<Rectangle> rectangles = new ArrayList<>();
		@Override
		public void fill(int left, int top, int right, int bottom, int color) {
			assertTrue(right > left);
			assertTrue(bottom > top);
			rectangles.add(new Rectangle(left, top, right, bottom, color));
		}
		int colorAt(int x, int y) {
			int color = 0;
			for (Rectangle rectangle : rectangles) {
				if (x >= rectangle.left() && x < rectangle.right() && y >= rectangle.top() && y < rectangle.bottom()) {
					color = rectangle.color();
				}
			}
			return color;
		}
	}
}
