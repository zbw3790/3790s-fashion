package vanillafashion.client.render;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistrySnapshot;
import vanillafashion.client.cape.ClientCapeRegistry;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.fashion.PlayerFashionEntry;
import vanillafashion.fashion.PlayerFashionAuthoritativeState;
import vanillafashion.fashion.PlayerFashionSnapshot;

class PlayerFashionRenderingTest {
	private static final UUID FIRST = new UUID(0, 1);
	private static final UUID SECOND = new UUID(0, 2);
	private static final UUID THIRD = new UUID(0, 3);
	private static final CapeId CAPE = new CapeId("founder");
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("vanilla_fashion", "test/cape");
	private static final Identifier WINGS = Identifier.fromNamespaceAndPath("vanilla_fashion", "test/wings");
	private final ClientPlayerFashionRegistry fashions = new ClientPlayerFashionRegistry();
	private final ClientCapeRegistry capes = new ClientCapeRegistry();
	private final Map<String, Identifier> textures = new HashMap<>();
	private final PlayerFashionAppearanceResolver resolver = new PlayerFashionAppearanceResolver(fashions, capes,
			metadata -> Optional.ofNullable(textures.get(metadata.capeSha256())),
			metadata -> metadata.elytraSha256().map(textures::get).map(ElytraTextureDecision::customTexture)
					.orElseGet(ElytraTextureDecision::vanillaDefault));

	@Test
	void uninitializedPlayerHasUnknownAppearance() {
		assertEquals(PlayerFashionRenderAppearance.Mode.UNKNOWN, resolver.resolve(FIRST).mode());
		assertFalse(resolver.resolve(FIRST).suppressesVanillaCape());
		assertEquals(ElytraTextureDecision.passThrough(), resolver.resolve(FIRST).elytraDecision());
	}

	@Test
	void unknownUuidInAvailableRegistryStaysUnknown() {
		select(Optional.empty());
		assertEquals(PlayerFashionRenderAppearance.Mode.UNKNOWN, resolver.resolve(SECOND).mode());
	}

	@Test
	void knownVanillaHasExplicitVanillaAppearance() {
		select(Optional.empty());
		assertEquals(PlayerFashionRenderAppearance.vanilla(), resolver.resolve(FIRST));
	}

	@Test
	void unavailableSnapshotDisablesOverride() {
		select(Optional.of(CAPE));
		fashions.replace(PlayerFashionSnapshot.unavailable());
		assertEquals(PlayerFashionRenderAppearance.unknown(), resolver.resolve(FIRST));
	}

	@Test
	void customMissingMetadataSuppressesWrongCapeAndUsesDefaultWings() {
		select(Optional.of(CAPE));
		var appearance = resolver.resolve(FIRST);
		assertTrue(appearance.suppressesVanillaCape());
		assertTrue(appearance.capeTexture().isEmpty());
		assertEquals(ElytraTextureDecision.vanillaDefault(), appearance.elytraDecision());
		assertEquals(Optional.of(CAPE), fashions.find(FIRST).orElseThrow().effectiveSelection());
	}

	@Test
	void capeNotReadyNeverUsesMissingOrPreviousTexture() {
		select(Optional.of(CAPE));
		metadata(false);
		assertTrue(resolver.resolve(FIRST).capeTexture().isEmpty());
		textures.put("a".repeat(64), TEXTURE);
		var frozen = resolver.resolve(FIRST);
		textures.clear();
		assertEquals(Optional.of(TEXTURE), frozen.capeTexture());
		assertTrue(resolver.resolve(FIRST).capeTexture().isEmpty());
		assertTrue(resolver.resolve(FIRST).suppressesVanillaCape());
	}

	@Test
	void readyCapeWithoutElytraUsesVanillaDefault() {
		select(Optional.of(CAPE));
		metadata(false);
		textures.put("a".repeat(64), TEXTURE);
		var appearance = resolver.resolve(FIRST);
		assertEquals(Optional.of(TEXTURE), appearance.capeTexture());
		assertEquals(ElytraTextureDecision.vanillaDefault(), appearance.elytraDecision());
	}

