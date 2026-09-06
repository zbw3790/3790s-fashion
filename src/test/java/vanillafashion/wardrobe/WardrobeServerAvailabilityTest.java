package vanillafashion.wardrobe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WardrobeServerAvailabilityTest {
	@Test
	void isInitiallyUnavailable() {
		WardrobeServerAvailability availability = new WardrobeServerAvailability();

		assertFalse(availability.isAvailable());
	}

	@Test
	void becomesAvailableAfterMarking() {
		WardrobeServerAvailability availability = new WardrobeServerAvailability();

		availability.markAvailable();

		assertTrue(availability.isAvailable());
	}

	@Test
	void becomesUnavailableAfterReset() {
		WardrobeServerAvailability availability = new WardrobeServerAvailability();
		availability.markAvailable();

		availability.reset();

		assertFalse(availability.isAvailable());
	}

	@Test
	void repeatedTransitionsRemainStable() {
		WardrobeServerAvailability availability = new WardrobeServerAvailability();

		availability.markAvailable();
		availability.markAvailable();
		assertTrue(availability.isAvailable());

		availability.reset();
		availability.reset();
		assertFalse(availability.isAvailable());

		availability.markAvailable();
		assertTrue(availability.isAvailable());
	}
}
