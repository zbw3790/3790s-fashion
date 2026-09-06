package vanillafashion.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapeAssetReaderTest {
	@TempDir
	Path tempDirectory;

	@Test
	void readsBytesWhenCurrentHashMatchesRegistry() throws IOException {
		byte[] bytes = {1, 2, 3, 4};
		Path path = Files.write(tempDirectory.resolve("cape.png"), bytes);
		CapeTextureAsset asset = asset(path, CapeAssetHash.sha256(bytes));

		CapeAssetReadResult result = CapeAssetReader.readVerified(asset);

		assertEquals(CapeAssetReadResult.Status.SUCCESS, result.status());
		assertArrayEquals(bytes, result.bytes().orElseThrow());
	}

	@Test
	void rejectsFileChangedAfterRegistryLoad() throws IOException {
		byte[] originalBytes = {1, 2, 3, 4};
		Path path = Files.write(tempDirectory.resolve("cape.png"), originalBytes);
		CapeTextureAsset asset = asset(path, CapeAssetHash.sha256(originalBytes));
		Files.write(path, new byte[] {4, 3, 2, 1});

		CapeAssetReadResult result = CapeAssetReader.readVerified(asset);

		assertEquals(CapeAssetReadResult.Status.HASH_MISMATCH, result.status());
		assertTrue(result.bytes().isEmpty());
	}

	private static CapeTextureAsset asset(Path path, String hash) {
		return new CapeTextureAsset(path, hash, 64, 32, CapeTextureType.CAPE);
	}
}
