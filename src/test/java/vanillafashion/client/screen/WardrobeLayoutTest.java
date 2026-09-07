package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;

class WardrobeLayoutTest {
	@Test
	void standardLayoutMatchesApprovedCoordinates() {
		WardrobeLayout layout = WardrobeLayout.calculate(211, 214, 9);
		assertEquals(WardrobeLayout.Mode.STANDARD, layout.mode());
		assertEquals(new WardrobeLayout.Bounds(8, 36, 195, 170), layout.frameBounds());
		assertRelative(layout, layout.previewBounds(), 8, 18, 81, 102);
		assertRelative(layout, layout.gridBounds(), 99, 18, 88, 102);
		assertRelative(layout, layout.previousPageButtonBounds(), 99, 122, 12, 17);
		assertRelative(layout, layout.nextPageButtonBounds(), 175, 122, 12, 17);
		assertRelative(layout, layout.statusBounds(), 8, 144, 115, 20);
		assertRelative(layout, layout.applyButtonBounds(), 131, 142, 56, 20);
		assertEquals(34, layout.previewEntitySize());
	}

	@Test
	void compactLayoutMatchesApprovedCoordinatesWithoutShrinkingControls() {
		WardrobeLayout layout = WardrobeLayout.calculate(192, 214, 9);
		assertEquals(WardrobeLayout.Mode.COMPACT, layout.mode());
		assertEquals(new WardrobeLayout.Bounds(8, 36, 176, 170), layout.frameBounds());
		assertRelative(layout, layout.previewBounds(), 8, 18, 62, 102);
		assertRelative(layout, layout.gridBounds(), 80, 18, 88, 102);
		assertRelative(layout, layout.previousPageButtonBounds(), 80, 122, 12, 17);
		assertRelative(layout, layout.nextPageButtonBounds(), 156, 122, 12, 17);
		assertRelative(layout, layout.statusBounds(), 8, 144, 96, 20);
		assertRelative(layout, layout.applyButtonBounds(), 112, 142, 56, 20);
		assertEquals(32, layout.previewEntitySize());
	}

	@Test
	void previewInsetKeepsModelAndModeButtonInsideFrozenOuterFrame() {
		for (int width : new int[]{192, 211, 320, 854}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			var outer = layout.previewBounds();
			var inner = layout.previewInnerBounds();
			var model = layout.previewModelBounds();
			var button = layout.previewModeButtonBounds();
			var icon = layout.previewModeIconBounds();
			assertEquals(new WardrobeLayout.Bounds(outer.x() + 1, outer.y() + 1,
					outer.width() - 2, 100), inner);
			assertEquals(new WardrobeLayout.Bounds(outer.x() + 2, outer.y() + 2,
					outer.width() - 4, 98), model);
			assertEquals(new WardrobeLayout.Bounds(outer.right() - 20, outer.y() + 2, 18, 18), button);
			assertEquals(new WardrobeLayout.Bounds(button.x() + 1, button.y() + 1, 16, 16), icon);
			assertContained(inner, outer);
			assertContained(model, inner);
			assertContained(button, inner);
			assertContained(icon, button);
			assertEquals(outer.centerX(), model.centerX());
			assertEquals(outer.centerY(), model.centerY());
			var drag = layout.previewDragBounds();
			assertContained(drag, model);
			assertEquals(2, drag.y() - button.bottom());
			assertFalse(drag.overlaps(button));
			assertEquals(new WardrobeLayout.Bounds(outer.x() + 2, outer.y() + 22,
					outer.width() - 4, 78), drag);
		}
	}

	@Test
	void previewDragAreaExcludesModeButtonBorderAndTopStrip() {
		for (int width : new int[]{192, 211}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			var outer = layout.previewBounds();
			var drag = layout.previewDragBounds();
			var button = layout.previewModeButtonBounds();
			assertTrue(drag.contains(drag.centerX(), drag.centerY()));
			assertTrue(drag.contains(drag.x(), drag.y()));
			assertFalse(drag.contains(button.centerX(), button.centerY()));
			assertFalse(drag.contains(outer.centerX(), button.y()));
			assertFalse(drag.contains(outer.x(), drag.centerY()));
			assertFalse(drag.contains(drag.right(), drag.centerY()));
			assertFalse(drag.contains(drag.centerX(), drag.bottom()));
		}
	}

