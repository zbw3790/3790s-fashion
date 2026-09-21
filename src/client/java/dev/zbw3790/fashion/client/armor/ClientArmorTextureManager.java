package dev.zbw3790.fashion.client.armor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** 纹理注册在消息队列；退役等待两个完整帧结束，避免延迟提交仍引用旧纹理。 */
public final class ClientArmorTextureManager {
    public interface OwnedTexture extends AutoCloseable { boolean ready(); @Override void close(); }
    @FunctionalInterface public interface Backend { OwnedTexture create(Identifier id,byte[] bytes) throws IOException; }
    public enum Result { REGISTERED, ALREADY_REGISTERED, MISSING, FAILED, STALE, LIMIT }
    private record Live(Identifier id,OwnedTexture texture) { }
    private record Retired(long frame,Live live) { }
    private static final AtomicLong SERIAL=new AtomicLong();
    private Object connection;
    private long frame;
    private final Map<String,Live> textures=new HashMap<>();
    private final List<Retired> retired=new ArrayList<>();
    private final Set<String> failed=new HashSet<>();
    public void begin(Object connection) { retireAll();this.connection=Objects.requireNonNull(connection); }
    public boolean matches(Object connection) { return connection!=null && connection==this.connection; }
    public boolean disconnect(Object connection) {if(!matches(connection)) return false;retireAll();this.connection=null;return true;}
    public void deactivate(Object connection) {if(matches(connection)) retireAll();}
    public void retain(Object connection,Set<String> required) {
        if(!matches(connection)) return;
        var iterator=textures.entrySet().iterator();
        while(iterator.hasNext()) {var entry=iterator.next();if(!required.contains(entry.getKey())) {retired.add(new Retired(frame+2,entry.getValue()));iterator.remove();}}
    }
    public Optional<Identifier> find(Object connection,String hash) {
        var live=matches(connection)?textures.get(hash):null;
        return live!=null && live.texture().ready()?Optional.of(live.id()):Optional.empty();
    }
    public Result register(Object connection,ClientArmorAssetStore store,String hash,Backend backend,Logger logger) {
        if(!matches(connection)) return Result.STALE;
        if(textures.containsKey(hash)) return Result.ALREADY_REGISTERED;
        if(failed.contains(hash)) return Result.FAILED;
        var bytes=store.find(hash);if(bytes.isEmpty()) return Result.MISSING;
        if(textures.size()+retired.size()>=1024) return Result.LIMIT;
        var id=Identifier.fromNamespaceAndPath("fashion_3790","armor_asset/"+SERIAL.incrementAndGet()+"/"+hash);
        OwnedTexture created=null;
        try {
            created=Objects.requireNonNull(backend.create(id,bytes.orElseThrow()));
            if(!created.ready()) throw new IOException("盔甲纹理注册未就绪。");
            textures.put(hash,new Live(id,created));created=null;return Result.REGISTERED;
        } catch(IOException | RuntimeException exception) {
            if(failed.size()<512 && failed.add(hash)) logger.warn("盔甲纹理注册失败，本连接不重复尝试：{}。",hash);
            return Result.FAILED;
        } finally {if(created!=null) created.close();}
    }
    private void retireAll() {textures.values().forEach(live->retired.add(new Retired(frame+2,live)));textures.clear();failed.clear();}
    /** 只能在 Minecraft.runTick 返回、世界与 GUI 提交完成后调用。 */
    public void frameCompleted() {
        frame++;var iterator=retired.iterator();
        while(iterator.hasNext()) {var item=iterator.next();if(item.frame()<=frame) {item.live().texture().close();iterator.remove();}}
    }
    public int size() {return textures.size();}
    /** 仅查询本连接已记录的失败，不重试或改变纹理所有权。 */
    public boolean failed(Object connection,String hash) {return matches(connection) && failed.contains(hash);}
    public int retiredCount() {return retired.size();}
    public static Backend minecraft(TextureManager manager) {
        return (id,bytes)->{
            NativeImage image=null;DynamicTexture texture=null;
            try {
                image=NativeImage.read(bytes);
                if(image.getWidth()!=64 || image.getHeight()!=32 || image.format()!=NativeImage.Format.RGBA) throw new IOException("盔甲图像必须为 64×32 RGBA。");
                texture=new DynamicTexture(()->"3790's Fashion 盔甲运行时纹理",image);image=null;
                manager.register(id,texture);DynamicTexture owned=texture;texture=null;
                return new OwnedTexture() {
                    private boolean closed;
                    public boolean ready() {return !closed && manager.getTexture(id)==owned && owned.getTexture()!=null;}
                    public void close() {if(!closed) {closed=true;if(manager.getTexture(id)==owned) manager.release(id);else owned.close();}}
                };
            } finally {if(texture!=null) texture.close();else if(image!=null) image.close();}
        };
    }
}
