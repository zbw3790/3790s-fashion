package dev.zbw3790.fashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import dev.zbw3790.fashion.client.render.WardrobePreviewTestSupport;
import dev.zbw3790.fashion.outfit.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance.Scene;

/** 复现原版提取之后再修改 GUI 朝向，验证最终提交及延迟消费，不只比较附件中的角度。 */
class OutfitInventoryPoseTest {
    @BeforeAll static void bootstrap() { WardrobePreviewTestSupport.bootstrap(); }
    private OutfitRendering.Binding binding(OutfitModel type) {
        var models=models(); return new OutfitRendering.Binding(null,model(models,type),new OutfitPartSnapshots(models,type));
    }
    private AvatarRenderState prepared(OutfitRendering.Binding binding, Scene scene) {
        var state=state(binding.snapshots().modelType);state.ageInTicks=17;
        var frame=OutfitRendering.prepareFrame(state,all(state,scene,TEST_OUTFIT,binding.snapshots().modelType,true),binding);
        OutfitRendering.publishFrame(state,frame);return state;
    }
    static Stream<Arguments> orientations() {
        return Stream.of(OutfitModel.values()).flatMap(type -> Stream.of(
                Arguments.of(type,-25f,0f),Arguments.of(type,0f,0f),Arguments.of(type,25f,0f),
                Arguments.of(type,5f,-20f),Arguments.of(type,5f,20f),Arguments.of(type,19f,-13f)));
    }
    private static float[] pose(ModelPart p) {
        return new float[]{p.x,p.y,p.z,p.xRot,p.yRot,p.zRot,p.xScale,p.yScale,p.zScale};
    }
    @ParameterizedTest @MethodSource("orientations")
    void guiPostExtractionOrientationUsesActuallyPosedVanillaModel(OutfitModel type,float yaw,float pitch) {
        var binding=binding(type);var state=prepared(binding,Scene.VANILLA_INVENTORY);
        // 对应 InventoryScreen 在 extractRenderState 返回后的最终赋值。
        state.bodyRot=180+yaw;state.yRot=yaw;state.xRot=pitch;
        binding.source().setupAnim(state);var transform=new PoseStack();transform.translate(3,4,5);transform.scale(1.2f,1.2f,1.2f);
        var queue=new Queue();new OutfitRendering.Layer(binding).submit(transform,queue.collector(),100,state,yaw,pitch);
        assertEquals(6,queue.calls.size());
        for(int i=0;i<6;i++) {
            var part=OutfitPart.values()[i];var name=OutfitPartSnapshots.baseName(part);var outer=OutfitPartSnapshots.outerName(part);
            var source=binding.source().root().getChild(name);var call=queue.calls.get(i);var copy=call.tree().getChild(name);
            assertArrayEquals(pose(source),pose(copy),0.000001f);
            assertArrayEquals(pose(source.getChild(outer)),pose(copy.getChild(outer)),0.000001f);
            assertArrayEquals(pose(binding.source().root()),pose(call.tree()),0.000001f);
            assertEquals(transform.last().pose(),call.pose().last().pose());
            assertTrue(copy.getChild(outer).visible);assertFalse(source.getChild(outer).visible);
            assertEquals(OutfitPartSnapshots.geometrySignature(source.getChild(outer)),OutfitPartSnapshots.geometrySignature(copy.getChild(outer)));
            assertFalse(consume(call.tree(),call.pose()).isEmpty());
        }
        assertEquals(yaw,state.yRot);assertEquals(pitch,state.xRot);
    }
    @ParameterizedTest @EnumSource(OutfitModel.class)
    void twoDrawsAtSameAgeKeepDistinctImmutableVerticesAfterOtherScenesAndReload(OutfitModel type) {
        var binding=binding(type);var layer=new OutfitRendering.Layer(binding);var state=prepared(binding,Scene.VANILLA_INVENTORY);
        var queue=new Queue();var transform=new PoseStack();transform.translate(3,4,5);
        state.yRot=-25;state.xRot=12;binding.source().setupAnim(state);
        layer.submit(transform,queue.collector(),100,state,0,0);
        var first=queue.calls.stream().map(c->consume(c.tree(),c.pose())).toList();
        state.yRot=27;state.xRot=-18;binding.source().setupAnim(state);transform.translate(7,0,0);
        layer.submit(transform,queue.collector(),100,state,0,0);
        assertNotEquals(first.getFirst(),consume(queue.calls.get(6).tree(),queue.calls.get(6).pose()));
        var second=queue.calls.subList(6,12).stream().map(c->consume(c.tree(),c.pose())).toList();
        // 世界、衣柜和新 bake 模拟后续调用，不能改写已交给队列的旧节点。
        for(var scene:List.of(Scene.WORLD,Scene.WARDROBE_PREVIEW)) {
            var other=prepared(binding,scene);other.yRot=77;binding.source().setupAnim(other);
            layer.submit(new PoseStack(),new Queue().collector(),100,other,0,0);
        }
        prepared(binding(type),Scene.VANILLA_INVENTORY);
        for(var part:binding.source().allParts()){part.x=99;part.visible=false;part.skipDraw=true;}
        transform.setIdentity();
        for(int i=0;i<6;i++) {
            assertNotSame(queue.calls.get(i).tree(),queue.calls.get(i+6).tree());
            assertEquals(first.get(i),consume(queue.calls.get(i).tree(),queue.calls.get(i).pose()));
            assertEquals(second.get(i),consume(queue.calls.get(i+6).tree(),queue.calls.get(i+6).pose()));
        }
    }
    @Test void guiRetainsOriginalVisibilityAndPartialSelectionGates() {
        var binding=binding(OutfitModel.WIDE);var state=state(OutfitModel.WIDE);state.showLeftSleeve=false;
        var selections=OutfitSelections.original().with(OutfitPart.HEAD,TEST_OUTFIT).with(OutfitPart.LEFT_ARM,TEST_OUTFIT);
        var input=appearance(state,Scene.VANILLA_INVENTORY,selections,java.util.Map.of(TEST_ID,asset(OutfitModel.WIDE,true)));
        OutfitRendering.publishFrame(state,OutfitRendering.prepareFrame(state,input,binding));state.yRot=28;
        binding.source().setupAnim(state);var queue=new Queue();new OutfitRendering.Layer(binding).submit(new PoseStack(),queue.collector(),100,state,0,0);
        assertEquals(1,queue.calls.size());assertTrue(queue.calls.getFirst().tree().hasChild("head"));
        assertFalse(state.showLeftSleeve);assertTrue(state.showJacket);assertFalse(state.showHat);
    }
    @ParameterizedTest @EnumSource(value=Scene.class,names={"WORLD","WARDROBE_PREVIEW"})
    void otherScenesKeepExistingPreparedSnapshotContract(Scene scene) {
        var binding=binding(OutfitModel.WIDE);var state=prepared(binding,scene);
        var queue=new Queue();var layer=new OutfitRendering.Layer(binding);layer.submit(new PoseStack(),queue.collector(),100,state,0,0);
        var before=queue.calls.stream().map(c->consume(c.tree(),c.pose())).toList();
        binding.source().head.yRot=2;state.yRot=77;
        var after=new Queue();layer.submit(new PoseStack(),after.collector(),100,state,0,0);
        assertEquals(before,after.calls.stream().map(c->consume(c.tree(),c.pose())).toList());
    }
}
