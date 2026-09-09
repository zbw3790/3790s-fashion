package vanillafashion.outfit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import static vanillafashion.outfit.OutfitDiagnostic.Code.*;

/** 受控扫描期间管理员不修改目录；前后复核不宣称对抗并发修改的原子隔离。 */
class OutfitFileAccess {
	record Stamp(Path logical, Path real, Object fileKey, long size,
			java.nio.file.attribute.FileTime created, java.nio.file.attribute.FileTime modified,
			boolean directory, boolean regularFile) {}
	static final class Failure extends IOException {
		final OutfitDiagnostic.Code code;
		Failure(OutfitDiagnostic.Code code) { super(code.message()); this.code = code; }
	}

	Stamp root(Path path) throws IOException {
		Files.createDirectories(path);
		Stamp stamp = stamp(path);
		if (!Files.isDirectory(stamp.real(), LinkOption.NOFOLLOW_LINKS)) throw new Failure(NOT_DIRECTORY);
		return stamp;
	}

	Stamp existingRoot(Path path) throws IOException {
        Stamp stamp = stamp(path);
        if (!stamp.directory()) throw new Failure(NOT_DIRECTORY);
        // 手动重载不创建根，也不接受把根改指到其他实际位置。
        if (!stamp.logical().equals(stamp.real())) throw new Failure(UNSAFE_PATH);
        return stamp;
    }

	Stamp directory(Path path, Stamp root) throws IOException {
		check(root, false);
		Stamp candidate = stamp(path);
		if (!candidate.real().startsWith(root.real()) || candidate.real().equals(root.real())) throw new Failure(UNSAFE_PATH);
		if (!Files.isDirectory(candidate.real(), LinkOption.NOFOLLOW_LINKS)) throw new Failure(NOT_DIRECTORY);
		return candidate;
	}

	List<Path> children(Stamp directory, int limit) throws IOException {
		check(directory, true);
		List<Path> entries;
		try (var stream = Files.list(directory.real())) {
			entries = stream.limit((long)limit + 1).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
		} catch (UncheckedIOException exception) { throw exception.getCause(); }
		if (entries.size() > limit) throw new Failure(SCAN_LIMIT);
		check(directory, true);
		return entries;
	}

	byte[] read(Path logical, Stamp directory, Stamp root, int maximum) throws IOException {
		check(root, false); check(directory, true);
		Stamp file = stamp(logical);
		if (!file.real().startsWith(directory.real()) || file.real().equals(directory.real())
				|| !file.real().startsWith(root.real())) throw new Failure(UNSAFE_PATH);
		if (!Files.isRegularFile(file.real(), LinkOption.NOFOLLOW_LINKS)) throw new Failure(NOT_REGULAR_FILE);
		if (file.size() > maximum) throw new Failure(TOO_LARGE);
		beforeOpen(logical);
		check(root, false); check(directory, true); check(file, true);
		ByteBuffer buffer = ByteBuffer.allocate(maximum + 1);
		// 固定解析后的目标，并拒绝该目标在打开时变成文件符号链接。
		try (SeekableByteChannel channel = Files.newByteChannel(file.real(),
				Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
			while (buffer.hasRemaining()) {
				int count = channel.read(buffer);
				if (count < 0) break;
				if (count == 0) throw new Failure(IO_ERROR);
			}
		}
		if (buffer.position() > maximum) throw new Failure(TOO_LARGE);
		afterRead(logical);
		check(file, true); check(directory, true); check(root, false);
		return java.util.Arrays.copyOf(buffer.array(), buffer.position());
	}

	// 包内测试可在固定边界注入文件变化，不向正式调用方暴露任意路径解析器。
	void beforeOpen(Path path) throws IOException {}
	void afterRead(Path path) throws IOException {}

	void check(Stamp expected, boolean content) throws IOException {
		Stamp actual = stamp(expected.logical());
		if (!actual.real().equals(expected.real()) || !Objects.equals(actual.fileKey(), expected.fileKey())
				|| !actual.created().equals(expected.created()) || actual.directory() != expected.directory()
				|| actual.regularFile() != expected.regularFile()
				|| (content && (actual.size() != expected.size() || !actual.modified().equals(expected.modified())))) {
			throw new Failure(CHANGED_DURING_READ);
		}
	}

	private Stamp stamp(Path path) throws IOException {
		Path logical = path.toAbsolutePath().normalize();
		Path real = logical.toRealPath();
		BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		if (attributes.isSymbolicLink()) throw new Failure(UNSAFE_PATH);
		// Windows 的公开 BasicFileAttributes 不提供 fileKey；创建时间只是受控扫描的变化线索，不能充当稳定文件 ID。
		return new Stamp(logical, real, attributes.fileKey(), attributes.size(), attributes.creationTime(),
				attributes.lastModifiedTime(), attributes.isDirectory(), attributes.isRegularFile());
	}

	boolean definitelyAbsent(Stamp root, OutfitId id) {
		try {
			check(root, false);
			boolean missing = Files.notExists(root.real().resolve(id.value()), LinkOption.NOFOLLOW_LINKS);
			check(root, false);
			return missing;
		} catch (IOException | SecurityException exception) { return false; }
	}

	static OutfitDiagnostic.Code reason(Exception exception) {
		if (exception instanceof Failure failure) return failure.code;
		if (exception instanceof NoSuchFileException) return MISSING_FILE;
		return IO_ERROR;
	}
}
