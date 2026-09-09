package vanillafashion.network;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/** 时装服务器入口的执行期防线；停止后 execute 可能在网络线程内联。 */
final class FashionServerTasks {
    private FashionServerTasks() { }

    static void execute(MinecraftServer server, Supplier<PlayerFashionNetworking.Channels> lookup,
            Consumer<PlayerFashionNetworking.Channels> task) {
        execute(server, server::isSameThread, server::isStopped, lookup, task);
    }

    static void execute(Executor executor, BooleanSupplier serverThread, BooleanSupplier stopped,
            Supplier<PlayerFashionNetworking.Channels> lookup, Consumer<PlayerFashionNetworking.Channels> task) {
        executor.execute(() -> {
            // 必须先核对真实线程身份；此前不能读取任何服务器所属容器。
            if (!serverThread.getAsBoolean() || stopped.getAsBoolean()) return;
            var channel = lookup.get();
            if (channel != null && channel.running()) task.accept(channel);
        });
    }

    static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("时装服务器生命周期必须在服务器线程执行。");
    }
}