	@Test
	void readyCustomElytraUsesItsOwnTexture() {
		select(Optional.of(CAPE));
		metadata(true);
		textures.put("a".repeat(64), TEXTURE);
		textures.put("b".repeat(64), WINGS);
		assertEquals(ElytraTextureDecision.customTexture(WINGS), resolver.resolve(FIRST).elytraDecision());
	}

	@Test
	void missingCustomElytraUsesDefault() {
		select(Optional.of(CAPE));
		metadata(true);
		textures.put("a".repeat(64), TEXTURE);
		assertEquals(ElytraTextureDecision.vanillaDefault(), resolver.resolve(FIRST).elytraDecision());
	}

	@Test
	void metadataThenAssetsRecoverWithoutFashionPacket() {
		select(Optional.of(CAPE));
		assertTrue(resolver.resolve(FIRST).capeTexture().isEmpty());
		metadata(true);
		assertTrue(resolver.resolve(FIRST).capeTexture().isEmpty());
		textures.put("a".repeat(64), TEXTURE);
		textures.put("b".repeat(64), WINGS);
		assertEquals(Optional.of(TEXTURE), resolver.resolve(FIRST).capeTexture());
		assertEquals(ElytraTextureDecision.customTexture(WINGS), resolver.resolve(FIRST).elytraDecision());
		assertEquals(Optional.of(CAPE), fashions.find(FIRST).orElseThrow().effectiveSelection());
	}

	@Test
	void assetsAndMetadataBeforeFashionAreAlsoSupported() {
		metadata(true);
		textures.put("a".repeat(64), TEXTURE);
		assertEquals(PlayerFashionRenderAppearance.unknown(), resolver.resolve(FIRST));
		select(Optional.of(CAPE));
		assertEquals(Optional.of(TEXTURE), resolver.resolve(FIRST).capeTexture());
	}

	@Test
	void removingMetadataKeepsAuthoritativeSelectionButSuppressesCape() {
		select(Optional.of(CAPE));
		metadata(false);
		textures.put("a".repeat(64), TEXTURE);
		capes.clear();
		assertTrue(resolver.resolve(FIRST).capeTexture().isEmpty());
		assertTrue(resolver.resolve(FIRST).suppressesVanillaCape());
		assertEquals(Optional.of(CAPE), fashions.find(FIRST).orElseThrow().effectiveSelection());
	}

	@Test
	void dormantWorldUsesVanillaEvenWhenCustomMetadataAndTextureAreReady() {
		select(PlayerFashionAuthoritativeState.dormant(CAPE));
		metadata(false);
		textures.put("a".repeat(64), TEXTURE);
		assertEquals(PlayerFashionRenderAppearance.vanilla(), resolver.resolve(FIRST));
		assertFalse(resolver.resolve(FIRST).suppressesVanillaCape());
	}

	@Test
	void activeWorldUsesCustomFromEffectiveSelection() {
		select(PlayerFashionAuthoritativeState.active(CAPE));
		metadata(false);
		textures.put("a".repeat(64), TEXTURE);
		assertEquals(Optional.of(TEXTURE), resolver.resolve(FIRST).capeTexture());
		assertTrue(resolver.resolve(FIRST).suppressesVanillaCape());
	}

	@Test
	void storedCustomAloneNeverDrivesWorldRenderer() {
		select(new PlayerFashionAuthoritativeState(Optional.of(CAPE), Optional.empty()));
		metadata(true);
		textures.put("a".repeat(64), TEXTURE);
		textures.put("b".repeat(64), WINGS);
		assertTrue(resolver.resolve(FIRST).capeTexture().isEmpty());
		assertEquals(ElytraTextureDecision.passThrough(), resolver.resolve(FIRST).elytraDecision());
	}

