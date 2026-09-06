package vanillafashion.fashion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vanillafashion.cape.CapeId;

class PlayerFashionAuthoritativeStateTest {
	private static final CapeId FIRST = new CapeId("first");
	private static final CapeId SECOND = new CapeId("second");

	@Test
	void vanillaInvariant() {
		var state = PlayerFashionAuthoritativeState.vanilla();
		assertTrue(state.storedSelection().isEmpty());
		assertTrue(state.effectiveSelection().isEmpty());
		assertTrue(state.isVanillaStored());
		assertFalse(state.isActiveCustom());
		assertFalse(state.isDormant());
	}

	@Test
	void activeCustomInvariant() {
		var state = PlayerFashionAuthoritativeState.active(FIRST);
		assertEquals(Optional.of(FIRST), state.storedSelection());
		assertEquals(Optional.of(FIRST), state.effectiveSelection());
		assertFalse(state.isVanillaStored());
		assertTrue(state.isActiveCustom());
		assertFalse(state.isDormant());
	}

	@Test
	void dormantInvariant() {
		var state = PlayerFashionAuthoritativeState.dormant(FIRST);
		assertEquals(Optional.of(FIRST), state.storedSelection());
		assertTrue(state.effectiveSelection().isEmpty());
		assertFalse(state.isVanillaStored());
		assertFalse(state.isActiveCustom());
		assertTrue(state.isDormant());
	}

	@Test
	void rejectsEffectiveWithoutStored() {
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionAuthoritativeState(
				Optional.empty(), Optional.of(FIRST)));
	}

	@Test
	void rejectsDifferentStoredAndEffectiveCustom() {
		assertThrows(IllegalArgumentException.class, () -> new PlayerFashionAuthoritativeState(
				Optional.of(FIRST), Optional.of(SECOND)));
	}

	@ParameterizedTest
	@MethodSource("validStates")
	void everyValidStateHasExactlyOneClassification(PlayerFashionAuthoritativeState state) {
		int classifications = (state.isVanillaStored() ? 1 : 0)
				+ (state.isActiveCustom() ? 1 : 0)
				+ (state.isDormant() ? 1 : 0);
		assertEquals(1, classifications);
	}

	@Test
	void constructorRejectsNullStoredSelection() {
		assertThrows(NullPointerException.class,
				() -> new PlayerFashionAuthoritativeState(null, Optional.empty()));
	}

	@Test
	void constructorRejectsNullEffectiveSelection() {
		assertThrows(NullPointerException.class,
				() -> new PlayerFashionAuthoritativeState(Optional.empty(), null));
	}

	@Test
	void activeFactoryRejectsNullCape() {
		assertThrows(NullPointerException.class, () -> PlayerFashionAuthoritativeState.active(null));
	}

	@Test
	void dormantFactoryRejectsNullCape() {
		assertThrows(NullPointerException.class, () -> PlayerFashionAuthoritativeState.dormant(null));
	}

	@Test
	void equalSemanticStatesRemainValueEqual() {
		assertEquals(PlayerFashionAuthoritativeState.active(FIRST),
				new PlayerFashionAuthoritativeState(Optional.of(FIRST), Optional.of(FIRST)));
	}

	private static Stream<PlayerFashionAuthoritativeState> validStates() {
		return Stream.of(
				PlayerFashionAuthoritativeState.vanilla(),
				PlayerFashionAuthoritativeState.active(FIRST),
				PlayerFashionAuthoritativeState.dormant(FIRST));
	}
}
