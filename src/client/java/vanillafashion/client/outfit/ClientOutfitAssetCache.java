package vanillafashion.client.outfit;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import vanillafashion.cape.CapeAssetHash;
import vanillafashion.outfit.OutfitPngValidator;


/**
 * 仅按内容哈希复用已经验证的 PNG 字节。缓存命中不代表玩家授权；
 * 调用方必须先用当前连接的 Snapshot 限定允许进入运行时的哈希。
 */
public final class ClientOutfitAssetCache {
	private static final String CACHE_DIRECTORY_NAME = "vanilla-fashion";
	private static final String CACHE_SUBDIRECTORY_NAME = "cache";
	private static final String ASSETS_DIRECTORY_NAME = "outfit-assets";

	private final Path assetsDirectory;
	private final Logger logger;

	public ClientOutfitAssetCache(Path assetsDirectory, Logger logger) {
		this.assetsDirectory = Objects.requireNonNull(
				assetsDirectory,
				"客户端 Outfit 缓存目录不能为 null。"
		).toAbsolutePath().normalize();
		this.logger = Objects.requireNonNull(logger, "客户端 Outfit 缓存日志记录器不能为 null。");
	}

	public static ClientOutfitAssetCache fromGameDirectory(Path gameDirectory, Logger logger) {
		Objects.requireNonNull(gameDirectory, "Minecraft 游戏目录不能为 null。");
		return new ClientOutfitAssetCache(
				gameDirectory
						.resolve(CACHE_DIRECTORY_NAME)
						.resolve(CACHE_SUBDIRECTORY_NAME)
						.resolve(ASSETS_DIRECTORY_NAME),
				logger
		);
	}

	public Path assetsDirectory() {
		return assetsDirectory;
	}

	public Path pathFor(String sha256) {
		String validatedHash = CapeAssetHash.requireValid(sha256, "Outfit 缓存 SHA-256 ");
		return assetsDirectory.resolve(validatedHash + ".png");
	}

	public Optional<byte[]> findValidated(String sha256) {
		Path path = pathFor(sha256);

		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return Optional.empty();
		}

		try {
			if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
				invalidateCorruptEntry(path, sha256);
				return Optional.empty();
			}

			long size = Files.size(path);

			if (size < 1 || size > OutfitPngValidator.MAX_ASSET_BYTES) {
				invalidateCorruptEntry(path, sha256);
				return Optional.empty();
			}

			byte[] bytes;
            try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(OutfitPngValidator.MAX_ASSET_BYTES + 1); }

			if (!isValid(sha256, bytes)) {
				invalidateCorruptEntry(path, sha256);
				return Optional.empty();
			}

			return Optional.of(bytes);
		} catch (IOException | SecurityException exception) {
			logger.warn("Vanilla Fashion 读取 Outfit 缓存失败，已按 cache miss 处理：{}。", shortHash(sha256));
			return Optional.empty();
		}
	}

	public StoreResult storeValidated(String sha256, byte[] pngBytes) {
		CapeAssetHash.requireValid(sha256, "Outfit 缓存 SHA-256 ");
		Objects.requireNonNull(pngBytes, "Outfit 缓存 PNG 字节不能为 null。");

		if (!isValid(sha256, pngBytes)) {
			return StoreResult.REJECTED_INVALID;
		}

		if (findValidated(sha256).isPresent()) {
			return StoreResult.ALREADY_PRESENT;
		}

		Path temporaryFile = null;

		try {
			Files.createDirectories(assetsDirectory);
			temporaryFile = Files.createTempFile(assetsDirectory, shortHash(sha256) + "-", ".tmp");
			Files.write(temporaryFile, pngBytes);

			byte[] temporaryBytes = Files.readAllBytes(temporaryFile);

			if (!isValid(sha256, temporaryBytes)) {
				return StoreResult.WRITE_FAILED;
			}

			Path target = pathFor(sha256);

			try {
				Files.move(
						temporaryFile,
						target,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING
				);
				temporaryFile = null;
				return StoreResult.STORED_ATOMIC;
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
				temporaryFile = null;
				return StoreResult.STORED_FALLBACK;
			} catch (IOException exception) {
				Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
				temporaryFile = null;
				return StoreResult.STORED_FALLBACK;
			}
		} catch (IOException | SecurityException exception) {
			logger.warn("Vanilla Fashion 写入 Outfit 内容缓存失败：{}。", shortHash(sha256));
			return StoreResult.WRITE_FAILED;
		} finally {
			if (temporaryFile != null) {
				try {
					Files.deleteIfExists(temporaryFile);
				} catch (IOException | SecurityException exception) {
					logger.warn("Vanilla Fashion 无法删除 Outfit 缓存临时文件：{}。", shortHash(sha256));
				}
			}
		}
	}

	public boolean invalidate(String sha256) {
		Path path = pathFor(sha256);

		try {
			return Files.deleteIfExists(path);
		} catch (IOException | SecurityException exception) {
			logger.warn("Vanilla Fashion 无法删除损坏的 Outfit 缓存：{}。", shortHash(sha256));
			return false;
		}
	}

	private void invalidateCorruptEntry(Path path, String sha256) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException | SecurityException exception) {
			logger.warn("Vanilla Fashion 无法删除损坏的 Outfit 缓存：{}。", shortHash(sha256));
		}
	}

	private static boolean isValid(String sha256, byte[] bytes) {
		return bytes.length >= 1
				&& bytes.length <= OutfitPngValidator.MAX_ASSET_BYTES
				&& sha256.equals(CapeAssetHash.sha256(bytes))
				&& OutfitPngValidator.validate(bytes) == OutfitPngValidator.Result.VALID;
	}

	private static String shortHash(String sha256) {
		return sha256.substring(0, 12);
	}

	public enum StoreResult {
		STORED_ATOMIC,
		STORED_FALLBACK,
		ALREADY_PRESENT,
		REJECTED_INVALID,
		WRITE_FAILED
	}
}
