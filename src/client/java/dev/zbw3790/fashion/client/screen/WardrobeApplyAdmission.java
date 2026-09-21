package dev.zbw3790.fashion.client.screen;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.fashion.PlayerFashionStoredState;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.armor.*;

/** 只证明改变后的新引用；清除与未变化 dormant 不跨资源域重验。 */
final class WardrobeApplyAdmission {
    private WardrobeApplyAdmission() { }
    static boolean changedFields(PlayerFashionStoredState baseline, PlayerFashionStoredState draft,
            Predicate<CapeId> cape, BiPredicate<OutfitPart,OutfitId> outfit) {
        return changedFields(baseline,draft,cape,outfit,(slot,id) -> false);
    }
    static boolean changedFields(PlayerFashionStoredState baseline, PlayerFashionStoredState draft,
            Predicate<CapeId> cape, BiPredicate<OutfitPart,OutfitId> outfit, BiPredicate<ArmorSlot,ArmorStyleId> armor) {
        if (!draft.cape().equals(baseline.cape()) && draft.cape().isPresent() && !cape.test(draft.cape().orElseThrow())) return false;
        for (OutfitPart part:OutfitPart.CANONICAL_ORDER) {
            var selected=draft.outfit().get(part);
            if (!selected.equals(baseline.outfit().get(part)) && selected instanceof OutfitPartSelection.Outfit custom
                    && !outfit.test(part,custom.id())) return false;
        }
        for (ArmorSlot slot:ArmorSlot.CANONICAL_ORDER) {
            var selected=draft.armor().get(slot);
            if (!selected.equals(baseline.armor().get(slot)) && selected instanceof ArmorSelection.Custom custom
                    && !armor.test(slot,custom.id())) return false;
        }
        return true;
    }
}
