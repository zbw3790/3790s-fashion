package dev.zbw3790.fashion.client.armor;

import java.nio.file.Path;
import org.slf4j.Logger;
import dev.zbw3790.fashion.client.asset.ValidatedAssetCache;
import dev.zbw3790.fashion.asset.BoundedPngValidator;
import dev.zbw3790.fashion.armor.ArmorAsset;

public final class ClientArmorAssetCache extends ValidatedAssetCache {
    public ClientArmorAssetCache(Path path,Logger logger) {super(path,logger,ArmorAsset.MAX_BYTES,
        bytes->BoundedPngValidator.validate(bytes,64,32,ArmorAsset.MAX_BYTES,true)==BoundedPngValidator.Result.VALID);}
    public static ClientArmorAssetCache fromGameDirectory(Path path,Logger logger) {return new ClientArmorAssetCache(path.resolve("3790s-fashion/cache/armor-assets"),logger);}
}
