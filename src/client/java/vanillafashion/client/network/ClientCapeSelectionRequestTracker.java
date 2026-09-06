package vanillafashion.client.network;

import java.util.HashSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/** 只在客户端线程使用；不保存 Screen，不把请求编号当作状态版本。 */
public final class ClientCapeSelectionRequestTracker {
	private final Set<Long> outstanding = new HashSet<>();
	private Object connection;
	private long nextId = 1;

	public ClientCapeSelectionRequestTracker() { }

	// 确定性边界验证入口；正常连接初始化始终从 1 开始。
	ClientCapeSelectionRequestTracker(Object connection, long nextId) {
		beginConnection(connection);
		if (nextId <= 0) {
			throw new IllegalArgumentException("下一个请求编号必须为正数。");
		}
		this.nextId = nextId;
	}

	public void beginConnection(Object connection) {
		clear();
		this.connection = Objects.requireNonNull(connection, "请求连接身份不能为 null。");
	}

	public boolean matchesConnection(Object connection) {
		return this.connection != null && this.connection == connection;
	}

	public boolean disconnect(Object connection) {
		if (!matchesConnection(connection)) {
			return false;
		}
		clear();
		return true;
	}

	public OptionalLong allocate() {
		if (connection == null) {
			return OptionalLong.empty();
		}
		if (nextId == 0) {
			if (!outstanding.isEmpty()) {
				return OptionalLong.empty();
			}
			nextId = 1;
		}
		long allocated = nextId;
		nextId = allocated == Long.MAX_VALUE ? 0 : allocated + 1;
		outstanding.add(allocated);
		return OptionalLong.of(allocated);
	}

	public boolean complete(long requestId) { return outstanding.remove(requestId); }
	public boolean hasOutstanding() { return !outstanding.isEmpty(); }
	public int outstandingCount() { return outstanding.size(); }

	private void clear() {
		outstanding.clear();
		nextId = 1;
		connection = null;
	}
}
