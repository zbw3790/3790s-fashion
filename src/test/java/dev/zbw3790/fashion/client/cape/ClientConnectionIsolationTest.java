package dev.zbw3790.fashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.network.ClientCapeSelectionRequestTracker;
import dev.zbw3790.fashion.client.network.ClientCapeSelectionResults;
import dev.zbw3790.fashion.client.network.ClientConnectionIdentity;
import dev.zbw3790.fashion.fashion.PlayerFashionAuthoritativeState;
import dev.zbw3790.fashion.fashion.PlayerFashionEntry;
import dev.zbw3790.fashion.fashion.PlayerFashionSnapshot;
import dev.zbw3790.fashion.network.CapeAssetDataPayload;
import dev.zbw3790.fashion.network.CapeSelectionReason;
import dev.zbw3790.fashion.network.CapeSelectionResultPayload;
import dev.zbw3790.fashion.wardrobe.WardrobeServerAvailability;

class ClientConnectionIsolationTest {
	private static final UUID SELF = new UUID(0, 7);
	private static final CapeId SERVER_A_CAPE = new CapeId("server_a");
	private static final CapeId SERVER_B_CAPE = new CapeId("server_b");

	@TempDir
	Path temporaryDirectory;

	@Test
	void lateServerAEventsAndCleanupCannotPolluteEstablishedServerB() throws IOException {
		Object senderA = new Object();
		Object senderB = new Object();
		Object connectionA = new Object();
		Object connectionB = new Object();
		byte[] bytesA = ClientCapeAssetStoreTest.png(64, 32, 0xFF3790FF);
		byte[] bytesB = ClientCapeAssetStoreTest.png(64, 32, 0xFF123080);
		String hashA = CapeAssetHash.sha256(bytesA);
		String hashB = CapeAssetHash.sha256(bytesB);
		String pendingB = "c".repeat(64);
		ClientCapeRegistry capes = new ClientCapeRegistry();
		ClientCapeAssetStore assets = new ClientCapeAssetStore();
		ClientCapeAssetCache cache = new ClientCapeAssetCache(
				temporaryDirectory.resolve("assets"), NOPLogger.NOP_LOGGER);
		ClientCapeAssetSync sync = new ClientCapeAssetSync(assets, cache);
		ClientCapeTextureRegistry textures = new ClientCapeTextureRegistry();
		ClientPlayerFashionRegistry fashions = new ClientPlayerFashionRegistry();
		ClientCapeSelectionRequestTracker requests = new ClientCapeSelectionRequestTracker();
		WardrobeServerAvailability availability = new WardrobeServerAvailability();

		fashions.beginConnection(connectionA);
		requests.beginConnection(connectionA);
		availability.markAvailable();
		CapeRegistrySnapshot snapshotA = snapshot(metadata(SERVER_A_CAPE, hashA, Optional.empty()));
		capes.replace(snapshotA);
		var requestA = sync.plan(snapshotA).getFirst();
		sync.markRequested(requestA);
		assertEquals(ClientCapeAssetSync.ReceiveResult.STORED,
				sync.receive(new CapeAssetDataPayload(hashA, bytesA)));
		textures.register(hashA, texture(hashA));
		fashions.replace(fashionSnapshot(Optional.of(SERVER_A_CAPE)));
		fashions.confirmSelfResult(SELF, PlayerFashionAuthoritativeState.active(SERVER_A_CAPE));
		requests.allocate();

		// 与生产 INIT 顺序一致：先建立新连接身份，再清理全部连接内状态；磁盘内容缓存保留。
		requests.beginConnection(connectionB);
		fashions.beginConnection(connectionB);
		availability.reset();
		textures.clear();
		capes.clear();
		assets.clear();
		sync.clear();
		assets.store(hashB, bytesB);
		CapeRegistrySnapshot snapshotB = snapshot(metadata(SERVER_B_CAPE, hashB, Optional.of(pendingB)));
		capes.replace(snapshotB);
		var requestB = sync.plan(snapshotB).getFirst();
		sync.markRequested(requestB);
		textures.register(hashB, texture(hashB));
		fashions.replace(fashionSnapshot(Optional.empty()));
		long requestIdB = requests.allocate().orElseThrow();

		assertFalse(ClientConnectionIdentity.runIfCurrent(senderA, senderB,
				() -> capes.replace(snapshotA)));
		assertFalse(ClientConnectionIdentity.runIfCurrent(senderA, senderB,
				() -> sync.receive(new CapeAssetDataPayload(hashA, bytesA))));
		assertFalse(ClientConnectionIdentity.runIfCurrent(senderA, senderB,
				() -> fashions.update(new PlayerFashionEntry(
						SELF, PlayerFashionAuthoritativeState.active(SERVER_A_CAPE)))));
		CapeSelectionResultPayload lateResult = new CapeSelectionResultPayload(
				requestIdB,
				true,
				PlayerFashionAuthoritativeState.active(SERVER_A_CAPE),
				CapeSelectionReason.APPLIED
		);
		assertFalse(ClientConnectionIdentity.runIfCurrent(senderA, senderB, () ->
				ClientCapeSelectionResults.apply(
						connectionB, SELF, lateResult, fashions, requests, () -> null, ignored -> true)));
		assertFalse(ClientConnectionIdentity.runIfCurrent(senderA, senderB, availability::markAvailable));

		// DISCONNECT 的实际入口先检查玩家时装连接身份，失败后不会执行其余清理。
		assertFalse(fashions.disconnect(connectionA));
		assertFalse(requests.disconnect(connectionA));

		assertFalse(availability.isAvailable());
		assertTrue(capes.find(SERVER_A_CAPE).isEmpty());
		assertTrue(capes.find(SERVER_B_CAPE).isPresent());
		assertFalse(assets.contains(hashA));
		assertArrayEquals(bytesB, assets.find(hashB).orElseThrow());
		assertEquals(java.util.Set.of(hashB, pendingB), sync.requiredHashes());
		assertEquals(java.util.Set.of(pendingB), sync.pendingHashes());
		assertTrue(textures.find(hashA).isEmpty());
		assertEquals(Optional.of(texture(hashB)), textures.find(hashB));
		assertEquals(PlayerFashionAuthoritativeState.vanilla(), fashions.find(SELF).orElseThrow());
		assertEquals(Optional.of(PlayerFashionAuthoritativeState.vanilla()), fashions.selfAuthority(SELF));
		assertEquals(1, requests.outstandingCount());
		assertTrue(cache.findValidated(hashA).isPresent());
	}

