package dev.zbw3790.fashion.client.render.armor;

import java.util.*;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;

/** 整槽先验结构检查；不支持时不进入半套 CUSTOM 提交。 */
public final class ArmorLayerPolicy {
    private static final Set<String> STANDARD=Set.of("iron","gold","diamond","netherite","chainmail","copper","turtle_scute");
    private ArmorLayerPolicy() { }
    public static boolean supported(EquipmentClientInfo info,EquipmentClientInfo.LayerType type) {
        if(type!=EquipmentClientInfo.LayerType.HUMANOID && type!=EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS) return false;
        var layers=info.getLayers(type);
        if(layers.stream().anyMatch(EquipmentClientInfo.Layer::usePlayerTexture)) return false;
        if(layers.size()==1) {
            var layer=layers.getFirst();
            return layer.dyeable().isEmpty() && layer.textureId().getNamespace().equals("minecraft") && STANDARD.contains(layer.textureId().getPath());
        }
        return layers.size()==2 && layers.getFirst().textureId().equals(Identifier.withDefaultNamespace("leather"))
                && layers.getFirst().dyeable().isPresent() && layers.get(1).textureId().equals(Identifier.withDefaultNamespace("leather_overlay"))
                && layers.get(1).dyeable().isEmpty();
    }
}
