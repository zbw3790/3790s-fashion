package vanillafashion.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import vanillafashion.cape.CapeAssetLimits;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistrySnapshot;
import vanillafashion.fashion.PlayerFashionSavedData;
import vanillafashion.fashion.PlayerFashionSnapshot;

class ReleaseProtocolBoundaryTest {
	private static final List<CustomPacketPayload.Type<?>> S2C = List.of(
			OpenWardrobePayload.TYPE,
			WardrobeAvailablePayload.TYPE,
			CapeRegistrySnapshotPayload.TYPE,
			CapeAssetDataPayload.TYPE,
			PlayerFashionSnapshotPayload.TYPE,
			PlayerFashionUpdatePayload.TYPE,
			PlayerFashionRemovePayload.TYPE,
			CapeSelectionResultPayload.TYPE
	);
	private static final List<CustomPacketPayload.Type<?>> C2S = List.of(
			CapeAssetRequestPayload.TYPE,
			SetCapeSelectionPayload.TYPE
	);

	@Test
	void finalProtocolHasEightClientboundAndTwoServerboundPayloads() {
		assertEquals(8, S2C.size());
		assertEquals(2, C2S.size());
		Set<Identifier> allIds = java.util.stream.Stream.concat(S2C.stream(), C2S.stream())
				.map(CustomPacketPayload.Type::id)
				.collect(java.util.stream.Collectors.toSet());
		assertEquals(10, allIds.size());
		assertEquals(Set.of(
				"open_wardrobe", "wardrobe_available", "cape_registry_snapshot", "cape_asset_data",
				"player_fashion_snapshot", "player_fashion_update", "player_fashion_remove",
				"cape_selection_result", "cape_asset_request", "set_cape_selection"
		), allIds.stream().map(Identifier::getPath).collect(java.util.stream.Collectors.toSet()));
		assertEquals(Set.of("vanilla_fashion"),
				allIds.stream().map(Identifier::getNamespace).collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	void everyFinalCollectionAndPersistenceLimitIsFrozen() {
		assertEquals(1024, CapeRegistrySnapshot.MAX_CAPE_ENTRIES);
		assertEquals(1024, PlayerFashionSnapshot.MAX_PLAYER_FASHION_ENTRIES);
		assertEquals(64, CapeId.MAX_LENGTH);
		assertEquals(65536, CapeAssetLimits.MAX_ASSET_BYTES);
		assertEquals(64, CapeAssetLimits.MAX_REQUEST_HASHES);
		assertEquals(2048, CapeAssetLimits.MAX_REQUESTED_HASHES_PER_CONNECTION);
		assertEquals(16384, PlayerFashionSavedData.MAX_PERSISTED_PLAYER_FASHION_ENTRIES);
	}

	@Test
	void clientRequestsCannotNameAnotherPlayerOrFilesystemLocation() {
		assertEquals(List.of("sha256Hashes"), componentNames(CapeAssetRequestPayload.class));
		assertEquals(List.of("requestId", "selection"), componentNames(SetCapeSelectionPayload.class));
		for (Class<?> request : List.of(CapeAssetRequestPayload.class, SetCapeSelectionPayload.class)) {
			for (RecordComponent component : request.getRecordComponents()) {
				assertFalse(component.getType() == UUID.class);
				assertFalse(component.getType() == Path.class);
				assertFalse(component.getType() == Identifier.class);
				assertFalse(component.getType() == byte[].class);
			}
		}
	}

	private static List<String> componentNames(Class<?> recordType) {
		return java.util.Arrays.stream(recordType.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
	}
}
