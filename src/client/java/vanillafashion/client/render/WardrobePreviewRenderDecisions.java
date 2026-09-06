package vanillafashion.client.render;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class WardrobePreviewRenderDecisions {
	private WardrobePreviewRenderDecisions() {
	}

	public static boolean allowVanillaCape(
			Optional<WardrobePreviewAppearance> previewAppearance,
			boolean normalDecision
	) {
		return Objects.requireNonNull(previewAppearance, "衣柜预览外观状态不能为空。")
				.map(appearance -> !appearance.suppressesVanillaCape())
				.orElse(normalDecision);
	}

	public static Optional<Identifier> capeTexture(
			Optional<WardrobePreviewAppearance> previewAppearance,
			Supplier<Optional<Identifier>> normalTexture
	) {
		Objects.requireNonNull(previewAppearance, "衣柜预览外观状态不能为空。");
		Objects.requireNonNull(normalTexture, "正常 Cape 纹理提供器不能为空。");

		if (previewAppearance.isPresent()) {
			WardrobePreviewAppearance appearance = previewAppearance.orElseThrow();
			return appearance.mode() == WardrobePreviewAppearance.Mode.SERVER_COSMETIC
					? appearance.capeTexture()
					: Optional.empty();
		}

		return Objects.requireNonNull(normalTexture.get(), "正常 Cape 纹理状态不能为空。");
	}

	public static ElytraTextureDecision elytraTexture(
			Optional<WardrobePreviewAppearance> previewAppearance,
			Supplier<ElytraTextureDecision> normalDecision
	) {
		Objects.requireNonNull(previewAppearance, "衣柜预览外观状态不能为空。");
		Objects.requireNonNull(normalDecision, "正常 Elytra 纹理决策提供器不能为空。");
		return previewAppearance
				.map(WardrobePreviewAppearance::elytraDecision)
				.orElseGet(() -> Objects.requireNonNull(normalDecision.get(), "正常 Elytra 纹理决策不能为空。"));
	}
}
