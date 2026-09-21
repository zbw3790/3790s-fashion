package dev.zbw3790.fashion.client.network;

import java.util.UUID;
import java.util.function.Supplier;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.screen.WardrobeSelectionSession;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.network.FullFashionSelectionResultPayload;

/** 先排序全局权威，再独立结算请求；低 revision ACK 也能解除自己的 pending。 */
public final class ClientFullFashionSelectionResults {
    private ClientFullFashionSelectionResults() { }
    public static boolean apply(Object connection, Object requestConnection, UUID self, FullFashionSelectionResultPayload result,
            ClientPlayerFashionRegistry registry, Supplier<WardrobeSelectionSession> currentScreen) {
        if (registry.route()!=FashionAuthorityRoute.V4 || registry.connectionIdentity()!=connection || !registry.fullRequests().matches(requestConnection)) return false;
        result.authority().ifPresent(state -> registry.full().result(connection,new FullPlayerFashionEntry(self,state)));
        boolean matched=registry.fullRequests().complete(requestConnection,result.requestId());
        WardrobeSelectionSession screen=currentScreen.get();
        if (matched && screen!=null && screen.owns(result.requestId())) screen.acceptFullResult(result,registry.fullSelfAuthority(self),registry.state());
        else if (screen!=null) screen.observeFull(registry.fullSelfAuthority(self),registry.state());
        return matched;
    }
}
