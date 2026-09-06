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
	private static final int FONT_LINE_HEIGHT = 9;
	private static final String CAPE_HASH = "a".repeat(64);

	@Test
	void wideLayoutKeepsPreviewAndGridSeparate() {
		WardrobeLayout layout = WardrobeLayout.calculate(854, 480, FONT_LINE_HEIGHT);

		assertFalse(layout.previewBounds().overlaps(layout.gridBounds()));
		assertTrue(layout.previewBounds().right() < layout.gridBounds().x());
	}

	@Test
	void narrowNormalLayoutStaysInsideScreen() {
		int screenWidth = 320;
		int screenHeight = 240;
		WardrobeLayout layout = WardrobeLayout.calculate(screenWidth, screenHeight, FONT_LINE_HEIGHT);

		assertTrue(layout.contentBounds().isWithin(screenWidth, screenHeight));
		assertTrue(layout.previewBounds().isWithin(screenWidth, screenHeight));
		assertTrue(layout.gridBounds().isWithin(screenWidth, screenHeight));
		assertTrue(layout.previousPageButtonBounds().isWithin(screenWidth, screenHeight));
		assertTrue(layout.nextPageButtonBounds().isWithin(screenWidth, screenHeight));
		assertTrue(layout.cancelButtonBounds().isWithin(screenWidth, screenHeight));
	}

	@Test
	void previewBoundsAndEntitySizeArePositive() {
		WardrobeLayout layout = WardrobeLayout.calculate(427, 240, FONT_LINE_HEIGHT);

		assertTrue(layout.previewBounds().width() > 0);
		assertTrue(layout.previewBounds().height() > 0);
		assertTrue(layout.previewEntitySize() > 0);
	}

	@Test
	void gridOriginDoesNotCoverPreview() {
		WardrobeLayout layout = WardrobeLayout.calculate(427, 240, FONT_LINE_HEIGHT);

		assertTrue(layout.gridBounds().x() >= layout.previewBounds().right());
		assertEquals(layout.previewBounds().y(), layout.gridBounds().y());
	}

	@Test
	void paginationIsBelowGrid() {
		WardrobeLayout layout = WardrobeLayout.calculate(427, 240, FONT_LINE_HEIGHT);

		assertTrue(layout.previousPageButtonBounds().y() >= layout.gridBounds().bottom());
		assertEquals(layout.previousPageButtonBounds().y(), layout.nextPageButtonBounds().y());
		assertTrue(layout.pageLabelY() >= layout.previousPageButtonBounds().y());
		assertTrue(layout.pageLabelY() < layout.previousPageButtonBounds().bottom());
	}

	@Test
	void selectionLabelAndCloseButtonStayInsideScreen() {
		int screenWidth = 427;
		int screenHeight = 240;
		WardrobeLayout layout = WardrobeLayout.calculate(screenWidth, screenHeight, FONT_LINE_HEIGHT);

		assertTrue(layout.selectionLabelY() >= layout.previousPageButtonBounds().bottom());
		assertTrue(layout.selectionLabelY() + FONT_LINE_HEIGHT <= screenHeight);
		assertEquals(layout.selectionLabelY() + FONT_LINE_HEIGHT, layout.statusLabelY());
		assertTrue(layout.cancelButtonBounds().y() >= layout.statusLabelY() + FONT_LINE_HEIGHT);
		assertTrue(layout.cancelButtonBounds().isWithin(screenWidth, screenHeight));
	}

	@Test
	void allCurrentPageEntryBoundsStayInsideGrid() {
		WardrobeLayout layout = WardrobeLayout.calculate(427, 240, FONT_LINE_HEIGHT);

		for (int index = 0; index < WardrobeCapeCatalog.PAGE_SIZE; index++) {
			WardrobeLayout.Bounds entry = layout.entryBounds(index);
			assertTrue(entry.x() >= layout.gridBounds().x());
			assertTrue(entry.y() >= layout.gridBounds().y());
			assertTrue(entry.right() <= layout.gridBounds().right());
			assertTrue(entry.bottom() <= layout.gridBounds().bottom());
			assertFalse(entry.overlaps(layout.previewBounds()));
		}
	}

	@Test
	void fourByThreeGridGeometryRemainsFrozen() {
		WardrobeLayout layout = WardrobeLayout.calculate(427, 240, FONT_LINE_HEIGHT);
		WardrobeLayout.Bounds first = layout.entryBounds(0);
		WardrobeLayout.Bounds fourth = layout.entryBounds(3);
		WardrobeLayout.Bounds fifth = layout.entryBounds(4);
		WardrobeLayout.Bounds last = layout.entryBounds(11);

		assertEquals(4, WardrobeCapeCatalog.PAGE_COLUMNS);
		assertEquals(3, WardrobeCapeCatalog.PAGE_ROWS);
		assertEquals(12, WardrobeCapeCatalog.PAGE_SIZE);
		assertEquals(first.y(), fourth.y());
		assertTrue(fourth.x() > first.x());
		assertEquals(first.x(), fifth.x());
		assertTrue(fifth.y() > first.y());
		assertEquals(layout.gridBounds().right(), last.right());
		assertEquals(layout.gridBounds().bottom(), last.bottom());
	}

	@Test
	void resizeRecalculatesLayout() {
		WardrobeLayout compact = WardrobeLayout.calculate(320, 240, FONT_LINE_HEIGHT);
		WardrobeLayout wide = WardrobeLayout.calculate(854, 480, FONT_LINE_HEIGHT);

		assertNotEquals(compact.contentBounds(), wide.contentBounds());
		assertNotEquals(compact.previewBounds(), wide.previewBounds());
		assertNotEquals(compact.gridBounds(), wide.gridBounds());
	}

	@Test
	void resizeCalculationDoesNotChangePageOrDraftSelection() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(
				metadata("cape_01"),
				metadata("cape_02"),
				metadata("cape_03"),
				metadata("cape_04"),
				metadata("cape_05"),
				metadata("cape_06"),
				metadata("cape_07"),
				metadata("cape_08"),
				metadata("cape_09"),
				metadata("cape_10"),
				metadata("cape_11"),
				metadata("cape_12")
		));
		selection.select(catalog.pageEntries().get(1).capeId());
		catalog.goToNextPage();

		WardrobeLayout.calculate(320, 240, FONT_LINE_HEIGHT);
		WardrobeLayout.calculate(854, 480, FONT_LINE_HEIGHT);

		assertEquals(1, catalog.pageIndex());
		assertEquals(Optional.of(new CapeId("cape_01")), selection.draft());
	}

	@Test
	void rejectsInvalidScreenDimensions() {
		assertThrows(IllegalArgumentException.class, () -> WardrobeLayout.calculate(0, 240, FONT_LINE_HEIGHT));
		assertThrows(IllegalArgumentException.class, () -> WardrobeLayout.calculate(427, 0, FONT_LINE_HEIGHT));
		assertThrows(IllegalArgumentException.class, () -> WardrobeLayout.calculate(427, 240, 0));
	}

	private static CapeCosmeticMetadata metadata(String id) {
		return new CapeCosmeticMetadata(new CapeId(id), CAPE_HASH, Optional.empty());
	}
	private static WardrobeSelectionSession knownVanillaSession() {
		var session = new WardrobeSelectionSession();
		session.observe(Optional.of(vanillafashion.fashion.PlayerFashionAuthoritativeState.vanilla()),
				vanillafashion.client.fashion.ClientPlayerFashionRegistry.State.AVAILABLE);
		return session;
	}

}
