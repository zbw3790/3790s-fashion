package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** 本帧世界外观，不持有玩家实体、UUID 查询器或可变 Registry。 */
public record PlayerFashionRenderAppearance(Mode mode, Optional<Identifier> capeTexture,
		ElytraTextureDecision elytraDecision) {
	public PlayerFashionRenderAppearance {
		Objects.requireNonNull(mode, "外观模式不能为 null。");
		Objects.requireNonNull(capeTexture, "披风纹理状态不能为 null。");
		Objects.requireNonNull(elytraDecision, "Elytra 决策不能为 null。");
		if (mode != Mode.SERVER_COSMETIC
				&& (capeTexture.isPresent() || elytraDecision.mode() != ElytraTextureDecision.Mode.PASS_THROUGH)) {
			throw new IllegalArgumentException("未知或 Vanilla 外观不能覆盖原版纹理。");
		}
		if (mode == Mode.SERVER_COSMETIC && elytraDecision.mode() == ElytraTextureDecision.Mode.PASS_THROUGH) {
			throw new IllegalArgumentException("服务器时装必须明确决定 Elytra 默认或自定义纹理。");
		}
	}

	public static PlayerFashionRenderAppearance unknown() {
		return new PlayerFashionRenderAppearance(Mode.UNKNOWN, Optional.empty(), ElytraTextureDecision.passThrough());
	}

	public static PlayerFashionRenderAppearance vanilla() {
		return new PlayerFashionRenderAppearance(Mode.VANILLA, Optional.empty(), ElytraTextureDecision.passThrough());
	}

	public static PlayerFashionRenderAppearance serverCosmetic(Optional<Identifier> cape, ElytraTextureDecision elytra) {
		return new PlayerFashionRenderAppearance(Mode.SERVER_COSMETIC, cape, elytra);
	}

	public boolean suppressesVanillaCape() {
		return mode == Mode.SERVER_COSMETIC;
	}

	public enum Mode {
		UNKNOWN,
		VANILLA,
		SERVER_COSMETIC
	}
}
