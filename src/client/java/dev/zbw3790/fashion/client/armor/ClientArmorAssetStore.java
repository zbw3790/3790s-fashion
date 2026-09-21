package dev.zbw3790.fashion.client.armor;

import java.util.*;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.armor.*;

/** 只保存本连接已验证原字节，授权与 pending 由 AssetSync 在写入前检查。 */
public final class ClientArmorAssetStore {
    public enum Result { STORED, INVALID_SIZE, HASH_MISMATCH, INVALID_PNG, INVALID_DIMENSIONS, LIMIT }
    private final Map<String,byte[]> assets=new HashMap<>();
    public Result store(String hash, byte[] bytes) {
        CapeAssetHash.requireValid(hash,"盔甲内容 hash "); Objects.requireNonNull(bytes);
        if (bytes.length<1 || bytes.length>ArmorAsset.MAX_BYTES) return Result.INVALID_SIZE;
        if (!hash.equals(CapeAssetHash.sha256(bytes))) return Result.HASH_MISMATCH;
        var validation=dev.zbw3790.fashion.asset.BoundedPngValidator.validate(bytes,64,32,ArmorAsset.MAX_BYTES,true);
        if (validation==dev.zbw3790.fashion.asset.BoundedPngValidator.Result.INVALID_DIMENSIONS) return Result.INVALID_DIMENSIONS;
        if (validation!=dev.zbw3790.fashion.asset.BoundedPngValidator.Result.VALID) return Result.INVALID_PNG;
        if (!assets.containsKey(hash) && assets.size()>=ArmorRegistrySnapshot.MAX_ASSETS) return Result.LIMIT;
        assets.put(hash,bytes.clone()); return Result.STORED;
    }
    public Optional<byte[]> find(String hash) { var bytes=assets.get(CapeAssetHash.requireValid(hash,"盔甲内容 hash ")); return bytes==null?Optional.empty():Optional.of(bytes.clone()); }
    public boolean contains(String hash) { return assets.containsKey(hash); }
    public int size() { return assets.size(); }
    public void retain(Set<String> required) { assets.keySet().retainAll(required); }
    public void clear() { assets.clear(); }
}
