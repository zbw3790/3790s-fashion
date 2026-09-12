package dev.zbw3790.fashion.client.render.outfit;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import dev.zbw3790.fashion.outfit.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport.*;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderDecisions.*;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;

class OutfitRenderDecisionsTest {
    @ParameterizedTest @MethodSource("dev.zbw3790.fashion.client.render.outfit.OutfitTestSupport#selections")
    void threeFormalStatesRespectOriginalVisibility(OutfitPartSelection choice) {
        for(boolean visible:new boolean[]{false,true}) {
            var result=decision(choice,visible,true,OutfitModel.WIDE,OutfitModel.WIDE);
            assertEquals(choice,result.effective());assertEquals(choice.equals(ORIGINAL)&&visible,result.originalVisible());
            assertEquals(choice instanceof OutfitPartSelection.Outfit&&visible,result.outfitVisible());
        }
    }
    @Test void missingResourceAndModelMismatchFallBackToOriginalWithoutChangingSelection() {
        for(boolean visible:new boolean[]{false,true}) {
            assertEquals(new Decision(ORIGINAL,visible,false),decision(TEST_OUTFIT,visible,false,OutfitModel.WIDE,OutfitModel.WIDE));
            assertEquals(new Decision(ORIGINAL,visible,false),decision(TEST_OUTFIT,visible,true,OutfitModel.SLIM,OutfitModel.WIDE));
            assertEquals(new Decision(NONE,false,false),decision(NONE,visible,false,null,OutfitModel.WIDE));
        }
    }
    @Test void unknownResourceAndUndeclaredPartNeverTurnIntoNoneOrInventProvidedParts() {
        var selections=OutfitSelections.original().with(OutfitPart.HEAD,TEST_OUTFIT);
        var input=new OutfitRenderAppearance(new UUID(0,1),selections,OutfitModel.WIDE,Map.of(),visible(true),OutfitRenderAppearance.Scene.WORLD);
        assertEquals(ORIGINAL,decide(input,OutfitPart.HEAD).effective());assertEquals(TEST_OUTFIT,input.selections().get(OutfitPart.HEAD));
        var bodyOnly=new OutfitRenderAppearance.ResolvedAsset(new OutfitMetadata(Set.of(OutfitPart.BODY),Set.of(OutfitModel.WIDE)),
                OutfitModel.WIDE,Optional.of(TEXTURE));
        input=new OutfitRenderAppearance(input.player(),selections,input.model(),Map.of(TEST_ID,bodyOnly),visible(true),input.scene());
        assertEquals(ORIGINAL,decide(input,OutfitPart.HEAD).effective());
    }
    @Test void visibilityKeepsVanillaPrecedence() {
        assertEquals(Visibility.NORMAL,visibility(false,false,true));assertEquals(Visibility.OBSERVER,visibility(true,false,true));
        assertEquals(Visibility.OUTLINE,visibility(true,true,true));assertEquals(Visibility.HIDDEN,visibility(true,true,false));
    }
}
