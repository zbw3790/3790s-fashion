package dev.zbw3790.fashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;
import dev.zbw3790.fashion.network.CapeAssetDataPayload;

class ClientCapeSnapshotTransitionTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void sameIdNewCapeHashRemovesOldMemoryAndRequestsNewHash() throws IOException {
		Asset oldCape = asset(0xFF3790FF);
		Asset newCape = asset(0xFF123080);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		store.store(oldCape.hash(), oldCape.bytes());
		ClientCapeAssetSync sync = sync(store, cache());
		sync.plan(snapshot(metadata("m4c_full", oldCape.hash(), Optional.empty())));

		var requests = sync.plan(snapshot(metadata("m4c_full", newCape.hash(), Optional.empty())));

		assertEquals(Set.of(newCape.hash()), sync.requiredHashes());
		assertEquals(List.of(newCape.hash()), requests.getFirst().sha256Hashes());
		assertFalse(store.contains(oldCape.hash()));
	}

	@Test
	void sameIdNewElytraHashRetainsCapeAndRequestsNewElytra() throws IOException {
		Asset cape = asset(0xFF3790FF);
		Asset oldElytra = asset(0xFF204080);
		Asset newElytra = asset(0xFF4060A0);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		store.store(cape.hash(), cape.bytes());
		store.store(oldElytra.hash(), oldElytra.bytes());
		ClientCapeAssetSync sync = sync(store, cache());
		sync.plan(snapshot(metadata("m4c_full", cape.hash(), Optional.of(oldElytra.hash()))));

		var requests = sync.plan(snapshot(
				metadata("m4c_full", cape.hash(), Optional.of(newElytra.hash()))
		));

		assertEquals(Set.of(cape.hash(), newElytra.hash()), sync.requiredHashes());
		assertEquals(List.of(newElytra.hash()), requests.getFirst().sha256Hashes());
		assertTrue(store.contains(cape.hash()));
		assertFalse(store.contains(oldElytra.hash()));
	}

	@Test
	void cosmeticRemovalClearsRequiredMemoryAndPending() throws IOException {
		Asset cape = asset(0xFF3790FF);
		String missingElytra = "e".repeat(64);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		store.store(cape.hash(), cape.bytes());
		ClientCapeAssetSync sync = sync(store, cache());
		var request = sync.plan(snapshot(
				metadata("m4c_full", cape.hash(), Optional.of(missingElytra))
		)).getFirst();
		sync.markRequested(request);

		sync.plan(CapeRegistrySnapshot.empty());

		assertTrue(sync.requiredHashes().isEmpty());
		assertTrue(sync.pendingHashes().isEmpty());
		assertEquals(0, store.size());
	}

	@Test
	void anotherCosmeticReferenceRetainsOldHashInMemory() throws IOException {
		Asset sharedCape = asset(0xFF3790FF);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		store.store(sharedCape.hash(), sharedCape.bytes());
		ClientCapeAssetSync sync = sync(store, cache());
		sync.plan(snapshot(
				metadata("first", sharedCape.hash(), Optional.empty()),
				metadata("second", sharedCape.hash(), Optional.empty())
		));

		sync.plan(snapshot(metadata("second", sharedCape.hash(), Optional.empty())));

		assertEquals(Set.of(sharedCape.hash()), sync.requiredHashes());
		assertTrue(store.contains(sharedCape.hash()));
	}

	@Test
	void removingOneSharedReferenceDoesNotReleaseTexture() throws IOException {
		Asset sharedCape = asset(0xFF3790FF);
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore(), cache());
		ClientCapeTextureRegistry textures = new ClientCapeTextureRegistry();
		Identifier identifier = ClientCapeTextureManager.identifierFor(sharedCape.hash());
		textures.register(sharedCape.hash(), identifier);
		sync.plan(snapshot(
				metadata("first", sharedCape.hash(), Optional.empty()),
				metadata("second", sharedCape.hash(), Optional.empty())
		));

		sync.plan(snapshot(metadata("second", sharedCape.hash(), Optional.empty())));

		assertTrue(textures.retain(sync.requiredHashes()).isEmpty());
		assertEquals(identifier, textures.find(sharedCape.hash()).orElseThrow());
	}

	@Test
	void removingLastReferenceReleasesTexture() throws IOException {
		Asset cape = asset(0xFF3790FF);
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore(), cache());
		ClientCapeTextureRegistry textures = new ClientCapeTextureRegistry();
		Identifier identifier = ClientCapeTextureManager.identifierFor(cape.hash());
		textures.register(cape.hash(), identifier);
		sync.plan(snapshot(metadata("m4c_full", cape.hash(), Optional.empty())));

		sync.plan(CapeRegistrySnapshot.empty());

		assertEquals(List.of(identifier), textures.retain(sync.requiredHashes()));
		assertTrue(textures.find(cape.hash()).isEmpty());
	}

	@Test
	void replacementClearsOldPendingAndRequestsNewHash() {
		String oldHash = "a".repeat(64);
		String newHash = "b".repeat(64);
		ClientCapeAssetSync sync = sync(new ClientCapeAssetStore(), cache());
		var oldRequest = sync.plan(snapshot(metadata(
				"m4c_full",
				oldHash,
				Optional.empty()
		))).getFirst();
		sync.markRequested(oldRequest);

		var newRequests = sync.plan(snapshot(metadata(
				"m4c_full",
				newHash,
				Optional.empty()
		)));

		assertTrue(sync.pendingHashes().isEmpty());
		assertEquals(List.of(newHash), newRequests.getFirst().sha256Hashes());
	}

	@Test
	void cachedReplacementHashNeedsNoNetworkRequest() throws IOException {
		Asset replacement = asset(0xFF123080);
		ClientCapeAssetCache cache = cache();
		cache.storeValidated(replacement.hash(), replacement.bytes());
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store, cache);

		assertTrue(sync.plan(snapshot(metadata(
				"m4c_full",
				replacement.hash(),
				Optional.empty()
		))).isEmpty());
		assertTrue(store.contains(replacement.hash()));
		assertEquals(1, sync.lastCacheHitCount());
	}

	@Test
	void oldDiskCacheCannotActivateNewSnapshotHash() throws IOException {
		Asset oldCape = asset(0xFF3790FF);
		Asset newCape = asset(0xFF123080);
		ClientCapeAssetCache cache = cache();
		cache.storeValidated(oldCape.hash(), oldCape.bytes());
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store, cache);

		var requests = sync.plan(snapshot(metadata(
				"m4c_full",
				newCape.hash(),
				Optional.empty()
		)));

		assertEquals(List.of(newCape.hash()), requests.getFirst().sha256Hashes());
		assertFalse(store.contains(oldCape.hash()));
		assertTrue(cache.findValidated(oldCape.hash()).isPresent());
	}

	@Test
	void registryFullReplaceUpdatesSameIdMetadata() throws IOException {
		Asset oldCape = asset(0xFF3790FF);
		Asset newCape = asset(0xFF123080);
		ClientCapeRegistry registry = new ClientCapeRegistry();
		registry.replace(snapshot(metadata("m4c_full", oldCape.hash(), Optional.empty())));

		registry.replace(snapshot(metadata("m4c_full", newCape.hash(), Optional.empty())));

		assertEquals(
				newCape.hash(),
				registry.find(new CapeId("m4c_full")).orElseThrow().capeSha256()
		);
	}

	@Test
	void registryRemovalMakesDevelopmentSelectionLookupUnavailable() throws IOException {
		Asset cape = asset(0xFF3790FF);
		ClientCapeRegistry registry = new ClientCapeRegistry();
		registry.replace(snapshot(metadata("m4c_full", cape.hash(), Optional.empty())));

		registry.replace(CapeRegistrySnapshot.empty());

		assertTrue(registry.find(new CapeId("m4c_full")).isEmpty());
	}

	@Test
	void staleAssetDataIsRejectedAfterSnapshotReplacement() throws IOException {
		Asset oldCape = asset(0xFF3790FF);
		Asset newCape = asset(0xFF123080);
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store, cache());
		var request = sync.plan(snapshot(metadata(
				"m4c_full",
				oldCape.hash(),
				Optional.empty()
		))).getFirst();
		sync.markRequested(request);
		sync.plan(snapshot(metadata("m4c_full", newCape.hash(), Optional.empty())));

		assertEquals(
				ClientCapeAssetSync.ReceiveResult.NOT_REQUIRED,
				sync.receive(new CapeAssetDataPayload(oldCape.hash(), oldCape.bytes()))
		);
		assertFalse(store.contains(oldCape.hash()));
	}

	@Test
	void crossServerResetClearsConnectionStateButPreservesDiskCache() throws IOException {
		Asset serverACape = asset(0xFF3790FF);
		Asset serverBCape = asset(0xFF123080);
		ClientCapeAssetCache cache = cache();
		ClientCapeRegistry registry = new ClientCapeRegistry();
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store, cache);
		ClientCapeTextureRegistry textures = new ClientCapeTextureRegistry();
		registry.replace(snapshot(metadata("server_a", serverACape.hash(), Optional.empty())));
		var request = sync.plan(snapshot(metadata(
				"server_a",
				serverACape.hash(),
				Optional.empty()
		))).getFirst();
		sync.markRequested(request);
		textures.register(
				serverACape.hash(),
				ClientCapeTextureManager.identifierFor(serverACape.hash())
		);
		cache.storeValidated(serverACape.hash(), serverACape.bytes());

		registry.clear();
		store.clear();
		sync.clear();
		textures.clear();
		registry.replace(snapshot(metadata("server_b", serverBCape.hash(), Optional.empty())));
		var serverBRequests = sync.plan(snapshot(metadata(
				"server_b",
				serverBCape.hash(),
				Optional.empty()
		)));

		assertTrue(registry.find(new CapeId("server_a")).isEmpty());
		assertTrue(registry.find(new CapeId("server_b")).isPresent());
		assertFalse(store.contains(serverACape.hash()));
		assertFalse(sync.pendingHashes().contains(serverACape.hash()));
		assertTrue(textures.find(serverACape.hash()).isEmpty());
		assertTrue(cache.findValidated(serverACape.hash()).isPresent());
		assertEquals(List.of(serverBCape.hash()), serverBRequests.getFirst().sha256Hashes());
	}

	@Test
	void lateDiskCacheHitClearsPendingWithoutDuplicateRequest() throws IOException {
		Asset cape = asset(0xFF3790FF);
		ClientCapeAssetCache cache = cache();
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetSync sync = sync(store, cache);
		var request = sync.plan(snapshot(metadata(
				"m4c_full",
				cape.hash(),
				Optional.empty()
		))).getFirst();
		sync.markRequested(request);
		cache.storeValidated(cape.hash(), cape.bytes());

		assertTrue(sync.plan(snapshot(metadata(
				"m4c_full",
				cape.hash(),
				Optional.empty()
		))).isEmpty());
		assertTrue(sync.pendingHashes().isEmpty());
		assertArrayEquals(cape.bytes(), store.find(cape.hash()).orElseThrow());
	}

	private ClientCapeAssetCache cache() {
		return new ClientCapeAssetCache(
				temporaryDirectory.resolve("assets"),
				NOPLogger.NOP_LOGGER
		);
	}

	private static ClientCapeAssetSync sync(
			ClientCapeAssetStore store,
			ClientCapeAssetCache cache
	) {
		return new ClientCapeAssetSync(store, cache);
	}

	private static CapeRegistrySnapshot snapshot(CapeCosmeticMetadata... entries) {
		return new CapeRegistrySnapshot(List.of(entries));
	}

	private static CapeCosmeticMetadata metadata(
			String id,
			String capeHash,
			Optional<String> elytraHash
	) {
		return new CapeCosmeticMetadata(new CapeId(id), capeHash, elytraHash);
	}

	private static Asset asset(int color) throws IOException {
		byte[] bytes = ClientCapeAssetStoreTest.png(64, 32, color);
		return new Asset(bytes, CapeAssetHash.sha256(bytes));
	}

	private record Asset(byte[] bytes, String hash) {
	}
}
