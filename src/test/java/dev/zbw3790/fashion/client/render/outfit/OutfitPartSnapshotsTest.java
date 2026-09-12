package dev.zbw3790.fashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import dev.zbw3790.fashion.outfit.*;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import dev.zbw3790.fashion.client.render.WardrobePreviewTestSupport;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderDecisions.*;

class OutfitPartSnapshotsTest {
    @BeforeAll static void bootstrap() { WardrobePreviewTestSupport.bootstrap(); }

    @ParameterizedTest @EnumSource(OutfitModel.class)
    void actualBakeRetainsAllSixOuterGeometriesAndFaces(OutfitModel type) {
        var models = models(); var source = model(models,type); var snapshots = new OutfitPartSnapshots(models,type);
        assertTrue(snapshots.matches(source));
        assertFalse(snapshots.matches(model(models,type == OutfitModel.WIDE ? OutfitModel.SLIM : OutfitModel.WIDE)));
        for (OutfitPart part : OutfitPart.values()) {
            var snapshot = snapshots.bodyPart(source,part,false,true);
            assertEquals(3,snapshot.getAllParts().size());
            var original = source.root().getChild(OutfitPartSnapshots.baseName(part)).getChild(OutfitPartSnapshots.outerName(part));
            var outer = snapshot.getChild(OutfitPartSnapshots.baseName(part)).getChild(OutfitPartSnapshots.outerName(part));
            assertEquals(OutfitPartSnapshots.geometrySignature(original),OutfitPartSnapshots.geometrySignature(outer));
            List<ModelPart.Cube> cubes = new ArrayList<>();
            outer.visit(new PoseStack(),(pose,path,index,cube)->cubes.add(cube));
            assertEquals(1,cubes.size()); assertEquals(6,cubes.getFirst().polygons.length);
            var cube = cubes.getFirst();
            float width = part == OutfitPart.HEAD || part == OutfitPart.BODY ? 8 : (part == OutfitPart.LEFT_ARM || part == OutfitPart.RIGHT_ARM) && type == OutfitModel.SLIM ? 3 : 4;
            assertEquals(width,cube.maxX-cube.minX);
            float expandedMin = Float.POSITIVE_INFINITY, expandedMax = Float.NEGATIVE_INFINITY;
            for (var polygon : cube.polygons) {
                assertEquals(4,polygon.vertices().length);
                for (var vertex : polygon.vertices()) {
                    assertTrue(vertex.u()>=0 && vertex.u()<=1 && vertex.v()>=0 && vertex.v()<=1);
                    expandedMin = Math.min(expandedMin,vertex.x()); expandedMax = Math.max(expandedMax,vertex.x());
                }
            }
            assertEquals(width + (part == OutfitPart.HEAD ? 1 : .5f),expandedMax-expandedMin,.00001f);
        }
    }

    @ParameterizedTest @EnumSource(OutfitModel.class)
    void queuedArmsRetainNineFloatsVisibilityAndSkipDrawWhenSourcesChange(OutfitModel type) {
        var models=models();var source=model(models,type);var snapshots=new OutfitPartSnapshots(models,type);
        source.leftArm.x=2;source.leftArm.y=3;source.leftArm.z=4;
        source.leftArm.xRot=.2f;source.leftArm.yRot=.3f;source.leftArm.zRot=.4f;
        source.leftArm.xScale=1.2f;source.leftArm.yScale=.8f;source.leftArm.zScale=1.1f;
        source.leftSleeve.xScale=.7f; source.rightSleeve.visible=false;
        PoseStack pose=new PoseStack();pose.translate(1,2,3);pose.scale(1.3f,.7f,2);
        var frozenPose=OutfitPartSnapshots.copyPose(pose);
        var left=snapshots.hand(source,OutfitPart.LEFT_ARM,true,true);
        var right=snapshots.hand(source,OutfitPart.RIGHT_ARM,true,true);
        var expectedLeft=consume(source.leftArm,pose);var expectedRight=consume(source.rightArm,pose);
        for (var part:source.allParts()) { part.x=99;part.yRot=3;part.xScale=4;part.visible=false;part.skipDraw=true; }
        pose.setIdentity();
        assertEquals(expectedRight,consume(right,frozenPose));assertEquals(expectedLeft,consume(left,frozenPose));
        assertEquals(expectedLeft,consume(left,frozenPose));assertEquals(expectedRight,consume(right,frozenPose));
        assertEquals(1.2f,left.xScale);assertEquals(.8f,left.yScale);assertEquals(1.1f,left.zScale);
        assertTrue(left.visible);assertFalse(left.skipDraw);assertFalse(right.getChild("right_sleeve").visible);
    }

    @Test void baseAndOuterDoNotShareMutableParentsOrAdditionalBaseCubes() {
        var models=models();var source=model(models,OutfitModel.WIDE);var snapshots=new OutfitPartSnapshots(models,OutfitModel.WIDE);
        var base=snapshots.hand(source,OutfitPart.RIGHT_ARM,true,false);var outer=snapshots.hand(source,OutfitPart.RIGHT_ARM,false,true);
        assertNotSame(base,outer);assertFalse(base.hasChild("right_sleeve"));
        List<String> paths=new ArrayList<>();outer.visit(new PoseStack(),(pose,path,index,cube)->paths.add(path));
        assertEquals(List.of("/right_sleeve"),paths);
        var expected=consume(outer,new PoseStack());base.visible=false;base.x=99;
        assertEquals(expected,consume(outer,new PoseStack()));
    }

    @Test void fullPoseCopyRetainsNormalNormalizationPolicy() {
        PoseStack pose=new PoseStack();pose.scale(2,3,4);pose.translate(7,8,9);
        var copy=OutfitPartSnapshots.copyPose(pose);
        var normal=pose.last().transformNormal(1,2,3,new org.joml.Vector3f());
        assertEquals(normal,copy.last().transformNormal(1,2,3,new org.joml.Vector3f()));
        pose.setIdentity();assertNotEquals(pose.last().pose(),copy.last().pose());
        assertEquals(normal,copy.last().transformNormal(1,2,3,new org.joml.Vector3f()));
    }
}
