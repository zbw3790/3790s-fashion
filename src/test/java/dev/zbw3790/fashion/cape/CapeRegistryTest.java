package dev.zbw3790.fashion.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CapeRegistryTest {
	@Test
	void sortsDefinitionsAndFindsById() {
		CapeCosmeticDefinition zeta = definition("zeta");
		CapeCosmeticDefinition alpha = definition("alpha");
		CapeCosmeticDefinition middle = definition("middle");
		CapeRegistry registry = new CapeRegistry(List.of(zeta, alpha, middle));

		assertEquals(List.of("alpha", "middle", "zeta"), ids(registry));
		assertSame(middle, registry.find(new CapeId("middle")).orElseThrow());
		assertEquals(3, registry.size());
		assertFalse(registry.isEmpty());
	}

	@Test
	void returnsEmptyForUnknownId() {
		CapeRegistry registry = new CapeRegistry(List.of(definition("known")));

		assertTrue(registry.find(new CapeId("unknown")).isEmpty());
	}

	@Test
	void copiesInputAndExposesUnmodifiableDefinitions() {
		List<CapeCosmeticDefinition> source = new ArrayList<>();
		source.add(definition("founder"));
		CapeRegistry registry = new CapeRegistry(source);

		source.clear();

		assertEquals(1, registry.size());
		assertThrows(UnsupportedOperationException.class, () -> registry.definitions().clear());
	}

	@Test
	void rejectsDuplicateIds() {
		CapeCosmeticDefinition first = definition("duplicate");
		CapeCosmeticDefinition second = definition("duplicate");

		assertThrows(IllegalArgumentException.class, () -> new CapeRegistry(List.of(first, second)));
	}

	private static List<String> ids(CapeRegistry registry) {
		return registry.definitions().stream()
				.map(definition -> definition.id().value())
				.toList();
	}

	private static CapeCosmeticDefinition definition(String id) {
		return new CapeCosmeticDefinition(
				new CapeId(id),
				new CapeTextureAsset(
						Path.of(id, "cape.png"),
						"0".repeat(64),
						64,
						32,
						CapeTextureType.CAPE
				),
				Optional.empty()
		);
	}
}
