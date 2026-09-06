package vanillafashion.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapeAssetIndexTest {
	private static final String CAPE_HASH = "a".repeat(64);
	private static final String ELYTRA_HASH = "b".repeat(64);

	@TempDir
	Path tempDirectory;

	@Test
	void findsCapeHash() {
		CapeTextureAsset cape = asset("cape.png", CAPE_HASH, CapeTextureType.CAPE);
		CapeAssetIndex index = CapeAssetIndex.from(new CapeRegistry(List.of(definition("cape_only", cape))));

		assertSame(cape, index.find(CAPE_HASH).orElseThrow());
	}

	@Test
	void findsElytraHash() {
		CapeTextureAsset cape = asset("cape.png", CAPE_HASH, CapeTextureType.CAPE);
		CapeTextureAsset elytra = asset("elytra.png", ELYTRA_HASH, CapeTextureType.ELYTRA);
		CapeAssetIndex index = CapeAssetIndex.from(new CapeRegistry(List.of(
				new CapeCosmeticDefinition(new CapeId("full"), cape, Optional.of(elytra))
		)));

		assertSame(elytra, index.find(ELYTRA_HASH).orElseThrow());
	}

	@Test
	void deduplicatesIdenticalContentHash() {
		CapeTextureAsset first = asset("first.png", CAPE_HASH, CapeTextureType.CAPE);
		CapeTextureAsset second = asset("second.png", CAPE_HASH, CapeTextureType.CAPE);
		CapeAssetIndex index = CapeAssetIndex.from(new CapeRegistry(List.of(
				definition("first", first),
				definition("second", second)
		)));

		assertEquals(1, index.size());
		assertSame(first, index.find(CAPE_HASH).orElseThrow());
		assertTrue(index.find("c".repeat(64)).isEmpty());
	}

	private CapeCosmeticDefinition definition(String id, CapeTextureAsset cape) {
		return new CapeCosmeticDefinition(new CapeId(id), cape, Optional.empty());
	}

	private CapeTextureAsset asset(String fileName, String hash, CapeTextureType type) {
		return new CapeTextureAsset(tempDirectory.resolve(fileName), hash, 64, 32, type);
	}
}
