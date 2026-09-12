package dev.zbw3790.fashion.client.render.outfit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import dev.zbw3790.fashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance.Scene;

class OutfitAppearanceResolverTest {
    private final Object connection=new Object();
    private final UUID player=new UUID(0,1);
    private OutfitAppearanceProvider.Context context(UUID id,OutfitModel model,Scene scene){
        return new OutfitAppearanceProvider.Context(connection,id,model,scene,visible(true));
    }
    private OutfitRenderAppearance appearance(OutfitAppearanceProvider.Context context){
        return new OutfitRenderAppearance(context.player(),OutfitSelections.original().with(OutfitPart.HEAD,TEST_OUTFIT),
                context.model(),Map.of(TEST_ID,asset(context.model(),true)),context.originalVisibility(),context.scene());
    }
    @Test void productionDefaultNeverSuppliesAnOverride(){
        var resolver=new OutfitAppearanceResolver(OutfitAppearanceProvider.EMPTY);
        assertFalse(resolver.hasSource());assertTrue(resolver.resolve(context(player,OutfitModel.WIDE,Scene.WORLD)).isEmpty());
    }
    @ParameterizedTest @EnumSource(Scene.class)
    void sourceReceivesActualPlayerModelVisibilityAndScene(Scene scene){
        var context=context(player,OutfitModel.SLIM,scene);AtomicInteger calls=new AtomicInteger();
        var resolver=new OutfitAppearanceResolver(actual->{assertSame(context,actual);calls.incrementAndGet();return Optional.of(appearance(actual));});
        var result=resolver.resolve(context).orElseThrow();
        assertEquals(scene,result.scene());assertEquals(OutfitModel.SLIM,result.model());assertEquals(player,result.player());
        assertEquals(TEST_OUTFIT,result.selections().get(OutfitPart.HEAD));assertEquals(1,calls.get());
    }
    @Test void rejectsCrossPlayerModelSceneAndChangedVisibility(){
        var world=context(player,OutfitModel.WIDE,Scene.WORLD);var fixed=appearance(world);
        var resolver=new OutfitAppearanceResolver(ignored->Optional.of(fixed));
        assertTrue(resolver.resolve(context(new UUID(0,2),OutfitModel.WIDE,Scene.WORLD)).isEmpty());
        assertTrue(resolver.resolve(context(player,OutfitModel.SLIM,Scene.WORLD)).isEmpty());
        assertTrue(resolver.resolve(context(player,OutfitModel.WIDE,Scene.WARDROBE_PREVIEW)).isEmpty());
        assertTrue(resolver.resolve(context(player,OutfitModel.WIDE,Scene.FIRST_PERSON)).isEmpty());
        var changed=visible(true);changed.put(OutfitPart.HEAD,false);
        assertTrue(resolver.resolve(new OutfitAppearanceProvider.Context(connection,player,OutfitModel.WIDE,Scene.WORLD,changed)).isEmpty());
        assertSame(fixed,resolver.resolve(world).orElseThrow());
    }
    @Test void missingResultNeverReusesPreviousAppearanceAndDisconnectedDoesNotCallSource(){
        var context=context(player,OutfitModel.WIDE,Scene.WORLD);AtomicInteger calls=new AtomicInteger();
        var resolver=new OutfitAppearanceResolver(current->calls.incrementAndGet()==1?Optional.of(appearance(current)):Optional.empty());
        assertTrue(resolver.resolve(context).isPresent());assertTrue(resolver.resolve(context).isEmpty());
        assertTrue(resolver.resolve(new OutfitAppearanceProvider.Context(null,player,OutfitModel.WIDE,Scene.WORLD,visible(true))).isEmpty());
        assertEquals(2,calls.get());
    }
    @Test void inputOwnsImmutableVisibilityAndResourceMappings(){
        var visibility=visible(true);var assets=new HashMap<OutfitId,OutfitRenderAppearance.ResolvedAsset>();
        assets.put(TEST_ID,asset(OutfitModel.WIDE,true));
        var selections=OutfitSelections.original().with(OutfitPart.HEAD,TEST_OUTFIT);
        var context=new OutfitAppearanceProvider.Context(connection,player,OutfitModel.WIDE,Scene.WORLD,visibility);
        var input=new OutfitRenderAppearance(player,selections,OutfitModel.WIDE,assets,visibility,Scene.WORLD);
        assets.clear();visibility.put(OutfitPart.HEAD,false);
        assertTrue(input.originalVisibility().get(OutfitPart.HEAD));assertTrue(context.originalVisibility().get(OutfitPart.HEAD));
        assertTrue(input.asset(OutfitPart.HEAD).orElseThrow().texture().isPresent());
        assertThrows(UnsupportedOperationException.class,()->input.assets().clear());
        assertThrows(UnsupportedOperationException.class,()->input.originalVisibility().clear());
        assertThrows(IllegalArgumentException.class,()->new OutfitRenderAppearance(player,selections,OutfitModel.WIDE,Map.of(),Map.of(),Scene.WORLD));
    }
    @Test void newResolvedResourceDoesNotMutateAnAlreadyPreparedInput(){
        var context=context(player,OutfitModel.WIDE,Scene.WORLD);var old=appearance(context);
        var replacement=net.minecraft.resources.Identifier.withDefaultNamespace("replacement_outfit");
        var next=new OutfitRenderAppearance(player,old.selections(),old.model(),Map.of(TEST_ID,
                new OutfitRenderAppearance.ResolvedAsset(old.asset(OutfitPart.HEAD).orElseThrow().metadata(),old.model(),Optional.of(replacement))),
                context.originalVisibility(),context.scene());
        assertEquals(TEXTURE,old.asset(OutfitPart.HEAD).orElseThrow().texture().orElseThrow());
        assertEquals(replacement,next.asset(OutfitPart.HEAD).orElseThrow().texture().orElseThrow());
    }
}
