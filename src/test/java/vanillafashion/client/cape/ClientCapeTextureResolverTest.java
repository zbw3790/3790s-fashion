package vanillafashion.client.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.client.render.ElytraTextureDecision;

class ClientCapeTextureResolverTest {
	private static final String CAPE_HASH = "a".repeat(64);
	private static final String ELYTRA_HASH = "b".repeat(64);
	private static final Identifier CAPE_TEXTURE =
			ClientCapeTextureManager.identifierFor(CAPE_HASH);
	private static final Identifier ELYTRA_TEXTURE =
			ClientCapeTextureManager.identifierFor(ELYTRA_HASH);

	@Test
	void resolvesReadyCapeTexture() {
		TestContext context = context(CAPE_HASH);

		assertEquals(CAPE_TEXTURE, context.resolver().resolveCape(metadata(Optional.empty())).orElseThrow());
	}

	@Test
	void reportsMissingCapeTextureAsUnavailable() {
		assertTrue(context().resolver().resolveCape(metadata(Optional.empty())).isEmpty());
	}

	@Test
	void usesVanillaDefaultWhenMetadataHasNoElytra() {
		assertEquals(
				ElytraTextureDecision.Mode.VANILLA_DEFAULT,
				context().resolver().resolveElytra(metadata(Optional.empty())).mode()
		);
	}

	@Test
	void resolvesReadyCustomElytraTexture() {
		TestContext context = context(ELYTRA_HASH);
		ElytraTextureDecision decision =
				context.resolver().resolveElytra(metadata(Optional.of(ELYTRA_HASH)));

		assertEquals(ElytraTextureDecision.Mode.CUSTOM_TEXTURE, decision.mode());
		assertEquals(ELYTRA_TEXTURE, decision.texture());
	}

	@Test
	void freezesMissingCustomElytraFallbackToVanillaDefault() {
		assertEquals(
				ElytraTextureDecision.Mode.VANILLA_DEFAULT,
				context().resolver().resolveElytra(metadata(Optional.of(ELYTRA_HASH))).mode()
		);
	}

	@Test
	void noSelectedServerCosmeticPassesThrough() {
		assertEquals(
				ElytraTextureDecision.Mode.PASS_THROUGH,
				context().resolver().resolveElytra(Optional.empty()).mode()
		);
	}

	private static CapeCosmeticMetadata metadata(Optional<String> elytraHash) {
		return new CapeCosmeticMetadata(new CapeId("m4c_full"), CAPE_HASH, elytraHash);
	}

	private static TestContext context(String... readyHashes) {
		ClientCapeTextureRegistry registry = new ClientCapeTextureRegistry();

		for (String hash : readyHashes) {
			registry.register(hash, ClientCapeTextureManager.identifierFor(hash));
		}

		ClientCapeTextureManager manager = new ClientCapeTextureManager(registry);
		return new TestContext(new ClientCapeTextureResolver(manager));
	}

	private record TestContext(ClientCapeTextureResolver resolver) {
	}
}
