package dev.zbw3790.fashion.outfit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

final class OutfitTestSupport {
	private OutfitTestSupport() {}
	static byte[] png(int width, int height, int argb) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, "png", output)) throw new IOException("测试 PNG 编码器不可用。");
			return output.toByteArray();
		} finally { image.flush(); }
	}
	static byte[] png() throws IOException { return png(64, 64, 0x60ff00ff); }
	static String metadata(String parts, String models) {
		return "{\"schema_version\":1,\"parts\":[" + parts + "],\"models\":[" + models + "]}";
	}
	static String metadata(String models) { return metadata("\"head\",\"body\"", models); }
	static Path directory(Path root, String name, String models) throws IOException {
		Path directory = Files.createDirectories(root.resolve(name));
		Files.writeString(directory.resolve("outfit.json"), metadata(models), StandardCharsets.UTF_8);
		return directory;
	}
	static byte[] paddedPng(byte[] png, int length) {
		int padding = length - png.length - 12;
		if (padding < 0) throw new IllegalArgumentException("测试填充长度不足。");
		ByteBuffer output = ByteBuffer.allocate(length);
		output.put(png, 0, png.length - 12);
		output.putInt(padding).putInt(0x76705467); // 私有辅助块，保持图像解码内容不变。
		output.put(new byte[padding]);
		CRC32 crc = new CRC32(); crc.update(output.array(), png.length - 8, padding + 4);
		output.putInt((int)crc.getValue()).put(png, png.length - 12, 12);
		return output.array();
	}
}
