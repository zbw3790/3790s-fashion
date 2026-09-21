package dev.zbw3790.fashion.client.render.armor;

import java.util.*;
import dev.zbw3790.fashion.client.armor.ClientArmorResources;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;

/** 只消费本帧不可变外观，不保存或替换任何装备。 */
public final class ArmorRendering {
    private static final RenderStateDataKey<ArmorSelections> KEY = RenderStateDataKey.create(() -> "fashion_3790:armor_visibility");
    private static final RenderStateDataKey<Map<ArmorSlot,ClientArmorResources.Ready>> TEXTURES=RenderStateDataKey.create(()->"fashion_3790:armor_textures");
    private static ClientArmorResources resources;
    private static final Set<String> warned=new HashSet<>();
    private ArmorRendering() { }

    public static void register(ClientPlayerFashionRegistry authority,ClientArmorResources source) {
        resources=Objects.requireNonNull(source);
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            for (var state : context.levelState().entityRenderStates) {
                if (!(state instanceof AvatarRenderState avatar)) continue;
                var entity = context.level().getEntity(avatar.id);
                ArmorSelections selections = entity instanceof Player player && player.getId() == avatar.id
                        ? authority.full().find(player.getUUID()).map(value -> value.effective().armor()).orElse(ArmorSelections.original())
                        : ArmorSelections.original();
                attach(avatar, selections);
            }
        });
    }

    /** 调用者已经完成本次实际玩家／独立 GUI 场景验证；失败时明确写入 Original。 */
    public static void attach(AvatarRenderState state, ArmorSelections selections) {
        ((FabricRenderState) state).setData(KEY, Objects.requireNonNull(selections));
        var textures=new EnumMap<ArmorSlot,ClientArmorResources.Ready>(ArmorSlot.class);
        if(resources!=null) for(var slot:ArmorSlot.CANONICAL_ORDER) if(selections.get(slot) instanceof ArmorSelection.Custom custom)
            resources.resolve(custom.id(),slot).ifPresent(ready->textures.put(slot,ready));
        ((FabricRenderState)state).setData(TEXTURES,Map.copyOf(textures));
    }

    public static boolean hidden(HumanoidRenderState state, EquipmentSlot slot) {
        if (!(state instanceof AvatarRenderState)) return false;
        ArmorSelections selections = ((FabricRenderState) state).getData(KEY);
        if (selections == null) return false;
        ArmorSlot visual = visualSlot(slot);
        return visual != null && selections.get(visual) == ArmorSelection.HIDDEN;
    }
    public static ArmorSlot visualSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorSlot.HEAD;
            case CHEST -> ArmorSlot.CHEST;
            case LEGS -> ArmorSlot.LEGS;
            case FEET -> ArmorSlot.FEET;
            default -> null;
        };
    }
    public static Optional<ClientArmorResources.Ready> ready(HumanoidRenderState state,EquipmentSlot slot) {
        if(!(state instanceof AvatarRenderState)) return Optional.empty();
        var textures=((FabricRenderState)state).getData(TEXTURES);var visual=visualSlot(slot);
        return textures==null || visual==null?Optional.empty():Optional.ofNullable(textures.get(visual));
    }
    public static Identifier replacement(EquipmentLayerRenderer renderer,HumanoidRenderState state,EquipmentSlot slot,
            EquipmentClientInfo.LayerType type,ResourceKey<EquipmentAsset> asset) {
        var ready=ready(state,slot);
        if(ready.isEmpty()) return null;
        if(!(renderer instanceof ArmorEquipmentAccess access) || !ArmorLayerPolicy.supported(access.fashion3790$equipment(asset),type)) {
            String reason=asset.identifier()+"/"+type;
            if(warned.size()<16 && warned.add(reason)) org.slf4j.LoggerFactory.getLogger("fashion_3790/client").warn("盔甲装备层结构不支持 CUSTOM，整槽回退原版：{}。",reason);
            return null;
        }
        var expected=slot==EquipmentSlot.LEGS?EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS:EquipmentClientInfo.LayerType.HUMANOID;
        return type==expected?ready.orElseThrow().texture():null;
    }
}
