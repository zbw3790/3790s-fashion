package vanillafashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class ClientCapeTextureRegistryTest {
	private static final String FIRST_HASH = "a".repeat(64);
	private static final String SECOND_HASH = "b".repeat(64);
	private static final Identifier FIRST_TEXTURE =
			Identifier.fromNamespaceAndPath("vanilla_fashion", "asset/" + FIRST_HASH);
	private static final Identifier SECOND_TEXTURE =
			Identifier.fromNamespaceAndPath("vanilla_fashion", "asset/" + SECOND_HASH);

	@Test
	void usesStableContentAddressedIdentifier() {
		assertEquals(FIRST_TEXTURE, ClientCapeTextureManager.identifierFor(FIRST_HASH));
	}

	@Test
	void deduplicatesTextureRegistrationByHash() {
		ClientCapeTextureRegistry registry = new ClientCapeTextureRegistry();

		assertTrue(registry.register(FIRST_HASH, FIRST_TEXTURE));
		assertFalse(registry.register(
				FIRST_HASH,
				Identifier.fromNamespaceAndPath("vanilla_fashion", "other")
		));
		assertEquals(FIRST_TEXTURE, registry.find(FIRST_HASH).orElseThrow());
	}

	@Test
	void retainReturnsOnlyIdentifiersNoLongerReferenced() {
		ClientCapeTextureRegistry registry = populatedRegistry();

		assertEquals(java.util.List.of(FIRST_TEXTURE), registry.retain(Set.of(SECOND_HASH)));
		assertTrue(registry.find(FIRST_HASH).isEmpty());
		assertEquals(SECOND_TEXTURE, registry.find(SECOND_HASH).orElseThrow());
	}

	@Test
	void clearReturnsAllRegisteredIdentifiers() {
		ClientCapeTextureRegistry registry = populatedRegistry();

		assertEquals(java.util.List.of(FIRST_TEXTURE, SECOND_TEXTURE), registry.clear());
		assertEquals(0, registry.size());
	}

	@Test
	void hashesReturnsImmutableSnapshot() {
		ClientCapeTextureRegistry registry = new ClientCapeTextureRegistry();
		registry.register(FIRST_HASH, FIRST_TEXTURE);
		Set<String> hashes = registry.hashes();
		registry.register(SECOND_HASH, SECOND_TEXTURE);

		assertEquals(Set.of(FIRST_HASH), hashes);
		assertThrows(UnsupportedOperationException.class, () -> hashes.add(SECOND_HASH));
	}

	private static ClientCapeTextureRegistry populatedRegistry() {
		ClientCapeTextureRegistry registry = new ClientCapeTextureRegistry();
		registry.register(FIRST_HASH, FIRST_TEXTURE);
		registry.register(SECOND_HASH, SECOND_TEXTURE);
		return registry;
	}
}
