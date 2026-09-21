package dev.zbw3790.fashion.armor;

import dev.zbw3790.fashion.asset.BoundedPngValidator;
import dev.zbw3790.fashion.cape.CapeAssetHash;

/** 校验后的不可变内容，外部只能取得副本。 */
public final class ArmorAsset {
    public static final int WIDTH=64, HEIGHT=32, MAX_BYTES=16384;
    private final String hash;
    private final byte[] bytes;
    private ArmorAsset(String hash,byte[] bytes) { this.hash=hash;this.bytes=bytes; }
    public static ArmorAsset fromBytes(byte[] input) {
        byte[] bytes=input.clone();
        if(BoundedPngValidator.validate(bytes,WIDTH,HEIGHT,MAX_BYTES,true)!=BoundedPngValidator.Result.VALID) throw new IllegalArgumentException("盔甲 PNG 不符合尺寸、RGBA、Alpha 或完整性要求。");
        return new ArmorAsset(CapeAssetHash.sha256(bytes),bytes);
    }
    public static ArmorAsset verified(String hash,byte[] bytes) {
        CapeAssetHash.requireValid(hash,"盔甲内容 hash ");
        var asset=fromBytes(bytes);
        if(!asset.hash.equals(hash)) throw new IllegalArgumentException("盔甲内容 hash 不符。");
        return asset;
    }
    public String hash() { return hash; }
    public byte[] bytes() { return bytes.clone(); }
    public int size() { return bytes.length; }
}
