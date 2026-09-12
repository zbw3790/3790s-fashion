package dev.zbw3790.fashion.cape;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import javax.imageio.ImageIO;

public final class CapePngValidator {
	private static final byte[] PNG_SIGNATURE = {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

	private CapePngValidator() {
	}

	public static ValidationResult validate(byte[] pngBytes) {
		Objects.requireNonNull(pngBytes, "PNG 字节不能为 null。");

		if (pngBytes.length < PNG_SIGNATURE.length
				|| !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(pngBytes, PNG_SIGNATURE.length))) {
			return ValidationResult.INVALID_PNG;
		}

		BufferedImage image;

		try (ByteArrayInputStream input = new ByteArrayInputStream(pngBytes)) {
			image = ImageIO.read(input);
		} catch (IOException | RuntimeException exception) {
			return ValidationResult.INVALID_PNG;
		}

		if (image == null) {
			return ValidationResult.INVALID_PNG;
		}

		if (image.getWidth() != CapeTextureAsset.REQUIRED_WIDTH
				|| image.getHeight() != CapeTextureAsset.REQUIRED_HEIGHT) {
			return ValidationResult.INVALID_DIMENSIONS;
		}

		return ValidationResult.VALID;
	}

	public enum ValidationResult {
		VALID,
		INVALID_PNG,
		INVALID_DIMENSIONS
	}
}
