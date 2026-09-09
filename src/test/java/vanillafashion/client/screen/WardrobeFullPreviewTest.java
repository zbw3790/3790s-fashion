package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vanillafashion.client.render.outfit.*;
import vanillafashion.client.outfit.ClientOutfitRegistry;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;

class WardrobeFullPreviewTest {
    private static OutfitAppearanceProvider.Context context(WardrobeS04Fixture f,OutfitRenderAppearance.Scene scene) {
        var visible=new EnumMap<OutfitPart,Boolean>(OutfitPart.class);
        for(var part:OutfitPart.values()) visible.put(part,true);
        return new OutfitAppearanceProvider.Context(f.connection,SELF,f.model.get(),scene,visible);
    }
    @Test void oneImmutableSnapshotIncludesBothDomainsAndCannotOverrideWorldOrHands() {
        var f=new WardrobeS04Fixture();var screen=f.open();
        screen.selectionSession().select(Optional.of(CAPE_A));screen.outfitContent().select(LOOK);
        var snapshot=screen.selectionSession().previewDraft().orElseThrow();
        assertEquals(Optional.of(CAPE_A),snapshot.cape());
        var provider=snapshot.provider(f.connection,SELF,f.resolver);
        var preview=provider.resolve(context(f,OutfitRenderAppearance.Scene.WARDROBE_PREVIEW)).orElseThrow();
        assertTrue(OutfitRenderDecisions.decide(preview,OutfitPart.HEAD).outfitVisible());
        var world=new NetworkOutfitAppearanceProvider(f.authority,f.resolver);
        for(var scene:List.of(OutfitRenderAppearance.Scene.WORLD,OutfitRenderAppearance.Scene.FIRST_PERSON)) {
            assertTrue(provider.resolve(context(f,scene)).isEmpty());
            assertEquals(OutfitSelections.original(),world.resolve(context(f,scene)).orElseThrow().selections());
        }
        screen.selectionSession().clearOutfit(OutfitPart.ALL,OutfitPartSelection.NONE);
        assertEquals(OutfitPartSelection.outfit(LOOK),snapshot.outfit().get(OutfitPart.BODY));
        assertEquals(FullPlayerFashionState.defaults(0),f.authority.fullSelfAuthority(SELF).orElseThrow());assertTrue(f.sent.isEmpty());
    }
    @Test void dormantOldActiveMaskDoesNotApplyToNewIdAndLateTextureRecoversOnlyPreview() {
        var old=new PlayerFashionStoredState(Optional.of(CAPE_A),OutfitSelections.original().with(OutfitPart.HEAD,OutfitPartSelection.outfit(new OutfitId("missing"))));
        var baseline=new FullPlayerFashionState(old,new PlayerFashionEffectiveState(Optional.empty(),OutfitSelections.original()),0);
        var f=new WardrobeS04Fixture(ClientOutfitRegistry.State.KNOWN,10,false,baseline);
        var screen=f.open();
        assertEquals(OutfitPartSelection.ORIGINAL,screen.selectionSession().previewDraft().orElseThrow().outfit().get(OutfitPart.HEAD));
        screen.selectionSession().selectOutfit(Set.of(OutfitPart.HEAD),LOOK,OutfitPart.ALL);
        var snapshot=screen.selectionSession().previewDraft().orElseThrow();assertTrue(snapshot.cape().isEmpty());
        assertTrue(screen.selectionSession().previewSelection().isEmpty());
        var context=context(f,OutfitRenderAppearance.Scene.WARDROBE_PREVIEW);var provider=snapshot.provider(f.connection,SELF,f.resolver);
        assertEquals(OutfitPartSelection.ORIGINAL,OutfitRenderDecisions.decide(provider.resolve(context).orElseThrow(),OutfitPart.HEAD).effective());
        var stored=screen.selectionSession().fullSnapshot();f.ready();
        assertTrue(OutfitRenderDecisions.decide(provider.resolve(context).orElseThrow(),OutfitPart.HEAD).outfitVisible());
        assertEquals(stored,screen.selectionSession().fullSnapshot());assertEquals(baseline,f.authority.fullSelfAuthority(SELF).orElseThrow());assertTrue(f.sent.isEmpty());
    }
}
