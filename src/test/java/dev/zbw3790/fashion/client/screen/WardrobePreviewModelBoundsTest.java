package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.client.render.WardrobePreviewTestSupport;

class WardrobePreviewModelBoundsTest {
	@BeforeAll
	static void bootstrap() {
		WardrobePreviewTestSupport.bootstrap();
	}

	@ParameterizedTest
	@ValueSource(ints = {200, 320})
	void standingPlayerAndActualWingVerticesFitEveryYawAndBoundedTilt(int screenWidth) {
		var layout = WardrobeLayout.calculate(screenWidth, 240, 9);
		var bounds = layout.previewModelBounds();
		for (boolean slim : new boolean[] {false, true}) {
			var player = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
					64, 64).bakeRoot(), slim);
			var wings = new ElytraModel(ElytraModel.createLayer().bakeRoot());
			for (float tilt : new float[] {-8.0F, 0.0F, 8.0F}) {
				var state = new AvatarRenderState();
				new WardrobePreviewConfiguration(WardrobePreviewMode.ELYTRA, tilt).apply(state, stack -> false);
				player.setupAnim(state);
				wings.setupAnim(state);
				var pose = new PoseStack();
				// 对应 LivingEntityRenderer 与 AvatarRenderer 的公开模型变换，不初始化 GPU。
				pose.scale(-0.9375F, -0.9375F, 0.9375F);
				pose.translate(0.0F, -1.501F, 0.0F);
				var playerVertices = new ArrayList<Vector3f>();
				player.root().getExtentsForGui(pose, point -> playerVertices.add(new Vector3f(point)));
				pose.translate(0.0F, 0.0F, 0.125F);
				var wingVertices = new ArrayList<Vector3f>();
				// 原版 API 遍历真实 Polygon 顶点，包含翼块的 CubeDeformation(1)。
				wings.root().getExtentsForGui(pose, point -> wingVertices.add(new Vector3f(point)));
				assertFalse(playerVertices.isEmpty());
				assertFalse(wingVertices.isEmpty());
				assertFitsAllRotations(playerVertices, bounds, layout.previewEntitySize());
				assertFitsAllRotations(wingVertices, bounds, layout.previewEntitySize());
				assertClearOfButton(playerVertices, layout, tilt);
				assertClearOfButton(wingVertices, layout, tilt);
			}
		}
	}

	private static void assertClearOfButton(List<Vector3f> vertices, WardrobeLayout layout, float tilt) {
		var bounds = layout.previewModelBounds();
		var button = layout.previewModeButtonBounds();
		double centerX = (bounds.x() + bounds.right()) / 2.0D;
		double centerY = (bounds.y() + bounds.bottom()) / 2.0D;
		double sin = Math.sin(Math.toRadians(tilt));
		double cos = Math.cos(Math.toRadians(tilt));
		// x-2y 分隔线区分头部上方的窄空间与下方翼宽；极值覆盖连续 yaw 及多边形内部。
		double buttonLimit = button.x() - centerX - 2.0D * (button.bottom() - centerY);
		for (var point : vertices) {
			double maximum = (Math.hypot(point.x(), point.z()) * Math.sqrt(1.0D + 4.0D * sin * sin)
					+ 2.0D * (point.y() * cos - 0.9625D)) * layout.previewEntitySize();
			assertTrue(maximum < buttonLimit,
					() -> "居中后的玩家或鞘翅无法与模式按钮分隔：" + point + "，投影上界=" + maximum
							+ "，按钮边界=" + buttonLimit);
		}
	}

	private static void assertFitsAllRotations(List<Vector3f> vertices, WardrobeLayout.Bounds bounds, int scale) {
		double sinLimit = Math.sin(Math.toRadians(8.0D));
		double cosLimit = Math.cos(Math.toRadians(8.0D));
		for (var point : vertices) {
			// x/z 径向上界覆盖连续的 360° yaw；独立组合倾角上界只会扩大安全范围。
			double radius = Math.hypot(point.x(), point.z());
			assertTrue(radius * scale < bounds.width() / 2.0D,
					() -> "玩家或鞘翅超出横向裁剪范围：" + point);
			double firstY = 0.9625D - point.y();
			double secondY = 0.9625D - point.y() * cosLimit;
			double minimumY = Math.min(firstY, secondY) - radius * sinLimit;
			double maximumY = Math.max(firstY, secondY) + radius * sinLimit;
			assertTrue(minimumY * scale > -bounds.height() / 2.0D,
					() -> "玩家或鞘翅超出顶部裁剪范围：" + point);
			assertTrue(maximumY * scale < bounds.height() / 2.0D,
					() -> "玩家或鞘翅超出底部裁剪范围：" + point);
		}
	}
}
