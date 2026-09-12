package dev.zbw3790.fashion.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CapeRegistrySnapshotTest {
	private static final String CAPE_HASH = "a".repeat(64);
	private static final String ELYTRA_HASH = "b".repeat(64);

	@Test
	void supportsEmptySnapshot() {
		CapeRegistrySnapshot snapshot = CapeRegistrySnapshot.empty();

		assertTrue(snapshot.isEmpty());
		assertEquals(0, snapshot.size());
	}

	@Test
	void sortsEntriesByCapeId() {
		CapeRegistrySnapshot snapshot = new CapeRegistrySnapshot(List.of(
				metadata("zeta"),
				metadata("alpha"),
				metadata("middle")
		));

		assertEquals(
				List.of("alpha", "middle", "zeta"),
				snapshot.entries().stream().map(entry -> entry.id().value()).toList()
		);
	}

	@Test
	void rejectsDuplicateCapeIds() {
		assertThrows(IllegalArgumentException.class, () -> new CapeRegistrySnapshot(List.of(
				metadata("duplicate"),
				metadata("duplicate")
		)));
	}

	@Test
	void findsEntryByCapeId() {
		CapeCosmeticMetadata expected = metadata("founder");
		CapeRegistrySnapshot snapshot = new CapeRegistrySnapshot(List.of(expected));

		assertEquals(expected, snapshot.find(new CapeId("founder")).orElseThrow());
		assertTrue(snapshot.find(new CapeId("missing")).isEmpty());
	}

	@Test
	void copiesInputAndExposesUnmodifiableEntries() {
		List<CapeCosmeticMetadata> source = new ArrayList<>();
		source.add(metadata("founder"));
		CapeRegistrySnapshot snapshot = new CapeRegistrySnapshot(source);

		source.clear();

		assertEquals(1, snapshot.size());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
	}

	@Test
	void mapsServerRegistryWithoutExposingPaths() {
		CapeRegistry registry = new CapeRegistry(List.of(
				definition("cape_only", CAPE_HASH, Optional.empty()),
				definition("full", CAPE_HASH, Optional.of(ELYTRA_HASH))
		));

		CapeRegistrySnapshot snapshot = CapeRegistrySnapshot.from(registry);

		assertEquals(CAPE_HASH, snapshot.find(new CapeId("cape_only")).orElseThrow().capeSha256());
		assertTrue(snapshot.find(new CapeId("cape_only")).orElseThrow().elytraSha256().isEmpty());
		assertEquals(
				ELYTRA_HASH,
				snapshot.find(new CapeId("full")).orElseThrow().elytraSha256().orElseThrow()
		);
		assertFalse(Arrays.stream(CapeCosmeticMetadata.class.getRecordComponents())
				.anyMatch(component -> component.getType() == Path.class));
	}

	@Test
	void rejectsSnapshotsAboveEntryLimit() {
		List<CapeCosmeticMetadata> entries = IntStream
				.rangeClosed(0, CapeRegistrySnapshot.MAX_CAPE_ENTRIES)
				.mapToObj(index -> metadata("cape_" + index))
				.toList();

		assertThrows(IllegalArgumentException.class, () -> new CapeRegistrySnapshot(entries));
	}

	private static CapeCosmeticMetadata metadata(String id) {
		return new CapeCosmeticMetadata(new CapeId(id), CAPE_HASH, Optional.empty());
	}

	private static CapeCosmeticDefinition definition(
			String id,
			String capeHash,
			Optional<String> elytraHash
	) {
		CapeTextureAsset cape = new CapeTextureAsset(
				Path.of(id, "cape.png"),
				capeHash,
				64,
				32,
				CapeTextureType.CAPE
		);
		Optional<CapeTextureAsset> elytra = elytraHash.map(hash -> new CapeTextureAsset(
				Path.of(id, "elytra.png"),
				hash,
				64,
				32,
				CapeTextureType.ELYTRA
		));

		return new CapeCosmeticDefinition(new CapeId(id), cape, elytra);
	}
}
