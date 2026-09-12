package dev.zbw3790.fashion.client.network;

import java.util.Objects;
import java.util.OptionalLong;

/** 连接级单 outstanding，编号耗尽不复用；Screen 关闭不取消请求。 */
public final class ClientFullFashionRequestTracker {
    private Object connection;
    private long lastId, outstanding;
    public void begin(Object connection) { this.connection=Objects.requireNonNull(connection); lastId=0; outstanding=0; }
    public boolean matches(Object connection) { return connection!=null && this.connection==connection; }
    public OptionalLong allocate() {
        if (connection==null || outstanding!=0 || lastId==Long.MAX_VALUE) return OptionalLong.empty();
        outstanding=++lastId; return OptionalLong.of(outstanding);
    }
    public boolean complete(Object connection, long requestId) {
        if (!matches(connection) || requestId<=0 || requestId!=outstanding) return false; outstanding=0; return true;
    }
    public boolean hasOutstanding() { return outstanding!=0; }
    public boolean disconnect(Object connection) { if (!matches(connection)) return false; clear(); return true; }
    public void clear() { connection=null; lastId=0; outstanding=0; }
}
