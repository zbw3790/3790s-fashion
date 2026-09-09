package vanillafashion.network;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistryKnowledge;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;

class FashionServerTasksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    /** 继承实际原版 execute／isSameThread，仅按 MinecraftServer 字节码添加 stopped 调度条件。 */
    static final class StopInlineLoop extends ReentrantBlockableEventLoop<Runnable> {
        final Thread owner = Thread.currentThread();
        volatile boolean stopped;
        StopInlineLoop() { super("时装停止调度回归", true); }
        @Override protected boolean scheduleExecutables() { return super.scheduleExecutables() && !stopped; }
        @Override protected Thread getRunningThread() { return owner; }
        @Override protected boolean shouldRun(Runnable task) { return true; }
        @Override public Runnable wrapRunnable(Runnable task) { return task; }
        void drain() { assertSame(owner, Thread.currentThread()); runAllTasks(); }
        void network(Runnable task) throws Exception {
            var error = new AtomicReference<Throwable>();
            // 故意同名，证明守卫依据 Thread 对象而非线程名字。
            var thread = new Thread(() -> { try { task.run(); } catch (Throwable ex) { error.set(ex); } }, owner.getName());
            thread.setDaemon(true); thread.start(); thread.join(5000);
            assertFalse(thread.isAlive(), "测试网络线程应已结束。");
            if (error.get() != null) throw new AssertionError("测试网络线程失败。", error.get());
        }
    }

    @Test void actualVanillaExecutorReproducesUnguardedStopInlineAndUsesThreadIdentity() throws Exception {
        var loop = new StopInlineLoop(); var ran = new AtomicReference<Thread>();
        assertEquals(BlockableEventLoop.class, loop.getClass().getMethod("execute", Runnable.class).getDeclaringClass());
        assertEquals(BlockableEventLoop.class, loop.getClass().getMethod("isSameThread").getDeclaringClass());
        loop.network(() -> { assertFalse(loop.isSameThread()); loop.execute(() -> ran.set(Thread.currentThread())); });
        assertNull(ran.get()); assertEquals(1, loop.getPendingTasksCount()); loop.drain(); assertSame(loop.owner, ran.get());
        loop.stopped = true; ran.set(null);
        loop.network(() -> loop.execute(() -> ran.set(Thread.currentThread())));
        assertNotNull(ran.get()); assertNotSame(loop.owner, ran.get());
        assertEquals(loop.owner.getName(), ran.get().getName());
    }

    @Test void stoppedInlineIsRejectedBeforeAnyServerContainerOrStopStateRead() throws Exception {
        var loop = new StopInlineLoop(); loop.stopped = true;
        var reads = new AtomicInteger(); var writes = new AtomicInteger();
        loop.network(() -> FashionServerTasks.execute(loop, loop::isSameThread,
                () -> { reads.incrementAndGet(); return loop.stopped; },
                () -> { reads.incrementAndGet(); return new PlayerFashionNetworking.Channels(); }, c -> writes.incrementAndGet()));
        assertEquals(0, reads.get()); assertEquals(0, writes.get()); assertEquals(0, loop.getPendingTasksCount());
    }

    @Test void stopBetweenDispatchAndExecuteCannotBypassTheExecutionTimeGuard() throws Exception {
        var loop = new StopInlineLoop(); var writes = new AtomicInteger();
        loop.network(() -> FashionServerTasks.execute(task -> { loop.stopped = true; loop.execute(task); },
                loop::isSameThread, () -> loop.stopped, PlayerFashionNetworking.Channels::new, c -> writes.incrementAndGet()));
        assertEquals(0, writes.get());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void queuedBeforeStoppingCannotRunAfterCleanupEvenBeforeStoppedFlag(boolean nativeStopped) throws Exception {
        var f = new Fixture(); var before = f.data.storedSnapshot(); int left = f.left.size();
        f.loop.network(() -> f.dispatch(c -> c.leave(f.service, f.ids[0], f.connections[0], f::left)));
        assertEquals(1, f.loop.getPendingTasksCount());
        f.stopping(); f.loop.stopped = nativeStopped; f.loop.drain();
        f.assertEmpty(); assertEquals(left, f.left.size()); assertEquals(before, f.data.storedSnapshot()); assertFalse(f.data.isDirty());
    }

    @ParameterizedTest @EnumSource(value = FashionAuthorityRoute.class, names = {"V2", "LEGACY"})
    void runningDisconnectStillBroadcastsFinalRevisionThenReleasesMembershipAndBudgets(FashionAuthorityRoute route) throws Exception {
        var f = new Fixture(); var id = f.ids[0]; var connection = f.connections[0];
        f.channel.routes.put(connection, route); var before = f.data.storedSnapshot();
        f.loop.network(() -> f.dispatch(c -> assertTrue(c.leave(f.service, id, connection, entry -> {
            f.left(entry); assertSame(f.loop.owner, Thread.currentThread());
            assertTrue(f.service.isCurrent(id, connection)); assertEquals(route, c.route(connection));
            assertEquals(1, entry.state().revision()); assertEquals(before.get(id), entry.state().stored());
            assertEquals(3, c.assets.connectionCount()); assertEquals(3, c.capes.connectionCount());
        }))));
        assertEquals(3, f.service.onlineCount()); f.loop.drain();
        assertEquals(2, f.service.onlineCount()); assertFalse(f.service.isCurrent(id, connection));
        assertEquals(2, f.channel.routes.size()); assertEquals(2, f.channel.assets.connectionCount()); assertEquals(2, f.channel.capes.connectionCount());
        assertEquals(before, f.data.storedSnapshot()); assertFalse(f.data.isDirty()); assertEquals(1, f.left.size());
    }

    @ParameterizedTest @ValueSource(strings = {"old-first", "new-first", "duplicate-old", "same-connection"})
    void sameUuidReplacementAndLateDisconnectPreserveTheNewMembership(String order) throws Exception {
        var f = new Fixture(); var id = f.ids[0]; var old = f.connections[0]; var newer = new Object();
        var before = f.data.storedSnapshot();
        if (order.equals("same-connection")) {
            f.dispatch(c -> c.join(f.service, id, old, f::left));
            assertEquals(1, f.service.authority(id).orElseThrow().revision()); assertTrue(f.left.isEmpty()); return;
        }
        if (order.equals("old-first")) f.dispatch(c -> c.leave(f.service, id, old, f::left));
        f.dispatch(c -> {
            c.join(f.service, id, newer, entry -> {
                f.left(entry); assertTrue(f.service.isCurrent(id, old)); assertEquals(1, entry.state().revision());
            });
            assertEquals(0, f.service.authority(id).orElseThrow().revision());
            assertFalse(c.routes.containsKey(old)); assertEquals(0, c.assets.attempts(old));
            c.routes.put(newer, FashionAuthorityRoute.V2); c.assets.open(newer, Set.of(Fixture.HASH)); c.capes.open(id, newer);
        });
        int repeats = order.equals("duplicate-old") ? 2 : 1;
        for (int i=0; i<repeats; i++) f.loop.network(() -> f.dispatch(c -> assertFalse(c.leave(f.service, id, old, f::left))));
        f.loop.drain(); assertTrue(f.service.isCurrent(id, newer)); assertEquals(0, f.service.authority(id).orElseThrow().revision());
        assertEquals(1, f.left.size()); assertEquals(3, f.channel.routes.size()); assertEquals(3, f.channel.capes.connectionCount());
        assertEquals(3, f.channel.assets.connectionCount()); assertEquals(before, f.data.storedSnapshot()); assertFalse(f.data.isDirty());
    }

    @ParameterizedTest @ValueSource(strings = {"ABAC", "CBBA", "BAAC", "AABC"})
    void manyStopDisconnectOrdersAreSideEffectFreeAndAllStateIsReleased(String order) throws Exception {
        var f = new Fixture(); var before = f.data.storedSnapshot(); f.stopping(); f.loop.stopped = true;
        for (char name : order.toCharArray()) {
            int index = name-'A';
            f.loop.network(() -> f.dispatch(c -> { f.mutations++; c.leave(f.service, f.ids[index], f.connections[index], f::left); }));
        }
        f.sessions.finishStopped(f.serverKey); f.sessions.finishStopped(f.serverKey);
        f.loop.network(() -> f.dispatch(c -> f.mutations++));
        f.assertEmpty(); assertEquals(0, f.sessions.size()); assertEquals(0, f.mutations); assertTrue(f.left.isEmpty());
        assertEquals(before, f.data.storedSnapshot()); assertFalse(f.data.isDirty());
    }

    @Test void concurrentStopCallbacksCannotTouchServerState() throws Exception {
        var f = new Fixture(); f.stopping(); f.loop.stopped = true; var threads = new ArrayList<Thread>();
        for (int i=0; i<3; i++) {
            var thread = new Thread(() -> f.dispatch(c -> f.mutations++)); thread.setDaemon(true); threads.add(thread); thread.start();
        }
        for (Thread t : threads) { t.join(5000); assertFalse(t.isAlive()); }
        f.assertEmpty(); assertEquals(0, f.mutations); assertTrue(f.left.isEmpty());
    }

    @Test void stoppingRejectsQueuedJoinRegisterAndRequestsWithoutCreatingNewChannels() throws Exception {
        var f = new Fixture(); var before = f.data.storedSnapshot();
        f.loop.network(() -> {
            f.dispatch(c -> { f.mutations++; c.join(f.service, new UUID(0,9), new Object(), f::left); });
            f.dispatch(c -> { f.mutations++; c.routes.put(new Object(), FashionAuthorityRoute.V2); });
            f.dispatch(c -> { f.mutations++; c.assets.open(new Object(), Set.of(Fixture.HASH)); });
            f.dispatch(c -> { f.mutations++; c.capes.open(new UUID(0,9), new Object()); });
            f.dispatch(c -> { f.mutations++; f.service.setSelection(f.ids[0], Optional.empty()); });
            f.dispatch(c -> { f.mutations++; f.service.apply(f.ids[0], f.connections[0], true, 1, PlayerFashionStoredState.DEFAULT); });
        });
        f.stopping(); f.loop.drain(); f.channel.join(f.service, new UUID(0,9), new Object(), f::left);
        f.assertEmpty(); assertEquals(0, f.mutations); assertEquals(before, f.data.storedSnapshot()); assertFalse(f.data.isDirty());
    }

    @Test void stoppedOnOwnerThreadAndReleasedServerAreAlsoRejected() {
        var f = new Fixture(); f.loop.stopped = true; f.dispatch(c -> f.mutations++);
        f.loop.stopped = false; f.sessions.finishStopped(f.serverKey); f.dispatch(c -> f.mutations++); assertEquals(0, f.mutations);
    }

    @Test void independentServerChannelsDoNotClearEachOthersBudgets() {
        var first = new Fixture(); var second = new Fixture(); first.stopping(); first.assertEmpty();
        assertEquals(3, second.service.onlineCount()); assertEquals(3, second.channel.routes.size());
        assertEquals(3, second.channel.capes.connectionCount()); assertEquals(3, second.channel.assets.connectionCount());
    }

    static final class Fixture {
        static final String HASH = "a".repeat(64);
        final StopInlineLoop loop = new StopInlineLoop();
        final Object serverKey = new Object();
        final PlayerFashionNetworking.ServerConnections sessions = new PlayerFashionNetworking.ServerConnections();
        final PlayerFashionNetworking.Channels channel = sessions.start(serverKey);
        final PlayerFashionSavedData data = new PlayerFashionSavedData();
        final CapeId founder = new CapeId("founder");
        final PlayerFashionService service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.empty()),
                new CapeRegistryKnowledge(Path.of("fashion-test-assets"), true, Set.of(founder), Set.of(founder)));
        final UUID[] ids = {new UUID(0,1),new UUID(0,2),new UUID(0,3)};
        final Object[] connections = {new Object(),new Object(),new Object()};
        final List<FullPlayerFashionEntry> left = new ArrayList<>();
        int mutations;
        Fixture() {
            var parts = OutfitSelections.original();
            for (var part : OutfitPart.CANONICAL_ORDER) parts = parts.with(part, OutfitPartSelection.NONE);
            for (int i=0; i<3; i++) {
                channel.join(service, ids[i], connections[i], this::left);
                assertEquals(FullFashionSelectionStatus.SUCCESS, service.apply(ids[i], connections[i], true, 0,
                        new PlayerFashionStoredState(Optional.of(founder), parts)).status());
                channel.routes.put(connections[i], i==1?FashionAuthorityRoute.LEGACY:FashionAuthorityRoute.V2);
                channel.assets.open(connections[i], Set.of(HASH));
                channel.assets.claim(connections[i], new OutfitAssetRequestPayload(List.of(HASH)));
                channel.capes.open(ids[i], connections[i]); channel.capes.claim(ids[i], connections[i], HASH);
            }
            data.setDirty(false);
        }
        void left(FullPlayerFashionEntry entry) { assertSame(loop.owner, Thread.currentThread()); left.add(entry); }
        void dispatch(Consumer<PlayerFashionNetworking.Channels> task) {
            FashionServerTasks.execute(loop, loop::isSameThread, () -> loop.stopped,
                    () -> { assertSame(loop.owner, Thread.currentThread()); return sessions.find(serverKey); }, task);
        }
        void stopping() {
            assertSame(loop.owner, Thread.currentThread()); assertTrue(sessions.beginStopping(serverKey)); service.stop();
            assertFalse(sessions.beginStopping(serverKey));
        }
        void assertEmpty() {
            assertEquals(0, service.onlineCount()); assertTrue(service.connections().isEmpty()); assertTrue(service.authority(ids[0]).isEmpty());
            assertEquals(0, channel.routes.size()); assertEquals(0, channel.assets.connectionCount()); assertEquals(0, channel.capes.connectionCount());
            assertTrue(service.outfits().isEmpty()); assertFalse(channel.running());
        }
    }
}
