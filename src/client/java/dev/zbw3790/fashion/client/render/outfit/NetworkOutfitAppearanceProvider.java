package dev.zbw3790.fashion.client.render.outfit;

import java.util.*;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.outfit.ClientOutfitTextureResolver;
import dev.zbw3790.fashion.outfit.*;

/** 世界、手部与当前 Cape-only 衣柜均消费同一连接的有效权威；不写入任何草稿或世界实体。 */
public final class NetworkOutfitAppearanceProvider implements OutfitAppearanceProvider {
    private final ClientPlayerFashionRegistry authority; private final ClientOutfitTextureResolver textures;
    public NetworkOutfitAppearanceProvider(ClientPlayerFashionRegistry authority, ClientOutfitTextureResolver textures) { this.authority=authority; this.textures=textures; }
    @Override public Optional<OutfitRenderAppearance> resolve(Context context) {
        if (context.connection()==null || context.connection()!=authority.connectionIdentity()) return Optional.empty();
        return authority.fullAuthority(context.player()).map(state -> {
            var selections=state.effective().outfit(); var assets=new HashMap<OutfitId,OutfitRenderAppearance.ResolvedAsset>();
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) if (selections.get(part) instanceof OutfitPartSelection.Outfit selected)
                textures.resolve(context.connection(),selected.id(),context.model()).ifPresent(asset -> assets.put(selected.id(),asset));
            return new OutfitRenderAppearance(context.player(),selections,context.model(),assets,context.originalVisibility(),context.scene());
        });
    }
    public static OutfitAppearanceProvider exclusive(OutfitAppearanceProvider production, OutfitAppearanceProvider developmentOverride) {
        return developmentOverride==OutfitAppearanceProvider.EMPTY?Objects.requireNonNull(production):Objects.requireNonNull(developmentOverride);
    }
}
