package dev.zbw3790.fashion.identity;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Objects;

/** 受控启动期间的一向身份迁移：不覆盖目标，不删除旧用户数据，不接受链接。 */
public final class LegacyIdentityMigration {
    public static final String CONFIG_ROOT = "3790s-fashion";
    public static final String LEGACY_CONFIG_ROOT = "vanilla-fashion";
    public static final String LEGACY_NAMESPACE = "vanilla_fashion";

    private LegacyIdentityMigration() {}

    /** 新目录一旦存在就具有优先权；复制不完整时绝不发布半份资产目录。 */
    public static Path configRoot(Path configDirectory) throws IOException {
        Path current = configDirectory.resolve(CONFIG_ROOT);
        Path legacy = configDirectory.resolve(LEGACY_CONFIG_ROOT);
        requireSafePath(current);
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw new IOException("新配置根不是目录。");
            return current;
        }
        requireSafePath(legacy);
        if (Files.notExists(legacy, LinkOption.NOFOLLOW_LINKS)) return current;
        if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) throw new IOException("旧配置根不是目录。");
        Path temporary = Files.createTempDirectory(configDirectory, ".fashion3790-migration-");
        try {
            Files.walkFileTree(legacy, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    requireSafePath(directory);
                    if (!directory.equals(legacy)) Files.createDirectory(temporary.resolve(legacy.relativize(directory)));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    requireSafePath(file);
                    if (!attributes.isRegularFile()) throw new IOException("旧配置含有非普通文件，迁移未发布。");
                    Files.copy(file, temporary.resolve(legacy.relativize(file)));
                    var after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!after.isRegularFile() || !Objects.equals(attributes.fileKey(), after.fileKey())
                            || attributes.size() != after.size() || !attributes.lastModifiedTime().equals(after.lastModifiedTime()))
                        throw new IOException("迁移期间旧文件发生变化，请停止修改资源后重试。");
                    return FileVisitResult.CONTINUE;
                }
            });
            requireSafePath(current);
            // 不使用 REPLACE_EXISTING；同文件系统目录移动只发布本轮已完成的副本。
            Files.move(temporary, current);
            return current;
        } finally {
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                try (var files = Files.walk(temporary)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                }
            }
        }
    }

    /** 调用方必须先完成内容解码；发布前拒绝链接和已有目标，保留旧源文件。 */
    public static void publishValidatedFile(Path target, byte[] bytes) throws IOException {
        requireSafePath(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".fashion3790-migration-", ".tmp");
        try {
            Files.write(temporary, bytes);
            requireSafePath(target);
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 检查已有各级父目录；这不是抵抗管理员并发替换目录的原子沙箱。 */
    public static void requireSafePath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            try {
                var attributes = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("迁移路径包含链接或特殊文件。");
            } catch (NoSuchFileException missing) {
                if (!Files.notExists(cursor, LinkOption.NOFOLLOW_LINKS)) throw new IOException("迁移路径存在性不明。", missing);
            }
        }
    }
}
