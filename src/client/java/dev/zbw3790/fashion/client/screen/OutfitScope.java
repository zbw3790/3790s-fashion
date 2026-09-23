package dev.zbw3790.fashion.client.screen;

import java.util.Set;
import dev.zbw3790.fashion.outfit.OutfitPart;

/** DETAIL 只是面板层级，头部入口和详细头部复用 HEAD。 */
enum OutfitScope {
    ALL("outfit.scope.all",OutfitPart.ALL),
    HEAD("outfit.scope.head",Set.of(OutfitPart.HEAD)),
    UPPER("outfit.scope.upper",Set.of(OutfitPart.BODY,OutfitPart.LEFT_ARM,OutfitPart.RIGHT_ARM)),
    LEGS("outfit.scope.legs",Set.of(OutfitPart.LEFT_LEG,OutfitPart.RIGHT_LEG)),
    BODY("outfit.scope.body",Set.of(OutfitPart.BODY)),
    LEFT_ARM("outfit.scope.left_arm",Set.of(OutfitPart.LEFT_ARM)),
    RIGHT_ARM("outfit.scope.right_arm",Set.of(OutfitPart.RIGHT_ARM)),
    LEFT_LEG("outfit.scope.left_leg",Set.of(OutfitPart.LEFT_LEG)),
    RIGHT_LEG("outfit.scope.right_leg",Set.of(OutfitPart.RIGHT_LEG));
    final String labelKey;
    final Set<OutfitPart> targets;
    OutfitScope(String label,Set<OutfitPart> targets) { this.labelKey=label;this.targets=OutfitPart.immutableSet(targets); }
    String label() { return WardrobeText.string(labelKey); }
    static OutfitScope detail(OutfitPart part) { return valueOf(part.name()); }
    static String partName(OutfitPart part) { return detail(part).label(); }
}
