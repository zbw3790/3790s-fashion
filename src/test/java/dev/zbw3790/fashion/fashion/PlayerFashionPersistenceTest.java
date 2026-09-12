package dev.zbw3790.fashion.fashion;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.fashion.FashionTestSupport.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.cape.CapeAssetHash;

class PlayerFashionPersistenceTest {
	@TempDir
	Path temporaryDirectory;

	@BeforeAll
	static void prepareMinecraftTypes() {
		bootstrap();
	}

	@Test
	void realStorageRoundtripRetainsOfflinePlayersAndClearsVanilla() throws IOException {
		assertTrue(FabricLoader.getInstance().isModLoaded("fabric-object-builder-api-v1"));
		Path directory = temporaryDirectory.resolve("save/data");
		Path file = createSavedFile(directory);
		assertEquals(directory.resolve("fashion_3790/player_fashion.dat"), file);
		try (SavedDataStorage storage = storage(directory)) {
			var loaded = load(storage, directory);
			assertTrue(loaded.degradedReason().isEmpty());
			assertEquals(Map.of(FIRST, FOUNDER, SECOND, BUILDER), loaded.data().snapshot());
			assertFalse(loaded.data().isDirty());
			var service = service(loaded.data(), valid(temporaryDirectory));
			assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(FIRST, Optional.empty()));
			storage.saveAndJoin();
		}
		try (SavedDataStorage storage = storage(directory)) {
			assertEquals(Map.of(SECOND, BUILDER), load(storage, directory).data().snapshot());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"garbage", "truncated", "unknown_version", "version_type", "missing_entries",
			"entries_type", "over_capacity", "missing_data"})
	void corruptionIsPreservedThroughRealSaveAndClose(String damage) throws IOException {
		Path directory = temporaryDirectory.resolve("save/data");
		Path file = createSavedFile(directory);
		byte[] original = Files.readAllBytes(file);
		CompoundTag outer = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		CompoundTag content = outer.getCompound("data").orElseThrow();
		switch (damage) {
			case "garbage" -> Files.write(file, new byte[] {7, 3, 1, 0});
			case "truncated" -> Files.write(file, Arrays.copyOf(original, 12));
			case "unknown_version" -> content.putInt("schema_version", 999);
			case "version_type" -> content.putString("schema_version", "1");
			case "missing_entries" -> content.remove("entries");
			case "entries_type" -> content.putString("entries", "损坏列表");
			case "over_capacity" -> {
				ListTag entries = content.getList("entries").orElseThrow();
				while (entries.size() <= PlayerFashionSavedData.MAX_PERSISTED_PLAYER_FASHION_ENTRIES) {
					entries.add(entry(new java.util.UUID(1, entries.size()).toString(), "founder"));
				}
			}
			case "missing_data" -> outer.remove("data");
			default -> throw new AssertionError("未知测试损坏类型。");
		}
		if (!damage.equals("garbage") && !damage.equals("truncated")) {
			NbtIo.writeCompressed(outer, file);
		}
		byte[] damaged = Files.readAllBytes(file);
		assertFalse(Arrays.equals(original, damaged));
		String damagedHash = CapeAssetHash.sha256(damaged);
		var controlType = new SavedDataType<>(Identifier.fromNamespaceAndPath("fashion_3790", "test_control"),
				PlayerFashionSavedData::new, PlayerFashionSavedData.CODEC, null);
		try (SavedDataStorage storage = storage(directory)) {
			var loaded = load(storage, directory);
			assertTrue(loaded.degradedReason().isPresent());
			var service = new PlayerFashionService(loaded, valid(temporaryDirectory));
			assertEquals(PlayerFashionService.Availability.PERSISTENCE_DEGRADED_READ_ONLY, service.availability());
			assertFalse(loaded.authoritativeStateKnown());
			assertFalse(service.canProvideAuthoritativeSnapshot());
			assertEquals(PlayerFashionSnapshot.unavailable(),
					PlayerFashionSnapshotBuilder.build(java.util.List.of(FIRST, SECOND), service));
			assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE,
					service.setSelection(FIRST, Optional.of(FOUNDER)));
			assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, service.setSelection(FIRST, Optional.empty()));
			service.reconcile(valid(temporaryDirectory));
			assertFalse(loaded.data().isDirty());
			// 不调用 storage.get 再次读取坏文件；以下真实保存证明没有注册空替身。
			// 同一真实存储继续保存其他脏数据，证明并非通过禁止全局保存来保护坏文件。
			storage.set(controlType, data(Map.of(SECOND, BUILDER)));
			storage.scheduleSave().join();
			storage.saveAndJoin();
			assertArrayEquals(damaged, Files.readAllBytes(file));
		}
		assertArrayEquals(damaged, Files.readAllBytes(file));
		assertEquals(damagedHash, CapeAssetHash.sha256(Files.readAllBytes(file)));
		try (SavedDataStorage storage = storage(directory)) {
			assertEquals(Map.of(SECOND, BUILDER), storage.get(controlType).snapshot());
			assertTrue(load(storage, directory).degradedReason().isPresent());
		}
		assertArrayEquals(damaged, Files.readAllBytes(file));
	}

    @Test
    void invalidEntryAndValidSiblingAreProtectedAsWholeFile() throws IOException {
        Path directory=temporaryDirectory.resolve("save/data"); Path file=createSavedFile(directory);
        CompoundTag outer=NbtIo.readCompressed(file,NbtAccounter.create(PlayerFashionPersistence.MAX_NBT_BYTES));
        outer.put("data",encoded(entry(FIRST.toString(),"founder"),entry("invalid","builder")));
        NbtIo.writeCompressed(outer,file); byte[] original=Files.readAllBytes(file);
        try (SavedDataStorage storage=storage(directory)) {
            var loaded=load(storage,directory); assertTrue(loaded.degradedReason().isPresent()); assertFalse(loaded.authoritativeStateKnown());
            assertFalse(loaded.data().isDirty()); storage.saveAndJoin();
        }
        assertArrayEquals(original,Files.readAllBytes(file));
    }

	@Test
	void missingFileIsNotWrittenUntilActualMutation() throws IOException {
		Path directory = temporaryDirectory.resolve("save/data");
		try (SavedDataStorage storage = storage(directory)) {
			var loaded = load(storage, directory);
			assertTrue(loaded.degradedReason().isEmpty());
			assertFalse(loaded.data().isDirty());
			storage.saveAndJoin();
		}
		assertFalse(Files.exists(PlayerFashionPersistence.dataFile(directory)));
	}

	@Test
	void directoryAtDataFileIsProtected() throws IOException {
		Path directory = temporaryDirectory.resolve("save/data");
		Path file = PlayerFashionPersistence.dataFile(directory);
		Files.createDirectories(file);
		Path marker = Files.writeString(file.resolve("marker"), "必须保留");
		try (SavedDataStorage storage = storage(directory)) {
			assertTrue(load(storage, directory).degradedReason().isPresent());
			storage.saveAndJoin();
		}
		assertEquals("必须保留", Files.readString(marker));
	}

	@Test
	void differentSaveDoesNotInheritStoredSelections() throws IOException {
		createSavedFile(temporaryDirectory.resolve("first/data"));
		Path second = temporaryDirectory.resolve("second/data");
		try (SavedDataStorage storage = storage(second)) {
			assertTrue(load(storage, second).data().snapshot().isEmpty());
		}
	}

	@Test
	void stableCodecRoundtripUsesNoVanillaEntries() {
		var initial = data(Map.of(SECOND, BUILDER, FIRST, FOUNDER));
		var tag = PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE, initial).getOrThrow();
		var restored = PlayerFashionSavedData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		assertEquals(initial.snapshot(), restored.snapshot());
		assertEquals(tag, PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE,
				data(Map.of(FIRST, FOUNDER, SECOND, BUILDER))).getOrThrow());
		assertFalse(restored.isDirty());
		restored.setSelection(FIRST, Optional.empty());
		var after = PlayerFashionSavedData.CODEC.parse(NbtOps.INSTANCE,
				PlayerFashionSavedData.CODEC.encodeStart(NbtOps.INSTANCE, restored).getOrThrow()).getOrThrow();
		assertEquals(Map.of(SECOND, BUILDER), after.snapshot());
	}

	private Path createSavedFile(Path directory) throws IOException {
		try (SavedDataStorage storage = storage(directory)) {
			var loaded = load(storage, directory);
			assertTrue(loaded.degradedReason().isEmpty());
			var service = service(loaded.data(), valid(temporaryDirectory));
			assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(FIRST, Optional.of(FOUNDER)));
			assertEquals(PlayerFashionService.MutationResult.CHANGED, service.setSelection(SECOND, Optional.of(BUILDER)));
			storage.saveAndJoin();
			assertFalse(loaded.data().isDirty());
		}
		Path file = PlayerFashionPersistence.dataFile(directory);
		assertTrue(Files.isRegularFile(file));
		assertTrue(Files.size(file) > 0);
		return file;
	}

	private PlayerFashionPersistence.LoadResult load(SavedDataStorage storage, Path directory) {
		return PlayerFashionPersistence.load(storage, directory, NOPLogger.NOP_LOGGER);
	}
}
