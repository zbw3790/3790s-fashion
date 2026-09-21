package dev.zbw3790.fashion.client.network;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.screen.WardrobeSelectionSession;
import dev.zbw3790.fashion.network.CapeSelectionResultPayload;

public final class ClientCapeSelectionResults {
	private ClientCapeSelectionResults() { }

	/** 先应用权威信息，再结束 outstanding，最后才查询当前 UI；返回匹配会话是否成功应用，不关闭 Screen。 */
	public static boolean apply(Object connection, UUID self, CapeSelectionResultPayload result,
			ClientPlayerFashionRegistry fashions, ClientCapeSelectionRequestTracker tracker,
			Supplier<WardrobeSelectionSession> currentSession, Predicate<CapeId> metadataPresent) {
		if (fashions.route()==dev.zbw3790.fashion.fashion.FashionAuthorityRoute.V4 || !tracker.matchesConnection(connection)) {
			return false;
		}
		fashions.confirmSelfResult(self, result.authoritativeState());
		tracker.complete(result.requestId());
		var session = currentSession.get();
		if (session == null) {
			return false;
		}
		if (session.owns(result.requestId())) {
			return session.acceptResult(result, fashions.state(), metadataPresent);
		}
		session.observe(fashions.selfAuthority(self), fashions.state());
		return false;
	}
}
