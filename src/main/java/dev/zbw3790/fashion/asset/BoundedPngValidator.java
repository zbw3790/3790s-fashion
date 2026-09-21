package dev.zbw3790.fashion.asset;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class BoundedPngValidator {
	private static final byte[] SIGNATURE = {(byte)137,80,78,71,13,10,26,10};
	private BoundedPngValidator() {}
	public enum Result { VALID, TOO_LARGE, INVALID_PNG, INVALID_DIMENSIONS }

	public static Result validate(byte[] bytes, int width, int height, int maximum, boolean binaryRgba) {
		Objects.requireNonNull(bytes, "PNG 字节不能为 null。");
		if (bytes.length > maximum) return Result.TOO_LARGE;
		if (bytes.length < 33 || !Arrays.equals(SIGNATURE, Arrays.copyOf(bytes, 8))) return Result.INVALID_PNG;
		ByteBuffer data = ByteBuffer.wrap(bytes);
		if (data.getInt(8) != 13 || data.getInt(12) != 0x49484452) return Result.INVALID_PNG;
		if (data.getInt(16) != width || data.getInt(20) != height) return Result.INVALID_DIMENSIONS;
		if (binaryRgba && (bytes[24]!=8 || bytes[25]!=6 || bytes[26]!=0 || bytes[27]!=0 || bytes[28]!=0)) return Result.INVALID_PNG;
        // 先检查有界块结构与 CRC，再交解码器；不会为伪造超大尺寸分配图像。
		boolean imageData = false;
		boolean ended = false;
		int offset = 8;
		while (offset < bytes.length) {
			if (bytes.length - offset < 12) return Result.INVALID_PNG;
			int length = data.getInt(offset);
			if (length < 0 || length > bytes.length - offset - 12) return Result.INVALID_PNG;
			int type = data.getInt(offset + 4);
			if (offset != 8 && type == 0x49484452) return Result.INVALID_PNG;
			CRC32 crc = new CRC32(); crc.update(bytes, offset + 4, length + 4);
			if ((int)crc.getValue() != data.getInt(offset + 8 + length)) return Result.INVALID_PNG;
			if (type == 0x49444154) imageData = true;
			if (type == 0x49454e44) {
				if (length != 0 || !imageData || offset + 12 != bytes.length) return Result.INVALID_PNG;
				ended = true;
			}
			offset += length + 12;
		}
		if (!ended) return Result.INVALID_PNG;
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			BufferedImage image = ImageIO.read(input);
			if (image == null) return Result.INVALID_PNG;
			try {
				if (binaryRgba) for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
                    int alpha=image.getRGB(x,y) >>> 24;
                    if (alpha!=0 && alpha!=255) return Result.INVALID_PNG;
                }
                return image.getWidth() == width && image.getHeight() == height
						? Result.VALID : Result.INVALID_DIMENSIONS;
			} finally { image.flush(); }
		} catch (IOException | RuntimeException exception) { return Result.INVALID_PNG; }
	}
}
