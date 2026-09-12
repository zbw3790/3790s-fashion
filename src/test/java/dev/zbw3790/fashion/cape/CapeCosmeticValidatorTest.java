package dev.zbw3790.fashion.cape;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapeCosmeticValidatorTest {
	private static final int RED = 0xFFFF0000;
	private static final int BLUE = 0xFF0000FF;
	private final CapeCosmeticValidator validator = new CapeCosmeticValidator();

	@TempDir
	Path tempDirectory;

	@Test
	void loadsCapeOnlyCosmetic() throws IOException {
		Path directory = createCosmeticDirectory("founder");
		Path capePath = writePng(
				directory.resolve("cape.png"),
				64,
				32,
				BufferedImage.TYPE_INT_ARGB,
				RED
		);

		CapeCosmeticDefinition definition = requireDefinition(validator.validate(directory));

		assertEquals(new CapeId("founder"), definition.id());
		assertEquals(capePath, definition.cape().source());
		assertEquals(CapeTextureType.CAPE, definition.cape().type());
		assertEquals(64, definition.cape().width());
		assertEquals(32, definition.cape().height());
		assertTrue(definition.elytra().isEmpty());
		assertTrue(Files.size(capePath) < CapeAssetLimits.MAX_ASSET_BYTES);
	}

	@Test
	void rejectsCapeAboveAssetSizeLimit() throws IOException {
		Path directory = createCosmeticDirectory("oversized-cape");
		Files.write(
				directory.resolve("cape.png"),
				new byte[CapeAssetLimits.MAX_ASSET_BYTES + 1]
		);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.CAPE_FILE_TOO_LARGE);
	}

	@Test
	void rejectsElytraAboveAssetSizeLimit() throws IOException {
		Path directory = createCosmeticDirectory("oversized-elytra");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Files.write(
				directory.resolve("elytra.png"),
				new byte[CapeAssetLimits.MAX_ASSET_BYTES + 1]
		);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.ELYTRA_FILE_TOO_LARGE);
	}

	@Test
	void loadsCapeAndElytraCosmetic() throws IOException {
		Path directory = createCosmeticDirectory("builder");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Path elytraPath = writePng(
				directory.resolve("elytra.png"),
				64,
				32,
				BufferedImage.TYPE_INT_ARGB,
				BLUE
		);

		CapeCosmeticDefinition definition = requireDefinition(validator.validate(directory));

		assertTrue(definition.elytra().isPresent());
		assertEquals(elytraPath, definition.elytra().orElseThrow().source());
		assertEquals(CapeTextureType.ELYTRA, definition.elytra().orElseThrow().type());
	}

	@Test
	void failsWhenCapeIsMissing() throws IOException {
		Path directory = createCosmeticDirectory("missing");

		assertIssue(validator.validate(directory), CapeValidationErrorCode.MISSING_CAPE);
	}

	@Test
	void failsWhenOnlyElytraExists() throws IOException {
		Path directory = createCosmeticDirectory("elytra-only");
		writePng(directory.resolve("elytra.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, BLUE);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.MISSING_CAPE);
	}

	@Test
	void rejectsWrongCapeDimensions() throws IOException {
		Path directory = createCosmeticDirectory("wide-cape");
		writePng(directory.resolve("cape.png"), 128, 64, BufferedImage.TYPE_INT_ARGB, RED);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.INVALID_CAPE_DIMENSIONS);
	}

	@Test
	void rejectsWrongElytraDimensions() throws IOException {
		Path directory = createCosmeticDirectory("wide-elytra");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		writePng(directory.resolve("elytra.png"), 128, 64, BufferedImage.TYPE_INT_ARGB, BLUE);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.INVALID_ELYTRA_DIMENSIONS);
	}

	@Test
	void rejectsJpegContentNamedAsPng() throws IOException {
		Path directory = createCosmeticDirectory("renamed-jpeg");
		writeImage(
				directory.resolve("cape.png"),
				"JPEG",
				64,
				32,
				BufferedImage.TYPE_INT_RGB,
				RED
		);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.INVALID_CAPE_PNG);
	}

	@Test
	void rejectsCorruptedPngWithValidSignature() throws IOException {
		Path directory = createCosmeticDirectory("corrupted");
		Files.write(directory.resolve("cape.png"), new byte[] {
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02
		});

		assertIssue(validator.validate(directory), CapeValidationErrorCode.INVALID_CAPE_PNG);
	}

	@Test
	void producesStableLowercaseSha256() throws IOException {
		Path directory = createCosmeticDirectory("stable-hash");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);

		String firstHash = requireDefinition(validator.validate(directory)).cape().sha256();
		String secondHash = requireDefinition(validator.validate(directory)).cape().sha256();

		assertTrue(firstHash.matches("[0-9a-f]{64}"));
		assertEquals(firstHash, secondHash);
	}

	@Test
	void sameFileContentProducesSameSha256() throws IOException {
		Path firstDirectory = createCosmeticDirectory("same-a");
		Path secondDirectory = createCosmeticDirectory("same-b");
		Path firstCape = writePng(
				firstDirectory.resolve("cape.png"),
				64,
				32,
				BufferedImage.TYPE_INT_ARGB,
				RED
		);
		Files.copy(firstCape, secondDirectory.resolve("cape.png"));

		String firstHash = requireDefinition(validator.validate(firstDirectory)).cape().sha256();
		String secondHash = requireDefinition(validator.validate(secondDirectory)).cape().sha256();

		assertEquals(firstHash, secondHash);
	}

	@Test
	void changedPngContentChangesSha256() throws IOException {
		Path directory = createCosmeticDirectory("changed-hash");
		Path capePath = writePng(
				directory.resolve("cape.png"),
				64,
				32,
				BufferedImage.TYPE_INT_ARGB,
				RED
		);
		String firstHash = requireDefinition(validator.validate(directory)).cape().sha256();

		writePng(capePath, 64, 32, BufferedImage.TYPE_INT_ARGB, BLUE);
		String secondHash = requireDefinition(validator.validate(directory)).cape().sha256();

		assertNotEquals(firstHash, secondHash);
	}

	@Test
	void ignoresUnrelatedFiles() throws IOException {
		Path directory = createCosmeticDirectory("extra-files");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Files.writeString(directory.resolve("note.txt"), "该文件应被忽略。");
		Files.write(directory.resolve("preview.png"), new byte[] {0x00, 0x01});
		Files.createDirectory(directory.resolve("nested"));

		assertTrue(validator.validate(directory).isSuccess());
	}

	@Test
	void rejectsNonDirectoryInput() throws IOException {
		Path file = Files.createFile(tempDirectory.resolve("not-a-directory"));

		assertIssue(validator.validate(file), CapeValidationErrorCode.NOT_A_DIRECTORY);
	}

	@Test
	void rejectsInvalidDirectoryId() throws IOException {
		Path directory = createCosmeticDirectory("Invalid");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.INVALID_ID);
	}

	@Test
	void rejectsCapeDirectory() throws IOException {
		Path directory = createCosmeticDirectory("cape-directory");
		Files.createDirectory(directory.resolve("cape.png"));

		assertIssue(validator.validate(directory), CapeValidationErrorCode.CAPE_NOT_REGULAR_FILE);
	}

	@Test
	void rejectsElytraDirectory() throws IOException {
		Path directory = createCosmeticDirectory("elytra-directory");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		Files.createDirectory(directory.resolve("elytra.png"));

		assertIssue(validator.validate(directory), CapeValidationErrorCode.ELYTRA_NOT_REGULAR_FILE);
	}

	@Test
	void rejectsWrongCaseCapeFileName() throws IOException {
		Path directory = createCosmeticDirectory("wrong-case-cape");
		writePng(directory.resolve("Cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);

		assertIssue(validator.validate(directory), CapeValidationErrorCode.MISSING_CAPE);
	}

	@Test
	void ignoresWrongCaseElytraFileName() throws IOException {
		Path directory = createCosmeticDirectory("wrong-case-elytra");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		writePng(directory.resolve("Elytra.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, BLUE);

		CapeCosmeticDefinition definition = requireDefinition(validator.validate(directory));

		assertTrue(definition.elytra().isEmpty());
	}

	@Test
	void acceptsGrayscalePng() throws IOException {
		Path directory = createCosmeticDirectory("grayscale");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_BYTE_GRAY, RED);

		assertTrue(validator.validate(directory).isSuccess());
	}

	@Test
	void acceptsIndexedElytraPng() throws IOException {
		Path directory = createCosmeticDirectory("indexed");
		writePng(directory.resolve("cape.png"), 64, 32, BufferedImage.TYPE_INT_ARGB, RED);
		writePng(directory.resolve("elytra.png"), 64, 32, BufferedImage.TYPE_BYTE_INDEXED, BLUE);

		assertTrue(validator.validate(directory).isSuccess());
	}

	private Path createCosmeticDirectory(String id) throws IOException {
		return Files.createDirectory(tempDirectory.resolve(id));
	}

	private static CapeCosmeticDefinition requireDefinition(CapeCosmeticValidationResult result) {
		assertTrue(result.isSuccess());
		assertTrue(result.issues().isEmpty());
		return result.definition().orElseThrow();
	}

	private static void assertIssue(
			CapeCosmeticValidationResult result,
			CapeValidationErrorCode expectedCode
	) {
		assertFalse(result.isSuccess());
		assertTrue(result.definition().isEmpty());
		assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == expectedCode));
	}
}
