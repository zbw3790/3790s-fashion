package dev.zbw3790.fashion.outfit;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.outfit.OutfitPart.*;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;

class OutfitSelectionsTest {
	private static final OutfitId A = new OutfitId("a");
	private static final OutfitId B = new OutfitId("b");

	@ParameterizedTest
	@ValueSource(strings={"a","0","black_suit","winter.hat","a-b_c.1","original","none"})
	void acceptsExactLogicalIds(String id) { assertEquals(id, new OutfitId(id).value()); }
	@ParameterizedTest
	@ValueSource(strings={"","A","a b","a/b","a\\b","..","a..b","帽子","_a",".a","-a","a\n"," a","a ","a:b"})
	void rejectsInvalidIds(String id) { assertThrows(IllegalArgumentException.class, () -> new OutfitId(id)); }
	@Test void idLengthAndNull() {
		assertEquals(64, new OutfitId("a".repeat(64)).value().length());
		assertThrows(IllegalArgumentException.class, () -> new OutfitId("a".repeat(65)));
		assertThrows(NullPointerException.class, () -> new OutfitId(null));
		assertThrows(NullPointerException.class, () -> outfit(null));
		assertNotEquals(ORIGINAL, outfit(new OutfitId("original")));
		assertNotEquals(NONE, outfit(new OutfitId("none")));
	}
	@Test void defaultAndFullMapAreImmutable() {
		OutfitSelections initial = OutfitSelections.original();
		for (OutfitPart part : ALL) assertEquals(ORIGINAL, initial.get(part));
		EnumMap<OutfitPart, OutfitPartSelection> map = new EnumMap<>(OutfitPart.class);
		for (OutfitPart part : ALL) map.put(part, NONE);
		OutfitSelections copied = OutfitSelections.from(map);
		map.put(HEAD, ORIGINAL);
		assertEquals(NONE, copied.get(HEAD));
		map.remove(HEAD);
		assertThrows(IllegalArgumentException.class, () -> OutfitSelections.from(map));
		map.put(HEAD, null);
		assertThrows(NullPointerException.class, () -> OutfitSelections.from(map));
		assertThrows(UnsupportedOperationException.class, () -> ALL.clear());
	}
	@Test void intersectionPreservesUntargetedAndUnprovidedParts() {
		OutfitSelections initial = OutfitSelections.original().with(LEFT_LEG, NONE).with(RIGHT_ARM, outfit(B));
		var edit = initial.applyOutfit(OutfitGroup.UPPER_GROUP.parts(), A, Set.of(HEAD, BODY, LEFT_ARM));
		assertEquals(Set.of(BODY, LEFT_ARM), edit.changedParts());
		assertEquals(outfit(A), edit.selections().get(BODY));
		assertEquals(outfit(B), edit.selections().get(RIGHT_ARM));
		assertEquals(NONE, edit.selections().get(LEFT_LEG));
		assertEquals(ORIGINAL, edit.selections().get(HEAD));
		assertEquals(ORIGINAL, initial.get(BODY));
		assertThrows(UnsupportedOperationException.class, () -> edit.changedParts().clear());
	}
	@Test void emptyIntersectionAndIdempotenceAreExplicitNoChange() {
		OutfitSelections initial = OutfitSelections.original();
		var absent = initial.applyOutfit(Set.of(HEAD), A, Set.of(BODY));
		assertFalse(absent.changed()); assertSame(initial, absent.selections());
		assertFalse(initial.applyOutfit(Set.of(), A, ALL).changed());
		var selected = initial.applyOutfit(ALL, A, ALL).selections();
		var repeated = selected.applyOutfit(ALL, A, ALL);
		assertFalse(repeated.changed()); assertSame(selected, repeated.selections());
		assertThrows(IllegalArgumentException.class, () -> initial.applyOutfit(ALL, A, Set.of()));
	}
	@Test void wholeGroupAndDetailedEditsCompose() {
		assertEquals(Set.of(HEAD), OutfitGroup.HEAD_GROUP.parts());
		assertEquals(Set.of(BODY,LEFT_ARM,RIGHT_ARM), OutfitGroup.UPPER_GROUP.parts());
		assertEquals(Set.of(LEFT_LEG,RIGHT_LEG), OutfitGroup.LEGS_GROUP.parts());
		var none = OutfitSelections.original().allNone();
		assertEquals(ALL, none.changedParts());
		assertFalse(none.selections().allNone().changed());
		var upper = none.selections().set(OutfitGroup.UPPER_GROUP.parts(), ORIGINAL).selections();
		assertEquals(new OutfitSelections.Uniform(ORIGINAL), upper.summarize(OutfitGroup.UPPER_GROUP.parts()));
		assertEquals(new OutfitSelections.Uniform(NONE), upper.summarize(OutfitGroup.LEGS_GROUP.parts()));
		assertEquals(OutfitSelections.Mixed.INSTANCE, upper.summarize(ALL));
		var head = upper.with(HEAD, outfit(A));
		assertEquals(new OutfitSelections.Uniform(outfit(A)), head.summarize(Set.of(HEAD)));
		assertEquals(OutfitSelections.original(), head.allOriginal().selections());
	}
	@Test void disjointEditsCommuteAndLaterEditWinsOnlyItsTarget() {
		var initial = OutfitSelections.original();
		assertEquals(initial.with(HEAD, outfit(A)).with(BODY, NONE), initial.with(BODY, NONE).with(HEAD, outfit(A)));
		var result = initial.applyOutfit(ALL, A, ALL).selections().with(HEAD, outfit(B));
		assertEquals(outfit(B), result.get(HEAD));
		for (OutfitPart part : ALL) if (part != HEAD) assertEquals(outfit(A), result.get(part));
		assertEquals(OutfitSelections.Mixed.INSTANCE, result.summarize(ALL));
		assertThrows(IllegalArgumentException.class, () -> result.summarize(Set.of()));
	}
	@Test void allTargetAndProviderCombinationsFollowIntersection() {
		for (int targetBits = 0; targetBits < 64; targetBits++) {
			for (int providedBits = 1; providedBits < 64; providedBits++) {
				Set<OutfitPart> targets = bits(targetBits), provided = bits(providedBits);
				var edit = OutfitSelections.original().applyOutfit(targets, A, provided);
				assertEquals((targetBits & providedBits) != 0, edit.changed());
				for (OutfitPart part : ALL) assertEquals(targets.contains(part) && provided.contains(part)
						? outfit(A) : ORIGINAL, edit.selections().get(part));
			}
		}
	}
	private static Set<OutfitPart> bits(int value) {
		Set<OutfitPart> result = EnumSet.noneOf(OutfitPart.class);
		for (OutfitPart part : ALL) if ((value & (1 << part.ordinal())) != 0) result.add(part);
		return result;
	}
}
