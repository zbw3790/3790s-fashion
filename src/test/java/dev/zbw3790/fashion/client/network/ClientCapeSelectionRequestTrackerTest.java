package dev.zbw3790.fashion.client.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ClientCapeSelectionRequestTrackerTest {
	private final Object connection = new Object();

	private ClientCapeSelectionRequestTracker tracker() {
		var tracker = new ClientCapeSelectionRequestTracker();
		tracker.beginConnection(connection);
		return tracker;
	}

	@Test void startsAtOne() { assertEquals(1, tracker().allocate().orElseThrow()); }
	@Test void unconnectedCannotAllocate() { assertTrue(new ClientCapeSelectionRequestTracker().allocate().isEmpty()); }

	@Test void allocationsAreDistinctAndMonotonic() {
		var tracker = tracker();
		assertEquals(1, tracker.allocate().orElseThrow());
		assertEquals(2, tracker.allocate().orElseThrow());
		assertEquals(3, tracker.allocate().orElseThrow());
		assertEquals(3, tracker.outstandingCount());
	}

	@Test void completionDoesNotReuseId() {
		var tracker = tracker();
		assertTrue(tracker.complete(tracker.allocate().orElseThrow()));
		assertFalse(tracker.hasOutstanding());
		assertEquals(2, tracker.allocate().orElseThrow());
	}

	@Test void unknownAndDuplicateCompletionAreSafe() {
		var tracker = tracker();
		long id = tracker.allocate().orElseThrow();
		assertFalse(tracker.complete(99));
		assertTrue(tracker.hasOutstanding());
		assertTrue(tracker.complete(id));
		assertFalse(tracker.complete(id));
	}

	@Test void disconnectClearsAndNewConnectionResets() {
		var tracker = tracker();
		tracker.allocate();
		assertTrue(tracker.disconnect(connection));
		assertFalse(tracker.hasOutstanding());
		assertTrue(tracker.allocate().isEmpty());
		tracker.beginConnection(new Object());
		assertEquals(1, tracker.allocate().orElseThrow());
	}

	@Test void oldDisconnectDoesNotClearNewOutstanding() {
		var tracker = tracker();
		var newer = new Object();
		tracker.beginConnection(newer);
		tracker.allocate();
		assertFalse(tracker.disconnect(connection));
		assertEquals(1, tracker.outstandingCount());
		assertTrue(tracker.matchesConnection(newer));
	}

	@Test void overflowWaitsForAllOutstandingThenRestartsAtOne() {
		var tracker = new ClientCapeSelectionRequestTracker(connection, Long.MAX_VALUE - 1);
		assertEquals(Long.MAX_VALUE - 1, tracker.allocate().orElseThrow());
		assertEquals(Long.MAX_VALUE, tracker.allocate().orElseThrow());
		assertTrue(tracker.allocate().isEmpty());
		tracker.complete(Long.MAX_VALUE);
		assertTrue(tracker.allocate().isEmpty());
		tracker.complete(Long.MAX_VALUE - 1);
		assertEquals(1, tracker.allocate().orElseThrow());
	}

	@Test void overflowNeverProducesZeroOrNegative() {
		var tracker = new ClientCapeSelectionRequestTracker(connection, Long.MAX_VALUE);
		long last = tracker.allocate().orElseThrow();
		assertTrue(last > 0);
		tracker.complete(last);
		assertEquals(1, tracker.allocate().orElseThrow());
		assertEquals(2, tracker.allocate().orElseThrow());
	}

	@Test void trackerHasNoScreenOwnership() {
		for (var field : ClientCapeSelectionRequestTracker.class.getDeclaredFields()) {
			assertFalse(field.getType().getName().contains("Screen"));
			assertFalse(java.util.Map.class.isAssignableFrom(field.getType()));
		}
	}
}
