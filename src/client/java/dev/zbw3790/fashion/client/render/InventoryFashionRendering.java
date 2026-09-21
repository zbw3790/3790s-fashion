package dev.zbw3790.fashion.client.render;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import dev.zbw3790.fashion.armor.ArmorSelections;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.render.armor.ArmorRendering;
import dev.zbw3790.fashion.client.render.outfit.OutfitRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** 原版物品栏显式实体来源；只读当前连接已应用状态，没有衣柜草稿或跨帧人物缓存。 */
public final class InventoryFashionRendering {
    private static ClientPlayerFashionRegistry authority;
    private static PlayerFashionAppearanceResolver appearances;
    private InventoryFashionRendering() { }
    public static void register(ClientPlayerFashionRegistry registry, PlayerFashionAppearanceResolver resolver) {
        authority=Objects.requireNonNull(registry);appearances=Objects.requireNonNull(resolver);
    }
    public static void extracted(LivingEntity source, EntityRenderState extracted) {
        if (!(extracted instanceof AvatarRenderState state)) return;
        var client=Minecraft.getInstance();
        Optional<UUID> player=trustedPlayer(source,client.level,client.getConnection(),
                authority==null?null:authority.connectionIdentity());
        PlayerFashionRenderState.attach(state, player.filter(id->appearances!=null)
                .map(id->appearances.resolve(id)).orElseGet(PlayerFashionRenderAppearance::unknown));
        ArmorRendering.attach(state, player.flatMap(id->authority.fullAuthority(id))
                .map(value->value.effective().armor()).orElseGet(ArmorSelections::original));
        OutfitRendering.prepareInventory(state,player);
    }
    static Optional<UUID> trustedPlayer(LivingEntity source, net.minecraft.world.level.Level level,
            Object connection, Object authorityConnection) {
        return source instanceof Player player && level!=null && source.level()==level
                && connection!=null && connection==authorityConnection && level.getEntity(source.getId())==source
                ?Optional.of(player.getUUID()):Optional.empty();
    }
}
