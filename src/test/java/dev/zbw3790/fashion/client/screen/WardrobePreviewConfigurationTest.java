package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.client.render.ElytraTextureDecision;
import dev.zbw3790.fashion.client.render.ElytraTextureOverrideResolver;
import dev.zbw3790.fashion.client.render.PlayerFashionRenderAppearance;
import dev.zbw3790.fashion.client.render.PlayerFashionRenderState;
import dev.zbw3790.fashion.client.render.WardrobePreviewAppearance;
import dev.zbw3790.fashion.client.render.WardrobePreviewRenderState;
import dev.zbw3790.fashion.client.render.WardrobePreviewTestSupport;

class WardrobePreviewConfigurationTest {
	private static final Identifier CAPE = Identifier.fromNamespaceAndPath("fashion_3790", "test/cape");
	private static final Identifier ELYTRA = Identifier.fromNamespaceAndPath("fashion_3790", "test/elytra");

	@BeforeAll
	static void bootstrap() {
		WardrobePreviewTestSupport.bootstrap();
	}

	@Test
	void modesCycleFromCapeThroughElytraBackToCape() {
		assertEquals(WardrobePreviewMode.ELYTRA, WardrobePreviewMode.CAPE.next());
		assertEquals(WardrobePreviewMode.CAPE, WardrobePreviewMode.CAPE.next().next());
	}

	@Test
	void modelCenterHasNoCameraOrHeadTilt() {
		var configuration = WardrobePreviewConfiguration.fromMouse(WardrobePreviewMode.CAPE, 100.0F, 100.0F);
		var state = state(ItemStack.EMPTY);
		configuration.apply(state, stack -> false);
		assertEquals(0.0F, configuration.verticalTiltDegrees());
		assertEquals(0.0F, state.xRot, 0.0F);
		assertEquals(new Quaternionf(), configuration.cameraOrientation());
	}

	@Test
	void mouseFollowIsSymmetricAtOneQuarterOfThePreviousGain() {
		var above = WardrobePreviewConfiguration.fromMouse(WardrobePreviewMode.CAPE, 100.0F, 60.0F);
		var below = WardrobePreviewConfiguration.fromMouse(WardrobePreviewMode.CAPE, 100.0F, 140.0F);
		assertEquals(3.9269908F, above.verticalTiltDegrees(), 0.00001F);
		assertEquals(-above.verticalTiltDegrees(), below.verticalTiltDegrees());
		assertEquals(5.0F, WardrobePreviewConfiguration.VERTICAL_GAIN_DEGREES);
	}

	@Test
	void extremeMousePositionsRemainBounded() {
		for (float mouseY : new float[] {-Float.MAX_VALUE, -100000.0F, 0.0F, 100000.0F, Float.MAX_VALUE}) {
			var configuration = WardrobePreviewConfiguration.fromMouse(WardrobePreviewMode.ELYTRA, 100.0F, mouseY);
			assertTrue(Float.isFinite(configuration.verticalTiltDegrees()));
			assertTrue(Math.abs(configuration.verticalTiltDegrees()) <= 8.0F);
		}
		assertEquals(8.0F, new WardrobePreviewConfiguration(WardrobePreviewMode.CAPE, 100.0F).verticalTiltDegrees());
		assertEquals(-8.0F, new WardrobePreviewConfiguration(WardrobePreviewMode.CAPE, -100.0F).verticalTiltDegrees());
	}

	@Test
	void bothModesUseTheSameFinalCameraAndHeadAngle() {
		for (var mode : WardrobePreviewMode.values()) {
			var configuration = WardrobePreviewConfiguration.fromMouse(mode, 100.0F, -1000.0F);
			var state = state(ItemStack.EMPTY);
			configuration.apply(state, stack -> false);
			float cameraDegrees = (float) Math.toDegrees(configuration.cameraOrientation()
					.getEulerAnglesXYZ(new Vector3f()).x());
			assertEquals(configuration.verticalTiltDegrees(), cameraDegrees, 0.00001F);
			assertEquals(-cameraDegrees, state.xRot, 0.00001F);
		}
	}

	@Test
	void invalidAngleCannotEnterDeferredSubmission() {
		assertThrows(IllegalArgumentException.class,
				() -> new WardrobePreviewConfiguration(WardrobePreviewMode.CAPE, Float.NaN));
		assertThrows(IllegalArgumentException.class,
				() -> new WardrobePreviewConfiguration(WardrobePreviewMode.CAPE, Float.POSITIVE_INFINITY));
	}

