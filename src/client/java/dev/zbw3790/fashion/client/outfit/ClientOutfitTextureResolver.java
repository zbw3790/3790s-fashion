package dev.zbw3790.fashion.client.outfit;

import java.util.Optional;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance;

public final class ClientOutfitTextureResolver {
    private final ClientOutfitRegistry registry; private final ClientOutfitAssetStore store; private final ClientOutfitTextureManager textures;
    public ClientOutfitTextureResolver(ClientOutfitRegistry registry, ClientOutfitAssetStore store, ClientOutfitTextureManager textures) { this.registry=registry; this.store=store; this.textures=textures; }
    public enum Availability { READY, LOADING, UNAVAILABLE, MODEL_INVALID, LOAD_FAILED, MODEL_MISMATCH, MODEL_UNKNOWN }
    public Availability availability(Object connection, OutfitId id, OutfitModel model) {
        if (model==null) return Availability.MODEL_UNKNOWN;
        if (!registry.matches(connection)) return Availability.UNAVAILABLE;
        var entry=registry.find(id);
        if (entry.isEmpty()) return Availability.UNAVAILABLE;
        if (!entry.orElseThrow().declaredModels().contains(model)) return Availability.MODEL_MISMATCH;
        String hash=entry.orElseThrow().validModels().get(model);
        if (hash==null) return Availability.MODEL_INVALID;
        if (textures.failed(connection,hash)) return Availability.LOAD_FAILED;
        return store.contains(hash) && textures.find(connection,hash).isPresent()?Availability.READY:Availability.LOADING;
    }
    public Optional<Boolean> areaTransparent(Object connection,OutfitId id,OutfitModel model,int u,int v,int width,int height) {
        return model!=null && registry.matches(connection)?registry.find(id).map(entry -> entry.validModels().get(model))
                .flatMap(hash -> textures.areaTransparent(connection,hash,u,v,width,height)):Optional.empty();
    }
    public Optional<Boolean> fullyTransparent(Object connection, OutfitId id, OutfitModel model) {
        return registry.matches(connection)?registry.find(id).map(entry -> entry.validModels().get(model))
                .flatMap(hash -> textures.fullyTransparent(connection,hash)):Optional.empty();
    }
    public Optional<OutfitRenderAppearance.ResolvedAsset> resolve(Object connection, OutfitId id, OutfitModel model) {
        if (model==null || !registry.matches(connection)) return Optional.empty();
        return registry.find(id).filter(entry -> entry.declaredModels().contains(model)).map(entry -> {
            var texture=Optional.ofNullable(entry.validModels().get(model)).filter(store::contains).flatMap(hash -> textures.find(connection,hash));
            return new OutfitRenderAppearance.ResolvedAsset(new OutfitMetadata(entry.parts(),entry.declaredModels()),model,texture);
        });
    }
}
