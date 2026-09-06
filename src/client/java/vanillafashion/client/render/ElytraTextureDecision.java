package vanillafashion.client.render;

import java.util.Objects;
import net.minecraft.resources.Identifier;

public record ElytraTextureDecision(Mode mode, Identifier texture) {
	private static final ElytraTextureDecision PASS_THROUGH =
			new ElytraTextureDecision(Mode.PASS_THROUGH, null);
	private static final ElytraTextureDecision VANILLA_DEFAULT =
			new ElytraTextureDecision(Mode.VANILLA_DEFAULT, null);

	public ElytraTextureDecision {
		Objects.requireNonNull(mode, "Elytra 纹理决策模式不能为空。");

		if (mode == Mode.CUSTOM_TEXTURE && texture == null) {
			throw new IllegalArgumentException("CUSTOM_TEXTURE 必须提供纹理 Identifier。");
		}

		if (mode != Mode.CUSTOM_TEXTURE && texture != null) {
			throw new IllegalArgumentException("只有 CUSTOM_TEXTURE 可以携带纹理 Identifier。");
		}
	}

	public static ElytraTextureDecision passThrough() {
		return PASS_THROUGH;
	}

	public static ElytraTextureDecision customTexture(Identifier texture) {
		return new ElytraTextureDecision(
				Mode.CUSTOM_TEXTURE,
				Objects.requireNonNull(texture, "自定义 Elytra 纹理 Identifier 不能为空。")
		);
	}

	public static ElytraTextureDecision vanillaDefault() {
		return VANILLA_DEFAULT;
	}

	public enum Mode {
		PASS_THROUGH,
		CUSTOM_TEXTURE,
		VANILLA_DEFAULT
	}
}
