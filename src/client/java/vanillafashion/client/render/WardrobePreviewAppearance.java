package vanillafashion.client.render;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public record WardrobePreviewAppearance(
		Mode mode,
		Optional<Identifier> capeTexture,
		ElytraTextureDecision elytraDecision
) {
	private static final WardrobePreviewAppearance VANILLA = new WardrobePreviewAppearance(
			Mode.VANILLA,
			Optional.empty(),
			ElytraTextureDecision.passThrough()
	);

	public WardrobePreviewAppearance {
		Objects.requireNonNull(mode, "衣柜预览外观模式不能为空。");
		capeTexture = Objects.requireNonNull(capeTexture, "衣柜预览 Cape 纹理状态不能为空。");
		Objects.requireNonNull(elytraDecision, "衣柜预览 Elytra 纹理决策不能为空。");

		if (mode == Mode.VANILLA
				&& (!capeTexture.isEmpty()
				|| elytraDecision.mode() != ElytraTextureDecision.Mode.PASS_THROUGH)) {
			throw new IllegalArgumentException("VANILLA 预览不能携带服务器纹理覆盖。");
		}

		if (mode == Mode.SERVER_COSMETIC
				&& elytraDecision.mode() == ElytraTextureDecision.Mode.PASS_THROUGH) {
			throw new IllegalArgumentException("SERVER_COSMETIC 预览必须冻结 Elytra 回退决策。");
		}
	}

	public static WardrobePreviewAppearance vanilla() {
		return VANILLA;
	}

	public static WardrobePreviewAppearance serverCosmetic(
			Optional<Identifier> capeTexture,
			ElytraTextureDecision elytraDecision
	) {
		return new WardrobePreviewAppearance(Mode.SERVER_COSMETIC, capeTexture, elytraDecision);
	}

	public boolean suppressesVanillaCape() {
		return mode == Mode.SERVER_COSMETIC;
	}

	public enum Mode {
		VANILLA,
		SERVER_COSMETIC
	}
}
