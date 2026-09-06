package vanillafashion.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class CapeCosmeticMetadataTest {
	private static final String CAPE_HASH = "a".repeat(64);
	private static final String ELYTRA_HASH = "b".repeat(64);

	@Test
	void acceptsCapeOnlyMetadata() {
		CapeCosmeticMetadata metadata = new CapeCosmeticMetadata(
				new CapeId("cape_only"),
				CAPE_HASH,
				Optional.empty()
		);

		assertEquals(CAPE_HASH, metadata.capeSha256());
		assertFalse(metadata.hasElytra());
	}

	@Test
	void acceptsCapeAndElytraMetadata() {
		CapeCosmeticMetadata metadata = new CapeCosmeticMetadata(
				new CapeId("full"),
				CAPE_HASH,
				Optional.of(ELYTRA_HASH)
		);

		assertTrue(metadata.hasElytra());
		assertEquals(ELYTRA_HASH, metadata.elytraSha256().orElseThrow());
	}

	@Test
	void rejectsInvalidCapeHashWithoutRepairingIt() {
		assertThrows(IllegalArgumentException.class, () -> new CapeCosmeticMetadata(
				new CapeId("invalid_cape"),
				"A".repeat(64),
				Optional.empty()
		));
	}

	@Test
	void rejectsInvalidElytraHashWithoutRepairingIt() {
		assertThrows(IllegalArgumentException.class, () -> new CapeCosmeticMetadata(
				new CapeId("invalid_elytra"),
				CAPE_HASH,
				Optional.of("b".repeat(63))
		));
	}
}
