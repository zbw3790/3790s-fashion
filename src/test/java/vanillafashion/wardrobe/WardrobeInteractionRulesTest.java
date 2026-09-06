package vanillafashion.wardrobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;

class WardrobeInteractionRulesTest {
	@Test
	void acceptsWhenEveryConditionMatches() {
		assertTrue(WardrobeInteractionRules.matches(matchingInput()));
	}

	@Test
	void rejectsNonArmorStand() {
		assertFalse(WardrobeInteractionRules.matches(new WardrobeInteractionRules.Input(
				false, true, true, false, true, true, true, true, true, true
		)));
	}

	@Test
	void rejectsOffHandInteraction() {
		assertFalse(WardrobeInteractionRules.matches(new WardrobeInteractionRules.Input(
				true, false, true, false, true, true, true, true, true, true
		)));
	}

	@Test
	void rejectsNonEmptyPlayerMainHand() {
		assertFalse(WardrobeInteractionRules.matches(new WardrobeInteractionRules.Input(
				true, true, false, false, true, true, true, true, true, true
		)));
	}

	@Test
	void rejectsSpectator() {
		assertFalse(WardrobeInteractionRules.matches(new WardrobeInteractionRules.Input(
				true, true, true, true, true, true, true, true, true, true
		)));
	}

	@Test
	void rejectsAnyOccupiedArmorStandSlot() {
		List.of(
				new WardrobeInteractionRules.Input(true, true, true, false, false, true, true, true, true, true),
				new WardrobeInteractionRules.Input(true, true, true, false, true, false, true, true, true, true),
				new WardrobeInteractionRules.Input(true, true, true, false, true, true, false, true, true, true),
				new WardrobeInteractionRules.Input(true, true, true, false, true, true, true, false, true, true),
				new WardrobeInteractionRules.Input(true, true, true, false, true, true, true, true, false, true),
				new WardrobeInteractionRules.Input(true, true, true, false, true, true, true, true, true, false)
		).forEach(input -> assertFalse(WardrobeInteractionRules.matches(input)));
	}

	@Test
	void unsupportedServerAlwaysKeepsVanillaInteraction() {
		assertEquals(
				InteractionResult.PASS,
				WardrobeInteractionHandler.clientPredictionResult(false, true)
		);
	}

	@Test
	void supportedServerConsumesOnlyMatchingCandidate() {
		assertEquals(
				InteractionResult.CONSUME,
				WardrobeInteractionHandler.clientPredictionResult(true, true)
		);
		assertEquals(
				InteractionResult.PASS,
				WardrobeInteractionHandler.clientPredictionResult(true, false)
		);
	}

	private static WardrobeInteractionRules.Input matchingInput() {
		return new WardrobeInteractionRules.Input(
				true, true, true, false, true, true, true, true, true, true
		);
	}
}
