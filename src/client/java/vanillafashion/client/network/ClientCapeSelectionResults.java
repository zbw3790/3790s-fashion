package vanillafashion.client.network;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import vanillafashion.cape.CapeId;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.client.screen.WardrobeSelectionSession;
import vanillafashion.network.CapeSelectionResultPayload;

public final class ClientCapeSelectionResults {
	private ClientCapeSelectionResults() { }

	/** 先应用权威信息，再结束 outstanding，最后才查询当前 UI；不保存旧 Screen。 */
	public static boolean apply(Object connection, UUID self, CapeSelectionResultPayload result,
			ClientPlayerFashionRegistry fashions, ClientCapeSelectionRequestTracker tracker,
			Supplier<WardrobeSelectionSession> currentSession, Predicate<CapeId> metadataPresent) {
		if (!tracker.matchesConnection(connection)) {
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
