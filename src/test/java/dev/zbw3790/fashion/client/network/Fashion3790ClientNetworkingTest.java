package dev.zbw3790.fashion.client.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class Fashion3790ClientNetworkingTest {
	@Test
	void disconnectCleanupIsDeferredToClientExecutor() {
		Queue<Runnable> queuedTasks = new ArrayDeque<>();
		AtomicBoolean cleaned = new AtomicBoolean();

		Fashion3790ClientNetworking.scheduleConnectionStateClear(
				queuedTasks::add,
				() -> cleaned.set(true)
		);

		assertFalse(cleaned.get());
		assertFalse(queuedTasks.isEmpty());

		queuedTasks.remove().run();

		assertTrue(cleaned.get());
		assertTrue(queuedTasks.isEmpty());
	}
}