	@Test
	void tinyViewportRetainsValidPreviewSubregionsForSafeDegradation() {
		for (int[] viewport : new int[][]{{1, 1}, {191, 214}, {211, 213}}) {
			WardrobeLayout layout = WardrobeLayout.calculate(viewport[0], viewport[1], 9);
			assertFalse(layout.fitsScreen());
			assertEquals(62, layout.previewBounds().width());
			assertEquals(102, layout.previewBounds().height());
			assertContained(layout.previewInnerBounds(), layout.previewBounds());
			assertContained(layout.previewModelBounds(), layout.previewInnerBounds());
			assertContained(layout.previewModeButtonBounds(), layout.previewInnerBounds());
			assertFalse(layout.previewDragBounds().overlaps(layout.previewModeButtonBounds()));
		}
	}

	@Test
	void tabUsesSelectedThirtyTwoPixelGeometryAndCenteredIcon() {
		for (int width : new int[]{192, 211, 854}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			assertRelative(layout, layout.tabBounds(), 0, -28, 32, 32);
			assertRelative(layout, layout.tabIconBounds(), 8, -19, 16, 16);
			assertRelative(layout, layout.tabJoinBounds(), 0, 0, 32, 4);
			assertEquals(layout.tabBounds().centerX(), layout.tabIconBounds().centerX());
			assertEquals(layout.frameBounds().y() + 4, layout.tabBounds().bottom());
		}
	}

	@Test
	void futureTabPitchLeavesOnePixelGapAndFitsCompact() {
		assertEquals(33, WardrobeLayout.TAB_PITCH);
		assertEquals(1, WardrobeLayout.TAB_PITCH - WardrobeLayout.TAB_WIDTH);
		assertEquals(98, 2 * WardrobeLayout.TAB_PITCH + WardrobeLayout.TAB_WIDTH);
		assertTrue(2 * WardrobeLayout.TAB_PITCH + WardrobeLayout.TAB_WIDTH <= WardrobeLayout.COMPACT_WIDTH);
	}

	@Test
	void approvedThresholdsIncludeEightPixelOuterMargins() {
		assertEquals(WardrobeLayout.Mode.STANDARD, WardrobeLayout.calculate(211, 214, 9).mode());
		assertEquals(WardrobeLayout.Mode.COMPACT, WardrobeLayout.calculate(210, 214, 9).mode());
		assertEquals(WardrobeLayout.Mode.COMPACT, WardrobeLayout.calculate(192, 214, 9).mode());
		assertFalse(WardrobeLayout.calculate(191, 214, 9).fitsScreen());
		assertFalse(WardrobeLayout.calculate(211, 213, 9).fitsScreen());
		assertFalse(WardrobeLayout.calculate(1, 1, 9).fitsScreen());
	}

	@Test
	void completeFrameAndTabRemainCenteredWithinSafeViewport() {
		for (int[] viewport : new int[][]{{192, 214}, {210, 215}, {211, 214}, {320, 240}, {853, 479}}) {
			WardrobeLayout layout = WardrobeLayout.calculate(viewport[0], viewport[1], 9);
			assertTrue(layout.fitsScreen());
			assertEquals((viewport[0] - layout.frameBounds().width()) / 2, layout.frameBounds().x());
			assertEquals((viewport[1] - 198) / 2, layout.tabBounds().y());
			assertTrue(layout.tabBounds().y() >= 8);
			assertTrue(layout.frameBounds().x() >= 8);
			assertTrue(viewport[0] - layout.frameBounds().right() >= 8);
			assertTrue(viewport[1] - layout.frameBounds().bottom() >= 8);
		}
	}

	@Test
	void previewGridPaginationStatusAndApplyNeverOverlap() {
		for (int width : new int[]{192, 211, 854}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			List<WardrobeLayout.Bounds> regions = List.of(layout.previewBounds(), layout.gridBounds(),
					layout.paginationBounds(), layout.statusBounds(), layout.applyButtonBounds());
			for (int i = 0; i < regions.size(); i++) {
				WardrobeLayout.Bounds region = regions.get(i);
				assertTrue(region.x() >= layout.frameBounds().x() + 3);
				assertTrue(region.y() >= layout.frameBounds().y() + 3);
				assertTrue(region.right() <= layout.frameBounds().right() - 3);
				assertTrue(region.bottom() <= layout.frameBounds().bottom() - 3);
				assertFalse(region.overlaps(layout.tabBounds()));
				for (int j = i + 1; j < regions.size(); j++) {
					assertFalse(region.overlaps(regions.get(j)));
				}
			}
			assertEquals(10, layout.gridBounds().x() - layout.previewBounds().right());
			assertEquals(8, layout.applyButtonBounds().x() - layout.statusBounds().right());
		}
	}

