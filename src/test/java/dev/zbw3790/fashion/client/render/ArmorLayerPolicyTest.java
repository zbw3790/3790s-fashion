package dev.zbw3790.fashion.client.render;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.client.render.armor.ArmorLayerPolicy;
import static org.junit.jupiter.api.Assertions.*;

class ArmorLayerPolicyTest {
    EquipmentClientInfo info(List<EquipmentClientInfo.Layer> layers){return new EquipmentClientInfo(Map.of(EquipmentClientInfo.LayerType.HUMANOID,layers,EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,layers));}
    @ParameterizedTest @ValueSource(strings={"iron","gold","diamond","netherite","chainmail","copper","turtle_scute"}) void standardMaterials(String id){var info=info(List.of(new EquipmentClientInfo.Layer(Identifier.withDefaultNamespace(id))));assertTrue(ArmorLayerPolicy.supported(info,EquipmentClientInfo.LayerType.HUMANOID));assertTrue(ArmorLayerPolicy.supported(info,EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS));}
    @Test void leatherRequiresExactDyeAndOverlayStructure(){var base=EquipmentClientInfo.Layer.leatherDyeable(Identifier.withDefaultNamespace("leather"),true);var overlay=new EquipmentClientInfo.Layer(Identifier.withDefaultNamespace("leather_overlay"));assertTrue(ArmorLayerPolicy.supported(info(List.of(base,overlay)),EquipmentClientInfo.LayerType.HUMANOID));assertFalse(ArmorLayerPolicy.supported(info(List.of(base)),EquipmentClientInfo.LayerType.HUMANOID));assertFalse(ArmorLayerPolicy.supported(info(List.of(overlay,base)),EquipmentClientInfo.LayerType.HUMANOID));}
    @Test void unknownMultilayerAndPlayerTextureFallBackWholeSlot(){var iron=new EquipmentClientInfo.Layer(Identifier.withDefaultNamespace("iron"));for(var layers:List.of(List.of(iron,iron),List.of(new EquipmentClientInfo.Layer(Identifier.withDefaultNamespace("unknown"))),List.of(new EquipmentClientInfo.Layer(Identifier.withDefaultNamespace("iron"),Optional.empty(),true))))assertFalse(ArmorLayerPolicy.supported(info(layers),EquipmentClientInfo.LayerType.HUMANOID));assertFalse(ArmorLayerPolicy.supported(info(List.of(iron)),EquipmentClientInfo.LayerType.WINGS));}
}
