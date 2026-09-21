package dev.zbw3790.fashion.armor;

import dev.zbw3790.fashion.outfit.ValidatedAssetFiles;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** 单样式隔离；根失败、总量超限或扫描中变化拒绝整次候选。 */
public final class ArmorRegistryLoader {
    private final ValidatedAssetFiles files;
    public ArmorRegistryLoader() { this(new ValidatedAssetFiles()); }
    public ArmorRegistryLoader(ValidatedAssetFiles files) { this.files=Objects.requireNonNull(files); }
    public ArmorRegistryLoadResult load(Path root) { return load(root,true); }
    public ArmorRegistryLoadResult loadExisting(Path root) { return load(root,false); }
    private record Read(Path path,ValidatedAssetFiles.Directory directory,byte[] bytes,int maximum) { }
    private ArmorRegistryLoadResult load(Path path,boolean create) {
        var entries=new ArrayList<ArmorRegistrySnapshot.Entry>();var assets=new HashMap<String,ArmorAsset>();var diagnostics=new ArrayList<String>();
        var reads=new ArrayList<Read>(); long total=0;
        try {
            var root=files.root(path,create);
            for(var child:files.children(root,4096)) {
                String directoryName=child.getFileName().toString();
                try {
                    var id=new ArmorStyleId(directoryName);var directory=files.directory(child,root);
                    var children=files.children(directory,3);
                    byte[] raw=files.read(child.resolve("armor.json"),directory,root,ArmorMetadataParser.MAX_BYTES);
                    var metadata=ArmorMetadataParser.parse(raw);
                    if(!id.equals(metadata.id())) throw new IllegalArgumentException("样式 ID 与目录不一致。");
                    var styleReads=new ArrayList<Read>();styleReads.add(new Read(child.resolve("armor.json"),directory,raw,ArmorMetadataParser.MAX_BYTES));
                    var styleAssets=new HashMap<String,ArmorAsset>();var hashes=new EnumMap<ArmorGeometry,String>(ArmorGeometry.class);
                    var declared=new HashSet<String>(metadata.textures().values());declared.add("armor.json");
                    if(!children.stream().map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(declared)) throw new IllegalArgumentException("样式含未声明文件或缺少文件。");
                    for(var texture:metadata.textures().entrySet()) {
                        Path file=child.resolve(texture.getValue());byte[] bytes=files.read(file,directory,root,ArmorAsset.MAX_BYTES);
                        var asset=ArmorAsset.fromBytes(bytes);hashes.put(texture.getKey(),asset.hash());styleAssets.put(asset.hash(),asset);
                        styleReads.add(new Read(file,directory,bytes,ArmorAsset.MAX_BYTES));
                    }
                    files.verify(directory);
                    for(var read:styleReads) total+=read.bytes().length;
                    if(total>ArmorRegistrySnapshot.MAX_TOTAL_BYTES || entries.size()>=ArmorRegistrySnapshot.MAX_STYLES) return ArmorRegistryLoadResult.unavailable("盔甲 Registry 总量超过预算。");
                    reads.addAll(styleReads);assets.putAll(styleAssets);entries.add(new ArmorRegistrySnapshot.Entry(id,metadata.name(),metadata.slots(),hashes));
                } catch(IOException | IllegalArgumentException exception) { diagnostics.add("盔甲样式已隔离："+directoryName+"；"+exception.getMessage()); }
            }
            // 再读一次所有发布内容，防止早读 metadata 与后读 PNG 形成混合版本。
            for(var read:reads) if(!Arrays.equals(read.bytes(),files.read(read.path(),read.directory(),root,read.maximum()))) return ArmorRegistryLoadResult.unavailable("盔甲文件在扫描期间变化。");
            files.verify(root);
            return new ArmorRegistryLoadResult(new ArmorRegistrySnapshot(true,entries),assets,diagnostics);
        } catch(IOException | SecurityException | IllegalArgumentException exception) { return ArmorRegistryLoadResult.unavailable("盔甲根扫描失败："+exception.getMessage()); }
    }
}
