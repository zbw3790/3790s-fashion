package dev.zbw3790.fashion.outfit;

import dev.zbw3790.fashion.asset.BoundedPngValidator;

/** 沿用原 Outfit 尺寸和颜色政策，共用有界块、CRC 与解码校验。 */
public final class OutfitPngValidator {
    public static final int WIDTH=64, HEIGHT=64, MAX_ASSET_BYTES=65536;
    private OutfitPngValidator() { }
    public enum Result { VALID, TOO_LARGE, INVALID_PNG, INVALID_DIMENSIONS }
    public static Result validate(byte[] bytes) {
        return Result.valueOf(BoundedPngValidator.validate(bytes,WIDTH,HEIGHT,MAX_ASSET_BYTES,false).name());
    }
}
