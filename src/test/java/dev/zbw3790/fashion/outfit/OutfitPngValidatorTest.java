package dev.zbw3790.fashion.outfit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapePngValidator;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.outfit.OutfitPngValidator.Result.*;
import static dev.zbw3790.fashion.outfit.OutfitTestSupport.*;

class OutfitPngValidatorTest {
	@ParameterizedTest @CsvSource({"64,32","63,64","64,65"})
	void rejectsOtherDimensions(int width, int height) throws Exception {
		assertEquals(INVALID_DIMENSIONS, OutfitPngValidator.validate(png(width, height, 0xff224466)));
	}
	@ParameterizedTest @ValueSource(ints={0,96,128,255})
	void acceptsFullAlphaRangeAndColoredBaseWithoutChangingBytes(int alpha) throws Exception {
		byte[] bytes = png(64,64,(alpha << 24) | 0x00ff00ff), original = bytes.clone();
		assertEquals(VALID, OutfitPngValidator.validate(bytes));
		assertArrayEquals(original, bytes);
		assertArrayEquals(original, OutfitAsset.fromBytes(bytes).bytes());
	}
	@Test void acceptsRgbWithoutAlpha() throws Exception {
		BufferedImage image = new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			assertTrue(ImageIO.write(image,"png",out));
			assertEquals(VALID, OutfitPngValidator.validate(out.toByteArray()));
		} finally { image.flush(); }
	}
	@Test void exactByteLimitAndOneOver() throws Exception {
		assertEquals(VALID, OutfitPngValidator.validate(paddedPng(png(),65536)));
		assertEquals(TOO_LARGE, OutfitPngValidator.validate(paddedPng(png(),65537)));
	}
	@Test void rejectsMalformedTruncatedCrcAndTrailingData() throws Exception {
		byte[] valid = png();
		assertEquals(INVALID_PNG, OutfitPngValidator.validate(new byte[40]));
		assertEquals(INVALID_PNG, OutfitPngValidator.validate(Arrays.copyOf(valid,valid.length - 1)));
		assertEquals(INVALID_PNG, OutfitPngValidator.validate(Arrays.copyOf(valid,valid.length + 1)));
		byte[] bad = valid.clone(); bad[29] ^= 1;
		assertEquals(INVALID_PNG, OutfitPngValidator.validate(bad));
		bad = valid.clone(); ByteBuffer.wrap(bad).putInt(33,Integer.MAX_VALUE);
		assertEquals(INVALID_PNG, OutfitPngValidator.validate(bad));
		bad = valid.clone(); ByteBuffer.wrap(bad).putInt(16,Integer.MAX_VALUE);
		assertEquals(INVALID_DIMENSIONS, OutfitPngValidator.validate(bad));
	}
	@Test void hashAndAssetOwnOriginalImmutableBytes() throws Exception {
		byte[] input = png(), original = input.clone();
		OutfitAsset asset = OutfitAsset.fromBytes(input);
		assertEquals(CapeAssetHash.sha256(original),asset.sha256());
		assertTrue(asset.sha256().matches("[0-9a-f]{64}"));
		assertEquals(original.length, asset.size());
		input[0] = 0; byte[] returned = asset.bytes(); returned[1] = 0;
		assertArrayEquals(original, asset.bytes());
		assertEquals(asset, OutfitAsset.fromBytes(original));
		assertThrows(IllegalArgumentException.class, () -> OutfitAsset.fromBytes(input));
	}
	@Test void capeDimensionsRemainStrictlyIndependent() throws Exception {
		assertEquals(CapePngValidator.ValidationResult.VALID,CapePngValidator.validate(png(64,32,0xff123456)));
		assertEquals(CapePngValidator.ValidationResult.INVALID_DIMENSIONS,CapePngValidator.validate(png()));
		assertEquals(INVALID_DIMENSIONS,OutfitPngValidator.validate(png(64,32,0xff123456)));
	}
}
