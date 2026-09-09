package vanillafashion.client.dev.spike;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import vanillafashion.client.render.outfit.OutfitAppearanceProvider;
import vanillafashion.client.render.outfit.OutfitRenderAppearance;
import vanillafashion.client.render.outfit.OutfitRenderAppearance.Scene;
import vanillafashion.outfit.*;

/** 仅开发命令适配器保存组合名；正式渲染层只收到 S01 领域值。 */
public final class OutfitSpikeInputs {
    private record Key(UUID player, Scene scene) { }
    private record Profile(OutfitModel model, String role) { }
    private final Map<Key, String> assignments = new HashMap<>();
    private Object connection;
    public void connection(Object current) {
        if (connection != current) { assignments.clear(); connection = current; }
    }
    public void assign(Object current, UUID player, Scene scene, String profile) {
        parse(profile);
        connection(current);
        if (current == null) throw new IllegalStateException("实验输入需要当前客户端连接。");
        if (scene == Scene.FIRST_PERSON) throw new IllegalArgumentException("第一人称实验沿用世界分配。");
        assignments.put(new Key(player, scene), profile);
    }
    public Optional<OutfitRenderAppearance> resolve(OutfitAppearanceProvider.Context context, Map<String, Identifier> textures) {
        connection(context.connection());
        if (connection == null) return Optional.empty();
        Scene assignedScene = context.scene() == Scene.FIRST_PERSON ? Scene.WORLD : context.scene();
        String profile = assignments.get(new Key(context.player(), assignedScene));
        return profile == null ? Optional.empty() : Optional.of(profile(context, profile, textures));
    }
    public static OutfitRenderAppearance profile(OutfitAppearanceProvider.Context context, String name, Map<String, Identifier> textures) {
        Profile profile = parse(name);
        String role = profile.role();
        OutfitModel model = profile.model();
        if (role.equals("mismatch")) model = model == OutfitModel.WIDE ? OutfitModel.SLIM : OutfitModel.WIDE;
        Set<OutfitPart> provided = switch (role) {
            case "head" -> Set.of(OutfitPart.HEAD);
            case "upper" -> OutfitGroup.UPPER_GROUP.parts();
            case "left" -> Set.of(OutfitPart.LEFT_ARM);
            default -> OutfitPart.ALL;
        };
        OutfitSelections selections = OutfitSelections.original();
        Map<OutfitId, OutfitRenderAppearance.ResolvedAsset> assets = Map.of();
        if (role.equals("none")) selections = selections.allNone().selections();
        else if (!role.equals("original")) {
            OutfitId id = new OutfitId(name);
            selections = selections.applyOutfit(OutfitPart.ALL, id, provided).selections();
            Optional<Identifier> texture = role.equals("unavailable") ? Optional.empty() : Optional.ofNullable(textures.get(name));
            assets = Map.of(id, new OutfitRenderAppearance.ResolvedAsset(new OutfitMetadata(provided, Set.of(model)), model, texture));
        }
        return new OutfitRenderAppearance(context.player(), selections, context.model(), assets, context.originalVisibility(), context.scene());
    }
    private static Profile parse(String profile) {
        String[] segments = profile.split("-", 2);
        if (segments.length != 2) throw new IllegalArgumentException("实验名称必须以 wide- 或 slim- 开头。");
        OutfitModel model = OutfitModel.fromName(segments[0]);
        if (!Set.of("all", "head", "upper", "left", "transparent", "alpha", "original", "none",
                "unavailable", "mismatch").contains(segments[1])) throw new IllegalArgumentException("未知的实验组合。");
        return new Profile(model, segments[1]);
    }
}
