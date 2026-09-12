package dev.zbw3790.fashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import dev.zbw3790.fashion.network.CapeAssetRequestPayload;

class ClientCapeAssetSyncTest {
	@TempDir
	Path temporaryDirectory;
	private int cacheIndex;

	@Test
	void deduplicatesSnapshotHashes() {
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore());
		CapeRegistrySnapshot snapshot = new CapeRegistrySnapshot(List.of(
				metadata("first", hash(1), Optional.empty()),
				metadata("second", hash(1), Optional.of(hash(2)))
		));

		List<CapeAssetRequestPayload> requests = sync.plan(snapshot);

		assertEquals(2, sync.requiredHashes().size());
		assertEquals(List.of(hash(1), hash(2)), requests.getFirst().sha256Hashes());
	}

	@Test
	void doesNotRequestStoredAsset() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		String hash = CapeAssetHash.sha256(png);
		store.store(hash, png);
		ClientCapeAssetSync sync = sync(store);

		assertTrue(sync.plan(snapshot(hash)).isEmpty());
	}

	@Test
	void doesNotRequestPendingAssetTwice() {
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore());
		CapeRegistrySnapshot snapshot = snapshot(hash(1));
		CapeAssetRequestPayload request = sync.plan(snapshot).getFirst();
		sync.markRequested(request);

		assertTrue(sync.plan(snapshot).isEmpty());
		assertEquals(1, sync.pendingHashes().size());
	}

	@Test
	void splitsMoreThanSixtyFourMissingAssets() {
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore());
		List<CapeCosmeticMetadata> metadata = new ArrayList<>();

		for (int index = 0; index < 65; index++) {
			metadata.add(metadata("cape_" + index, hash(index + 1), Optional.empty()));
		}

		List<CapeAssetRequestPayload> requests = sync.plan(new CapeRegistrySnapshot(metadata));

		assertEquals(2, requests.size());
		assertEquals(64, requests.get(0).sha256Hashes().size());
		assertEquals(1, requests.get(1).sha256Hashes().size());
	}

	@Test
	void validPendingAssetIsStoredAndRemovedFromPending() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store);
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		String hash = CapeAssetHash.sha256(png);
		CapeAssetRequestPayload request = sync.plan(snapshot(hash)).getFirst();
		sync.markRequested(request);

		assertEquals(
				ClientCapeAssetSync.ReceiveResult.STORED,
				sync.receive(new CapeAssetDataPayload(hash, png))
		);
		assertTrue(sync.pendingHashes().isEmpty());
		assertTrue(store.contains(hash));
	}

	@Test
	void rejectsAssetNotReferencedByCurrentSnapshot() throws IOException {
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore());
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		String incomingHash = CapeAssetHash.sha256(png);
		sync.plan(snapshot(hash(2)));

		assertEquals(
				ClientCapeAssetSync.ReceiveResult.NOT_REQUIRED,
				sync.receive(new CapeAssetDataPayload(incomingHash, png))
		);
	}

	@Test
	void rejectsRequiredAssetThatWasNotRequested() throws IOException {
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore());
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		String incomingHash = CapeAssetHash.sha256(png);
		sync.plan(snapshot(incomingHash));

		assertEquals(
				ClientCapeAssetSync.ReceiveResult.NOT_PENDING,
				sync.receive(new CapeAssetDataPayload(incomingHash, png))
		);
	}

	@Test
	void emptySnapshotRetainsNothingAndClearsPending() throws IOException {
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store);
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFFFF0000);
		String storedHash = CapeAssetHash.sha256(png);
		store.store(storedHash, png);
		CapeAssetRequestPayload request = sync.plan(snapshot(hash(2))).getFirst();
		sync.markRequested(request);

		sync.plan(CapeRegistrySnapshot.empty());

		assertEquals(0, store.size());
		assertTrue(sync.requiredHashes().isEmpty());
		assertTrue(sync.pendingHashes().isEmpty());
	}

	private ClientCapeAssetSync sync(ClientCapeAssetStore store) {
		return new ClientCapeAssetSync(
				store,
				new ClientCapeAssetCache(
						temporaryDirectory.resolve("cache-" + cacheIndex++),
						NOPLogger.NOP_LOGGER
				)
		);
	}

	private static CapeRegistrySnapshot snapshot(String capeHash) {
		return new CapeRegistrySnapshot(List.of(metadata("cape", capeHash, Optional.empty())));
	}

	private static CapeCosmeticMetadata metadata(String id, String capeHash, Optional<String> elytraHash) {
		return new CapeCosmeticMetadata(new CapeId(id), capeHash, elytraHash);
	}

	private static String hash(int value) {
		return "%064x".formatted(value);
	}
}
