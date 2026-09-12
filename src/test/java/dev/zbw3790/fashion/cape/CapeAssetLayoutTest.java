package dev.zbw3790.fashion.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static dev.zbw3790.fashion.cape.CapeTestAssets.writeImage;
import static dev.zbw3790.fashion.cape.CapeTestAssets.writePng;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CapeAssetLayoutTest {
	private final CapeCosmeticValidator validator = new CapeCosmeticValidator();

	@TempDir
	Path temporaryDirectory;

	@ParameterizedTest
	@CsvSource({
			"false,false,false,MISSING_CAPE",
			"true,false,false,CAPE_ONLY",
			"false,true,false,MISSING_CAPE",
			"true,true,false,SPLIT",
			"false,false,true,SHARED",
			"true,false,true,ASSET_LAYOUT_CONFLICT",
			"false,true,true,ASSET_LAYOUT_CONFLICT",
			"true,true,true,ASSET_LAYOUT_CONFLICT"
	})
	void classifiesEveryRecognizedCombination(boolean cape, boolean elytra, boolean shared, String expected)
			throws IOException {
		Path directory = directory("layout");
		if (cape) writeValid(directory.resolve("cape.png"));
		if (elytra) writeValid(directory.resolve("elytra.png"));
		if (shared) writeValid(directory.resolve("cape_elytra.png"));

		if (expected.equals("MISSING_CAPE") || expected.equals("ASSET_LAYOUT_CONFLICT")) {
			assertSingleIssue(directory, CapeValidationErrorCode.valueOf(expected));
		} else {
			assertLayout(requireDefinition(directory), expected);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"CAPE_ONLY", "SPLIT", "SHARED"})
	void ignoresUnrelatedFilesAndDoesNotDescendIntoDirectories(String layout) throws IOException {
		Path directory = directory("extras");
		writeValid(directory.resolve(layout.equals("SHARED") ? "cape_elytra.png" : "cape.png"));
		if (layout.equals("SPLIT")) writeValid(directory.resolve("elytra.png"));
		Files.writeString(directory.resolve("README.txt"), "普通说明文件不影响布局。");
		Files.writeString(directory.resolve("author.txt"), "测试作者。");
		Files.write(directory.resolve("preview.jpg"), new byte[] {1, 2, 3});
		Path nested = Files.createDirectory(directory.resolve("nested"));
		writeValid(nested.resolve("cape.png"));
		writeValid(nested.resolve("elytra.png"));
		writeValid(nested.resolve("cape_elytra.png"));

		assertLayout(requireDefinition(directory), layout);
	}

	@ParameterizedTest
	@CsvSource({"true,false", "false,true", "true,true"})
	void classifiesConflictBeforeReadingBrokenShared(boolean cape, boolean elytra) throws IOException {
		Path directory = directory("broken-conflict");
		if (cape) writeValid(directory.resolve("cape.png"));
		if (elytra) writeValid(directory.resolve("elytra.png"));
		Files.write(directory.resolve("cape_elytra.png"), new byte[] {1, 2});

		assertSingleIssue(directory, CapeValidationErrorCode.ASSET_LAYOUT_CONFLICT);
	}

	@ParameterizedTest
	@CsvSource({
			"cape.png,CAPE_NOT_REGULAR_FILE",
			"elytra.png,ELYTRA_NOT_REGULAR_FILE",
			"cape_elytra.png,SHARED_NOT_REGULAR_FILE"
	})
	void recognizedDirectoriesArePresentButNotValidFiles(String name, CapeValidationErrorCode expected)
			throws IOException {
		Path directory = directory("not-regular");
		if (name.equals("elytra.png")) writeValid(directory.resolve("cape.png"));
		Files.createDirectory(directory.resolve(name));

		assertSingleIssue(directory, expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"cape.png", "elytra.png", "cape_elytra.png"})
	void recognizedDirectoryStillCreatesLayoutConflict(String directoryName) throws IOException {
		Path directory = directory("directory-conflict");
		Files.createDirectory(directory.resolve(directoryName));
		writeValid(directory.resolve(directoryName.equals("cape_elytra.png") ? "cape.png" : "cape_elytra.png"));

		assertSingleIssue(directory, CapeValidationErrorCode.ASSET_LAYOUT_CONFLICT);
	}

	@Test
	void brokenSplitElytraDoesNotFallBackToCapeOnly() throws IOException {
		Path directory = directory("broken-split");
		writeValid(directory.resolve("cape.png"));
		Files.write(directory.resolve("elytra.png"), new byte[] {1, 2});

		assertSingleIssue(directory, CapeValidationErrorCode.INVALID_ELYTRA_PNG);
	}

	@Test
	void brokenSharedReportsOnlyOnePhysicalIssue() throws IOException {
		Path directory = directory("broken-shared");
		Files.write(directory.resolve("cape_elytra.png"), new byte[] {
				(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0
		});

		assertSingleIssue(directory, CapeValidationErrorCode.INVALID_SHARED_PNG);
	}

	@Test
	void rejectsJpegRenamedAsSharedPng() throws IOException {
		Path directory = directory("fake-shared");
		writeImage(directory.resolve("cape_elytra.png"), "JPEG", 64, 32, BufferedImage.TYPE_INT_RGB, 0xFF3790FF);

		assertSingleIssue(directory, CapeValidationErrorCode.INVALID_SHARED_PNG);
	}

	@Test
	void rejectsEmptyShared() throws IOException {
		Path directory = directory("empty-shared");
		Files.createFile(directory.resolve("cape_elytra.png"));

		assertSingleIssue(directory, CapeValidationErrorCode.INVALID_SHARED_PNG);
	}

	@Test
	void rejectsWrongSharedDimensions() throws IOException {
		Path directory = directory("wrong-shared");
		writePng(directory.resolve("cape_elytra.png"), 128, 64, BufferedImage.TYPE_INT_ARGB, 0xFF3790FF);

		assertSingleIssue(directory, CapeValidationErrorCode.INVALID_SHARED_DIMENSIONS);
	}

	@Test
	void rejectsOversizedSharedBeforePngValidation() throws IOException {
		Path directory = directory("large-shared");
		Files.write(directory.resolve("cape_elytra.png"), new byte[CapeAssetLimits.MAX_ASSET_BYTES + 1]);

		assertSingleIssue(directory, CapeValidationErrorCode.SHARED_FILE_TOO_LARGE);
	}

	@Test
	void acceptsSharedAtExactByteLimit() throws IOException {
		Path directory = directory("limit-shared");
		Path shared = writeValid(directory.resolve("cape_elytra.png"));
		Files.write(shared, Arrays.copyOf(Files.readAllBytes(shared), CapeAssetLimits.MAX_ASSET_BYTES));

		assertLayout(requireDefinition(directory), "SHARED");
	}

	@Test
	void producesTwoRolesFromSameUnchangedPhysicalContent() throws IOException {
		Path directory = directory("shared");
		Path shared = writeValid(directory.resolve("cape_elytra.png"));
		byte[] original = Files.readAllBytes(shared);
		CapeCosmeticDefinition definition = requireDefinition(directory);
		CapeTextureAsset cape = definition.cape();
		CapeTextureAsset elytra = definition.elytra().orElseThrow();

		assertEquals(shared, cape.source());
		assertEquals(cape.source(), elytra.source());
		assertEquals(CapeAssetHash.sha256(original), cape.sha256());
		assertEquals(cape.sha256(), elytra.sha256());
		assertEquals(CapeTextureType.CAPE, cape.type());
		assertEquals(CapeTextureType.ELYTRA, elytra.type());
		assertEquals(64, cape.width());
		assertEquals(32, elytra.height());
		assertArrayEquals(original, Files.readAllBytes(shared));
		try (var files = Files.list(directory)) {
			assertEquals(List.of("cape_elytra.png"), files.map(path -> path.getFileName().toString()).toList());
		}
	}

	@Test
	void transparentElytraUvStillDeclaresCustomElytra() throws IOException {
		Path directory = directory("transparent-wings");
		// helper 只绘制 (0, 0)，Elytra 的整个 UV 区保持透明。
		writeValid(directory.resolve("cape_elytra.png"));
		CapeCosmeticDefinition definition = requireDefinition(directory);
		CapeCosmeticMetadata metadata = metadata(definition);

		assertTrue(definition.elytra().isPresent());
		assertTrue(metadata.hasElytra());
		assertEquals(Optional.of(metadata.capeSha256()), metadata.elytraSha256());
	}

	@Test
	void sharedMetadataRetainsBothSlotsWithOneHash() throws IOException {
		Path directory = directory("metadata-shared");
		writeValid(directory.resolve("cape_elytra.png"));
		CapeCosmeticMetadata metadata = metadata(requireDefinition(directory));

		assertEquals(Optional.of(metadata.capeSha256()), metadata.elytraSha256());
	}

	@Test
	void sharedAssetIndexContainsOneReadableContent() throws IOException {
		Path directory = directory("index-shared");
		Path shared = writeValid(directory.resolve("cape_elytra.png"));
		CapeCosmeticDefinition definition = requireDefinition(directory);
		CapeAssetIndex index = CapeAssetIndex.from(new CapeRegistry(List.of(definition)));

		assertEquals(1, index.size());
		assertArrayEquals(Files.readAllBytes(shared),
				CapeAssetReader.readVerified(index.find(definition.elytra().orElseThrow().sha256()).orElseThrow())
						.bytes().orElseThrow());
	}

	@Test
	void capeOnlyMetadataHasNoElytra() throws IOException {
		Path directory = directory("metadata-cape");
		writeValid(directory.resolve("cape.png"));

		assertTrue(metadata(requireDefinition(directory)).elytraSha256().isEmpty());
	}

	@Test
	void splitDifferentContentKeepsIndependentHashes() throws IOException {
		Path directory = directory("split-different");
		writeValid(directory.resolve("cape.png"));
		writePng(directory.resolve("elytra.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, 0xFFFF9037);
		CapeCosmeticDefinition definition = requireDefinition(directory);

		assertNotEquals(definition.cape().sha256(), definition.elytra().orElseThrow().sha256());
		assertEquals(2, CapeAssetIndex.from(new CapeRegistry(List.of(definition))).size());
	}

	@Test
	void splitIdenticalContentKeepsTwoRolesAndDistinctPaths() throws IOException {
		Path directory = directory("split-identical");
		Path cape = writeValid(directory.resolve("cape.png"));
		Files.copy(cape, directory.resolve("elytra.png"));
		CapeCosmeticDefinition definition = requireDefinition(directory);

		assertLayout(definition, "SPLIT");
		assertNotEquals(definition.cape().source(), definition.elytra().orElseThrow().source());
		assertEquals(definition.cape().sha256(), definition.elytra().orElseThrow().sha256());
		assertEquals(Optional.of(metadata(definition).capeSha256()), metadata(definition).elytraSha256());
		assertEquals(1, CapeAssetIndex.from(new CapeRegistry(List.of(definition))).size());
	}

	@Test
	void wrongCaseSharedNameIsNotRecognized() throws IOException {
		Path directory = directory("case-shared");
		writeValid(directory.resolve("Cape_Elytra.png"));
		assertSingleIssue(directory, CapeValidationErrorCode.MISSING_CAPE);
		writeValid(directory.resolve("cape.png"));

		assertLayout(requireDefinition(directory), "CAPE_ONLY");
	}

	private Path directory(String id) throws IOException {
		return Files.createDirectory(temporaryDirectory.resolve(id));
	}

	private static Path writeValid(Path path) throws IOException {
		return writePng(path, 64, 32, BufferedImage.TYPE_INT_ARGB, 0xFF3790FF);
	}

	private CapeCosmeticDefinition requireDefinition(Path directory) {
		CapeCosmeticValidationResult result = validator.validate(directory);
		assertTrue(result.isSuccess(), () -> "合法布局不应被拒绝：" + result.issues());
		return result.definition().orElseThrow();
	}

	private void assertSingleIssue(Path directory, CapeValidationErrorCode code) {
		CapeCosmeticValidationResult result = validator.validate(directory);
		assertFalse(result.isSuccess());
		assertTrue(result.definition().isEmpty());
		assertEquals(List.of(code), result.issues().stream().map(CapeValidationIssue::code).toList());
		if (code.name().contains("SHARED")) {
			assertEquals(directory.resolve("cape_elytra.png"), result.issues().getFirst().path());
		}
	}

	private static CapeCosmeticMetadata metadata(CapeCosmeticDefinition definition) {
		return CapeRegistrySnapshot.from(new CapeRegistry(List.of(definition))).entries().getFirst();
	}

	private static void assertLayout(CapeCosmeticDefinition definition, String layout) {
		assertEquals(CapeTextureType.CAPE, definition.cape().type());
		if (layout.equals("CAPE_ONLY")) {
			assertEquals("cape.png", definition.cape().source().getFileName().toString());
			assertTrue(definition.elytra().isEmpty());
		} else {
			CapeTextureAsset elytra = definition.elytra().orElseThrow();
			assertEquals(CapeTextureType.ELYTRA, elytra.type());
			if (layout.equals("SHARED")) {
				assertEquals("cape_elytra.png", definition.cape().source().getFileName().toString());
				assertEquals(definition.cape().source(), elytra.source());
			} else {
				assertEquals("cape.png", definition.cape().source().getFileName().toString());
				assertEquals("elytra.png", elytra.source().getFileName().toString());
			}
		}
	}
}
