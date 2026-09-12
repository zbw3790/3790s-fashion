package dev.zbw3790.fashion.fashion;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.cape.CapeRegistryKnowledge;
import dev.zbw3790.fashion.network.*;

class CapeSelectionHandlerTest {
	@TempDir Path root;

	private CapeSelectionHandler.Outcome process(PlayerFashionService service, Optional<dev.zbw3790.fashion.cape.CapeId> choice) {
		return CapeSelectionHandler.process(FIRST, new SetCapeSelectionPayload(17, choice),
				Optional.of(service), true, true).orElseThrow();
	}

	@Test void customChangedMarksFormalSavedDataDirty() {
		var data = data(Map.of());
		var service = service(data, valid(root));
		var outcome = process(service, Optional.of(FOUNDER));
		assertEquals(CapeSelectionReason.APPLIED, outcome.result().reason());
		assertTrue(outcome.result().accepted());
		assertEquals(17, outcome.result().requestId());
		assertEquals(PlayerFashionAuthoritativeState.active(FOUNDER), outcome.result().authoritativeState());
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
		assertTrue(data.isDirty());
		assertEquals(FIRST, outcome.update().orElseThrow().entry().playerId());
		assertEquals(outcome.result().authoritativeState(), outcome.update().orElseThrow().entry().state());
	}

