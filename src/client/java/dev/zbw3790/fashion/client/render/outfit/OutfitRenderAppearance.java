package dev.zbw3790.fashion.client.render.outfit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import dev.zbw3790.fashion.outfit.*;

/** 已解析的单玩家、单场景外观投影；不承担权威状态、存档或纹理生命周期。 */
public record OutfitRenderAppearance(UUID player, OutfitSelections selections, OutfitModel model,
        Map<OutfitId, ResolvedAsset> assets, Map<OutfitPart, Boolean> originalVisibility, Scene scene) {
    public enum Scene { WORLD, WARDROBE_PREVIEW, FIRST_PERSON }

    /** metadata 可信而纹理缺失仍保留声明；未知 ID 则不出现在资产映射中。 */
    public record ResolvedAsset(OutfitMetadata metadata, OutfitModel model, Optional<Identifier> texture) {
        public ResolvedAsset {
            Objects.requireNonNull(metadata, "解析资产需要可信 metadata。");
            Objects.requireNonNull(model, "解析资产需要模型类型。");
            Objects.requireNonNull(texture, "纹理就绪状态不能为 null。");
            if (!metadata.models().contains(model)) throw new IllegalArgumentException("解析资产模型必须已声明。");
        }
    }

    public OutfitRenderAppearance {
        Objects.requireNonNull(player, "外观投影必须属于玩家。");
        Objects.requireNonNull(selections, "外观投影必须有完整六部位选择。");
        Objects.requireNonNull(model, "外观投影必须有实际玩家模型。");
        Objects.requireNonNull(scene, "外观投影必须指定场景。");
        assets = Map.copyOf(assets);
        originalVisibility = copyVisibility(originalVisibility);
    }
    public Optional<ResolvedAsset> asset(OutfitPart part) {
        return selections.get(part) instanceof OutfitPartSelection.Outfit selected
                ? Optional.ofNullable(assets.get(selected.id())) : Optional.empty();
    }
    public static Map<OutfitPart, Boolean> copyVisibility(Map<OutfitPart, Boolean> visibility) {
        if (!Objects.requireNonNull(visibility).keySet().equals(OutfitPart.ALL))
            throw new IllegalArgumentException("原始外层开关必须完整包含六部位。");
        EnumMap<OutfitPart, Boolean> copy = new EnumMap<>(OutfitPart.class);
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) copy.put(part, Objects.requireNonNull(visibility.get(part)));
        return Collections.unmodifiableMap(copy);
    }
    public static OutfitModel modelOf(PlayerModelType model) {
        return switch (model) { case WIDE -> OutfitModel.WIDE; case SLIM -> OutfitModel.SLIM; };
    }
}
