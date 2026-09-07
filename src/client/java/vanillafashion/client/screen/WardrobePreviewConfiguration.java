package vanillafashion.client.screen;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

record WardrobePreviewConfiguration(WardrobePreviewMode mode, float verticalTiltDegrees) {
	static final float MOUSE_ANGLE_DIVISOR = 40.0F;
	static final float VERTICAL_GAIN_DEGREES = 5.0F;
	static final float MAX_VERTICAL_TILT_DEGREES = 8.0F;
	static final float STANDING_WING_ANGLE_RADIANS = (float) Math.PI / 12.0F;
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0D);

	WardrobePreviewConfiguration {
		Objects.requireNonNull(mode, "衣柜预览模式不能为空。");
		if (!Float.isFinite(verticalTiltDegrees)) {
			throw new IllegalArgumentException("衣柜预览倾角必须为有限值。");
		}
		verticalTiltDegrees = Math.clamp(verticalTiltDegrees,
				-MAX_VERTICAL_TILT_DEGREES, MAX_VERTICAL_TILT_DEGREES);
	}

	static WardrobePreviewConfiguration fromMouse(WardrobePreviewMode mode, float centerY, float mouseY) {
		float angle = (float) Math.atan((centerY - mouseY) / MOUSE_ANGLE_DIVISOR);
		return new WardrobePreviewConfiguration(mode, angle * VERTICAL_GAIN_DEGREES);
	}

	Quaternionf cameraOrientation() {
		return new Quaternionf().rotateX(verticalTiltDegrees * DEGREES_TO_RADIANS);
	}

	void apply(AvatarRenderState state, Predicate<ItemStack> hasWings) {
		Objects.requireNonNull(state, "预览专用玩家状态不能为空。");
		Objects.requireNonNull(hasWings, "预览装备层查询不能为空。");
		// 调用方每次通过 createRenderState 创建独立状态；不修改实体取得的 ItemStack。
		state.chestEquipment = mode == WardrobePreviewMode.ELYTRA
				? new ItemStack(Items.ELYTRA)
				: hasWings.test(state.chestEquipment) ? ItemStack.EMPTY : state.chestEquipment.copy();
		state.xRot = -verticalTiltDegrees;

		if (mode == WardrobePreviewMode.ELYTRA) {
			// 直接冻结本次 GUI 状态的原版站姿，不触碰玩家的 Pose 或 ElytraAnimationState。
			state.pose = Pose.STANDING;
			state.isCrouching = false;
			state.isFallFlying = false;
			state.isVisuallySwimming = false;
			state.isPassenger = false;
			state.isAutoSpinAttack = false;
			state.swimAmount = 0.0F;
			state.fallFlyingTimeInTicks = 0.0F;
			state.shouldApplyFlyingYRot = false;
			state.flyingYRot = 0.0F;
			state.walkAnimationPos = 0.0F;
			state.walkAnimationSpeed = 0.0F;
			state.bedOrientation = null;
			state.elytraRotX = STANDING_WING_ANGLE_RADIANS;
			state.elytraRotY = 0.0F;
			state.elytraRotZ = -STANDING_WING_ANGLE_RADIANS;
		}
	}
}