	@Test void vanillaClearChangesOnlySenderAndDirties() {
		var data = data(Map.of(FIRST, FOUNDER, SECOND, BUILDER));
		var service = service(data, valid(root));
		var outcome = process(service, Optional.empty());
		assertEquals(CapeSelectionReason.APPLIED, outcome.result().reason());
		assertEquals(PlayerFashionAuthoritativeState.vanilla(), outcome.result().authoritativeState());
		assertTrue(service.getStoredSelection(FIRST).isEmpty());
		assertEquals(Optional.of(BUILDER), service.getStoredSelection(SECOND));
		assertTrue(data.isDirty());
		assertTrue(outcome.update().isPresent());
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void noChangeAcknowledgesWithoutDirtyOrDelta(boolean custom) {
		var choice = custom ? Optional.of(FOUNDER) : Optional.<dev.zbw3790.fashion.cape.CapeId>empty();
		var data = data(custom ? Map.of(FIRST, FOUNDER) : Map.of());
		var outcome = process(service(data, valid(root)), choice);
		assertEquals(CapeSelectionReason.NO_CHANGE, outcome.result().reason());
		assertTrue(outcome.result().accepted());
		assertEquals(choice.map(PlayerFashionAuthoritativeState::active)
				.orElseGet(PlayerFashionAuthoritativeState::vanilla), outcome.result().authoritativeState());
		assertTrue(outcome.update().isEmpty());
		assertFalse(data.isDirty());
	}

	@Test void rejectedCapeReturnsExistingEffectiveNotDraft() {
		var data = data(Map.of(FIRST, FOUNDER));
		var outcome = process(service(data, valid(root)), Optional.of(new dev.zbw3790.fashion.cape.CapeId("missing")));
		assertRejected(outcome, CapeSelectionReason.CAPE_NOT_AVAILABLE,
				PlayerFashionAuthoritativeState.active(FOUNDER));
		assertFalse(data.isDirty());
	}

	@ParameterizedTest @ValueSource(strings = {"readonly", "registry", "stopped", "unknown"})
	void unsafeServiceRejectsBothCustomAndVanilla(String failure) {
		var data = data(Map.of(FIRST, FOUNDER));
		var loaded = failure.equals("readonly") || failure.equals("unknown")
				? new PlayerFashionPersistence.LoadResult(data, Optional.of("测试只读原因"), !failure.equals("unknown"))
				: new PlayerFashionPersistence.LoadResult(data, Optional.empty());
		var knowledge = failure.equals("registry")
				? new CapeRegistryKnowledge(root, false, java.util.Set.of(), java.util.Set.of()) : valid(root);
		var service = new PlayerFashionService(loaded, knowledge);
		if (failure.equals("stopped")) { service.stop(); }
		for (var choice : List.of(Optional.<dev.zbw3790.fashion.cape.CapeId>empty(), Optional.of(BUILDER))) {
			assertRejected(process(service, choice), CapeSelectionReason.SERVICE_UNAVAILABLE,
					PlayerFashionAuthoritativeState.fromService(FIRST, service));
			assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
			assertFalse(data.isDirty());
		}
	}

	@Test void absentServiceStillReturnsRejectedAcknowledgement() {
		var outcome = CapeSelectionHandler.process(FIRST, new SetCapeSelectionPayload(2, Optional.empty()),
				Optional.empty(), true, false).orElseThrow();
		assertRejected(outcome, CapeSelectionReason.SERVICE_UNAVAILABLE,
				PlayerFashionAuthoritativeState.vanilla());
	}

	@Test void noResultChannelCannotMutateOrInvokePermission() {
		var calls = new AtomicInteger();
		var data = data(Map.of(FIRST, FOUNDER));
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.empty()),
				valid(root), (id, cape) -> { calls.incrementAndGet(); return true; });
		assertTrue(CapeSelectionHandler.process(FIRST, new SetCapeSelectionPayload(1, Optional.of(BUILDER)),
				Optional.of(service), false, true).isEmpty());
		assertEquals(0, calls.get());
		assertFalse(data.isDirty());
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
	}

	@Test void synchronizationOverflowCannotMutateDespiteWritableService() {
		var data = data(Map.of(FIRST, FOUNDER));
		var outcome = CapeSelectionHandler.process(FIRST, new SetCapeSelectionPayload(1, Optional.empty()),
				Optional.of(service(data, valid(root))), true, false).orElseThrow();
		assertRejected(outcome, CapeSelectionReason.SERVICE_UNAVAILABLE,
				PlayerFashionAuthoritativeState.active(FOUNDER));
		assertFalse(data.isDirty());
	}

	@Test void permissionSeamUsesSenderAndReturnsFiniteRejection() {
		var calls = new AtomicInteger();
		var data = data(Map.of(FIRST, FOUNDER));
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.empty()),
				valid(root), (id, cape) -> {
					assertEquals(FIRST, id);
					assertEquals(BUILDER, cape);
					calls.incrementAndGet();
					return false;
				});
		assertRejected(process(service, Optional.of(BUILDER)), CapeSelectionReason.NOT_ALLOWED,
				PlayerFashionAuthoritativeState.active(FOUNDER));
		assertEquals(1, calls.get());
		assertFalse(data.isDirty());
	}

	@Test void unavailableCapeDoesNotInvokePermissionPolicy() {
		var calls = new AtomicInteger();
		var data = data(Map.of());
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.empty()),
				valid(root), (id, cape) -> { calls.incrementAndGet(); return false; });
		assertRejected(process(service, Optional.of(new dev.zbw3790.fashion.cape.CapeId("absent"))),
				CapeSelectionReason.CAPE_NOT_AVAILABLE, PlayerFashionAuthoritativeState.vanilla());
		assertEquals(0, calls.get());
	}

	@Test void vanillaDoesNotNeedCapePermission() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.empty()),
				valid(root), (id, cape) -> { fail("原版清除不应调用 Cape 权限入口。"); return false; });
		assertEquals(CapeSelectionReason.APPLIED, process(service, Optional.empty()).result().reason());
	}

	@Test void storageLimitRejectsNewRecordButAllowsClearAndReplacement() {
		var records = new HashMap<UUID, dev.zbw3790.fashion.cape.CapeId>();
		for (int index = 1; index <= PlayerFashionSavedData.MAX_PERSISTED_PLAYER_FASHION_ENTRIES; index++) {
			records.put(new UUID(0, index), FOUNDER);
		}
		var data = data(records);
		var service = service(data, valid(root));
		var outcome = CapeSelectionHandler.process(new UUID(9, 9), new SetCapeSelectionPayload(1, Optional.of(BUILDER)),
				Optional.of(service), true, true).orElseThrow();
		assertRejected(outcome, CapeSelectionReason.STORAGE_LIMIT, PlayerFashionAuthoritativeState.vanilla());
		assertFalse(data.isDirty());
		assertEquals(CapeSelectionReason.APPLIED, process(service, Optional.of(BUILDER)).result().reason());
		assertEquals(CapeSelectionReason.APPLIED, process(service, Optional.empty()).result().reason());
	}

	@Test void explicitVanillaClearsDormantStoredRecordNotNoChange() {
		var data = data(Map.of(FIRST, FOUNDER));
		var knowledge = new CapeRegistryKnowledge(root, true, java.util.Set.of(BUILDER), java.util.Set.of(FOUNDER, BUILDER));
		var service = service(data, knowledge);
		assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
		var outcome = process(service, Optional.empty());
		assertEquals(CapeSelectionReason.APPLIED, outcome.result().reason());
		assertEquals(PlayerFashionAuthoritativeState.vanilla(), outcome.result().authoritativeState());
		assertTrue(data.isDirty());
	}

	@Test void rejectedRequestReturnsDormantStoredAndEffectivePair() {
		var service = service(data(Map.of(FIRST, FOUNDER)),
				new CapeRegistryKnowledge(root, true, java.util.Set.of(BUILDER), java.util.Set.of(FOUNDER, BUILDER)));
		var outcome = process(service, Optional.of(new dev.zbw3790.fashion.cape.CapeId("missing")));
		assertRejected(outcome, CapeSelectionReason.CAPE_NOT_AVAILABLE,
				PlayerFashionAuthoritativeState.dormant(FOUNDER));
	}

	@Test void changedBroadcastIncludesRequesterAndFiltersUnsupportedBeforeReply() {
		var service = service(data(Map.of()), valid(root));
		var outcome = process(service, Optional.of(FOUNDER));
		var events = new ArrayList<String>();
		CapeSelectionHandler.deliver(outcome, List.of("请求者", "远端", "不支持"), id -> !id.equals("不支持"),
				(id, update) -> events.add(id), result -> events.add("结果"));
		assertEquals(List.of("请求者", "远端", "结果"), events);
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void unchangedOrRejectedSendsOnlyReply(boolean rejected) {
		var outcome = process(service(data(Map.of()), valid(root)),
				rejected ? Optional.of(new dev.zbw3790.fashion.cape.CapeId("missing")) : Optional.empty());
		var results = new ArrayList<CapeSelectionResultPayload>();
		CapeSelectionHandler.deliver(outcome, List.of(FIRST, SECOND),
				id -> { fail("无 delta 不应检查广播能力。"); return true; },
				(id, update) -> fail("无 delta 不应广播。"), results::add);
		assertEquals(List.of(outcome.result()), results);
	}

	@Test void opaqueIdsDoNotGrantOrderOrIdempotencySemantics() {
		var service = service(data(Map.of()), valid(root));
		for (var request : List.of(new SetCapeSelectionPayload(100, Optional.of(FOUNDER)),
				new SetCapeSelectionPayload(1, Optional.of(BUILDER)), new SetCapeSelectionPayload(1, Optional.empty()))) {
			var result = CapeSelectionHandler.process(FIRST, request, Optional.of(service), true, true).orElseThrow().result();
			assertEquals(request.requestId(), result.requestId());
			assertEquals(CapeSelectionReason.APPLIED, result.reason());
			assertEquals(request.selection(), service.getStoredSelection(FIRST));
		}
	}

    @Test void v2RouteCannotUseLegacyWriteOrReceiveLegacyResult() {
        var data=data(Map.of(FIRST,FOUNDER));var service=service(data,valid(root));Object connection=new Object();service.join(FIRST,connection,e->{});
        var result=CapeSelectionHandler.process(FIRST,new SetCapeSelectionPayload(1,Optional.of(BUILDER)),Optional.of(service),FashionAuthorityRoute.V2,true,true);
        assertTrue(result.isEmpty());assertEquals(Optional.of(FOUNDER),service.getStoredSelection(FIRST));assertEquals(0,service.authority(FIRST).orElseThrow().revision());assertFalse(data.isDirty());
    }

	private static void assertRejected(CapeSelectionHandler.Outcome outcome, CapeSelectionReason reason,
			PlayerFashionAuthoritativeState authoritative) {
		assertFalse(outcome.result().accepted());
		assertEquals(reason, outcome.result().reason());
		assertEquals(authoritative, outcome.result().authoritativeState());
		assertTrue(outcome.update().isEmpty());
	}
}
