package dev.zbw3790.fashion.outfit;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

/** 复用既有读取前后身份检查；Armor 额外禁止所有路径重定向。 */
public class ValidatedAssetFiles {
    public static final class Directory {
        private final OutfitFileAccess.Stamp stamp;
        private Directory(OutfitFileAccess.Stamp stamp) { this.stamp=stamp; }
    }
    private final OutfitFileAccess access=new OutfitFileAccess();
    public Directory root(Path path, boolean create) throws IOException {
        if (create) Files.createDirectories(path);
        return new Directory(access.existingRoot(path));
    }
    public Directory directory(Path path, Directory root) throws IOException {
        rejectRedirect(path);
        return new Directory(access.directory(path,root.stamp));
    }
    public List<Path> children(Directory directory,int maximum) throws IOException { return access.children(directory.stamp,maximum); }
    public byte[] read(Path path,Directory directory,Directory root,int maximum) throws IOException {
        rejectRedirect(path);
        return access.read(path,directory.stamp,root.stamp,maximum);
    }
    public void verify(Directory directory) throws IOException { access.check(directory.stamp,true); }
    private static void rejectRedirect(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().equals(path.toRealPath())) throw new IOException("资源路径不能为符号链接或重定向。");
    }
}
