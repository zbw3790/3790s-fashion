package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.client.render.ElytraTextureDecision;
import dev.zbw3790.fashion.client.render.WardrobePreviewAppearance;

class WardrobePreviewAppearanceResolverTest {
	private static final String CAPE_HASH = "a".repeat(64);
	private static final String SECOND_CAPE_HASH = "b".repeat(64);
	private static final String ELYTRA_HASH = "c".repeat(64);
	private static final Identifier CAPE_TEXTURE =
			Identifier.fromNamespaceAndPath("fashion_3790", "preview/cape");
	private static final Identifier ELYTRA_TEXTURE =
			Identifier.fromNamespaceAndPath("fashion_3790", "preview/elytra");

	@Test
	void emptyDraftResolvesTrueVanillaWithoutTextureLookup() {
		AtomicInteger lookups = new AtomicInteger();
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.empty())),
				lookups,
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.vanillaDefault()
		);

		assertSame(WardrobePreviewAppearance.vanilla(), resolver.resolve(Optional.empty()));
		assertEquals(0, lookups.get());
	}

	@Test
	void unknownDraftResolvesTrueVanillaWithoutTextureLookup() {
		AtomicInteger lookups = new AtomicInteger();
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.empty())),
				lookups,
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.vanillaDefault()
		);

		assertSame(
				WardrobePreviewAppearance.vanilla(),
				resolver.resolve(Optional.of(new CapeId("removed")))
		);
		assertEquals(0, lookups.get());
	}

	@Test
	void knownDraftResolvesReadyCapeAndVanillaElytra() {
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.empty())),
				new AtomicInteger(),
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.vanillaDefault()
		);

		WardrobePreviewAppearance appearance = resolver.resolve(Optional.of(new CapeId("alpha")));

		assertEquals(WardrobePreviewAppearance.Mode.SERVER_COSMETIC, appearance.mode());
		assertEquals(Optional.of(CAPE_TEXTURE), appearance.capeTexture());
		assertEquals(ElytraTextureDecision.Mode.VANILLA_DEFAULT, appearance.elytraDecision().mode());
	}

	@Test
	void knownDraftKeepsMissingCapeAsServerAppearance() {
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.empty())),
				new AtomicInteger(),
				Optional.empty(),
				ElytraTextureDecision.vanillaDefault()
		);

		WardrobePreviewAppearance appearance = resolver.resolve(Optional.of(new CapeId("alpha")));

		assertEquals(WardrobePreviewAppearance.Mode.SERVER_COSMETIC, appearance.mode());
		assertTrue(appearance.capeTexture().isEmpty());
		assertTrue(appearance.suppressesVanillaCape());
	}

	@Test
	void knownDraftResolvesCustomElytra() {
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.of(ELYTRA_HASH))),
				new AtomicInteger(),
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.customTexture(ELYTRA_TEXTURE)
		);

		WardrobePreviewAppearance appearance = resolver.resolve(Optional.of(new CapeId("alpha")));

		assertEquals(ElytraTextureDecision.Mode.CUSTOM_TEXTURE, appearance.elytraDecision().mode());
		assertEquals(ELYTRA_TEXTURE, appearance.elytraDecision().texture());
	}

	@Test
	void duplicateMetadataKeepsFirstEntry() {
		CapeCosmeticMetadata first = metadata("duplicate", CAPE_HASH, Optional.empty());
		CapeCosmeticMetadata second = metadata("duplicate", SECOND_CAPE_HASH, Optional.empty());
		WardrobePreviewAppearanceResolver resolver = new WardrobePreviewAppearanceResolver(
				List.of(first, second),
				metadata -> metadata.capeSha256().equals(CAPE_HASH)
						? Optional.of(CAPE_TEXTURE)
						: Optional.empty(),
				metadata -> ElytraTextureDecision.vanillaDefault()
		);

		WardrobePreviewAppearance appearance = resolver.resolve(Optional.of(new CapeId("duplicate")));

		assertEquals(Optional.of(CAPE_TEXTURE), appearance.capeTexture());
	}

	@Test
	void knownDraftRunsBothTextureDecisionsExactlyOnce() {
		AtomicInteger lookups = new AtomicInteger();
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.empty())),
				lookups,
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.vanillaDefault()
		);

		resolver.resolve(Optional.of(new CapeId("alpha")));

		assertEquals(2, lookups.get());
	}

	@Test
	void resolverDoesNotPromoteDraftToAnyMutableState() {
		WardrobePreviewAppearanceResolver resolver = resolver(
				List.of(metadata("alpha", CAPE_HASH, Optional.empty())),
				new AtomicInteger(),
				Optional.of(CAPE_TEXTURE),
				ElytraTextureDecision.vanillaDefault()
		);

		resolver.resolve(Optional.of(new CapeId("alpha")));

		assertFalse(resolver.resolve(Optional.empty()).suppressesVanillaCape());
	}

	private static WardrobePreviewAppearanceResolver resolver(
			List<CapeCosmeticMetadata> metadata,
			AtomicInteger lookups,
			Optional<Identifier> capeTexture,
			ElytraTextureDecision elytraDecision
	) {
		return new WardrobePreviewAppearanceResolver(
				metadata,
				entry -> {
					lookups.incrementAndGet();
					return capeTexture;
				},
				entry -> {
					lookups.incrementAndGet();
					return elytraDecision;
				}
		);
	}

	private static CapeCosmeticMetadata metadata(
			String id,
			String capeHash,
			Optional<String> elytraHash
	) {
		return new CapeCosmeticMetadata(new CapeId(id), capeHash, elytraHash);
	}
}
