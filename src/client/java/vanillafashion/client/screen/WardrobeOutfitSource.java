package vanillafashion.client.screen;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import vanillafashion.client.outfit.*;
import vanillafashion.outfit.*;

/** 衣柜有限只读视图；不请求、注册或缓存任何资源。 */
public final class WardrobeOutfitSource {
    final ClientOutfitTextureResolver textures;
    private final ClientOutfitRegistry registry;
    private final ClientOutfitAssetSync sync;
    private final Supplier<Object> connection;
    private final Supplier<OutfitModel> model;
    public WardrobeOutfitSource(ClientOutfitRegistry registry,ClientOutfitTextureResolver textures,
            ClientOutfitAssetSync sync,Supplier<Object> connection,Supplier<OutfitModel> model) {
        this.registry=registry;this.textures=textures;this.sync=sync;this.connection=connection;this.model=model;
    }
    static WardrobeOutfitSource empty() {
        var registry=new ClientOutfitRegistry();
        return new WardrobeOutfitSource(registry,new ClientOutfitTextureResolver(registry,new ClientOutfitAssetStore(),
                new ClientOutfitTextureManager()),null,() -> null,() -> OutfitModel.WIDE);
    }
    Object connection() { return connection.get(); }
    OutfitModel model() { return model.get(); }
    ClientOutfitRegistry.State state() { return registry.matches(connection())?registry.state():ClientOutfitRegistry.State.UNSUPPORTED; }
    List<OutfitRegistrySnapshot.Entry> entries() { return registry.matches(connection())?registry.entries():List.of(); }
    Optional<OutfitRegistrySnapshot.Entry> find(OutfitId id) { return registry.matches(connection())?registry.find(id):Optional.empty(); }
    ClientOutfitTextureResolver.Availability availability(OutfitId id) {
        var value=textures.availability(connection(),id,model());
        String hash=model()==null?null:find(id).map(entry -> entry.validModels().get(model())).orElse(null);
        return hash!=null && sync!=null && sync.failed(connection(),hash)?ClientOutfitTextureResolver.Availability.LOAD_FAILED:value;
    }
    boolean admitted(OutfitPart part,OutfitId id) {
        return state()==ClientOutfitRegistry.State.KNOWN && find(id).filter(entry -> entry.parts().contains(part)).isPresent()
                && availability(id)==ClientOutfitTextureResolver.Availability.READY;
    }
    Optional<Identifier> texture(OutfitId id) {
        return textures.resolve(connection(),id,model()).flatMap(value -> value.texture());
    }
    boolean transparent(OutfitId id) { return textures.fullyTransparent(connection(),id,model()).orElse(false); }
}
