package dev.zbw3790.fashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeAssetLimits;

class ClientCapeAssetCacheTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void resolvesContentAddressedDirectoryFromGameDirectory() {
		ClientCapeAssetCache cache = ClientCapeAssetCache.fromGameDirectory(
				temporaryDirectory,
				NOPLogger.NOP_LOGGER
		);

		assertEquals(
				temporaryDirectory.resolve("3790s-fashion/cache/assets").toAbsolutePath().normalize(),
				cache.assetsDirectory()
		);
	}

	@Test
	void missingHashReturnsCacheMiss() {
		assertTrue(cache().findValidated("a".repeat(64)).isEmpty());
	}

	@Test
	void storesAndReadsValidatedPng() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);

		assertTrue(Set.of(
				ClientCapeAssetCache.StoreResult.STORED_ATOMIC,
				ClientCapeAssetCache.StoreResult.STORED_FALLBACK
		).contains(cache.storeValidated(hash, png)));
		assertArrayEquals(png, cache.findValidated(hash).orElseThrow());
	}

	@Test
	void reusesExistingValidatedContent() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		cache.storeValidated(hash, png);

		assertEquals(
				ClientCapeAssetCache.StoreResult.ALREADY_PRESENT,
				cache.storeValidated(hash, png)
		);
	}

	@Test
	void rejectsBytesWhoseContentHashDoesNotMatch() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);

		assertEquals(
				ClientCapeAssetCache.StoreResult.REJECTED_INVALID,
				cache.storeValidated("a".repeat(64), png)
		);
	}

	@Test
	void deletesCachedFileWithWrongContentHash() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] expected = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		byte[] wrong = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		String hash = CapeAssetHash.sha256(expected);
		Path path = cache.pathFor(hash);
		Files.createDirectories(path.getParent());
		Files.write(path, wrong);

		assertTrue(cache.findValidated(hash).isEmpty());
		assertFalse(Files.exists(path));
	}

	@Test
	void deletesCachedFileWithInvalidPng() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] bytes = {1, 2, 3};
		String hash = CapeAssetHash.sha256(bytes);
		Path path = cache.pathFor(hash);
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);

		assertTrue(cache.findValidated(hash).isEmpty());
		assertFalse(Files.exists(path));
	}

	@Test
	void deletesCachedFileWithWrongDimensions() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(32, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		Path path = cache.pathFor(hash);
		Files.createDirectories(path.getParent());
		Files.write(path, png);

		assertTrue(cache.findValidated(hash).isEmpty());
		assertFalse(Files.exists(path));
	}

	@Test
	void deletesOversizedCachedFileBeforeDecoding() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] bytes = new byte[CapeAssetLimits.MAX_ASSET_BYTES + 1];
		String hash = CapeAssetHash.sha256(bytes);
		Path path = cache.pathFor(hash);
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);

		assertTrue(cache.findValidated(hash).isEmpty());
		assertFalse(Files.exists(path));
	}

	@Test
	void safeWriteLeavesNoTemporaryFiles() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		cache.storeValidated(CapeAssetHash.sha256(png), png);

		try (var files = Files.list(cache.assetsDirectory())) {
			assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
		}
	}

	private ClientCapeAssetCache cache() {
		return new ClientCapeAssetCache(
				temporaryDirectory.resolve("assets"),
				NOPLogger.NOP_LOGGER
		);
	}
}
