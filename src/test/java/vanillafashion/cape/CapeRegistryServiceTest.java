package vanillafashion.cape;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CapeRegistryServiceTest {
	@Test
	void startsEmptyAndCanReplaceCurrentRegistry() {
		CapeRegistryService service = new CapeRegistryService();
		CapeRegistry registry = new CapeRegistry(List.of(definition("founder")));

		assertTrue(service.current().isEmpty());

		service.replace(registry);

		assertSame(registry, service.current());
	}

	@Test
	void clearRestoresEmptyRegistry() {
		CapeRegistryService service = new CapeRegistryService();
		service.replace(new CapeRegistry(List.of(definition("founder"))));

		service.clear();

		assertSame(CapeRegistry.empty(), service.current());
		assertTrue(service.current().isEmpty());
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
