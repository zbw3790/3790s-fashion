package dev.zbw3790.fashion.client.screen;

import java.util.Set;
import dev.zbw3790.fashion.outfit.OutfitPart;

/** DETAIL 只是面板层级，头部入口和详细头部复用 HEAD。 */
enum OutfitScope {
    ALL("整套",OutfitPart.ALL),
    HEAD("头部",Set.of(OutfitPart.HEAD)),
    UPPER("上身",Set.of(OutfitPart.BODY,OutfitPart.LEFT_ARM,OutfitPart.RIGHT_ARM)),
    LEGS("腿部",Set.of(OutfitPart.LEFT_LEG,OutfitPart.RIGHT_LEG)),
    BODY("躯干",Set.of(OutfitPart.BODY)),
    LEFT_ARM("左袖",Set.of(OutfitPart.LEFT_ARM)),
    RIGHT_ARM("右袖",Set.of(OutfitPart.RIGHT_ARM)),
    LEFT_LEG("左裤腿",Set.of(OutfitPart.LEFT_LEG)),
    RIGHT_LEG("右裤腿",Set.of(OutfitPart.RIGHT_LEG));
    final String label;
    final Set<OutfitPart> targets;
    OutfitScope(String label,Set<OutfitPart> targets) { this.label=label;this.targets=OutfitPart.immutableSet(targets); }
    static OutfitScope detail(OutfitPart part) { return valueOf(part.name()); }
    static String partName(OutfitPart part) { return detail(part).label; }
}
