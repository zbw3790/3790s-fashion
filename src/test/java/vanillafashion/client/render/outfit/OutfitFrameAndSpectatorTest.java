package vanillafashion.client.render.outfit;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vanillafashion.client.render.WardrobePreviewTestSupport;
import vanillafashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.client.render.outfit.OutfitTestSupport.*;
import static vanillafashion.client.render.outfit.OutfitRenderDecisions.*;
import static vanillafashion.client.render.outfit.OutfitRenderAppearance.Scene;
import static vanillafashion.outfit.OutfitPartSelection.*;

class OutfitFrameAndSpectatorTest {
    @BeforeAll static void bootstrap(){WardrobePreviewTestSupport.bootstrap();}
    private OutfitRendering.Binding binding(OutfitModel type){
        var models=models();return new OutfitRendering.Binding(null,model(models,type),new OutfitPartSnapshots(models,type));
    }
    private OutfitRenderAppearance input(AvatarRenderState state,Scene scene){
        return all(state,scene,TEST_OUTFIT,OutfitRenderAppearance.modelOf(state.skin.model()),true);
    }
    @ParameterizedTest @EnumSource(OutfitModel.class)
    void normalPreparationPreservesOriginalSwitchesBeforePublishingSuppressions(OutfitModel type){
        var state=state(type);state.showLeftSleeve=false;var binding=binding(type);
        var frame=OutfitRendering.prepareFrame(state,input(state,Scene.WORLD),binding);
        assertNotNull(frame);assertTrue(state.showHat);assertFalse(state.showLeftSleeve);
        assertFalse(frame.decisions().get(OutfitPart.LEFT_ARM).outfitVisible());assertEquals(5,frame.outers().size());
        OutfitRendering.publishFrame(state,frame);
        for(OutfitPart part:OutfitPart.CANONICAL_ORDER)assertFalse(OutfitRendering.visible(state,part));
        Queue queue=new Queue();new OutfitRendering.Layer(binding).submit(new PoseStack(),queue.collector(),100,state,0,0);
        assertEquals(5,queue.calls.size());
        for(Call call:queue.calls){List<String> paths=new java.util.ArrayList<>();call.tree().visit(new PoseStack(),(p,path,i,c)->paths.add(path));
            assertEquals(1,paths.size());assertEquals(2,paths.getFirst().chars().filter(c->c=='/').count());}
    }
    static Stream<Arguments> resourceCases(){return Stream.of(
        Arguments.of(TEST_OUTFIT,OutfitModel.WIDE,true,TEST_OUTFIT,6),
        Arguments.of(NONE,OutfitModel.WIDE,false,NONE,0),
        Arguments.of(TEST_OUTFIT,OutfitModel.WIDE,false,ORIGINAL,0),
        Arguments.of(TEST_OUTFIT,OutfitModel.SLIM,true,ORIGINAL,0),
        Arguments.of(ORIGINAL,OutfitModel.WIDE,false,ORIGINAL,0));}
    @ParameterizedTest @MethodSource("resourceCases")
    void readyAssetNoneAndFallbackHaveSeparateSuppressionSemantics(OutfitPartSelection choice,OutfitModel resourceModel,
            boolean ready,OutfitPartSelection effective,int outers){
        var state=state(OutfitModel.WIDE);var frame=OutfitRendering.prepareFrame(state,all(state,Scene.WORLD,choice,resourceModel,ready),binding(OutfitModel.WIDE));
        assertNotNull(frame);assertEquals(effective,frame.decisions().get(OutfitPart.HEAD).effective());
        OutfitRendering.publishFrame(state,frame);
        assertEquals(effective.equals(ORIGINAL),state.showHat);assertEquals(outers,frame.outers().size());
    }
    @ParameterizedTest @MethodSource("vanillafashion.client.render.outfit.OutfitTestSupport#selections")
    void spectatorHasExactlyOneBaseAndAtMostOneHatAndNeverRunsNormalLayer(OutfitPartSelection choice){
        var state=state(OutfitModel.WIDE);state.isSpectator=true;var binding=binding(OutfitModel.WIDE);
        var frame=OutfitRendering.prepareFrame(state,all(state,Scene.WARDROBE_PREVIEW,choice,OutfitModel.WIDE,true),binding);
        OutfitRendering.publishFrame(state,frame);
        for(OutfitPart part:OutfitPart.CANONICAL_ORDER)assertTrue(OutfitRendering.visible(state,part));
        assertTrue(state.isSpectator);
        Queue queue=new Queue();new OutfitRendering.Layer(binding).submit(new PoseStack(),queue.collector(),100,state,0,0);
        assertTrue(queue.calls.isEmpty());
        var type=binding.source().renderType(state.skin.body().texturePath());
        var prepared=OutfitSpectatorSubmission.prepare(frame,state,new PoseStack(),type,null);
        if(choice.equals(ORIGINAL)){assertNull(prepared);return;}
        assertNotNull(prepared);AtomicInteger count=new AtomicInteger();
        OutfitSpectatorSubmission.emit(prepared,queue.collector(),1,2,3,null,4,null,recordingOriginal(count));
        assertEquals(1,count.get());assertEquals(choice instanceof OutfitPartSelection.Outfit?2:1,queue.calls.size());
        assertFalse(queue.calls.getFirst().tree().getChild("head").hasChild("hat"));
        if(choice instanceof OutfitPartSelection.Outfit){var hat=queue.calls.getLast().tree();List<String> paths=new java.util.ArrayList<>();
            hat.visit(new PoseStack(),(p,path,i,c)->paths.add(path));assertEquals(List.of("/head/hat"),paths);}
    }
    @Test void normalPlayerUnknownAndMismatchedGeometryNeverProduceSpectatorSubmission(){
        var state=state(OutfitModel.WIDE);var binding=binding(OutfitModel.WIDE);
        var frame=OutfitRendering.prepareFrame(state,input(state,Scene.WORLD),binding);
        assertNull(OutfitSpectatorSubmission.prepare(frame,state,new PoseStack(),binding.source().renderType(state.skin.body().texturePath()),null));
        assertNull(OutfitRendering.prepareFrame(state,null,binding));
        assertNull(OutfitRendering.prepareFrame(state,input(state,Scene.WORLD),binding(OutfitModel.SLIM)));
    }
    @Test void spectatorUnknownPreservesEveryOriginalArgument(){
        var binding=binding(OutfitModel.WIDE);var state=state(OutfitModel.WIDE);state.isSpectator=true;
        var collector=new Queue().collector();var pose=new PoseStack();var type=RenderTypes.entityTranslucent(TEXTURE);
        var crumbling=new CrumblingOverlay(5,new PoseStack().last());
        for(Object receiver:new Object[]{new Object(),null}){
            AtomicInteger count=new AtomicInteger();
            OutfitSpectatorSubmission.submit(receiver,collector,binding.source(),state,pose,type,1,2,3,null,4,crumbling,args->{
                count.incrementAndGet();assertArrayEquals(new Object[]{collector,binding.source(),state,pose,type,1,2,3,null,4,crumbling},args);return null;
            },state);assertEquals(1,count.get());
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void spectatorSubmissionExceptionNeverReplaysMain(int failAt){
        var state=state(OutfitModel.WIDE);state.isSpectator=true;var binding=binding(OutfitModel.WIDE);
        var frame=OutfitRendering.prepareFrame(state,input(state,Scene.WORLD),binding);
        var prepared=OutfitSpectatorSubmission.prepare(frame,state,new PoseStack(),binding.source().renderType(state.skin.body().texturePath()),null);
        Queue queue=new Queue();queue.failAt=failAt;AtomicInteger count=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->OutfitSpectatorSubmission.emit(prepared,queue.collector(),1,2,3,null,4,null,recordingOriginal(count)));
        assertEquals(1,count.get());assertEquals(failAt,queue.calls.size());
    }
    @Test void framesAndRepeatedSubmissionsNeverShareMutableQueuedTrees(){
        var binding=binding(OutfitModel.WIDE);var world=state(OutfitModel.WIDE);world.yRot=45;
        var frame=OutfitRendering.prepareFrame(world,input(world,Scene.WORLD),binding);OutfitRendering.publishFrame(world,frame);
        Queue queue=new Queue();var layer=new OutfitRendering.Layer(binding);layer.submit(new PoseStack(),queue.collector(),100,world,0,0);
        var first=queue.calls.getFirst();var expected=consume(first.tree(),first.pose());
        var gui=state(OutfitModel.WIDE);gui.yRot=-75;
        var head=appearance(gui,Scene.WARDROBE_PREVIEW,OutfitSelections.original().with(OutfitPart.HEAD,TEST_OUTFIT),Map.of(TEST_ID,asset(OutfitModel.WIDE,true)));
        var otherPlayer=new OutfitRenderAppearance(new UUID(0,2),head.selections(),head.model(),head.assets(),head.originalVisibility(),head.scene());
        var preview=OutfitRendering.prepareFrame(gui,otherPlayer,binding);
        assertNotEquals(frame.input().player(),preview.input().player());assertNotEquals(frame.input().scene(),preview.input().scene());
        assertEquals(1,preview.outers().size());
        layer.submit(new PoseStack(),queue.collector(),100,world,0,0);
        assertNotSame(first.tree(),queue.calls.get(6).tree());queue.calls.get(6).tree().visible=false;
        var rebuilt=binding(OutfitModel.WIDE);OutfitRendering.prepareFrame(gui,input(gui,Scene.WORLD),rebuilt);
        assertEquals(expected,consume(first.tree(),first.pose()));
        OutfitRendering.publishFrame(gui,preview);OutfitRendering.prepareWorld(gui,Optional.empty());
        assertNull(OutfitRendering.find(gui));
    }
    @ParameterizedTest @EnumSource(Visibility.class)
    void vanillaVisibilityBranchIsRetainedForOuterAndSpectator(Visibility mode){
        var state=state(OutfitModel.WIDE);state.isSpectator=true;
        state.isInvisible=mode!=Visibility.NORMAL;state.isInvisibleToPlayer=mode!=Visibility.NORMAL&&mode!=Visibility.OBSERVER;
        state.outlineColor=mode==Visibility.OUTLINE?0xff00ff:0;
        var binding=binding(OutfitModel.WIDE);var frame=OutfitRendering.prepareFrame(state,input(state,Scene.WORLD),binding);
        assertEquals(mode,frame.visibility());assertEquals(mode==Visibility.HIDDEN?0:1,frame.outers().size());
        var baseType=switch(mode){case NORMAL->binding.source().renderType(state.skin.body().texturePath());
            case OBSERVER->RenderTypes.entityTranslucentCullItemTarget(state.skin.body().texturePath());
            case OUTLINE->RenderTypes.outline(state.skin.body().texturePath());case HIDDEN->null;};
        var prepared=OutfitSpectatorSubmission.prepare(frame,state,new PoseStack(),baseType,null);
        assertEquals(mode!=Visibility.HIDDEN,prepared!=null);if(prepared!=null)assertSame(baseType,prepared.baseType());
        assertNull(OutfitSpectatorSubmission.prepare(frame,state,new PoseStack(),RenderTypes.entitySolid(TEXTURE),null));
    }
    @ParameterizedTest @EnumSource(Visibility.class)
    void normalLayerRetainsAlphaRenderTypeColorAndOutline(Visibility mode){
        var state=state(OutfitModel.SLIM);
        state.isInvisible=mode!=Visibility.NORMAL;state.isInvisibleToPlayer=mode!=Visibility.NORMAL&&mode!=Visibility.OBSERVER;
        state.outlineColor=mode==Visibility.OUTLINE?0xff00ff:0;
        var binding=binding(OutfitModel.SLIM);var frame=OutfitRendering.prepareFrame(state,input(state,Scene.WORLD),binding);
        OutfitRendering.publishFrame(state,frame);Queue queue=new Queue();
        new OutfitRendering.Layer(binding).submit(new PoseStack(),queue.collector(),1,state,0,0);
        assertEquals(mode==Visibility.HIDDEN?0:6,queue.calls.size());
        for(Call call:queue.calls){assertSame(OutfitRendering.outfitType(mode,TEXTURE),call.args()[3]);
            assertEquals(mode==Visibility.OBSERVER?0x26ffffff:-1,call.args()[6]);assertEquals(state.outlineColor,call.args()[8]);}
    }
    @Test void sixPartsMayResolveDifferentIdsEvenWhenTextureIsShared(){
        var state=state(OutfitModel.WIDE);var selections=OutfitSelections.original();
        Map<OutfitId,OutfitRenderAppearance.ResolvedAsset> assets=new java.util.HashMap<>();
        for(OutfitPart part:OutfitPart.CANONICAL_ORDER){var id=new OutfitId("part_"+part.serializedName());
            selections=selections.with(part,outfit(id));assets.put(id,asset(OutfitModel.WIDE,true));}
        var input=appearance(state,Scene.WORLD,selections,assets);
        var frame=OutfitRendering.prepareFrame(state,input,binding(OutfitModel.WIDE));
        assertEquals(6,frame.outers().size());assertEquals(6,frame.input().assets().size());
        for(OutfitPart part:OutfitPart.CANONICAL_ORDER)assertEquals(selections.get(part),frame.decisions().get(part).effective());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Operation<Void> recordingOriginal(AtomicInteger count){return args->{
        count.incrementAndGet();((SubmitNodeCollector)args[0]).submitModel((Model)args[1],args[2],(PoseStack)args[3],(RenderType)args[4],
                (int)args[5],(int)args[6],(int)args[7],(TextureAtlasSprite)args[8],(int)args[9],(CrumblingOverlay)args[10]);return null;};}
}
