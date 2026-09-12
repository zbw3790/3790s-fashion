package dev.zbw3790.fashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistrySnapshot;

class ClientCapeRegistryTest {
	private static final String CAPE_HASH = "a".repeat(64);

	@Test
	void startsEmpty() {
		ClientCapeRegistry registry = new ClientCapeRegistry();

		assertTrue(registry.isEmpty());
		assertEquals(0, registry.size());
	}

	@Test
	void replacesCurrentSnapshot() {
		ClientCapeRegistry registry = new ClientCapeRegistry();
		CapeCosmeticMetadata metadata = metadata("founder");

		registry.replace(new CapeRegistrySnapshot(List.of(metadata)));

		assertFalse(registry.isEmpty());
		assertEquals(metadata, registry.find(new CapeId("founder")).orElseThrow());
		assertEquals(List.of(metadata), registry.entries());
	}

	@Test
	void clearsCurrentSnapshot() {
		ClientCapeRegistry registry = new ClientCapeRegistry();
		registry.replace(new CapeRegistrySnapshot(List.of(metadata("founder"))));

		registry.clear();

		assertTrue(registry.isEmpty());
		assertTrue(registry.find(new CapeId("founder")).isEmpty());
	}

	@Test
	void secondReplaceDoesNotMergeSnapshots() {
		ClientCapeRegistry registry = new ClientCapeRegistry();
		registry.replace(new CapeRegistrySnapshot(List.of(metadata("server_a"))));

		registry.replace(new CapeRegistrySnapshot(List.of(metadata("server_b"))));

		assertEquals(1, registry.size());
		assertTrue(registry.find(new CapeId("server_a")).isEmpty());
		assertTrue(registry.find(new CapeId("server_b")).isPresent());
	}

	private static CapeCosmeticMetadata metadata(String id) {
		return new CapeCosmeticMetadata(new CapeId(id), CAPE_HASH, Optional.empty());
	}
}
