package vanillafashion.fashion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.world.level.storage.SavedDataStorage;
import org.slf4j.Logger;

public final class PlayerFashionPersistence {
	public static final long MAX_NBT_BYTES = 64L * 1024 * 1024;

	private PlayerFashionPersistence() {
	}

	public static Path dataFile(Path dataDirectory) {
		return PlayerFashionSavedData.TYPE.id().withSuffix(".dat").resolveAgainst(dataDirectory);
	}

	public static LoadResult load(SavedDataStorage storage, Path dataDirectory, Logger logger) {
		Path file = dataFile(dataDirectory);
		try {
			boolean missing;
			try {
				BasicFileAttributes attributes = Files.readAttributes(
						file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (!attributes.isRegularFile()) {
					return degraded(logger, "玩家时装存档路径不是普通文件。");
				}
				missing = false;
			} catch (NoSuchFileException exception) {
				missing = Files.notExists(file, LinkOption.NOFOLLOW_LINKS);
				if (!missing) {
					return degraded(logger, "无法确认玩家时装存档是否存在。");
				}
			}
            if (!missing) {
                // 同一次有界读取的 NBT 直接进入正式 Codec；不再让 storage.get 无界重读。
                net.minecraft.nbt.CompoundTag root;
                try (var input = new java.io.PushbackInputStream(Files.newInputStream(file), 2)) {
                    byte[] signature = input.readNBytes(2); input.unread(signature);
                    var budget = net.minecraft.nbt.NbtAccounter.create(MAX_NBT_BYTES);
                    root = signature.length == 2 && (signature[0] & 255) == 31 && (signature[1] & 255) == 139
                            ? net.minecraft.nbt.NbtIo.readCompressed(input, budget)
                            : net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(input), budget);
                }
                var content = root.getCompound("data").orElseThrow(() -> new IllegalArgumentException("存档缺少 data 复合标签。"));
                var loaded = PlayerFashionSavedData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, content).getOrThrow();
                storage.set(PlayerFashionSavedData.TYPE, loaded);
                loaded.setDirty(false);
                return new LoadResult(loaded, Optional.empty());
            }
			// 只有前后均能确认缺失时才建立默认值，不为坏文件注册空替身。
			if (missing && Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
				PlayerFashionSavedData created = new PlayerFashionSavedData();
				storage.set(PlayerFashionSavedData.TYPE, created);
				created.setDirty(false);
				return new LoadResult(created, Optional.empty());
			}
			return degraded(logger, "玩家时装存档读取或解码失败；原文件保留，所有持久化修改已禁止。");
		} catch (IOException | RuntimeException exception) {
			return degraded(logger, "无法安全访问玩家时装存档；原文件保留，所有持久化修改已禁止。");
		}
	}

	private static LoadResult degraded(Logger logger, String reason) {
		logger.warn("Vanilla Fashion 持久化已降级为只读：{}", reason);
		// 此空对象不注册到 SavedDataStorage，后续 Vanilla save 无法覆盖坏文件。
		return new LoadResult(new PlayerFashionSavedData(), Optional.of(reason), false);
	}

	public record LoadResult(PlayerFashionSavedData data, Optional<String> degradedReason,
			boolean authoritativeStateKnown) {
		/** 已有可信数据可以只读；读取失败的占位对象必须显式传入 false。 */
		public LoadResult(PlayerFashionSavedData data, Optional<String> degradedReason) {
			this(data, degradedReason, true);
		}

		public LoadResult {
			Objects.requireNonNull(data, "玩家时装持久化数据不能为 null。");
			Objects.requireNonNull(degradedReason, "持久化降级原因不能为 null。");
			if (!authoritativeStateKnown && degradedReason.isEmpty()) {
				throw new IllegalArgumentException("未知持久化状态必须同时禁止写入并说明降级原因。");
			}
		}
	}
}
