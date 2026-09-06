package vanillafashion.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vanillafashion.cape.CapeTestAssets.createCosmeticDirectory;
import static vanillafashion.cape.CapeTestAssets.writePng;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapeRegistryLoaderTest {
	private static final int RED = 0xFFFF0000;
	private static final int BLUE = 0xFF0000FF;
	private final CapeRegistryLoader loader = new CapeRegistryLoader();

	@TempDir
	Path tempDirectory;

	@Test
	void createsMissingRootAndReturnsEmptyRegistry() throws IOException {
		Path root = tempDirectory.resolve("missing").resolve("capes");

		CapeRegistryLoadResult result = loader.load(root);

		assertTrue(Files.isDirectory(root));
		assertTrue(result.registry().isEmpty());
		assertTrue(result.rejectedEntries().isEmpty());
	}

	@Test
	void loadsEmptyRoot() throws IOException {
		Path root = createRoot();

		CapeRegistryLoadResult result = loader.load(root);

		assertTrue(result.registry().isEmpty());
		assertTrue(result.rejectedEntries().isEmpty());
	}

	@Test
	void loadsCapeOnlyAndKeepsEmptyElytra() throws IOException {
		Path root = createRoot();
		Path directory = createCosmeticDirectory(root, "cape-only");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);

		CapeRegistryLoadResult result = loader.load(root);
		CapeCosmeticDefinition definition = result.registry().find(new CapeId("cape-only")).orElseThrow();

		assertEquals(1, result.registry().size());
		assertTrue(definition.elytra().isEmpty());
		assertTrue(result.rejectedEntries().isEmpty());
	}

	@Test
	void loadsCapeAndElytraAndKeepsElytraAsset() throws IOException {
		Path root = createRoot();
		Path directory = createCosmeticDirectory(root, "full-set");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Path elytra = writePng(
				directory.resolve("elytra.png"),
				64,
				32,
				BufferedImage.TYPE_INT_ARGB,
				BLUE
		);

		CapeRegistryLoadResult result = loader.load(root);
		CapeCosmeticDefinition definition = result.registry().find(new CapeId("full-set")).orElseThrow();

		assertEquals(elytra, definition.elytra().orElseThrow().source());
		assertTrue(result.rejectedEntries().isEmpty());
	}

	@Test
	void loadsMultipleCosmeticsInStableIdOrder() throws IOException {
		Path root = createRoot();
		createValidCapeOnly(root, "zeta");
		createValidCapeOnly(root, "alpha");
		createValidCapeOnly(root, "middle");

		CapeRegistryLoadResult result = loader.load(root);

		assertEquals(List.of("alpha", "middle", "zeta"), ids(result.registry()));
		assertEquals(3, result.registry().size());
	}

	@Test
	void rejectsWrongDimensionsWithoutDroppingValidEntry() throws IOException {
		Path root = createRoot();
		createValidCapeOnly(root, "valid");
		Path broken = createCosmeticDirectory(root, "broken");
		writePng(broken.resolve("cape.png"), 32, 32, BufferedImage.TYPE_INT_ARGB, RED);

		CapeRegistryLoadResult result = loader.load(root);

		assertEquals(List.of("valid"), ids(result.registry()));
		assertEquals(1, result.rejectedEntries().size());
		assertRejected(result, "broken", CapeValidationErrorCode.INVALID_CAPE_DIMENSIONS);
	}

	@Test
	void rejectsInvalidIdWithoutDroppingValidEntry() throws IOException {
		Path root = createRoot();
		createValidCapeOnly(root, "valid");
		createValidCapeOnly(root, "BadCape");

		CapeRegistryLoadResult result = loader.load(root);

		assertEquals(List.of("valid"), ids(result.registry()));
		assertRejected(result, "BadCape", CapeValidationErrorCode.INVALID_ID);
	}

	@Test
	void ignoresOrdinaryFilesInRoot() throws IOException {
		Path root = createRoot();
		Files.writeString(root.resolve("note.txt"), "该文件应被忽略。");
		createValidCapeOnly(root, "valid");

		CapeRegistryLoadResult result = loader.load(root);

		assertEquals(List.of("valid"), ids(result.registry()));
		assertTrue(result.rejectedEntries().isEmpty());
	}

	@Test
	void doesNotRecursivelyScanNestedEntries() throws IOException {
		Path root = createRoot();
		Path nested = createCosmeticDirectory(root, "nested");
		Path inner = createCosmeticDirectory(nested, "inner");
		writePng(inner.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);

		CapeRegistryLoadResult result = loader.load(root);

		assertTrue(result.registry().isEmpty());
		assertEquals(1, result.rejectedEntries().size());
		assertRejected(result, "nested", CapeValidationErrorCode.MISSING_CAPE);
	}

	@Test
	void returnsEmptyRegistryWhenAllEntriesAreInvalid() throws IOException {
		Path root = createRoot();
		Path broken = createCosmeticDirectory(root, "broken");
		writePng(broken.resolve("cape.png"), 128, 64, BufferedImage.TYPE_INT_ARGB, RED);
		createCosmeticDirectory(root, "missing");

		CapeRegistryLoadResult result = loader.load(root);

		assertTrue(result.registry().isEmpty());
		assertEquals(2, result.rejectedEntries().size());
		assertEquals(
				List.of("broken", "missing"),
				result.rejectedEntries().stream()
						.map(entry -> entry.directory().getFileName().toString())
						.toList()
		);
		assertThrows(UnsupportedOperationException.class, () -> result.rejectedEntries().clear());
	}

	@Test
	void reportsRootIoFailureToCaller() throws IOException {
		Path rootFile = Files.createTempFile(tempDirectory, "not-a-directory-", ".txt");

		assertThrows(IOException.class, () -> loader.load(rootFile));
	}

	@Test
	void sharedLoadsAlongsideRejectedKnownExistingEntriesInStableOrder() throws IOException {
		Path root = createRoot();
		Path shared = createCosmeticDirectory(root, "z-shared");
		writePng(shared.resolve("cape_elytra.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, BLUE);
		createValidCapeOnly(root, "a-cape");
		Path conflict = createCosmeticDirectory(root, "conflict");
		writePng(conflict.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Files.write(conflict.resolve("cape_elytra.png"), new byte[] {1});
		Path broken = createCosmeticDirectory(root, "broken-shared");
		Files.write(broken.resolve("cape_elytra.png"), new byte[] {1});
		Path dimensions = createCosmeticDirectory(root, "wrong-shared");
		writePng(dimensions.resolve("cape_elytra.png"), 32, 32, BufferedImage.TYPE_INT_ARGB, BLUE);
		Path split = createCosmeticDirectory(root, "invalid-split");
		writePng(split.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Files.write(split.resolve("elytra.png"), new byte[] {1});

		CapeRegistryLoadResult result = loader.load(root);

		assertEquals(List.of("a-cape", "z-shared"), ids(result.registry()));
		assertTrue(result.registry().find(new CapeId("z-shared")).orElseThrow().elytra().isPresent());
		assertEquals(4, result.rejectedEntries().size());
		assertRejected(result, "conflict", CapeValidationErrorCode.ASSET_LAYOUT_CONFLICT);
		assertRejected(result, "broken-shared", CapeValidationErrorCode.INVALID_SHARED_PNG);
		assertRejected(result, "wrong-shared", CapeValidationErrorCode.INVALID_SHARED_DIMENSIONS);
		assertRejected(result, "invalid-split", CapeValidationErrorCode.INVALID_ELYTRA_PNG);
		for (CapeRejectedEntry rejected : result.rejectedEntries()) {
			assertTrue(Files.isDirectory(rejected.directory()));
			CapeId rejectedId = new CapeId(rejected.directory().getFileName().toString());
			assertTrue(result.registry().find(rejectedId).isEmpty());
		}
	}

	private Path createRoot() throws IOException {
		return Files.createTempDirectory(tempDirectory, "capes-");
	}

	private void createValidCapeOnly(Path root, String id) throws IOException {
		Path directory = createCosmeticDirectory(root, id);
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
	}

	private static List<String> ids(CapeRegistry registry) {
		return registry.definitions().stream()
				.map(definition -> definition.id().value())
				.toList();
	}

	private static void assertRejected(
			CapeRegistryLoadResult result,
			String directoryName,
			CapeValidationErrorCode code
	) {
		CapeRejectedEntry rejectedEntry = result.rejectedEntries().stream()
				.filter(entry -> entry.directory().getFileName().toString().equals(directoryName))
				.findFirst()
				.orElseThrow();

		assertFalse(rejectedEntry.issues().isEmpty());
		assertTrue(rejectedEntry.issues().stream().anyMatch(issue -> issue.code() == code));
	}
}
