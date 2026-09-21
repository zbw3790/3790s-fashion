package dev.zbw3790.fashion.armor;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** 独立像素构造器；期望 hash 由调用者对文件字节直接计算。 */
public final class ArmorTextureFixture {
    private ArmorTextureFixture() { }
    public static byte[] png(int color) throws IOException {return png(64,32,color);}
    public static byte[] png(int width,int height,int color) throws IOException {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) image.setRGB(x,y,color ^ ((x*7+y*11)&255));
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);image.flush();return out.toByteArray();
    }
    public static String metadata(String id,String slots,String textures) {
        return "{\"format_version\":1,\"id\":\""+id+"\",\"name\":\"测试样式\",\"slots\":"+slots+",\"textures\":"+textures+"}";
    }
    public static Path style(Path root,String id,int color) throws IOException {
        Path directory=root.resolve(id);Files.createDirectories(directory);
        Files.writeString(directory.resolve("armor.json"),metadata(id,"[\"head\",\"chest\",\"legs\",\"feet\"]","{\"outer\":\"outer.png\",\"inner\":\"inner.png\"}"));
        Files.write(directory.resolve("outer.png"),png(color));Files.write(directory.resolve("inner.png"),png(color ^ 0x00336699));return directory;
    }
}
