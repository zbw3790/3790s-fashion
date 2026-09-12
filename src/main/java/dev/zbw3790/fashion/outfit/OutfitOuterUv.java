package dev.zbw3790.fashion.outfit;

import java.awt.image.BufferedImage;
import java.util.*;

/** 原版外层立方体的六个 UV 面；不包含 Base，也不依赖客户端模型类。 */
public final class OutfitOuterUv {
    private OutfitOuterUv() { }
    public record Face(int u, int v, int width, int height) { }
    public static List<Face> faces(OutfitPart part, OutfitModel model) {
        Objects.requireNonNull(model);
        int arm = model == OutfitModel.SLIM ? 3 : 4;
        return switch (part) {
            case HEAD -> cube(32, 0, 8, 8, 8);
            case BODY -> cube(16, 32, 8, 12, 4);
            case RIGHT_ARM -> cube(40, 32, arm, 12, 4);
            case LEFT_ARM -> cube(48, 48, arm, 12, 4);
            case RIGHT_LEG -> cube(0, 32, 4, 12, 4);
            case LEFT_LEG -> cube(0, 48, 4, 12, 4);
        };
    }
    private static List<Face> cube(int u, int v, int w, int h, int d) {
        return List.of(new Face(u+d,v,w,d), new Face(u+d+w,v,w,d),
                new Face(u,v+d,d,h), new Face(u+d,v+d,w,h),
                new Face(u+d+w,v+d,d,h), new Face(u+d+w+d,v+d,w,h));
    }
    public static Set<OutfitPart> infer(BufferedImage image, OutfitModel model) {
        if (image.getWidth()!=64 || image.getHeight()!=64) throw new IllegalArgumentException("推导必须使用已验证的 64×64 装束图像。");
        var parts=EnumSet.noneOf(OutfitPart.class);
        for (var part:OutfitPart.CANONICAL_ORDER) {
            outer: for (var face:faces(part,model))
                for (int y=face.v();y<face.v()+face.height();y++)
                    for (int x=face.u();x<face.u()+face.width();x++)
                        if ((image.getRGB(x,y)>>>24)>0) { parts.add(part); break outer; }
        }
        return Set.copyOf(parts);
    }
}