	@Test
	void capeHidesWingsWithoutChangingTheSourceStack() {
		var original = new ItemStack(Items.ELYTRA);
		original.setDamageValue(19);
		var state = state(original);
		cape().apply(state, stack -> stack.is(Items.ELYTRA));
		assertTrue(state.chestEquipment.isEmpty());
		assertTrue(original.is(Items.ELYTRA));
		assertEquals(1, original.getCount());
		assertEquals(19, original.getDamageValue());
	}

	@Test
	void capeKeepsOrdinaryArmorThroughAnIndependentCopy() {
		var original = new ItemStack(Items.IRON_CHESTPLATE);
		original.setDamageValue(7);
		var state = state(original);
		cape().apply(state, stack -> false);
		assertNotSame(original, state.chestEquipment);
		assertTrue(ItemStack.matches(original, state.chestEquipment));
		state.chestEquipment.setDamageValue(11);
		assertEquals(7, original.getDamageValue());
	}

	@ParameterizedTest
	@MethodSource("chestEquipment")
	void elytraWorksForEmptyArmorAndRealWingsWithoutChangingTheSource(ItemStack original) {
		var snapshot = original.copy();
		var state = state(original);
		elytra().apply(state, stack -> fail("鞘翅模式直接使用独立预览输入，无需查询真实装备层。"));
		assertTrue(state.chestEquipment.is(Items.ELYTRA));
		assertNotSame(original, state.chestEquipment);
		assertTrue(ItemStack.matches(snapshot, original));
		assertEquals(1, state.chestEquipment.getCount());
	}

	@Test
	void elytraUsesStableStandingStateButPreservesVisibility() {
		var state = state(ItemStack.EMPTY);
		state.pose = Pose.FALL_FLYING;
		state.isFallFlying = true;
		state.isCrouching = true;
		state.isVisuallySwimming = true;
		state.isPassenger = true;
		state.isAutoSpinAttack = true;
		state.swimAmount = 1.0F;
		state.fallFlyingTimeInTicks = 50.0F;
		state.shouldApplyFlyingYRot = true;
		state.flyingYRot = 1.0F;
		state.walkAnimationSpeed = 1.0F;
		state.elytraRotX = 1.0F;
		state.elytraRotY = 1.0F;
		state.elytraRotZ = 1.0F;
		state.isInvisible = true;
		state.showCape = false;
		elytra().apply(state, stack -> false);
		assertEquals(Pose.STANDING, state.pose);
		assertFalse(state.isFallFlying || state.isCrouching || state.isVisuallySwimming || state.isPassenger);
		assertFalse(state.isAutoSpinAttack || state.shouldApplyFlyingYRot);
		assertEquals(0.0F, state.swimAmount);
		assertEquals(0.0F, state.fallFlyingTimeInTicks);
		assertEquals(0.0F, state.flyingYRot);
		assertEquals(0.0F, state.walkAnimationSpeed);
		assertEquals((float) Math.PI / 12.0F, state.elytraRotX);
		assertEquals(0.0F, state.elytraRotY);
		assertEquals(-(float) Math.PI / 12.0F, state.elytraRotZ);
		assertTrue(state.isInvisible);
		assertFalse(state.showCape);
	}

	@Test
	void laterModeAndWorldUpdatesDoNotMutateAlreadySubmittedPreview() {
		var original = new ItemStack(Items.ELYTRA);
		original.setDamageValue(13);
		var world = state(original);
		world.pose = Pose.CROUCHING;
		var worldAppearance = PlayerFashionRenderAppearance.serverCosmetic(Optional.of(CAPE),
				ElytraTextureDecision.customTexture(ELYTRA));
		PlayerFashionRenderState.attach(world, worldAppearance);

		var first = state(original.copy());
		cape().apply(first, stack -> true);
		WardrobePreviewRenderState.attach(first, WardrobePreviewAppearance.vanilla());
		var submitted = submission(first, cape());

		var second = state(original.copy());
		elytra().apply(second, stack -> true);
		WardrobePreviewRenderState.attach(second, WardrobePreviewAppearance.serverCosmetic(
				Optional.of(CAPE), ElytraTextureDecision.customTexture(ELYTRA)));
		var nextSubmission = submission(second, elytra());
		original.setDamageValue(17);

		assertSame(first, submitted.renderState());
		assertNotSame(submitted.renderState(), nextSubmission.renderState());
		assertTrue(first.chestEquipment.isEmpty());
		assertTrue(second.chestEquipment.is(Items.ELYTRA));
		assertEquals(0, second.chestEquipment.getDamageValue());
		assertEquals(WardrobePreviewAppearance.vanilla(), WardrobePreviewRenderState.find(first).orElseThrow());
		assertEquals(ElytraTextureDecision.customTexture(ELYTRA), ElytraTextureOverrideResolver.resolve(second));
		assertSame(original, world.chestEquipment);
		assertEquals(Pose.CROUCHING, world.pose);
		assertSame(worldAppearance, PlayerFashionRenderState.find(world));
		assertTrue(WardrobePreviewRenderState.find(world).isEmpty());
	}

