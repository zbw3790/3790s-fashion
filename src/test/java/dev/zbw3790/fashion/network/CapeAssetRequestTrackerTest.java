package dev.zbw3790.fashion.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeAssetLimits;

class CapeAssetRequestTrackerTest {
	@Test
	void sameHashIsOnlyAcceptedOncePerConnection() {
		CapeAssetRequestTracker tracker = new CapeAssetRequestTracker();
		UUID playerId = UUID.randomUUID();
		Object connection = new Object();
		tracker.open(playerId, connection);

		assertEquals(CapeAssetRequestTracker.ClaimResult.ACCEPTED,
				tracker.claim(playerId, connection, hash(1)));
		assertEquals(CapeAssetRequestTracker.ClaimResult.DUPLICATE,
				tracker.claim(playerId, connection, hash(1)));
	}

	@Test
	void closeClearsConnectionState() {
		CapeAssetRequestTracker tracker = new CapeAssetRequestTracker();
		UUID playerId = UUID.randomUUID();
		Object first = new Object();
		Object second = new Object();
		tracker.open(playerId, first);
		tracker.claim(playerId, first, hash(1));
		tracker.close(playerId, first);
		tracker.open(playerId, second);

		assertEquals(1, tracker.connectionCount());
		assertEquals(CapeAssetRequestTracker.ClaimResult.ACCEPTED,
				tracker.claim(playerId, second, hash(1)));
	}

	@Test
	void rejectsRequestsAbovePerConnectionLimit() {
		CapeAssetRequestTracker tracker = new CapeAssetRequestTracker();
		UUID playerId = UUID.randomUUID();
		Object connection = new Object();
		tracker.open(playerId, connection);

		for (int index = 0; index < CapeAssetLimits.MAX_REQUESTED_HASHES_PER_CONNECTION; index++) {
			assertEquals(CapeAssetRequestTracker.ClaimResult.ACCEPTED,
					tracker.claim(playerId, connection, hash(index)));
		}

		assertEquals(CapeAssetRequestTracker.ClaimResult.LIMIT_REACHED,
				tracker.claim(playerId, connection, hash(3000)));
	}

	@Test
	void staleDisconnectCannotRemoveReplacementConnection() {
		CapeAssetRequestTracker tracker = new CapeAssetRequestTracker();
		UUID playerId = UUID.randomUUID();
		Object oldConnection = new Object();
		Object currentConnection = new Object();
		tracker.open(playerId, oldConnection);
		tracker.open(playerId, currentConnection);

		assertFalse(tracker.close(playerId, oldConnection));
		assertEquals(1, tracker.connectionCount());
		assertEquals(CapeAssetRequestTracker.ClaimResult.ACCEPTED,
				tracker.claim(playerId, currentConnection, hash(1)));
	}

	@Test
	void staleOrUnknownConnectionCannotClaimRequestBudget() {
		CapeAssetRequestTracker tracker = new CapeAssetRequestTracker();
		UUID playerId = UUID.randomUUID();
		Object currentConnection = new Object();
		tracker.open(playerId, currentConnection);

		assertEquals(CapeAssetRequestTracker.ClaimResult.STALE_CONNECTION,
				tracker.claim(playerId, new Object(), hash(1)));
		assertEquals(CapeAssetRequestTracker.ClaimResult.STALE_CONNECTION,
				tracker.claim(UUID.randomUUID(), new Object(), hash(2)));
		assertEquals(CapeAssetRequestTracker.ClaimResult.ACCEPTED,
				tracker.claim(playerId, currentConnection, hash(1)));
	}

	private static String hash(int value) {
		return "%064x".formatted(value);
	}
}
