package dev.zbw3790.fashion.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class WardrobePreviewRenderDecisionsTest {
	private static final Identifier NORMAL_CAPE =
			Identifier.fromNamespaceAndPath("fashion_3790", "normal/cape");
	private static final Identifier PREVIEW_CAPE =
			Identifier.fromNamespaceAndPath("fashion_3790", "preview/cape");
	private static final Identifier PREVIEW_ELYTRA =
			Identifier.fromNamespaceAndPath("fashion_3790", "preview/elytra");

	@Test
	void absentPreviewKeepsAllowedNormalCapeDecision() {
		assertTrue(WardrobePreviewRenderDecisions.allowVanillaCape(Optional.empty(), true));
	}

	@Test
	void absentPreviewKeepsDeniedNormalCapeDecision() {
		assertFalse(WardrobePreviewRenderDecisions.allowVanillaCape(Optional.empty(), false));
	}

	@Test
	void vanillaDraftBypassesDevelopmentCapeTakeover() {
		assertTrue(WardrobePreviewRenderDecisions.allowVanillaCape(
				Optional.of(WardrobePreviewAppearance.vanilla()),
				false
		));
	}

	@Test
	void serverDraftSuppressesVanillaCape() {
		assertFalse(WardrobePreviewRenderDecisions.allowVanillaCape(
				Optional.of(serverAppearance(Optional.of(PREVIEW_CAPE))),
				true
		));
	}

	@Test
	void absentPreviewUsesNormalCapeTexture() {
		assertEquals(
				Optional.of(NORMAL_CAPE),
				WardrobePreviewRenderDecisions.capeTexture(
						Optional.empty(),
						() -> Optional.of(NORMAL_CAPE)
				)
		);
	}

	@Test
	void vanillaDraftHasNoCustomCapeTexture() {
		assertTrue(WardrobePreviewRenderDecisions.capeTexture(
				Optional.of(WardrobePreviewAppearance.vanilla()),
				WardrobePreviewRenderDecisionsTest::unexpectedCapeFallback
		).isEmpty());
	}

	@Test
	void serverDraftUsesReadyCapeTexture() {
		assertEquals(
				Optional.of(PREVIEW_CAPE),
				WardrobePreviewRenderDecisions.capeTexture(
						Optional.of(serverAppearance(Optional.of(PREVIEW_CAPE))),
						WardrobePreviewRenderDecisionsTest::unexpectedCapeFallback
				)
		);
	}

	@Test
	void serverDraftKeepsMissingCapeUnavailable() {
		assertTrue(WardrobePreviewRenderDecisions.capeTexture(
				Optional.of(serverAppearance(Optional.empty())),
				WardrobePreviewRenderDecisionsTest::unexpectedCapeFallback
		).isEmpty());
	}

	@Test
	void absentPreviewUsesNormalElytraDecision() {
		assertEquals(
				ElytraTextureDecision.Mode.CUSTOM_TEXTURE,
				WardrobePreviewRenderDecisions.elytraTexture(
						Optional.empty(),
						() -> ElytraTextureDecision.customTexture(PREVIEW_ELYTRA)
				).mode()
		);
	}

	@Test
	void vanillaDraftPassesElytraThroughWithoutNormalProbe() {
		assertEquals(
				ElytraTextureDecision.Mode.PASS_THROUGH,
				WardrobePreviewRenderDecisions.elytraTexture(
						Optional.of(WardrobePreviewAppearance.vanilla()),
						WardrobePreviewRenderDecisionsTest::unexpectedElytraFallback
				).mode()
		);
	}

	@Test
	void serverDraftUsesVanillaElytraFallbackWithoutNormalProbe() {
		assertEquals(
				ElytraTextureDecision.Mode.VANILLA_DEFAULT,
				WardrobePreviewRenderDecisions.elytraTexture(
						Optional.of(serverAppearance(Optional.of(PREVIEW_CAPE))),
						WardrobePreviewRenderDecisionsTest::unexpectedElytraFallback
				).mode()
		);
	}

	@Test
	void serverDraftUsesCustomElytraWithoutNormalProbe() {
		WardrobePreviewAppearance appearance = WardrobePreviewAppearance.serverCosmetic(
				Optional.of(PREVIEW_CAPE),
				ElytraTextureDecision.customTexture(PREVIEW_ELYTRA)
		);

		assertEquals(
				PREVIEW_ELYTRA,
				WardrobePreviewRenderDecisions.elytraTexture(
						Optional.of(appearance),
						WardrobePreviewRenderDecisionsTest::unexpectedElytraFallback
				).texture()
		);
	}

	private static WardrobePreviewAppearance serverAppearance(Optional<Identifier> capeTexture) {
		return WardrobePreviewAppearance.serverCosmetic(
				capeTexture,
				ElytraTextureDecision.vanillaDefault()
		);
	}

	private static Optional<Identifier> unexpectedCapeFallback() {
		return fail("存在衣柜预览外观时不应读取普通世界 Cape 决策。");
	}

	private static ElytraTextureDecision unexpectedElytraFallback() {
		return fail("存在衣柜预览外观时不应读取普通世界 Elytra 决策。");
	}
}
