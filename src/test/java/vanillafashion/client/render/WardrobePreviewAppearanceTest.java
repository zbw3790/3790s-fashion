package vanillafashion.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class WardrobePreviewAppearanceTest {
	private static final Identifier CAPE_TEXTURE =
			Identifier.fromNamespaceAndPath("vanilla_fashion", "preview/cape");
	private static final Identifier ELYTRA_TEXTURE =
			Identifier.fromNamespaceAndPath("vanilla_fashion", "preview/elytra");

	@Test
	void vanillaAppearanceIsStableSingleton() {
		assertSame(WardrobePreviewAppearance.vanilla(), WardrobePreviewAppearance.vanilla());
	}

	@Test
	void vanillaAppearancePassesThroughBothVanillaLayers() {
		WardrobePreviewAppearance appearance = WardrobePreviewAppearance.vanilla();

		assertEquals(WardrobePreviewAppearance.Mode.VANILLA, appearance.mode());
		assertTrue(appearance.capeTexture().isEmpty());
		assertEquals(ElytraTextureDecision.Mode.PASS_THROUGH, appearance.elytraDecision().mode());
		assertFalse(appearance.suppressesVanillaCape());
	}

	@Test
	void serverAppearancePreservesCustomTextures() {
		WardrobePreviewAppearance appearance = WardrobePreviewAppearance.serverCosmetic(
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.customTexture(ELYTRA_TEXTURE)
		);

		assertEquals(WardrobePreviewAppearance.Mode.SERVER_COSMETIC, appearance.mode());
		assertEquals(Optional.of(CAPE_TEXTURE), appearance.capeTexture());
		assertEquals(ELYTRA_TEXTURE, appearance.elytraDecision().texture());
		assertTrue(appearance.suppressesVanillaCape());
	}

	@Test
	void serverAppearanceAllowsMissingCapeWithVanillaElytraFallback() {
		WardrobePreviewAppearance appearance = WardrobePreviewAppearance.serverCosmetic(
				Optional.empty(),
				ElytraTextureDecision.vanillaDefault()
		);

		assertTrue(appearance.capeTexture().isEmpty());
		assertEquals(ElytraTextureDecision.Mode.VANILLA_DEFAULT, appearance.elytraDecision().mode());
	}

	@Test
	void vanillaModeRejectsCapeOverride() {
		assertThrows(IllegalArgumentException.class, () -> new WardrobePreviewAppearance(
				WardrobePreviewAppearance.Mode.VANILLA,
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.passThrough()
		));
	}

	@Test
	void vanillaModeRejectsElytraOverride() {
		assertThrows(IllegalArgumentException.class, () -> new WardrobePreviewAppearance(
				WardrobePreviewAppearance.Mode.VANILLA,
				Optional.empty(),
				ElytraTextureDecision.vanillaDefault()
		));
	}

	@Test
	void serverModeRequiresFrozenElytraFallback() {
		assertThrows(IllegalArgumentException.class, () -> WardrobePreviewAppearance.serverCosmetic(
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.passThrough()
		));
	}
}
