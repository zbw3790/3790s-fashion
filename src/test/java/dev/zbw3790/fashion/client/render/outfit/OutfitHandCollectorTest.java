package dev.zbw3790.fashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import dev.zbw3790.fashion.outfit.*;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.client.render.WardrobePreviewTestSupport;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderDecisions.*;

class OutfitHandCollectorTest {
    private PlayerModel source;
    private OutfitPartSnapshots snapshots;
    private Queue queue;
    private RenderType skin,outer;
    private PoseStack pose;
    @BeforeAll static void bootstrap() { WardrobePreviewTestSupport.bootstrap(); }
    @BeforeEach void setup() {
        var models=models();source=model(models,OutfitModel.WIDE);snapshots=new OutfitPartSnapshots(models,OutfitModel.WIDE);
        queue=new Queue();pose=new PoseStack();pose.translate(1,2,3);
        skin=RenderTypes.entityTranslucent(Identifier.withDefaultNamespace("test_skin"));
        outer=RenderTypes.entityTranslucent(Identifier.withDefaultNamespace("test_outfit"));
    }
    private OutfitHandCollector adapter(OutfitPartSelection choice) {
        return new OutfitHandCollector(queue.collector(),source,snapshots,OutfitPart.RIGHT_ARM,
                decision(choice,true,true,OutfitModel.WIDE,OutfitModel.WIDE),skin,outer);
    }
    private void target(OrderedSubmitNodeCollector collector) {
        collector.submitModelPart(source.rightArm,pose,skin,73,91,null,0x70abcdef,null,0x123456);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport#selections")
    void operationOnceAndExpectedBaseOuterCounts(OutfitPartSelection choice) {
        AtomicInteger calls=new AtomicInteger();
        adapter(choice).run(collector->{calls.incrementAndGet();target(collector);assertEquals(0,queue.calls.size());});
        assertEquals(1,calls.get());assertEquals(choice==TEST_OUTFIT?2:1,queue.calls.size());
        var first=queue.calls.getFirst();assertSame(skin,first.args()[3]);assertEquals(73,first.args()[4]);
        assertEquals(91,first.args()[5]);assertEquals(0x70abcdef,first.args()[6]);assertEquals(0x123456,first.args()[8]);
        assertNull(first.args()[7]);assertNull(first.args()[9]);
        assertEquals(choice==ORIGINAL,first.tree().hasChild("right_sleeve"));
        if(choice==TEST_OUTFIT){assertSame(outer,queue.calls.getLast().args()[3]);assertNotSame(first.tree(),queue.calls.getLast().tree());}
    }
    @Test void zeroTargetDoesNotAddAnything() { adapter(TEST_OUTFIT).run(collector->{});assertTrue(queue.calls.isEmpty()); }
    @Test void secondTargetFallsBackAndIsNotReplayed() {
        adapter(TEST_OUTFIT).run(collector->{target(collector);target(collector);});
        assertEquals(List.of("submitModel","submitModelPart"),queue.calls.stream().map(Call::method).toList());
        assertTrue(queue.calls.getFirst().tree().hasChild("right_sleeve"));
        assertSame(source.rightArm,queue.calls.getLast().args()[0]);
    }
    @Test void nonTargetBeforeTargetPreservesOrderAndOriginalReferences() {
        adapter(TEST_OUTFIT).run(collector->{collector.submitModelPart(source.head,pose,skin,1,2,null);target(collector);});
        assertEquals(2,queue.calls.size());assertSame(source.head,queue.calls.getFirst().args()[0]);
        assertSame(source.rightArm,queue.calls.getLast().args()[0]);
    }
    @Test void nonTargetAfterTargetFlushesFrozenOriginalFirst() {
        adapter(TEST_OUTFIT).run(collector->{target(collector);collector.submitModelPart(source.head,pose,skin,1,2,null);});
        assertEquals(2,queue.calls.size());assertNotSame(source.rightArm,queue.calls.getFirst().args()[0]);
        assertTrue(queue.calls.getFirst().tree().hasChild("right_sleeve"));assertSame(source.head,queue.calls.getLast().args()[0]);
    }
    @Test void orderedDelegateAndNonDefaultCrumblingRemainIntact() {
        var crumbling=new CrumblingOverlay(4,new PoseStack().last());
        adapter(TEST_OUTFIT).run(collector->collector.order(7).submitModelPart(source.rightArm,pose,skin,111,222,null,0x34567890,crumbling,42));
        assertEquals(2,queue.calls.size());
        for(Call call:queue.calls){assertEquals(7,call.order());assertEquals(111,call.args()[4]);assertEquals(222,call.args()[5]);
            assertEquals(0x34567890,call.args()[6]);assertSame(crumbling,call.args()[9]);assertEquals(42,call.args()[8]);}
    }
    @Test void customPhaseAndNodeAreExplicitlyForwardedAfterOriginalFallback() {
        var phase=new net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase<net.minecraft.client.renderer.feature.submit.SubmitNode>(collection->null);
        net.minecraft.client.renderer.feature.submit.SubmitNode node=()->null;
        adapter(TEST_OUTFIT).run(collector->{target(collector);((FabricOrderedSubmitNodeCollector)collector.order(9)).submitCustom(phase,node);});
        assertEquals(List.of("submitModel","submitCustom"),queue.calls.stream().map(Call::method).toList());
        assertEquals(9,queue.calls.getLast().order());assertArrayEquals(new Object[]{phase,node},queue.calls.getLast().args());
    }
    @ParameterizedTest @ValueSource(ints={6,8,9,10})
    void allActualPartOverloadsAndFinalModelRouteAreHandled(int arity) {
        adapter(NONE).run(c->{switch(arity){
            case 6->c.submitModelPart(source.rightArm,pose,skin,1,2,null);
            case 8->c.submitModelPart(source.rightArm,pose,skin,1,2,null,3,null);
            case 9->c.submitModelPart(source.rightArm,pose,skin,1,2,null,3,null,4);
            case 10->c.submitModel(new Model.Simple(source.rightArm,id->skin),Unit.INSTANCE,pose,skin,1,2,3,null,4,null);
        }});
        assertEquals(1,queue.calls.size());assertFalse(queue.calls.getFirst().tree().hasChild("right_sleeve"));
        assertEquals(arity==6?-1:3,queue.calls.getFirst().args()[6]);
        assertEquals(arity>=9?4:0,queue.calls.getFirst().args()[8]);
    }
    @Test void reflectionEnumeratesEveryCurrentPartOverloadAndFinalModelContract() {
        var expected=Arrays.stream(OrderedSubmitNodeCollector.class.getMethods()).filter(m->m.getName().equals("submitModelPart"))
                .map(m->List.of(m.getParameterTypes())).collect(java.util.stream.Collectors.toSet());
        assertEquals(3,expected.size());
        adapter(ORIGINAL).run(c->{
            var actual=Arrays.stream(c.getClass().getDeclaredMethods()).filter(m->m.getName().equals("submitModelPart"))
                    .map(m->List.of(m.getParameterTypes())).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected,actual);
            assertTrue(Arrays.stream(c.getClass().getDeclaredMethods()).anyMatch(m->m.getName().equals("submitModel")&&m.getParameterCount()==10));
            assertTrue(Arrays.stream(c.getClass().getDeclaredMethods()).anyMatch(m->m.getName().equals("submitCustom")));
        });
    }
    @Test void originalExceptionDiscardsPendingWithoutReplaying() {
        AtomicInteger count=new AtomicInteger();var failure=new IllegalStateException("测试原异常。");
        assertSame(failure,assertThrows(IllegalStateException.class,()->adapter(TEST_OUTFIT).run(c->{count.incrementAndGet();target(c);throw failure;})));
        assertEquals(1,count.get());assertTrue(queue.calls.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void exceptionAfterEmissionAttemptNeverReplaysEvenIfDelegateRecordedFirst(int failAt) {
        queue.failAt=failAt;AtomicInteger count=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->adapter(TEST_OUTFIT).run(c->{count.incrementAndGet();target(c);}));
        assertEquals(1,count.get());assertEquals(failAt,queue.calls.size());
    }
    @Test void failedSecondSubmitOnFallbackDoesNotInvokeRawTwice() {
        queue.failAt=1;AtomicInteger count=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->adapter(TEST_OUTFIT).run(c->{count.incrementAndGet();target(c);target(c);}));
        assertEquals(1,count.get());assertEquals(1,queue.calls.size());
    }
    @Test void twoHandsQueueBeforeConsumptionWithOppositeOriginalSwitches() {
        source.rightSleeve.visible=true;
        adapter(ORIGINAL).run(this::target);
        var right=queue.calls.getFirst();var expectedRight=consume(right.tree(),right.pose());
        source.rightSleeve.visible=false;source.leftSleeve.visible=false;source.leftArm.zRot=-.1f;
        new OutfitHandCollector(queue.collector(),source,snapshots,OutfitPart.LEFT_ARM,
                decision(ORIGINAL,false,true,OutfitModel.WIDE,OutfitModel.WIDE),skin,outer)
                .run(c->c.submitModelPart(source.leftArm,pose,skin,1,2,null));
        var left=queue.calls.getLast();var expectedLeft=consume(left.tree(),left.pose());
        source.leftSleeve.visible=true;source.rightArm.skipDraw=true;source.leftArm.xScale=4;pose.setIdentity();
        assertEquals(expectedLeft,consume(left.tree(),left.pose()));assertEquals(expectedRight,consume(right.tree(),right.pose()));
        assertEquals(expectedRight,consume(right.tree(),right.pose()));assertEquals(expectedLeft,consume(left.tree(),left.pose()));
    }
    @Test void spriteAndUnsupportedRenderTypePassOriginalArgumentsWithoutReplacement() {
        var id=Identifier.withDefaultNamespace("test_sprite");
        try(var contents=new net.minecraft.client.renderer.texture.SpriteContents(id,
                new net.minecraft.client.resources.metadata.animation.FrameSize(2,2),
                new com.mojang.blaze3d.platform.NativeImage(2,2,true))) {
            var sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite(id,contents,64,64,0,0,0){};
            var crumbling=new CrumblingOverlay(2,new PoseStack().last());
            adapter(TEST_OUTFIT).run(c->c.submitModelPart(source.rightArm,pose,skin,17,19,sprite,23,crumbling,29));
            assertEquals(1,queue.calls.size());
            assertArrayEquals(new Object[]{source.rightArm,pose,skin,17,19,sprite,23,crumbling,29},queue.calls.getFirst().args());
        }
        queue.calls.clear();var unknown=RenderTypes.entitySolid(Identifier.withDefaultNamespace("test_other"));
        adapter(TEST_OUTFIT).run(c->c.submitModelPart(source.rightArm,pose,unknown,1,2,null));
        assertSame(source.rightArm,queue.calls.getFirst().args()[0]);assertSame(unknown,queue.calls.getFirst().args()[2]);
    }
    @Test void unmatchedGeometryForwardsOnceEvenIfRealDelegateThrows() {
        source.rightArm.visit(new PoseStack(),(p,path,index,cube)->{if(path.isEmpty())cube.polygons[0]=cube.polygons[1];});
        queue.failAt=1;AtomicInteger count=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->adapter(TEST_OUTFIT).run(c->{count.incrementAndGet();target(c);}));
        assertEquals(1,count.get());assertEquals(1,queue.calls.size());assertSame(source.rightArm,queue.calls.getFirst().args()[0]);
    }
    @Test void defaultOffPassesOriginalCollectorOnceWithoutClientAccess() {
        var collector=queue.collector();AtomicInteger count=new AtomicInteger();
        OutfitRendering.hand(null,collector,null,true,OutfitPart.RIGHT_ARM,c->{assertSame(collector,c);count.incrementAndGet();});
        assertEquals(1,count.get());
    }
}
