package vanillafashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeCosmeticValidator;
import vanillafashion.cape.CapeRegistry;
import vanillafashion.cape.CapeRegistrySnapshot;
import vanillafashion.client.render.ElytraTextureDecision;
import vanillafashion.network.CapeAssetDataPayload;

class ClientSharedCapeAssetsTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void sharedContentIsRequestedStoredAndCachedOnlyOnce() throws IOException {
		SharedAsset shared = sharedAsset();
		String hash = shared.metadata().capeSha256();
		ClientCapeAssetStore store = new ClientCapeAssetStore();
		ClientCapeAssetCache cache = new ClientCapeAssetCache(temporaryDirectory.resolve("cache"), NOPLogger.NOP_LOGGER);
		ClientCapeAssetSync sync = new ClientCapeAssetSync(store, cache);
		var requests = sync.plan(shared.snapshot());

		assertEquals(Set.of(hash), sync.requiredHashes());
		assertEquals(1, requests.size());
		assertEquals(List.of(hash), requests.getFirst().sha256Hashes());
		sync.markRequested(requests.getFirst());
		assertTrue(sync.plan(shared.snapshot()).isEmpty());

		assertEquals(ClientCapeAssetSync.ReceiveResult.STORED,
				sync.receive(new CapeAssetDataPayload(hash, shared.bytes())));
		assertEquals(1, store.size());
		assertArrayEquals(shared.bytes(), store.find(hash).orElseThrow());
		assertArrayEquals(shared.bytes(), cache.findValidated(hash).orElseThrow());
		assertTrue(sync.plan(shared.snapshot()).isEmpty());
		assertEquals(ClientCapeAssetSync.ReceiveResult.NOT_PENDING,
				sync.receive(new CapeAssetDataPayload(hash, shared.bytes())));
		try (var files = Files.list(cache.assetsDirectory())) {
			assertEquals(List.of(cache.pathFor(hash)), files.toList());
		}

		// 新连接只从同一份磁盘内容恢复，不因逻辑角色不同请求或缓存第二份。
		ClientCapeAssetStore reconnectedStore = new ClientCapeAssetStore();
		ClientCapeAssetSync reconnected = new ClientCapeAssetSync(reconnectedStore, cache);
		assertTrue(reconnected.plan(shared.snapshot()).isEmpty());
		assertEquals(1, reconnected.lastCacheHitCount());
		assertEquals(1, reconnectedStore.size());
	}

	@Test
	void bothResolversUseOneRegisteredIdentifier() throws IOException {
		SharedAsset shared = sharedAsset();
		String hash = shared.metadata().capeSha256();
		ClientCapeTextureRegistry registry = new ClientCapeTextureRegistry();
		Identifier identifier = ClientCapeTextureManager.identifierFor(hash);

		assertTrue(registry.register(hash, identifier));
		assertFalse(registry.register(shared.metadata().elytraSha256().orElseThrow(), identifier));
		ClientCapeTextureManager manager = new ClientCapeTextureManager(registry);
		ClientCapeTextureResolver resolver = new ClientCapeTextureResolver(manager);

		assertEquals(1, manager.size());
		assertEquals(identifier, resolver.resolveCape(shared.metadata()).orElseThrow());
		assertEquals(ElytraTextureDecision.Mode.CUSTOM_TEXTURE, resolver.resolveElytra(shared.metadata()).mode());
		assertEquals(identifier, resolver.resolveElytra(shared.metadata()).texture());
	}

	@Test
	void sharedRolesBecomeReadyTogetherWithoutLosingCustomElytraMetadata() throws IOException {
		SharedAsset shared = sharedAsset();
		ClientCapeTextureRegistry registry = new ClientCapeTextureRegistry();
		ClientCapeTextureResolver resolver = new ClientCapeTextureResolver(new ClientCapeTextureManager(registry));

		assertTrue(shared.metadata().hasElytra());
		assertTrue(resolver.resolveCape(shared.metadata()).isEmpty());
		assertEquals(ElytraTextureDecision.Mode.VANILLA_DEFAULT, resolver.resolveElytra(shared.metadata()).mode());
		Identifier identifier = ClientCapeTextureManager.identifierFor(shared.metadata().capeSha256());
		registry.register(shared.metadata().capeSha256(), identifier);
		assertEquals(identifier, resolver.resolveCape(shared.metadata()).orElseThrow());
		assertEquals(identifier, resolver.resolveElytra(shared.metadata()).texture());
		assertTrue(shared.metadata().hasElytra());
	}

	private SharedAsset sharedAsset() throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("shared"));
		byte[] png = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		Files.write(directory.resolve("cape_elytra.png"), png);
		var result = new CapeCosmeticValidator().validate(directory);
		assertTrue(result.isSuccess(), () -> "共享测试条目校验失败：" + result.issues());
		CapeRegistrySnapshot snapshot = CapeRegistrySnapshot.from(
				new CapeRegistry(List.of(result.definition().orElseThrow())));
		return new SharedAsset(png, snapshot);
	}

	private record SharedAsset(byte[] bytes, CapeRegistrySnapshot snapshot) {
		CapeCosmeticMetadata metadata() {
			return snapshot.entries().getFirst();
		}
	}
}
