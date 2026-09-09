package vanillafashion.client.screen;

import java.util.HashMap;
import java.util.Optional;
import vanillafashion.fashion.*;
import vanillafashion.outfit.*;
import vanillafashion.client.outfit.ClientOutfitTextureResolver;
import vanillafashion.client.render.outfit.*;

/** 同一帧的完整不可变草稿和基线；未改部位保留有效权威，新 ID 独立解析就绪状态。 */
record WardrobePreviewDraft(PlayerFashionStoredState draft, FullPlayerFashionState baseline) {
    Optional<vanillafashion.cape.CapeId> cape() {
        return draft.cape().equals(baseline.stored().cape())?baseline.effective().cape():draft.cape();
    }
    OutfitSelections outfit() {
        var result=draft.outfit();
        for (OutfitPart part:OutfitPart.CANONICAL_ORDER)
            if (draft.outfit().get(part).equals(baseline.stored().outfit().get(part)))
                result=result.with(part,baseline.effective().outfit().get(part));
        return result;
    }
    OutfitAppearanceProvider provider(Object expectedConnection,java.util.UUID expectedPlayer,ClientOutfitTextureResolver textures) {
        return context -> {
            if (context.scene()!=OutfitRenderAppearance.Scene.WARDROBE_PREVIEW || context.connection()!=expectedConnection || !context.player().equals(expectedPlayer)) return Optional.empty();
            var selections=outfit();
            var assets=new HashMap<OutfitId,OutfitRenderAppearance.ResolvedAsset>();
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER)
                if (selections.get(part) instanceof OutfitPartSelection.Outfit selected)
                    textures.resolve(context.connection(),selected.id(),context.model()).ifPresent(value -> assets.put(selected.id(),value));
            return Optional.of(new OutfitRenderAppearance(context.player(),selections,context.model(),assets,context.originalVisibility(),context.scene()));
        };
    }
}
