package vanillafashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vanillafashion.cape.CapeAssetHash;

class ClientCapeAssetStoreTest {
	@Test
	void acceptsValidHashAndPngBytes() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] png = png(64, 32, 0xFFFF0000);
		String hash = CapeAssetHash.sha256(png);

		assertEquals(ClientCapeAssetStore.StoreResult.STORED, store.store(hash, png));
		assertTrue(store.contains(hash));
		assertArrayEquals(png, store.find(hash).orElseThrow());
	}

	@Test
	void rejectsWrongHash() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] png = png(64, 32, 0xFFFF0000);

		assertEquals(ClientCapeAssetStore.StoreResult.HASH_MISMATCH, store.store("a".repeat(64), png));
		assertEquals(0, store.size());
	}

	@Test
	void rejectsInvalidPng() {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] bytes = {1, 2, 3};

		assertEquals(
				ClientCapeAssetStore.StoreResult.INVALID_PNG,
				store.store(CapeAssetHash.sha256(bytes), bytes)
		);
	}

	@Test
	void rejectsWrongDimensions() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] png = png(32, 32, 0xFFFF0000);

		assertEquals(
				ClientCapeAssetStore.StoreResult.INVALID_DIMENSIONS,
				store.store(CapeAssetHash.sha256(png), png)
		);
	}

	@Test
	void retainRemovesUnreferencedAssets() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] first = png(64, 32, 0xFFFF0000);
		byte[] second = png(64, 32, 0xFF0000FF);
		String firstHash = CapeAssetHash.sha256(first);
		String secondHash = CapeAssetHash.sha256(second);
		store.store(firstHash, first);
		store.store(secondHash, second);

		store.retain(Set.of(secondHash));

		assertFalse(store.contains(firstHash));
		assertTrue(store.contains(secondHash));
		assertEquals(1, store.size());
	}

	@Test
	void clearRemovesAllAssets() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] png = png(64, 32, 0xFFFF0000);
		store.store(CapeAssetHash.sha256(png), png);

		store.clear();

		assertEquals(0, store.size());
	}

	@Test
	void inputAndOutputUseDefensiveCopies() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] png = png(64, 32, 0xFFFF0000);
		byte[] expected = png.clone();
		String hash = CapeAssetHash.sha256(png);
		store.store(hash, png);
		png[0] = 0;
		byte[] found = store.find(hash).orElseThrow();
		found[1] = 0;

		assertArrayEquals(expected, store.find(hash).orElseThrow());
	}

	static byte[] png(int width, int height, int color) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, color);
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		if (!ImageIO.write(image, "PNG", output)) {
			throw new IOException("测试环境无法生成 PNG。");
		}

		return output.toByteArray();
	}
}
