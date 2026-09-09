package vanillafashion.client.outfit;

import java.io.IOException;
import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import vanillafashion.cape.CapeAssetHash;

/** 纹理所有权随连接；原版 DynamicTexture 保留 CPU 图像参与资源重载。 */
public final class ClientOutfitTextureManager {
    public interface OwnedTexture extends AutoCloseable { boolean ready(); default Optional<Boolean> fullyTransparent() { return Optional.empty(); } default Optional<Boolean> areaTransparent(int u,int v,int width,int height) { return Optional.empty(); } @Override void close(); }
    @FunctionalInterface public interface Backend { OwnedTexture create(Identifier id, byte[] bytes) throws IOException; }
    public enum Result { REGISTERED, ALREADY_REGISTERED, MISSING, FAILED, STALE }
    private Object connection;
    private final Map<String,OwnedTexture> textures=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    public static Identifier identifierFor(String hash) { return Identifier.fromNamespaceAndPath("vanilla_fashion","outfit_asset/"+CapeAssetHash.requireValid(hash,"装束纹理 hash ")); }
    public void begin(Object connection) { release(); this.connection=Objects.requireNonNull(connection); }
    public boolean matches(Object connection) { return connection!=null && this.connection==connection; }
    public boolean disconnect(Object connection) { if (!matches(connection)) return false; release(); this.connection=null; return true; }
    public void deactivate(Object connection) { if (matches(connection)) release(); }
    public void retain(Object connection, Set<String> required) {
        if (!matches(connection)) return;
        var iterator=textures.entrySet().iterator();
        while (iterator.hasNext()) { var entry=iterator.next(); if (!required.contains(entry.getKey())) { entry.getValue().close(); iterator.remove(); } }
        failed.retainAll(required);
    }
    public Optional<Identifier> find(Object connection, String hash) {
        if (!matches(connection)) return Optional.empty(); var texture=textures.get(hash);
        return texture!=null && texture.ready()?Optional.of(identifierFor(hash)):Optional.empty();
    }
    public Result register(Object connection, ClientOutfitAssetStore store, String hash, Backend backend, Logger logger) {
        if (!matches(connection)) return Result.STALE;
        if (textures.containsKey(hash)) return Result.ALREADY_REGISTERED;
        if (failed.contains(hash)) return Result.FAILED;
        var bytes=store.find(hash); if (bytes.isEmpty()) return Result.MISSING;
        OwnedTexture created=null;
        try {
            created=Objects.requireNonNull(backend.create(identifierFor(hash),bytes.orElseThrow()));
            if (!created.ready()) throw new IOException("纹理注册尚未就绪。");
            textures.put(hash,created); created=null; return Result.REGISTERED;
        } catch (IOException | RuntimeException exception) {
            failed.add(hash); logger.warn("装束纹理注册失败，本连接不自动重试：{}。",hash.substring(0,12)); return Result.FAILED;
        } finally { if (created!=null) created.close(); }
    }
    private void release() { textures.values().forEach(OwnedTexture::close); textures.clear(); failed.clear(); }
    public boolean failed(Object connection, String hash) { return matches(connection) && failed.contains(hash); }
    public Optional<Boolean> fullyTransparent(Object connection, String hash) {
        var texture=matches(connection)?textures.get(hash):null;
        return texture==null?Optional.empty():texture.fullyTransparent();
    }
    public Optional<Boolean> areaTransparent(Object connection,String hash,int u,int v,int width,int height) {
        if (u<0 || v<0 || width<1 || height<1 || u+width>64 || v+height>64) throw new IllegalArgumentException("装束样片区域越界。");
        var texture=matches(connection)?textures.get(hash):null;
        return texture==null?Optional.empty():texture.areaTransparent(u,v,width,height);
    }
    public int size() { return textures.size(); }
    public static Backend minecraft(TextureManager manager) {
        return (id,bytes) -> {
            NativeImage image=null; DynamicTexture texture=null;
            try {
                image=NativeImage.read(bytes);
                if (image.getWidth()!=64 || image.getHeight()!=64 || image.format()!=NativeImage.Format.RGBA) throw new IOException("装束图像必须为 64×64 RGBA。");
                BitSet alphaPixels=new BitSet(4096);
                for (int y=0;y<64;y++) for (int x=0;x<64;x++) if ((image.getPixel(x,y)>>>24)!=0) alphaPixels.set(y*64+x);
                boolean fullyTransparent=alphaPixels.isEmpty();
                texture=new DynamicTexture(() -> "Vanilla Fashion 装束运行时纹理",image); image=null;
                manager.register(id,texture); DynamicTexture owned=texture; texture=null;
                return new OwnedTexture() {
                    private boolean closed;
                    public Optional<Boolean> fullyTransparent() { return Optional.of(fullyTransparent); }
                    public Optional<Boolean> areaTransparent(int u,int v,int width,int height) {
                        for (int y=v;y<v+height;y++) for(int x=u;x<u+width;x++) if(alphaPixels.get(y*64+x)) return Optional.of(false);
                        return Optional.of(true);
                    }
                    public boolean ready() { return !closed && manager.getTexture(id)==owned && owned.getTexture()!=null; }
                    public void close() { if (!closed) { closed=true; if (manager.getTexture(id)==owned) manager.release(id); else owned.close(); } }
                };
            } finally { if (texture!=null) texture.close(); else if (image!=null) image.close(); }
        };
    }
}
