package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;

@org.junit.jupiter.api.extension.ExtendWith(WardrobeLanguageTestSupport.class)
class WardrobeCapeCatalogTest {
	private static final String CAPE_HASH = "a".repeat(64);
	private static final String SECOND_CAPE_HASH = "b".repeat(64);

	@Test
	void emptyRegistryContainsOnlyVanillaEntry() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of());

		assertEquals(0, catalog.capeCount());
		assertEquals(1, catalog.pageCount());
		assertEquals(1, catalog.pageEntries().size());
		assertTrue(catalog.pageEntries().getFirst().isVanilla());
	}

	@Test
	void newCatalogStartsWithVanillaDraftSelection() {
		var selection = knownVanillaSession();
		List<CapeCosmeticMetadata> entries = List.of(metadata("alpha"));
		WardrobeCapeCatalog firstScreenCatalog = new WardrobeCapeCatalog(entries);
		selection.select(firstScreenCatalog.pageEntries().get(1).capeId());

		WardrobeCapeCatalog reopenedScreenCatalog = new WardrobeCapeCatalog(entries);

		var reopened = knownVanillaSession();
		assertTrue(reopened.draft().isEmpty());
		assertEquals(1, reopenedScreenCatalog.capeCount());
		assertEquals("原版", reopened.draft().map(CapeId::value).orElse("原版"));
	}

	@Test
	void elevenCapesAndVanillaFitOnOnePage() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(11));

		assertEquals(1, catalog.pageCount());
		assertEquals(WardrobeCapeCatalog.PAGE_SIZE, catalog.pageEntries().size());
	}

	@Test
	void twelveCapesAndVanillaUseTwoPages() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(12));

		assertEquals(2, catalog.pageCount());
		assertEquals(WardrobeCapeCatalog.PAGE_SIZE, catalog.pageEntries().size());
		catalog.goToNextPage();
		assertEquals(1, catalog.pageEntries().size());
	}

	@Test
	void sortsCapeIdsByStableNaturalOrder() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(
				metadata("zeta"),
				metadata("alpha"),
				metadata("middle")
		));

		assertEquals(
				List.of("原版", "alpha", "middle", "zeta"),
				catalog.pageEntries().stream().map(WardrobeCapeCatalog.Entry::displayName).toList()
		);
	}

	@Test
	void vanillaEntryIsAlwaysFirst() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("alpha")));

		assertTrue(catalog.pageEntries().getFirst().isVanilla());
		assertEquals("原版", catalog.pageEntries().getFirst().displayName());
	}

	@Test
	void pageNavigationClampsAtBothEnds() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(24));

		catalog.goToPreviousPage();
		assertEquals(0, catalog.pageIndex());
		catalog.goToNextPage();
		catalog.goToNextPage();
		catalog.goToNextPage();
		assertEquals(catalog.pageCount() - 1, catalog.pageIndex());
		assertFalse(catalog.canGoToNextPage());
		catalog.goToPreviousPage();
		catalog.goToPreviousPage();
		catalog.goToPreviousPage();
		assertEquals(0, catalog.pageIndex());
		assertFalse(catalog.canGoToPreviousPage());
	}

	@Test
	void pageChangeKeepsDraftSelection() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(20));
		WardrobeCapeCatalog.Entry selectedEntry = catalog.pageEntries().get(1);

		selection.select(selectedEntry.capeId());
		catalog.goToNextPage();

		assertEquals(selectedEntry.capeId(), selection.draft());
	}

	@Test
	void selectingVanillaClearsDraftSelection() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("alpha")));
		selection.select(catalog.pageEntries().get(1).capeId());

		selection.select(catalog.pageEntries().getFirst().capeId());

		assertTrue(selection.draft().isEmpty());
		assertEquals("原版", selection.draft().map(CapeId::value).orElse("原版"));
	}

	@Test
	void selectingCapeStoresCapeIdAsDraft() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("alpha")));

		selection.select(catalog.pageEntries().get(1).capeId());

		assertEquals(Optional.of(new CapeId("alpha")), selection.draft());
		assertEquals("alpha", selection.draft().map(CapeId::value).orElse("原版"));
	}

	@Test
	void metadataRemovalPreservesSessionDraft() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("removed")));
		selection.select(catalog.pageEntries().get(1).capeId());

		catalog.replace(List.of(metadata("remaining")));

		assertEquals(Optional.of(new CapeId("removed")), selection.draft());
		assertEquals("remaining", catalog.pageEntries().get(1).displayName());
	}

	@Test
	void replaceKeepsSelectionWhenIdStillExists() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("kept")));
		selection.select(catalog.pageEntries().get(1).capeId());

		catalog.replace(List.of(metadata("added"), metadata("kept")));

		assertEquals(Optional.of(new CapeId("kept")), selection.draft());
	}

	@Test
	void maximumProtocolRegistryHasExpectedPageCount() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(1024));

		assertEquals(86, catalog.pageCount());
	}

	@Test
	void everyPageReturnsAtMostTwelveEntries() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(1024));

		for (int page = 0; page < catalog.pageCount(); page++) {
			assertTrue(catalog.pageEntries().size() <= WardrobeCapeCatalog.PAGE_SIZE);
			catalog.goToNextPage();
		}
	}

	@Test
	void replaceClampsCurrentPageToNewLastPage() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(metadataEntries(30));
		catalog.goToNextPage();
		catalog.goToNextPage();

		catalog.replace(metadataEntries(3));

		assertEquals(0, catalog.pageIndex());
		assertEquals(1, catalog.pageNumber());
	}

	@Test
	void catalogDoesNotDependOnTextureAvailability() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("not_ready")));
		WardrobeCapeCatalog.Entry entry = catalog.pageEntries().get(1);

		assertEquals("not_ready", entry.displayName());
		assertEquals(CAPE_HASH, entry.metadata().orElseThrow().capeSha256());
	}

	@Test
	void duplicateMetadataCannotCreateDuplicateCatalogEntries() {
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(
				metadata("duplicate"),
				new CapeCosmeticMetadata(new CapeId("duplicate"), SECOND_CAPE_HASH, Optional.empty())
		));

		assertEquals(1, catalog.capeCount());
		assertEquals(2, catalog.pageEntries().size());
		assertEquals(CAPE_HASH, catalog.pageEntries().get(1).metadata().orElseThrow().capeSha256());
	}

	@Test
	void capeThumbnailUsesFrozenNorthFaceUv() {
		assertEquals(4, WardrobeCapeCatalog.PAGE_COLUMNS);
		assertEquals(3, WardrobeCapeCatalog.PAGE_ROWS);
		assertEquals(12, WardrobeCapeCatalog.PAGE_SIZE);
		assertEquals(64, CapeGridEntryWidget.TEXTURE_WIDTH);
		assertEquals(32, CapeGridEntryWidget.TEXTURE_HEIGHT);
		assertEquals(1, CapeGridEntryWidget.CAPE_NORTH_X);
		assertEquals(1, CapeGridEntryWidget.CAPE_NORTH_Y);
		assertEquals(10, CapeGridEntryWidget.CAPE_NORTH_WIDTH);
		assertEquals(16, CapeGridEntryWidget.CAPE_NORTH_HEIGHT);
	}

	private static List<CapeCosmeticMetadata> metadataEntries(int count) {
		var entries = new ArrayList<CapeCosmeticMetadata>(count);
		for (int index = 1; index <= count; index++) {
			entries.add(metadata(String.format(Locale.ROOT, "cape_%04d", index)));
		}
		return List.copyOf(entries);
	}

	private static CapeCosmeticMetadata metadata(String id) {
		return new CapeCosmeticMetadata(new CapeId(id), CAPE_HASH, Optional.empty());
	}
	private static WardrobeSelectionSession knownVanillaSession() {
		var session = new WardrobeSelectionSession();
		session.observe(Optional.of(dev.zbw3790.fashion.fashion.PlayerFashionAuthoritativeState.vanilla()),
				dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry.State.AVAILABLE);
		return session;
	}

}
