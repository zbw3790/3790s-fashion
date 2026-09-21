package dev.zbw3790.fashion.client.screen;

import java.util.List;
import dev.zbw3790.fashion.armor.ArmorSlot;

/** 26.2 HumanoidModel 的正面 UV 样片；只读现有 64×32 盔甲纹理，不补皮肤、染色或装备。 */
final class ArmorThumbnail {
    record Face(int x,int y,int width,int height,int u,int v,int scale,boolean mirror) { }
    private ArmorThumbnail() { }
    static List<Face> plan(ArmorSlot slot) {
        return switch(slot) {
            case HEAD -> List.of(new Face(2,2,8,8,8,8,2,false));
            case CHEST -> List.of(new Face(6,4,8,12,20,20,1,false),
                    new Face(2,4,4,12,44,20,1,false),new Face(14,4,4,12,44,20,1,true));
            // 内层腰部和双腿属于同一资源；保留透明区，避免将下半身样片误解为皮肤合成。
            case LEGS -> List.of(new Face(6,2,8,4,20,28,1,false),
                    new Face(6,6,4,12,4,20,1,false),new Face(10,6,4,12,4,20,1,true));
            case FEET -> List.of(new Face(5,4,4,12,4,20,1,false),new Face(11,4,4,12,4,20,1,true));
        };
    }
}
