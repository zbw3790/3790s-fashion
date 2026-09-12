package dev.zbw3790.fashion.client.render.outfit;

import dev.zbw3790.fashion.outfit.OutfitPart;
import dev.zbw3790.fashion.outfit.OutfitPartSelection;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.ORIGINAL;

/** 正式六部位纯决策；资源未知或不可用不会把选择解释成 NONE。 */
public final class OutfitRenderDecisions {
    private OutfitRenderDecisions() { }
    public enum Visibility { NORMAL, OBSERVER, OUTLINE, HIDDEN }
    public record Decision(OutfitPartSelection effective, boolean originalVisible, boolean outfitVisible) { }

    public static Decision decide(OutfitRenderAppearance appearance, OutfitPart part) {
        OutfitPartSelection selected = appearance.selections().get(part);
        OutfitPartSelection effective = selected;
        boolean visible = appearance.originalVisibility().get(part);
        if (selected instanceof OutfitPartSelection.Outfit) {
            boolean ready = appearance.asset(part).filter(asset -> asset.model() == appearance.model()
                    && asset.metadata().models().contains(appearance.model()) && asset.metadata().parts().contains(part)
                    && asset.texture().isPresent()).isPresent();
            if (!ready) effective = ORIGINAL;
        }
        return new Decision(effective, effective.equals(ORIGINAL) && visible,
                effective instanceof OutfitPartSelection.Outfit && visible);
    }
    public static Visibility visibility(boolean invisible, boolean invisibleToViewer, boolean outline) {
        if (!invisible) return Visibility.NORMAL;
        if (!invisibleToViewer) return Visibility.OBSERVER;
        return outline ? Visibility.OUTLINE : Visibility.HIDDEN;
    }
}