	@Test
	void currentSenderRunsExactlyOnceAndNullOrEqualDistinctSenderIsRejected() {
		Object current = new String("当前连接");
		Object equalButDistinct = new String("当前连接");
		int[] runs = {0};

		assertTrue(ClientConnectionIdentity.runIfCurrent(current, current, () -> runs[0]++));
		assertFalse(ClientConnectionIdentity.runIfCurrent(equalButDistinct, current, () -> runs[0]++));
		assertFalse(ClientConnectionIdentity.runIfCurrent(null, current, () -> runs[0]++));
		assertFalse(ClientConnectionIdentity.runIfCurrent(current, null, () -> runs[0]++));
		assertEquals(1, runs[0]);
	}

	private static CapeRegistrySnapshot snapshot(CapeCosmeticMetadata metadata) {
		return new CapeRegistrySnapshot(List.of(metadata));
	}

	private static CapeCosmeticMetadata metadata(
			CapeId id,
			String capeHash,
			Optional<String> elytraHash
	) {
		return new CapeCosmeticMetadata(id, capeHash, elytraHash);
	}

	private static PlayerFashionSnapshot fashionSnapshot(Optional<CapeId> selection) {
		return new PlayerFashionSnapshot(true, List.of(new PlayerFashionEntry(
				SELF,
				selection.map(PlayerFashionAuthoritativeState::active)
						.orElseGet(PlayerFashionAuthoritativeState::vanilla)
		)));
	}

	private static Identifier texture(String hash) {
		return ClientCapeTextureManager.identifierFor(hash);
	}
}
