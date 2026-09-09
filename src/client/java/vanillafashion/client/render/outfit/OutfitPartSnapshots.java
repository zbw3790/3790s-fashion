package vanillafashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import vanillafashion.outfit.OutfitModel;
import vanillafashion.outfit.OutfitPart;

/** 几何只来自独立 bake；排队树不引用原 Renderer 的可变部件。 */
public final class OutfitPartSnapshots {
	private final PlayerModel template;
	private final PlayerModel animator;
	private final Map<String, List<ModelPart.Cube>> geometry;
	private final String signature;
	public final OutfitModel modelType;

	public OutfitPartSnapshots(EntityModelSet models, OutfitModel type) {
		modelType = type;
		var layer = type == OutfitModel.SLIM ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER;
		template = new PlayerModel(models.bakeLayer(layer), type == OutfitModel.SLIM);
		animator = new PlayerModel(models.bakeLayer(layer), type == OutfitModel.SLIM);
		geometry = cubes(template.root());
		signature = geometrySignature(template.root());
	}

	public static String baseName(OutfitPart part) { return part.serializedName(); }
	public static String outerName(OutfitPart part) {
		return switch (part) {
			case HEAD -> "hat"; case BODY -> "jacket";
			case LEFT_ARM -> "left_sleeve"; case RIGHT_ARM -> "right_sleeve";
			case LEFT_LEG -> "left_pants"; case RIGHT_LEG -> "right_pants";
		};
	}

	public boolean matches(PlayerModel source) {
		if (source == null || source.getClass() != PlayerModel.class) return false;
		if (source.root().getAllParts().size() != template.root().getAllParts().size()) return false;
		for (OutfitPart part : OutfitPart.values()) {
			if (!source.root().hasChild(baseName(part))
					|| !source.root().getChild(baseName(part)).hasChild(outerName(part))) return false;
		}
		return signature.equals(geometrySignature(source.root()));
	}

	public static String geometrySignature(ModelPart root) {
		StringBuilder value = new StringBuilder();
		cubes(root).forEach((path, entries) -> {
			value.append(path).append(':');
			for (ModelPart.Cube cube : entries) {
				value.append(List.of(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ));
				for (var polygon : cube.polygons) {
					value.append(polygon.normal());
					for (var vertex : polygon.vertices()) value.append(vertex);
				}
			}
		});
		return value.toString();
	}

	private static Map<String, List<ModelPart.Cube>> cubes(ModelPart root) {
		Map<String, List<ModelPart.Cube>> result = new TreeMap<>();
		// visit 不按 visible/skipDraw 过滤，几何核验不会因当前动画隐藏而失真。
		root.visit(new PoseStack(), (pose, path, index, cube) ->
				result.computeIfAbsent(path, ignored -> new ArrayList<>()).add(cube));
		result.replaceAll((path, entries) -> List.copyOf(entries));
		return result;
	}

	public static void copyValues(ModelPart from, ModelPart to) {
		to.x = from.x; to.y = from.y; to.z = from.z;
		to.xRot = from.xRot; to.yRot = from.yRot; to.zRot = from.zRot;
		to.xScale = from.xScale; to.yScale = from.yScale; to.zScale = from.zScale;
		to.visible = from.visible; to.skipDraw = from.skipDraw;
	}

	public static PoseStack copyPose(PoseStack from) {
		PoseStack copy = new PoseStack();
		copy.last().set(from.last().copy());
		return copy;
	}

	private ModelPart node(String path, ModelPart pose, boolean draw, Map<String, ModelPart> children) {
		ModelPart result = new ModelPart(draw ? geometry.getOrDefault(path, List.of()) : List.of(), children);
		copyValues(pose, result);
		return result;
	}

	public ModelPart hand(PlayerModel source, OutfitPart part, boolean base, boolean outer) {
		if (part != OutfitPart.LEFT_ARM && part != OutfitPart.RIGHT_ARM) throw new IllegalArgumentException("需要手臂部位。");
		ModelPart arm = source.root().getChild(baseName(part));
		String path = "/" + baseName(part);
		Map<String, ModelPart> children = outer ? Map.of(outerName(part),
				node(path + "/" + outerName(part), arm.getChild(outerName(part)), true, Map.of())) : Map.of();
		return node(path, arm, base, children);
	}

	public ModelPart bodyPart(PlayerModel source, OutfitPart part, boolean base, boolean outer) {
		return copyBodyTree(source.root(), part, base, outer);
	}

	ModelPart copyBodyTree(ModelPart root, OutfitPart part, boolean base, boolean outer) {
		ModelPart parent = root.getChild(baseName(part));
		String path = "/" + baseName(part);
		Map<String, ModelPart> children = outer ? Map.of(outerName(part),
				node(path + "/" + outerName(part), parent.getChild(outerName(part)), true, Map.of())) : Map.of();
		return node("", root, false, Map.of(baseName(part), node(path, parent, base, children)));
	}

	/** animator 只在本模块同步使用；调用者必须立刻冻结，绝不将其排队。 */
	PlayerModel animate(AvatarRenderState state) {
		for (ModelPart part : animator.allParts()) { part.resetPose(); part.visible = true; part.skipDraw = false; }
		animator.setupAnim(state);
		return animator;
	}
}
