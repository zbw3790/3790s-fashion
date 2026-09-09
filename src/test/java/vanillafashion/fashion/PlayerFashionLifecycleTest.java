package vanillafashion.fashion;

import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.fashion.FashionTestSupport.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.NOPLogger;
import vanillafashion.cape.CapeRegistryLoader;
import vanillafashion.cape.CapeRegistryService;

class PlayerFashionLifecycleTest {
	@TempDir
	Path temporaryDirectory;

	@BeforeAll
	static void prepareMinecraftTypes() {
		bootstrap();
	}

    @Test
    void stoppingReleasesOnlineAuthorityBeforeFinalRemovalWithoutWritingStored() throws IOException {
        Path capes = temporaryDirectory.resolve("capes");
        writeCape(capes, "founder", "cape_elytra.png");
        writeCape(capes, "builder", "cape_elytra.png");
        Path directory = temporaryDirectory.resolve("save/data");
        seed(directory);
        Path file = directory.resolve("vanilla_fashion/player_fashion.dat");
        byte[] before = Files.readAllBytes(file);
        var registry = new CapeRegistryService();
        var lifecycle = new PlayerFashionLifecycle(registry, NOPLogger.NOP_LOGGER);
        try (SavedDataStorage storage = storage(directory)) {
            var service = lifecycle.start(storage, capes, directory);
            Object connection = new Object();
            service.join(FIRST, connection, e -> fail("初次加入不应产生 LEFT。"));
            var stored = service.stored(FIRST);
            lifecycle.beginStopping(storage);
            assertSame(service, lifecycle.current(storage).orElseThrow());
            assertEquals(PlayerFashionService.Availability.STOPPED, service.availability());
            assertEquals(0, service.onlineCount()); assertTrue(service.authority(FIRST).isEmpty());
            assertTrue(service.outfits().isEmpty()); assertEquals(stored, service.stored(FIRST));
            assertFalse(service.leave(FIRST, connection, e -> fail("停止后不应重复 LEFT。")));
            assertThrows(IllegalStateException.class, () -> service.join(FIRST, new Object(), e -> {}));
            lifecycle.beginStopping(storage); lifecycle.stop(storage); lifecycle.stop(storage);
            assertTrue(lifecycle.current(storage).isEmpty()); assertTrue(registry.current().isEmpty());
            storage.saveAndJoin();
        }
        assertArrayEquals(before, Files.readAllBytes(file));
    }

