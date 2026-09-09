package vanillafashion.client.screen;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.player.PlayerModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vanillafashion.client.render.WardrobePreviewTestSupport;
import vanillafashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;

class OutfitOuterUvTruthTest {
    @BeforeAll static void boot() { WardrobePreviewTestSupport.bootstrap(); }
    @ParameterizedTest @EnumSource(OutfitModel.class)
    void allSixFacesExactlyMatchActualBakedVanillaOuterCubes(OutfitModel model) {
        var baked=new PlayerModel(EntityModelSet.vanilla().bakeLayer(model==OutfitModel.SLIM?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),model==OutfitModel.SLIM);
        for (var part:OutfitPart.values()) {
            ModelPart outer=switch(part) {
                case HEAD->baked.hat;case BODY->baked.jacket;case LEFT_ARM->baked.leftSleeve;case RIGHT_ARM->baked.rightSleeve;
                case LEFT_LEG->baked.leftPants;case RIGHT_LEG->baked.rightPants;
            };
            var actual=new ArrayList<OutfitOuterUv.Face>();
            outer.visit(new PoseStack(),(pose,path,index,cube)->{
                for (var polygon:cube.polygons) {
                    float minU=1,minV=1,maxU=0,maxV=0;
                    for(var vertex:polygon.vertices()) {
                        minU=Math.min(minU,vertex.u());minV=Math.min(minV,vertex.v());maxU=Math.max(maxU,vertex.u());maxV=Math.max(maxV,vertex.v());
                    }
                    actual.add(new OutfitOuterUv.Face(Math.round(minU*64),Math.round(minV*64),Math.round((maxU-minU)*64),Math.round((maxV-minV)*64)));
                }
            });
            assertEquals(6,actual.size());assertEquals(new HashSet<>(actual),new HashSet<>(OutfitOuterUv.faces(part,model)),part.toString());
        }
    }
}
