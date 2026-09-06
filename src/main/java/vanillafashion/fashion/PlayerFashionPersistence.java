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
			PlayerFashionSavedData loaded = storage.get(PlayerFashionSavedData.TYPE);
			if (loaded != null) {
				if (loaded.rejectedEntryCount() > 0) {
					logger.warn("玩家时装存档已跳过 {} 条无效或重复记录，其余合法记录保留。", loaded.rejectedEntryCount());
				}
				return new LoadResult(loaded, Optional.empty());
			}
			// get 失败不调用 computeIfAbsent；只有前后均能确认缺失时才建立默认值。
			if (missing && Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
				PlayerFashionSavedData created = new PlayerFashionSavedData();
				storage.set(PlayerFashionSavedData.TYPE, created);
				created.setDirty(false);
				return new LoadResult(created, Optional.empty());
			}
			return degraded(logger, "玩家时装存档读取或解码失败；原文件保留，所有持久化修改已禁止。");
		} catch (IOException | SecurityException exception) {
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
