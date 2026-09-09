package vanillafashion.network;

import java.util.Objects;
import vanillafashion.outfit.*;

/** 不可变连接视图持有内容来源；旧客户端不受全局目录替换影响。 */
public record ConnectionOutfitAssetView(long generation, OutfitRegistrySnapshot snapshot, OutfitAssetIndex assets) {
    public ConnectionOutfitAssetView {
        if (generation < 0) throw new IllegalArgumentException("装束目录代次不能为负数。");
        Objects.requireNonNull(snapshot); Objects.requireNonNull(assets);
        for (String hash : snapshot.requiredHashes()) if (assets.find(hash).isEmpty())
            throw new IllegalArgumentException("连接视图缺少已授权的资产内容。");
    }
    public static ConnectionOutfitAssetView from(long generation, OutfitRegistryLoadResult loaded) {
        return new ConnectionOutfitAssetView(generation, OutfitRegistrySnapshot.from(loaded), loaded.assets());
    }
}