	@ParameterizedTest
	@EnumSource(value = PlayerFashionRenderAppearance.Mode.class, names = {"UNKNOWN", "VANILLA"})
	void nonCustomAppearanceCannotCarryServerTextures(PlayerFashionRenderAppearance.Mode mode) {
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionRenderAppearance(
				mode, Optional.of(TEXTURE), ElytraTextureDecision.passThrough()));
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionRenderAppearance(
				mode, Optional.empty(), ElytraTextureDecision.vanillaDefault()));
	}

	@Test
	void customCannotPassThroughOfficialElytraTexture() {
		assertThrows(IllegalArgumentException.class, () -> PlayerFashionRenderAppearance.serverCosmetic(
				Optional.empty(), ElytraTextureDecision.passThrough()));
	}

	@Test
	void worldOverridesVanillaUsingActualFabricDataKey() {
		var state = state(11);
		var world = custom(TEXTURE);
		PlayerFashionRenderState.attach(state, world);
		assertSame(world, PlayerFashionRenderState.find(state));
		assertFalse(PlayerFashionRenderDecisions.allowVanillaCape(state));
		assertEquals(Optional.of(TEXTURE), PlayerFashionRenderDecisions.capeTexture(state));
		assertEquals(ElytraTextureDecision.vanillaDefault(), PlayerFashionRenderDecisions.elytraTexture(state));
	}

	@Test
	void previewCustomWinsOverWorld() {
		var state = state(11);
		PlayerFashionRenderState.attach(state, custom(TEXTURE));
		WardrobePreviewRenderState.attach(state, WardrobePreviewAppearance.serverCosmetic(
				Optional.of(WINGS), ElytraTextureDecision.customTexture(WINGS)));
		assertEquals(Optional.of(WINGS), PlayerFashionRenderDecisions.capeTexture(state));
		assertEquals(ElytraTextureDecision.customTexture(WINGS), PlayerFashionRenderDecisions.elytraTexture(state));
		assertEquals(Optional.of(TEXTURE), PlayerFashionRenderState.find(state).capeTexture());
	}

	@Test
	void vanillaPreviewBypassesCustomWorldAndUnknownDoesNotOverridePreview() {
		var state = state(11);
		PlayerFashionRenderState.attach(state, custom(TEXTURE));
		WardrobePreviewRenderState.attach(state, WardrobePreviewAppearance.vanilla());
		assertTrue(PlayerFashionRenderDecisions.allowVanillaCape(state));
		assertTrue(PlayerFashionRenderDecisions.capeTexture(state).isEmpty());
		assertEquals(ElytraTextureDecision.passThrough(), PlayerFashionRenderDecisions.elytraTexture(state));
		PlayerFashionRenderState.attach(state, PlayerFashionRenderAppearance.unknown());
		WardrobePreviewRenderState.attach(state, WardrobePreviewAppearance.serverCosmetic(
				Optional.of(WINGS), ElytraTextureDecision.vanillaDefault()));
		assertEquals(Optional.of(WINGS), PlayerFashionRenderDecisions.capeTexture(state));
	}

	@Test
	void unknownWorldKeepsVanilla() {
		var state = state(11);
		assertTrue(PlayerFashionRenderDecisions.allowVanillaCape(state));
		assertTrue(PlayerFashionRenderDecisions.capeTexture(state).isEmpty());
		assertEquals(ElytraTextureDecision.passThrough(), PlayerFashionRenderDecisions.elytraTexture(state));
	}

	@Test
	void eachAvatarGetsItsOwnAppearanceInSameExtraction() {
		var first = state(11);
		var second = state(22);
		var third = state(33);
		var identities = Map.of(11, FIRST, 22, SECOND, 33, THIRD);
		var appearances = Map.of(FIRST, custom(TEXTURE), SECOND, custom(WINGS),
				THIRD, PlayerFashionRenderAppearance.vanilla());
		PlayerFashionWorldRendering.extract(List.of(first, second, third, new EntityRenderState()),
				id -> Optional.ofNullable(identities.get(id)), appearances::get);
		assertEquals(Optional.of(TEXTURE), PlayerFashionRenderState.find(first).capeTexture());
		assertEquals(Optional.of(WINGS), PlayerFashionRenderState.find(second).capeTexture());
		assertEquals(PlayerFashionRenderAppearance.vanilla(), PlayerFashionRenderState.find(third));
	}

	@Test
	void lookupMissOverwritesStaleAppearanceOnReusedState() {
		var state = state(11);
		PlayerFashionRenderState.attach(state, custom(TEXTURE));
		PlayerFashionWorldRendering.extract(List.of(state), id -> Optional.empty(),
				id -> fail("身份未知时不得查询时装。"));
		assertEquals(PlayerFashionRenderAppearance.unknown(), PlayerFashionRenderState.find(state));
	}

	@Test
	void changedIdOnRespawnUsesFreshLookup() {
		var state = state(11);
		PlayerFashionWorldRendering.extract(List.of(state), id -> Optional.of(FIRST), id -> custom(TEXTURE));
		state.id = 22;
		PlayerFashionWorldRendering.extract(List.of(state),
				id -> id == 22 ? Optional.of(SECOND) : Optional.empty(), id -> custom(WINGS));
		assertEquals(Optional.of(WINGS), PlayerFashionRenderState.find(state).capeTexture());
	}

	@Test
	void removedFashionNoLongerOverridesExistingEntity() {
		select(Optional.of(CAPE));
		var state = state(11);
		PlayerFashionWorldRendering.extract(List.of(state), id -> Optional.of(FIRST), resolver::resolve);
		assertTrue(PlayerFashionRenderState.find(state).suppressesVanillaCape());
		fashions.remove(FIRST);
		PlayerFashionWorldRendering.extract(List.of(state), id -> Optional.of(FIRST), resolver::resolve);
		assertEquals(PlayerFashionRenderAppearance.unknown(), PlayerFashionRenderState.find(state));
	}

	@Test
	void nullEntityHasNoIdentity() {
		assertTrue(PlayerFashionWorldRendering.playerId(null, 11).isEmpty());
	}

	@Test
	void actualClientPlayerTypesMatchThePlayerGuard() {
		assertTrue(net.minecraft.world.entity.player.Player.class
				.isAssignableFrom(net.minecraft.client.player.LocalPlayer.class));
		assertTrue(net.minecraft.world.entity.player.Player.class
				.isAssignableFrom(net.minecraft.client.player.RemotePlayer.class));
		assertFalse(net.minecraft.world.entity.player.Player.class
				.isAssignableFrom(net.minecraft.world.entity.decoration.ArmorStand.class));
	}

	@Test
	void existingElytraMixinResolverUsesWorldThenPreview() {
		var state = state(11);
		assertEquals(ElytraTextureDecision.passThrough(), ElytraTextureOverrideResolver.resolve(state));
		PlayerFashionRenderState.attach(state, PlayerFashionRenderAppearance.serverCosmetic(
				Optional.of(TEXTURE), ElytraTextureDecision.customTexture(WINGS)));
		assertEquals(ElytraTextureDecision.customTexture(WINGS), ElytraTextureOverrideResolver.resolve(state));
		WardrobePreviewRenderState.attach(state, WardrobePreviewAppearance.vanilla());
		assertEquals(ElytraTextureDecision.passThrough(), ElytraTextureOverrideResolver.resolve(state));
	}

	private void select(Optional<CapeId> selection) {
		select(selection.map(PlayerFashionAuthoritativeState::active)
				.orElseGet(PlayerFashionAuthoritativeState::vanilla));
	}

	private void select(PlayerFashionAuthoritativeState state) {
		fashions.replace(new PlayerFashionSnapshot(true, List.of(new PlayerFashionEntry(FIRST, state))));
	}

	private void metadata(boolean hasElytra) {
		capes.replace(new CapeRegistrySnapshot(List.of(new CapeCosmeticMetadata(
				CAPE, "a".repeat(64), hasElytra ? Optional.of("b".repeat(64)) : Optional.empty()))));
	}

	private static AvatarRenderState state(int id) {
		var state = new AvatarRenderState();
		state.id = id;
		return state;
	}

	private static PlayerFashionRenderAppearance custom(Identifier texture) {
		return PlayerFashionRenderAppearance.serverCosmetic(Optional.of(texture), ElytraTextureDecision.vanillaDefault());
	}
}
