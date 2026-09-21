package dev.zbw3790.fashion.client.outfit;

import java.nio.file.Path;
import org.slf4j.Logger;
import dev.zbw3790.fashion.client.asset.ValidatedAssetCache;
import dev.zbw3790.fashion.outfit.OutfitPngValidator;

/** 内容缓存只复用验证字节，授权仍由当前连接决定。 */
public final class ClientOutfitAssetCache extends ValidatedAssetCache {
    public ClientOutfitAssetCache(Path path,Logger logger) {super(path,logger,OutfitPngValidator.MAX_ASSET_BYTES,bytes->OutfitPngValidator.validate(bytes)==OutfitPngValidator.Result.VALID);}
    public static ClientOutfitAssetCache fromGameDirectory(Path path,Logger logger) {return new ClientOutfitAssetCache(path.resolve("3790s-fashion/cache/outfit-assets"),logger);}
}
