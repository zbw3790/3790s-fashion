package dev.zbw3790.fashion.cape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class CapeIdTest {
	@Test
	void acceptsValidIdsAndProvidesStableValueSemantics() {
		for (String value : List.of(
				"founder",
				"builder",
				"event_2026",
				"minecon.2026",
				"red-cape",
				"2026_event"
		)) {
			CapeId id = new CapeId(value);

			assertEquals(value, id.value());
			assertEquals(value, id.toString());
			assertEquals(new CapeId(value), id);
			assertEquals(new CapeId(value).hashCode(), id.hashCode());
		}
	}

	@Test
	void rejectsUppercaseIds() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId("Founder"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("FOUNDER"));
	}

	@Test
	void rejectsInvalidInitialCharacters() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId("_my_cape"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("-cape"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId(".cape"));
	}

	@Test
	void rejectsWhitespaceSeparatorsAndUnicode() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId("cape test"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("cape/test"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("cape\\test"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("红色披风"));
	}

	@Test
	void rejectsEmptyId() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId(""));
	}

	@Test
	void rejectsIdLongerThanMaximum() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId("a".repeat(65)));
	}

	@Test
	void doesNotTrimOrNormalizeId() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId(" founder"));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("founder "));
		assertThrows(IllegalArgumentException.class, () -> new CapeId("Founder"));
	}

	@Test
	void rejectsNullId() {
		assertThrows(IllegalArgumentException.class, () -> new CapeId(null));
	}
}
