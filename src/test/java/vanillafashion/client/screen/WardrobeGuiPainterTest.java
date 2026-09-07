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