	@Test
	void repeatedFreshExtractionsRestoreCapeArmorAndPoseWithoutResidue() {
		var original = new ItemStack(Items.IRON_CHESTPLATE);
		for (int index = 0; index < 8; index++) {
			var mode = index % 2 == 0 ? WardrobePreviewMode.ELYTRA : WardrobePreviewMode.CAPE;
			var state = state(original.copy());
			state.pose = Pose.CROUCHING;
			state.isCrouching = true;
			new WardrobePreviewConfiguration(mode, 2.0F).apply(state, stack -> false);
			assertTrue(state.chestEquipment.is(mode == WardrobePreviewMode.ELYTRA
					? Items.ELYTRA : Items.IRON_CHESTPLATE));
			assertEquals(mode == WardrobePreviewMode.ELYTRA ? Pose.STANDING : Pose.CROUCHING, state.pose);
			assertEquals(-2.0F, state.xRot);
			assertTrue(original.is(Items.IRON_CHESTPLATE));
		}
	}

	@Test
	void modeChangesPreserveOriginalCapeOnlySplitSharedAndFallbackTextures() {
		var capeOnly = new CapeCosmeticMetadata(new CapeId("cape-only"), "a".repeat(64), Optional.empty());
		var split = new CapeCosmeticMetadata(new CapeId("split"), "a".repeat(64), Optional.of("b".repeat(64)));
		var shared = new CapeCosmeticMetadata(new CapeId("shared"), "a".repeat(64), Optional.of("a".repeat(64)));
		var resolver = new WardrobePreviewAppearanceResolver(List.of(capeOnly, split, shared),
				metadata -> Optional.of(CAPE), metadata -> metadata.elytraSha256()
						.map(hash -> ElytraTextureDecision.customTexture(hash.equals("a".repeat(64)) ? CAPE : ELYTRA))
						.orElseGet(ElytraTextureDecision::vanillaDefault));
		var appearances = List.of(resolver.resolve(Optional.empty()), resolver.resolve(Optional.of(capeOnly.id())),
				resolver.resolve(Optional.of(split.id())), resolver.resolve(Optional.of(shared.id())),
				WardrobePreviewAppearance.serverCosmetic(Optional.empty(), ElytraTextureDecision.vanillaDefault()),
				resolver.resolve(Optional.of(new CapeId("dormant-missing"))));
		var expected = List.of(ElytraTextureDecision.passThrough(), ElytraTextureDecision.vanillaDefault(),
				ElytraTextureDecision.customTexture(ELYTRA), ElytraTextureDecision.customTexture(CAPE),
				ElytraTextureDecision.vanillaDefault(), ElytraTextureDecision.passThrough());
		for (int index = 0; index < appearances.size(); index++) {
			for (var mode : WardrobePreviewMode.values()) {
				var state = state(ItemStack.EMPTY);
				new WardrobePreviewConfiguration(mode, 0.0F).apply(state, stack -> false);
				WardrobePreviewRenderState.attach(state, appearances.get(index));
				assertEquals(expected.get(index), ElytraTextureOverrideResolver.resolve(state));
			}
		}
	}

	private static Stream<ItemStack> chestEquipment() {
		return Stream.of(ItemStack.EMPTY, new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.ELYTRA));
	}

	private static WardrobePreviewConfiguration cape() {
		return new WardrobePreviewConfiguration(WardrobePreviewMode.CAPE, 0.0F);
	}

	private static WardrobePreviewConfiguration elytra() {
		return new WardrobePreviewConfiguration(WardrobePreviewMode.ELYTRA, 0.0F);
	}

	private static AvatarRenderState state(ItemStack equipment) {
		var state = new AvatarRenderState();
		state.chestEquipment = equipment;
		state.pose = Pose.STANDING;
		return state;
	}

	private static GuiEntityRenderState submission(AvatarRenderState state, WardrobePreviewConfiguration configuration) {
		var camera = configuration.cameraOrientation();
		return new GuiEntityRenderState(state, new Vector3f(0.0F, 0.9625F, 0.0F),
				new Quaternionf().rotateZ((float) Math.PI).mul(camera), camera, 0, 0, 77, 78, 34, null);
	}
}
