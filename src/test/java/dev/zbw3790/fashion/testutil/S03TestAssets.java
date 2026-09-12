package dev.zbw3790.fashion.testutil;

import java.io.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import dev.zbw3790.fashion.outfit.*;

/** 每项测试自己创建的有限资产，不读取游戏目录或开发者 fixture。 */
public final class S03TestAssets {
    private S03TestAssets() { }
    public static byte[] png(int width,int height,int color) {
        try {var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB); image.setRGB(0,0,color);
            var output=new ByteArrayOutputStream(); if(!ImageIO.write(image,"PNG",output))throw new IOException("没有 PNG 编码器。"); image.flush();return output.toByteArray();
        }catch(IOException e){throw new IllegalStateException("测试 PNG 生成失败。",e);}
    }
    public static byte[] png(){return png(64,64,0xff3790ff);}
    public static Path outfit(Path root,String id,Set<OutfitPart> parts,boolean wide,boolean slim) throws IOException {
        Path directory=Files.createDirectories(root.resolve(id));
        String partList=OutfitPart.CANONICAL_ORDER.stream().filter(parts::contains).map(p->"\""+p.serializedName()+"\"").collect(java.util.stream.Collectors.joining(","));
        Files.writeString(directory.resolve("outfit.json"),"{\"schema_version\":1,\"parts\":["+partList+"],\"models\":[\"wide\",\"slim\"]}");
        if(wide)Files.write(directory.resolve("wide.png"),png());
        if(slim)Files.write(directory.resolve("slim.png"),png());return directory;
    }
}
