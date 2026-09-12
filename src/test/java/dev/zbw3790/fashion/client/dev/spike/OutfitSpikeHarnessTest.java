package dev.zbw3790.fashion.client.dev.spike;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.client.render.outfit.*;
import dev.zbw3790.fashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance.Scene;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;

class OutfitSpikeHarnessTest {
    private final Object connection=new Object();
    private final UUID a=new UUID(0,1),b=new UUID(0,2);
    private static final Identifier TEXTURE=Identifier.withDefaultNamespace("test");
    private OutfitAppearanceProvider.Context context(Object current,UUID player,Scene scene){
        Map<OutfitPart,Boolean> visible=new EnumMap<>(OutfitPart.class);
        for(OutfitPart part:OutfitPart.CANONICAL_ORDER)visible.put(part,true);
        return new OutfitAppearanceProvider.Context(current,player,OutfitModel.WIDE,scene,visible);
    }
    @Test void neitherProductionNorImplicitDevelopmentInvokesRegistration(){
        for(boolean development:new boolean[]{false,true})for(boolean explicit:new boolean[]{false,true}){
            AtomicInteger calls=new AtomicInteger();OutfitAppearanceProvider provider=context->java.util.Optional.empty();
            var result=OutfitSpikeHarness.registerWhen(development,explicit,()->{calls.incrementAndGet();return provider;});
            assertEquals(development&&explicit?1:0,calls.get());
            assertSame(development&&explicit?provider:OutfitAppearanceProvider.EMPTY,result);
        }
    }
    @Test void connectionPlayerAndSceneKeepAssignmentsIndependent(){
        var inputs=new OutfitSpikeInputs();var textures=Map.of("wide-all",TEXTURE);
        inputs.assign(connection,a,Scene.WORLD,"wide-all");
        assertTrue(inputs.resolve(context(connection,a,Scene.WORLD),textures).isPresent());
        assertTrue(inputs.resolve(context(connection,b,Scene.WORLD),textures).isEmpty());
        assertTrue(inputs.resolve(context(connection,a,Scene.WARDROBE_PREVIEW),textures).isEmpty());
        inputs.assign(connection,a,Scene.WARDROBE_PREVIEW,"wide-none");
        assertEquals(NONE,inputs.resolve(context(connection,a,Scene.WARDROBE_PREVIEW),textures).orElseThrow().selections().get(OutfitPart.HEAD));
        var hand=inputs.resolve(context(connection,a,Scene.FIRST_PERSON),textures).orElseThrow();
        assertEquals(Scene.FIRST_PERSON,hand.scene());assertEquals(outfit(new OutfitId("wide-all")),hand.selections().get(OutfitPart.HEAD));
        assertTrue(inputs.resolve(context(new Object(),a,Scene.WORLD),textures).isEmpty());
        assertTrue(inputs.resolve(context(null,a,Scene.WORLD),textures).isEmpty());
    }
    @Test void profilesUseFormalMetadataModelsPartsAndSelections(){
        var context=context(connection,a,Scene.WORLD);
        var upper=OutfitSpikeInputs.profile(context,"wide-upper",Map.of("wide-upper",TEXTURE));
        assertEquals(OutfitGroup.UPPER_GROUP.parts(),upper.assets().get(new OutfitId("wide-upper")).metadata().parts());
        assertEquals(ORIGINAL,upper.selections().get(OutfitPart.HEAD));
        assertEquals(outfit(new OutfitId("wide-upper")),upper.selections().get(OutfitPart.BODY));
        var transparent=OutfitSpikeInputs.profile(context,"wide-transparent",Map.of("wide-transparent",TEXTURE));
        assertTrue(transparent.asset(OutfitPart.HEAD).orElseThrow().texture().isPresent());
        assertInstanceOf(OutfitPartSelection.Outfit.class,transparent.selections().get(OutfitPart.HEAD));
        var unavailable=OutfitSpikeInputs.profile(context,"wide-unavailable",Map.of());
        assertTrue(unavailable.asset(OutfitPart.HEAD).orElseThrow().texture().isEmpty());
        var mismatch=OutfitSpikeInputs.profile(context,"wide-mismatch",Map.of("wide-mismatch",TEXTURE));
        assertEquals(OutfitModel.SLIM,mismatch.asset(OutfitPart.HEAD).orElseThrow().model());
        assertEquals(OutfitModel.WIDE,mismatch.model());
        assertThrows(IllegalArgumentException.class,()->OutfitSpikeInputs.profile(context,"bad-profile",Map.of()));
    }
    @Test void invalidCommandDoesNotReplaceExistingAssignmentAndDisconnectClears(){
        var inputs=new OutfitSpikeInputs();inputs.assign(connection,a,Scene.WORLD,"wide-all");
        assertThrows(IllegalArgumentException.class,()->inputs.assign(connection,a,Scene.WORLD,"wide-invalid"));
        assertEquals(outfit(new OutfitId("wide-all")),inputs.resolve(context(connection,a,Scene.WORLD),Map.of()).orElseThrow().selections().get(OutfitPart.HEAD));
        inputs.connection(null);assertTrue(inputs.resolve(context(connection,a,Scene.WORLD),Map.of()).isEmpty());
    }
}
