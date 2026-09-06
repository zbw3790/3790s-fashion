package vanillafashion.cape;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

final class CapeTestAssets {
	private CapeTestAssets() {
	}

	static Path createCosmeticDirectory(Path root, String id) throws IOException {
		return Files.createDirectory(root.resolve(id));
	}

	static Path writePng(
			Path path,
			int width,
			int height,
			int imageType,
			int color
	) throws IOException {
		return writeImage(path, "PNG", width, height, imageType, color);
	}

	static Path writeImage(
			Path path,
			String format,
			int width,
			int height,
			int imageType,
			int color
	) throws IOException {
		BufferedImage image = new BufferedImage(width, height, imageType);
		image.setRGB(0, 0, color);

		if (!ImageIO.write(image, format, path.toFile())) {
			throw new IOException("测试环境无法写入 " + format + " 图片。");
		}

		return path;
	}
}
