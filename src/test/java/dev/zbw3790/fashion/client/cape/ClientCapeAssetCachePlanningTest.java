package dev.zbw3790.fashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;
import dev.zbw3790.fashion.network.CapeAssetDataPayload;

class ClientCapeAssetCachePlanningTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void diskCacheHitProducesNoNetworkRequest() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		cache.storeValidated(hash, png);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = new ClientCapeAssetSync(store, cache);

		assertTrue(sync.plan(snapshot(hash)).isEmpty());
		assertEquals(1, sync.lastCacheHitCount());
		assertTrue(store.contains(hash));
	}

	@Test
	void mixedMemoryCacheAndMissRequestsOnlyTrueMiss() throws IOException {
		ClientCapeAssetCache cache = cache();
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] memoryPng = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		byte[] cachePng = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String memoryHash = CapeAssetHash.sha256(memoryPng);
		String cacheHash = CapeAssetHash.sha256(cachePng);
		String missingHash = "f".repeat(64);
		store.store(memoryHash, memoryPng);
		cache.storeValidated(cacheHash, cachePng);
		ClientCapeAssetSync sync = new ClientCapeAssetSync(store, cache);
		CapeRegistrySnapshot snapshot = new CapeRegistrySnapshot(List.of(
				metadata("memory", memoryHash),
				metadata("cache", cacheHash),
				metadata("missing", missingHash)
		));

		var requests = sync.plan(snapshot);

		assertEquals(1, requests.size());
		assertEquals(List.of(missingHash), requests.getFirst().sha256Hashes());
		assertEquals(1, sync.lastCacheHitCount());
	}

	@Test
	void receivedAssetIsReusableByFreshConnectionStore() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		ClientCapeAssetSync first = new ClientCapeAssetSync(new ClientCapeAssetStore(), cache);
		var request = first.plan(snapshot(hash)).getFirst();
		first.markRequested(request);
		assertEquals(
				ClientCapeAssetSync.ReceiveResult.STORED,
				first.receive(new CapeAssetDataPayload(hash, png))
		);

		ClientCapeAssetSync second = new ClientCapeAssetSync(new ClientCapeAssetStore(), cache);

		assertTrue(second.plan(snapshot(hash)).isEmpty());
		assertEquals(1, second.lastCacheHitCount());
	}

	@Test
	void clearingConnectionStateDoesNotDeleteDiskCache() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		cache.storeValidated(hash, png);
		ClientCapeAssetSync sync = new ClientCapeAssetSync(new ClientCapeAssetStore(), cache);
		sync.plan(snapshot(hash));

		sync.clear();

		assertTrue(cache.findValidated(hash).isPresent());
	}

	@Test
	void corruptCacheEntryFallsBackToNetworkRequest() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] expected = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(expected);
		Path path = cache.pathFor(hash);
		Files.createDirectories(path.getParent());
		Files.write(path, new byte[] {1, 2, 3});
		ClientCapeAssetSync sync = new ClientCapeAssetSync(new ClientCapeAssetStore(), cache);

		assertEquals(List.of(hash), sync.plan(snapshot(hash)).getFirst().sha256Hashes());
		assertEquals(0, sync.lastCacheHitCount());
	}

	@Test
	void corruptCacheIsReplacedAfterValidatedNetworkRedownload() throws IOException {
		ClientCapeAssetCache cache = cache();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		Path path = cache.pathFor(hash);
		Files.createDirectories(path.getParent());
		Files.write(path, new byte[] {1, 2, 3});
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = new ClientCapeAssetSync(store, cache);
		var request = sync.plan(snapshot(hash)).getFirst();
		sync.markRequested(request);

		assertEquals(
				ClientCapeAssetSync.ReceiveResult.STORED,
				sync.receive(new CapeAssetDataPayload(hash, png))
		);
		assertArrayEquals(png, cache.findValidated(hash).orElseThrow());
		assertTrue(sync.pendingHashes().isEmpty());
	}

	@Test
	void unwritableCacheStillKeepsValidatedNetworkAssetInMemory() throws IOException {
		Path blockingFile = temporaryDirectory.resolve("缓存目录占位文件");
		Files.writeString(blockingFile, "阻止创建下级目录");
		ClientCapeAssetCache cache = new ClientCapeAssetCache(
				blockingFile.resolve("assets"), NOPLogger.NOP_LOGGER);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = new ClientCapeAssetSync(store, cache);
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		String hash = CapeAssetHash.sha256(png);
		var request = sync.plan(snapshot(hash)).getFirst();
		sync.markRequested(request);

		assertEquals(ClientCapeAssetSync.ReceiveResult.STORED,
				sync.receive(new CapeAssetDataPayload(hash, png)));
		assertTrue(store.contains(hash));
		assertArrayEquals(png, store.find(hash).orElseThrow());
		assertEquals(ClientCapeAssetCache.StoreResult.WRITE_FAILED,
				sync.lastCacheStoreResult().orElseThrow());
		assertTrue(sync.pendingHashes().isEmpty());
	}

	private ClientCapeAssetCache cache() {
		return new ClientCapeAssetCache(
				temporaryDirectory.resolve("assets"),
				NOPLogger.NOP_LOGGER
		);
	}

	private static CapeRegistrySnapshot snapshot(String hash) {
		return new CapeRegistrySnapshot(List.of(metadata("cape", hash)));
	}

	private static CapeCosmeticMetadata metadata(String id, String hash) {
		return new CapeCosmeticMetadata(new CapeId(id), hash, Optional.empty());
	}
}