	@Test
	void fourByThreeSlotsAreContiguousAndContainFullSizeThumbnails() {
		for (int width : new int[]{192, 211}) {
			WardrobeLayout layout = WardrobeLayout.calculate(width, 240, 9);
			assertEquals(4, WardrobeCapeCatalog.PAGE_COLUMNS);
			assertEquals(3, WardrobeCapeCatalog.PAGE_ROWS);
			assertEquals(12, WardrobeCapeCatalog.PAGE_SIZE);
			for (int index = 0; index < 12; index++) {
				WardrobeLayout.Bounds entry = layout.entryBounds(index);
				assertEquals(new WardrobeLayout.Bounds(layout.gridBounds().x() + index % 4 * 22,
						layout.gridBounds().y() + index / 4 * 34, 22, 34), entry);
				assertEquals(new WardrobeLayout.Bounds(entry.x() + 1, entry.y() + 1, 20, 32),
						layout.thumbnailBounds(index));
			}
			assertEquals(layout.gridBounds().right(), layout.entryBounds(11).right());
			assertEquals(layout.gridBounds().bottom(), layout.entryBounds(11).bottom());
		}
	}

	@Test
	void titleAndTwoStatusLinesUseFixedLogicalCoordinates() {
		WardrobeLayout layout = WardrobeLayout.calculate(320, 240, 9);
		assertEquals(layout.frameBounds().x() + 8, layout.titleX());
		assertEquals(layout.frameBounds().y() + 6, layout.titleY());
		assertEquals(layout.frameBounds().y() + 126, layout.pageLabelY());
		assertEquals(layout.frameBounds().y() + 144, layout.selectionLabelY());
		assertEquals(layout.selectionLabelY() + 10, layout.statusLabelY());
	}

	@Test
	void resizeRecalculatesOnlyGeometryWithoutTouchingDraftOrPage() {
		var selection = new WardrobeSelectionSession();
		selection.observe(Optional.of(vanillafashion.fashion.PlayerFashionAuthoritativeState.vanilla()),
				vanillafashion.client.fashion.ClientPlayerFashionRegistry.State.AVAILABLE);
		var catalog = new WardrobeCapeCatalog(java.util.stream.IntStream.range(0, 12)
				.mapToObj(index -> new CapeCosmeticMetadata(new CapeId("cape_" + index),
						"a".repeat(64), Optional.empty())).toList());
		selection.select(catalog.pageEntries().get(1).capeId());
		catalog.goToNextPage();
		WardrobeLayout compact = WardrobeLayout.calculate(192, 214, 9);
		WardrobeLayout standard = WardrobeLayout.calculate(854, 480, 9);
		assertNotEquals(compact.frameBounds(), standard.frameBounds());
		assertEquals(1, catalog.pageIndex());
		assertEquals(Optional.of(new CapeId("cape_0")), selection.draft());
	}

	@Test
	void boundsUseExclusiveRightAndBottomEdges() {
		var bounds = new WardrobeLayout.Bounds(10, 20, 22, 34);
		assertTrue(bounds.contains(10, 20));
		assertTrue(bounds.contains(31.99, 53.99));
		assertFalse(bounds.contains(32, 20));
		assertFalse(bounds.contains(10, 54));
		assertFalse(bounds.overlaps(new WardrobeLayout.Bounds(32, 20, 22, 34)));
	}

	@Test
	void rejectsInvalidInputsAndOutOfPageIndices() {
		assertThrows(IllegalArgumentException.class, () -> WardrobeLayout.calculate(0, 240, 9));
		assertThrows(IllegalArgumentException.class, () -> WardrobeLayout.calculate(427, 0, 9));
		assertThrows(IllegalArgumentException.class, () -> WardrobeLayout.calculate(427, 240, 0));
		WardrobeLayout layout = WardrobeLayout.calculate(320, 240, 9);
		assertThrows(IllegalArgumentException.class, () -> layout.entryBounds(-1));
		assertThrows(IllegalArgumentException.class, () -> layout.entryBounds(12));
		assertThrows(IllegalArgumentException.class, () -> layout.thumbnailBounds(12));
	}

	private static void assertContained(WardrobeLayout.Bounds inner, WardrobeLayout.Bounds outer) {
		assertTrue(inner.x() >= outer.x());
		assertTrue(inner.y() >= outer.y());
		assertTrue(inner.right() <= outer.right());
		assertTrue(inner.bottom() <= outer.bottom());
	}

	private static void assertRelative(WardrobeLayout layout, WardrobeLayout.Bounds actual,
			int x, int y, int width, int height) {
		assertEquals(new WardrobeLayout.Bounds(layout.frameBounds().x() + x,
				layout.frameBounds().y() + y, width, height), actual);
	}
}
