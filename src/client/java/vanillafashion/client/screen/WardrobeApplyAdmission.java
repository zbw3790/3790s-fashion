package vanillafashion.client.screen;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import vanillafashion.cape.CapeId;
import vanillafashion.fashion.PlayerFashionStoredState;
import vanillafashion.outfit.*;

/** 只证明改变后的新引用；清除与未变化 dormant 不跨资源域重验。 */
final class WardrobeApplyAdmission {
    private WardrobeApplyAdmission() { }
    static boolean changedFields(PlayerFashionStoredState baseline, PlayerFashionStoredState draft,
            Predicate<CapeId> cape, BiPredicate<OutfitPart,OutfitId> outfit) {
        if (!draft.cape().equals(baseline.cape()) && draft.cape().isPresent() && !cape.test(draft.cape().orElseThrow())) return false;
        for (OutfitPart part:OutfitPart.CANONICAL_ORDER) {
            var selected=draft.outfit().get(part);
            if (!selected.equals(baseline.outfit().get(part)) && selected instanceof OutfitPartSelection.Outfit custom
                    && !outfit.test(part,custom.id())) return false;
        }
        return true;
    }
}