	@Test
	void startupLoadsRegistryBeforeDataAndPublishesOnlyReconciledService() throws IOException {
		Path capes = temporaryDirectory.resolve("capes");
		writeCape(capes, "founder", "cape_elytra.png");
		Path directory = temporaryDirectory.resolve("save/data");
		seed(directory);
		var registry = new CapeRegistryService();
		var lifecycle = new PlayerFashionLifecycle(registry, NOPLogger.NOP_LOGGER);
		boolean[] observed = {false};
		try (SavedDataStorage storage = new SavedDataStorage(directory, DataFixers.getDataFixer(),
				HolderLookup.Provider.create(Stream.empty())) {
			@Override
			public <T extends SavedData> void set(SavedDataType<T> type, T value) {
				if (type.equals(PlayerFashionSavedData.TYPE)) {
					assertEquals(1, registry.current().size());
					assertTrue(registry.current().find(FOUNDER).isPresent());
					assertTrue(lifecycle.current(this).isEmpty());
					observed[0] = true;
				}
				super.set(type, value);
			}
		}) {
			assertTrue(lifecycle.current(storage).isEmpty());
			var service = lifecycle.start(storage, capes, directory);
			assertTrue(observed[0]);
			assertSame(service, lifecycle.current(storage).orElseThrow());
			assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
			assertTrue(service.getStoredSelection(SECOND).isEmpty());
			assertEquals(1, service.storedCount());
			assertThrows(IllegalStateException.class, () -> lifecycle.start(storage, capes, directory));
			storage.saveAndJoin();
			lifecycle.stop(storage);
			assertTrue(lifecycle.current(storage).isEmpty());
			assertTrue(registry.current().isEmpty());
			assertEquals(PlayerFashionService.Availability.STOPPED, service.availability());
		}
		try (SavedDataStorage storage = storage(directory)) {
			assertEquals(Map.of(FIRST, FOUNDER), storage.get(PlayerFashionSavedData.TYPE).snapshot());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"shared_conflict", "broken_shared", "wrong_shared_size", "broken_split", "file_placeholder"})
	void realRejectedAssetRetainsStoredAcrossStorageReloadAndRepair(String damage) throws IOException {
		Path capes = temporaryDirectory.resolve("capes");
		Path founder = writeCape(capes, "founder", "cape_elytra.png");
		Path shared = founder.resolve("cape_elytra.png");
		switch (damage) {
			case "shared_conflict" -> writeCape(capes, "founder", "cape.png");
			case "broken_shared" -> Files.write(shared, new byte[] {1});
			case "wrong_shared_size" -> ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), "PNG", shared.toFile());
			case "broken_split" -> {
				Files.delete(shared);
				writeCape(capes, "founder", "cape.png");
				Files.write(founder.resolve("elytra.png"), new byte[] {1});
			}
			case "file_placeholder" -> {
				Files.delete(shared);
				Files.delete(founder);
				Files.writeString(founder, "同名文件不得当成删除");
			}
			default -> throw new AssertionError("未知测试资产类型。");
		}
		Path directory = temporaryDirectory.resolve("save/data");
		seedFounder(directory);
		byte[] before = Files.readAllBytes(PlayerFashionPersistence.dataFile(directory));
		var loadedRegistry = new CapeRegistryLoader().load(capes);
		assertTrue(loadedRegistry.registry().isEmpty());
		assertTrue(loadedRegistry.knownExistingIds().contains(FOUNDER));
		if (!damage.equals("file_placeholder")) {
			assertEquals(1, loadedRegistry.rejectedEntries().size());
		}
		var lifecycle = new PlayerFashionLifecycle(new CapeRegistryService(), NOPLogger.NOP_LOGGER);
		try (SavedDataStorage storage = storage(directory)) {
			var service = lifecycle.start(storage, capes, directory);
			assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
			assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
			assertFalse(storage.get(PlayerFashionSavedData.TYPE).isDirty());
			storage.saveAndJoin();
			lifecycle.stop(storage);
		}
		assertArrayEquals(before, Files.readAllBytes(PlayerFashionPersistence.dataFile(directory)));
		// 只清理 @TempDir 内本测试创建的已知文件，模拟管理员停服修复。
		if (damage.equals("file_placeholder")) {
			Files.delete(founder);
		} else {
			Files.deleteIfExists(founder.resolve("cape.png"));
			Files.deleteIfExists(founder.resolve("elytra.png"));
		}
		writeCape(capes, "founder", "cape_elytra.png");
		try (SavedDataStorage storage = storage(directory)) {
			var service = lifecycle.start(storage, capes, directory);
			assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
			assertFalse(storage.get(PlayerFashionSavedData.TYPE).isDirty());
			lifecycle.stop(storage);
		}
	}

	@Test
	void actualSharedDirectoryDeletionPersistsAndRecreationDoesNotRestore() throws IOException {
		Path capes = temporaryDirectory.resolve("capes");
		Path founder = writeCape(capes, "founder", "cape_elytra.png");
		Path directory = temporaryDirectory.resolve("save/data");
		seedFounder(directory);
		Files.delete(founder.resolve("cape_elytra.png"));
		Files.delete(founder);
		var lifecycle = new PlayerFashionLifecycle(new CapeRegistryService(), NOPLogger.NOP_LOGGER);
		try (SavedDataStorage storage = storage(directory)) {
			var service = lifecycle.start(storage, capes, directory);
			assertTrue(service.getStoredSelection(FIRST).isEmpty());
			assertTrue(storage.get(PlayerFashionSavedData.TYPE).isDirty());
			storage.saveAndJoin();
			lifecycle.stop(storage);
		}
		writeCape(capes, "founder", "cape_elytra.png");
		try (SavedDataStorage storage = storage(directory)) {
			var service = lifecycle.start(storage, capes, directory);
			assertTrue(service.getStoredSelection(FIRST).isEmpty());
			assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
			lifecycle.stop(storage);
		}
	}

	@Test
	void actualRootFailurePreservesBytesAndRecoveryRestoresEffective() throws IOException {
		Path capes = Files.writeString(temporaryDirectory.resolve("capes"), "根目录被文件占用");
		Path directory = temporaryDirectory.resolve("save/data");
		seedFounder(directory);
		byte[] before = Files.readAllBytes(PlayerFashionPersistence.dataFile(directory));
		var registry = new CapeRegistryService();
		var lifecycle = new PlayerFashionLifecycle(registry, NOPLogger.NOP_LOGGER);
		try (SavedDataStorage storage = storage(directory)) {
			var service = lifecycle.start(storage, capes, directory);
			assertEquals(PlayerFashionService.Availability.REGISTRY_UNAVAILABLE, service.availability());
			assertEquals(Optional.of(FOUNDER), service.getStoredSelection(FIRST));
			assertTrue(service.getEffectiveSelection(FIRST).isEmpty());
			assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, service.setSelection(FIRST, Optional.empty()));
			storage.saveAndJoin();
			lifecycle.stop(storage);
		}
		assertArrayEquals(before, Files.readAllBytes(PlayerFashionPersistence.dataFile(directory)));
		Files.delete(capes);
		writeCape(capes, "founder", "cape_elytra.png");
		try (SavedDataStorage storage = storage(directory)) {
			var service = lifecycle.start(storage, capes, directory);
			assertEquals(Optional.of(FOUNDER), service.getEffectiveSelection(FIRST));
			lifecycle.stop(storage);
		}
	}

	@Test
	void stoppedWorldReferenceDoesNotLeakIntoNextWorld() throws IOException {
		Path capes = temporaryDirectory.resolve("capes");
		writeCape(capes, "founder", "cape.png");
		var lifecycle = new PlayerFashionLifecycle(new CapeRegistryService(), NOPLogger.NOP_LOGGER);
		Path first = temporaryDirectory.resolve("first/data");
		Path second = temporaryDirectory.resolve("second/data");
		seedFounder(first);
		PlayerFashionService previous;
		try (SavedDataStorage storage = storage(first)) {
			previous = lifecycle.start(storage, capes, first);
			lifecycle.stop(storage);
		}
		try (SavedDataStorage storage = storage(second)) {
			var next = lifecycle.start(storage, capes, second);
			assertNotSame(previous, next);
			assertTrue(next.getStoredSelection(FIRST).isEmpty());
			assertEquals(PlayerFashionService.MutationResult.SERVICE_UNAVAILABLE, previous.setSelection(FIRST, Optional.empty()));
			lifecycle.stop(storage);
		}
	}

	private void seed(Path directory) {
		try (SavedDataStorage storage = storage(directory)) {
			storage.set(PlayerFashionSavedData.TYPE, data(Map.of(FIRST, FOUNDER, SECOND, BUILDER)));
			storage.saveAndJoin();
		}
	}

	private void seedFounder(Path directory) {
		try (SavedDataStorage storage = storage(directory)) {
			storage.set(PlayerFashionSavedData.TYPE, data(Map.of(FIRST, FOUNDER)));
			storage.saveAndJoin();
		}
	}

	private Path writeCape(Path root, String id, String filename) throws IOException {
		Path entry = Files.createDirectories(root.resolve(id));
		BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0xFF3790FF);
		assertTrue(ImageIO.write(image, "PNG", entry.resolve(filename).toFile()));
		return entry;
	}
}
