package vanillafashion.fashion;

import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.fashion.FashionTestSupport.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistryKnowledge;

class PlayerFashionServiceTest {
	@TempDir
	Path root;

	@Test
	void unknownPlayerIsVanillaAndQueryDoesNotDirty() {
		var data = data(Map.of());
		var service = service(data, valid(root));
		assertTrue(service.getStoredSelection(FIRST).isEmpty());
		assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
		assertEquals(PlayerFashionService.MutationResult.NO_CHANGE, service.setSelection(FIRST, Optional.empty()));
		assertEquals(0, service.storedCount());
		assertFalse(data.isDirty());
	}

	@Test
	void selectionAndClearChangeOnlyPersistedMap() {
		var data = data(Map.of());
		var service = service(data, valid(root));
		assertTrue(service.canPlayerSelect(FIRST, FOUNDER));
		assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(FIRST, Optional.of(FOUNDER)));
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
		assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
		assertTrue(data.isDirty());
		data.setDirty(false);
		assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(FIRST, Optional.empty()));
		assertTrue(data.snapshot().isEmpty());
		assertTrue(data.isDirty());
	}

	@Test
	void repeatedSelectionDoesNotDirty() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = service(data, valid(root));
		assertEquals(PlayerFashionService.MutationResult.NO_CHANGE, service.setSelection(FIRST, Optional.of(FOUNDER)));
		assertFalse(data.isDirty());
	}

	@Test
	void differentPlayersHaveIndependentSelections() {
		var service = service(data(Map.of()), valid(root));
		service.setSelection(FIRST, Optional.of(FOUNDER));
		service.setSelection(SECOND, Optional.of(BUILDER));
		assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
		assertEquals(Optional.of(BUILDER), service.getEffectiveSelection(SECOND));
	}

	@Test
	void unavailableCapeIsRejectedWithoutMutation() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = service(data, valid(root));
		CapeId unknown = new CapeId("not-in-registry");
		assertFalse(service.canPlayerSelect(FIRST, unknown));
		assertEquals(PlayerFashionService.MutationResult.CAPE_NOT_AVAILABLE, service.setSelection(FIRST, Optional.of(unknown)));
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
		assertFalse(data.isDirty());
	}

	@Test
	void exactCapacityCanLoadAndRejectsOnlyNewEntry() {
		Map<UUID, CapeId> selections = new HashMap<>();
		for (int index = 0; index < PlayerFashionSavedData.MAX_PERSISTED_PLAYER_FASHION_ENTRIES; index++) {
			selections.put(new UUID(0, index), FOUNDER);
		}
		var encoded = PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data(selections)).getOrThrow();
		var data = PlayerFashionSavedData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
		var service = service(data, valid(root));
		assertEquals(16384, service.storedCount());
		UUID additional = new UUID(1, 0);
		assertEquals(PlayerFashionService.MutationResult.STORAGE_LIMIT, service.setSelection(additional, Optional.of(FOUNDER)));
		assertFalse(data.isDirty());
		assertTrue(service.getStoredSelection(additional).isEmpty());
		assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(FIRST, Optional.of(BUILDER)));
		assertEquals(16384, service.storedCount());
		assertEquals(PlayerFashionService.MutationResult.NO_CHANGE, service.setSelection(FIRST, Optional.of(BUILDER)));
		assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(SECOND, Optional.empty()));
		assertEquals(16383, service.storedCount());
		assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(additional, Optional.of(FOUNDER)));
		assertEquals(16384, service.storedCount());
	}

	@Test
	void validReconciliationRetainsStoredAndDoesNotDirty() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = service(data, valid(root));
		assertEquals(new PlayerFashionService.ReconciliationResult(0, 0), service.reconcile(valid(root)));
		assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
		assertFalse(data.isDirty());
	}

	@Test
	void rejectedSelectionBecomesDormantAndRepairsWithoutNewSelection() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = service(data, valid(root));
		var rejected = new CapeRegistryKnowledge(root, true, Set.of(), Set.of(FOUNDER));
		assertEquals(new PlayerFashionService.ReconciliationResult(0, 1), service.reconcile(rejected));
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
		assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
		assertFalse(data.isDirty());
		assertEquals(PlayerFashionService.MutationResult.CAPE_NOT_AVAILABLE, service.setSelection(FIRST, Optional.of(FOUNDER)));
		assertEquals(new PlayerFashionService.ReconciliationResult(0, 0), service.reconcile(valid(root)));
		assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
		assertFalse(data.isDirty());
	}

	@Test
	void definiteDeletionClearsOfflineEntriesAndRecreationDoesNotRestore() {
		var data = data(Map.of(FIRST, FOUNDER, SECOND, FOUNDER));
		var service = service(data, valid(root));
		var absent = new CapeRegistryKnowledge(root, true, Set.of(), Set.of());
		assertEquals(new PlayerFashionService.ReconciliationResult(2, 0), service.reconcile(absent));
		assertTrue(data.isDirty());
		assertEquals(0, service.storedCount());
		data.setDirty(false);
		service.reconcile(valid(root));
		assertTrue(service.getStoredSelection(FIRST).isEmpty());
		assertTrue(service.getEffectiveSelection(SECOND).isEmpty());
		assertFalse(data.isDirty());
	}

	@Test
	void rootFailurePreservesAllStateAndRefusesIncludingVanilla() {
		var data = data(Map.of(FIRST, FOUNDER, SECOND, BUILDER));
		var service = service(data, valid(root));
		assertEquals(new PlayerFashionService.ReconciliationResult(0, 2),
				service.reconcile(CapeRegistryKnowledge.unavailable(root)));
		assertEquals(PlayerFashionService.Availability.REGISTRY_UNAVAILABLE, service.availability());
		assertEquals(Map.of(FIRST, FOUNDER, SECOND, BUILDER), data.snapshot());
		assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
		assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, service.setSelection(FIRST, Optional.empty()));
		assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, service.setSelection(SECOND, Optional.of(FOUNDER)));
		assertFalse(data.isDirty());
		service.reconcile(valid(root));
		assertEquals(PlayerFashionService.Availability.NORMAL_WRITABLE, service.availability());
		assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
	}

	@Test
	void readOnlyPreservesSafeDataAndCannotClearEvenOnTrustedDeletion() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.of("测试只读")), valid(root));
		assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
		assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, service.setSelection(FIRST, Optional.empty()));
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of()));
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
		assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
		service.reconcile(CapeRegistryKnowledge.unavailable(root));
		assertEquals(PlayerFashionService.Availability.PERSISTENCE_DEGRADED_READ_ONLY, service.availability());
		assertTrue(service.degradedReason().isPresent());
		assertFalse(data.isDirty());
	}

	@Test
	void existingButUnclassifiedPathCannotBeTreatedAsDeletion() throws IOException {
		Files.writeString(root.resolve("founder"), "同名文件不是明确删除");
		var data = data(Map.of(FIRST, FOUNDER));
		var service = service(data, valid(root));
		service.reconcile(new CapeRegistryKnowledge(root, true, Set.of(), Set.of()));
		assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
		assertFalse(data.isDirty());
	}

	@Test
	void missingOrUnreadableRootCannotConfirmDeletion() {
		var unknownRoot = root.resolve("does-not-exist");
		var knowledge = new CapeRegistryKnowledge(unknownRoot, true, Set.of(), Set.of());
		var data = data(Map.of(FIRST, FOUNDER));
		service(data, knowledge).reconcile(knowledge);
		assertEquals(Optional.of(FOUNDER), data.selection(FIRST));
		assertFalse(data.isDirty());
	}

	@Test
	void stoppedReferenceCannotMutatePreviousSave() {
		var data = data(Map.of(FIRST, FOUNDER));
		var service = service(data, valid(root));
		service.stop();
		assertEquals(PlayerFashionService.Availability.STOPPED, service.availability());
		assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
		assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, service.setSelection(FIRST, Optional.empty()));
		assertFalse(data.isDirty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"invalid_uuid", "short_uuid", "invalid_cape", "missing_uuid", "missing_cape", "wrong_type", "non_compound"})
	void malformedEntryRejectsWholeFileIncludingValidSibling(String damage) {
		CompoundTag broken = entry(SECOND.toString(), "builder");
		switch (damage) {
			case "invalid_uuid" -> broken.putString("uuid", "bad");
			case "short_uuid" -> broken.putString("uuid", "1-1-1-1-1");
			case "invalid_cape" -> broken.putString("cape_id", "../bad");
			case "missing_uuid" -> broken.remove("uuid");
			case "missing_cape" -> broken.remove("cape_id");
			case "wrong_type" -> broken.putInt("cape_id", 12);
			case "non_compound" -> { }
			default -> throw new AssertionError("未知测试坏项类型。");
		}
		CompoundTag rootTag = encoded(entry(FIRST.toString(), "founder"), broken);
		if (damage.equals("non_compound")) {
			ListTag entries = rootTag.getList("entries").orElseThrow();
			entries.set(1, StringTag.valueOf("不是复合标签"));
		}
		assertTrue(PlayerFashionSavedData.CODEC.parse(NbtOps.INSTANCE, rootTag).error().isPresent());
	}

	@Test
	void storedSnapshotCannotBeMutatedExternally() {
		var data = data(Map.of(FIRST, FOUNDER));
		assertThrows(UnsupportedOperationException.class, () -> data.snapshot().clear());
		assertEquals(Optional.of(FOUNDER), data.selection(FIRST));
	}
}
