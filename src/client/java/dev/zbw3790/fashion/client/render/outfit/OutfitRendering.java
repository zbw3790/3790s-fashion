package dev.zbw3790.fashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import net.minecraft.world.entity.player.PlayerModelPart;
import dev.zbw3790.fashion.outfit.*;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.ORIGINAL;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance.Scene;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderDecisions.*;

/** 正式渲染消费端；来源、资源注册和玩家权威状态由外部职责提供。 */
public final class OutfitRendering {
    private static final Logger LOGGER = LoggerFactory.getLogger("fashion_3790/outfit_rendering");
    private static OutfitAppearanceResolver appearances = new OutfitAppearanceResolver(OutfitAppearanceProvider.EMPTY);
    private static final Map<AvatarRenderer<?>, Binding> BINDINGS = new WeakHashMap<>();
    private static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "fashion_3790:outfit");
    private static EntityModelSet modelSet;
    private static boolean registered;
    private static boolean warned;

    record Binding(AvatarRenderer<?> renderer, PlayerModel source, OutfitPartSnapshots snapshots) { }
    record Frame(Binding binding, OutfitRenderAppearance input, Map<OutfitPart, Decision> decisions, Map<OutfitPart, ModelPart> outers,
            Map<OutfitPart, RenderType> types, Map<OutfitPart, Boolean> originalVisibility, ModelPart baseHead, Visibility visibility) {
        Frame { decisions = Map.copyOf(decisions); outers = Map.copyOf(outers); types = Map.copyOf(types); originalVisibility = Map.copyOf(originalVisibility); }
    }

    private OutfitRendering() { }

    public static void register(OutfitAppearanceProvider provider) {
        Objects.requireNonNull(provider);
        if (registered) return;
        appearances = new OutfitAppearanceResolver(provider);
        registered = true;
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (type != EntityTypes.PLAYER || renderer.getClass() != AvatarRenderer.class) return;
            var avatar = (AvatarRenderer<?>) renderer;
            if (modelSet != context.getModelSet()) { BINDINGS.clear(); modelSet = context.getModelSet(); }
            for (OutfitModel model : OutfitModel.CANONICAL_ORDER) {
                var snapshots = new OutfitPartSnapshots(modelSet, model);
                if (snapshots.matches(avatar.getModel())) {
                    Binding binding = new Binding(avatar, avatar.getModel(), snapshots);
                    BINDINGS.put(avatar, binding);
                    helper.register(new Layer(binding));
                    return;
                }
            }
            warn("玩家几何不符合原版模板，保持原版。");
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            BINDINGS.clear(); modelSet = null;
            appearances = new OutfitAppearanceResolver(OutfitAppearanceProvider.EMPTY);
        });
    }

    public static void prepareWorld(AvatarRenderState state, Optional<UUID> player) {
        prepare(state, player.orElse(null), Scene.WORLD);
    }

    public static void preparePreview(AvatarRenderState state, UUID player) {
        prepare(state, player, Scene.WARDROBE_PREVIEW);
    }

    /** 仅本次独立预览 RenderState 使用传入来源，不替换世界或第一人称的正式来源。 */
    public static void preparePreview(AvatarRenderState state, UUID player, OutfitAppearanceProvider preview) {
        prepare(state,player,Scene.WARDROBE_PREVIEW,new OutfitAppearanceResolver(preview));
    }
    private static void prepare(AvatarRenderState state, UUID player, Scene scene) {
        prepare(state,player,scene,appearances);
    }
    private static void prepare(AvatarRenderState state, UUID player, Scene scene, OutfitAppearanceResolver source) {
        // 默认无来源和本帧身份查找失败时，同样清除复用状态上的旧结果。
        ((FabricRenderState) state).setData(KEY, null);
        if (!source.hasSource() || player == null || state.skin == null) return;
        var client = Minecraft.getInstance();
        var renderer = client.getEntityRenderDispatcher().getRenderer(state);
        Binding binding = BINDINGS.get(renderer);
        if (binding == null) return;
        Frame frame;
        try {
            var context = new OutfitAppearanceProvider.Context(client.getConnection(), player,
                    OutfitRenderAppearance.modelOf(state.skin.model()), scene, visibilitySnapshot(state));
            var input = source.resolve(context);
            frame = input.isPresent() ? prepareFrame(state, input.orElseThrow(), binding) : null;
        } catch (RuntimeException exception) { warn("外层本帧准备失败，保持原版外层。"); return; }
        if (frame != null) publishFrame(state, frame);
    }
    static Map<OutfitPart, Boolean> visibilitySnapshot(AvatarRenderState state) {
        Map<OutfitPart, Boolean> result = new EnumMap<>(OutfitPart.class);
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) result.put(part, visible(state, part));
        return OutfitRenderAppearance.copyVisibility(result);
    }

    static void publishFrame(AvatarRenderState state, Frame frame) {
        ((FabricRenderState) state).setData(KEY, frame);
        if (frame != null && !state.isSpectator) {
            // 六个原始 V 已经保存，且所有有限树、材质均已完成，才发布抑制标记。
            for (OutfitPart part : OutfitPart.values()) setVisible(state, part, frame.decisions().get(part).originalVisible());
        }
    }

    static Frame prepareFrame(AvatarRenderState state, OutfitRenderAppearance input, Binding binding) {
        if (input == null || state.skin == null || input.model() != OutfitRenderAppearance.modelOf(state.skin.model())
                || binding.snapshots().modelType != input.model() || !binding.snapshots().matches(binding.source())
                || !input.originalVisibility().equals(visibilitySnapshot(state))) return null;
        Map<OutfitPart, Decision> decisions = new EnumMap<>(OutfitPart.class);
        Map<OutfitPart, ModelPart> outers = new EnumMap<>(OutfitPart.class);
        Map<OutfitPart, RenderType> types = new EnumMap<>(OutfitPart.class);
        Visibility visibility = visibility(state.isInvisible, state.isInvisibleToPlayer, state.appearsGlowing());
        PlayerModel animated = binding.snapshots().animate(state);
        for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
            Decision decision = decide(input, part);
            decisions.put(part, decision);
            if (decision.outfitVisible() && visibility != Visibility.HIDDEN && (!state.isSpectator || part == OutfitPart.HEAD)) {
                types.put(part, outfitType(visibility, input.asset(part).orElseThrow().texture().orElseThrow()));
                outers.put(part, binding.snapshots().bodyPart(animated, part, false, true));
            }
        }
        ModelPart head = state.isSpectator && !decisions.get(OutfitPart.HEAD).effective().equals(ORIGINAL)
                ? binding.snapshots().bodyPart(animated, OutfitPart.HEAD, true, false) : null;
        return new Frame(binding, input, decisions, outers, types, input.originalVisibility(), head, visibility);
    }

    static Frame find(AvatarRenderState state) { return ((FabricRenderState) state).getData(KEY); }
    static boolean current(Frame frame, Object renderer, Model<?> model) {
        return frame != null && frame.binding().renderer() == renderer && frame.binding().source() == model
                && BINDINGS.get(renderer) == frame.binding() && frame.binding().snapshots().matches(frame.binding().source());
    }

    static RenderType outfitType(Visibility visibility, Identifier texture) {
        return switch (visibility) {
            case NORMAL -> RenderTypes.entityTranslucent(texture);
            case OBSERVER -> RenderTypes.entityTranslucentCullItemTarget(texture);
            case OUTLINE -> RenderTypes.outline(texture);
            case HIDDEN -> null;
        };
    }

    public static void hand(AvatarRenderer<?> renderer, SubmitNodeCollector collector, Identifier skin,
            boolean sleeve, OutfitPart part, Consumer<SubmitNodeCollector> original) {
        if (!appearances.hasSource()) { original.accept(collector); return; }
        var client = Minecraft.getInstance();
        var player = client.player;
        Binding binding = BINDINGS.get(renderer);
        if (player == null || binding == null || client.getEntityRenderDispatcher().getRenderer(player) != renderer
                || !player.getSkin().body().texturePath().equals(skin) || binding.source() != renderer.getModel()
                || binding.snapshots().modelType != OutfitRenderAppearance.modelOf(player.getSkin().model())) {
            original.accept(collector); return;
        }
        OutfitHandCollector adapter;
        try {
            Map<OutfitPart, Boolean> visible = new EnumMap<>(OutfitPart.class);
            for (OutfitPart item : OutfitPart.CANONICAL_ORDER) visible.put(item, player.isModelPartShown(skinPart(item)));
            visible.put(part, sleeve); // 目标臂以原调用实际传入的开关为准。
            var context = new OutfitAppearanceProvider.Context(client.getConnection(), player.getUUID(),
                    OutfitRenderAppearance.modelOf(player.getSkin().model()), Scene.FIRST_PERSON, visible);
            var input = appearances.resolve(context);
            if (input.isEmpty() || !binding.snapshots().matches(renderer.getModel())) adapter = null;
            else {
                var appearance = input.orElseThrow();
                Decision decision = decide(appearance, part);
                RenderType outer = decision.outfitVisible()
                        ? RenderTypes.entityTranslucent(appearance.asset(part).orElseThrow().texture().orElseThrow()) : null;
                adapter = new OutfitHandCollector(collector, renderer.getModel(), binding.snapshots(), part,
                        decision, RenderTypes.entityTranslucent(skin), outer);
            }
        } catch (RuntimeException exception) { adapter = null; warn("手部外层准备失败，保持原版。"); }
        if (adapter == null) original.accept(collector);
        else adapter.run(original);
    }
    private static PlayerModelPart skinPart(OutfitPart part) {
        return switch (part) {
            case HEAD -> PlayerModelPart.HAT; case BODY -> PlayerModelPart.JACKET;
            case LEFT_ARM -> PlayerModelPart.LEFT_SLEEVE; case RIGHT_ARM -> PlayerModelPart.RIGHT_SLEEVE;
            case LEFT_LEG -> PlayerModelPart.LEFT_PANTS_LEG; case RIGHT_LEG -> PlayerModelPart.RIGHT_PANTS_LEG;
        };
    }

    static boolean visible(AvatarRenderState state, OutfitPart part) {
        return switch (part) {
            case HEAD -> state.showHat; case BODY -> state.showJacket;
            case LEFT_ARM -> state.showLeftSleeve; case RIGHT_ARM -> state.showRightSleeve;
            case LEFT_LEG -> state.showLeftPants; case RIGHT_LEG -> state.showRightPants;
        };
    }
    private static void setVisible(AvatarRenderState state, OutfitPart part, boolean value) {
        switch (part) {
            case HEAD -> state.showHat = value; case BODY -> state.showJacket = value;
            case LEFT_ARM -> state.showLeftSleeve = value; case RIGHT_ARM -> state.showRightSleeve = value;
            case LEFT_LEG -> state.showLeftPants = value; case RIGHT_LEG -> state.showRightPants = value;
        }
    }
    private static void warn(String message) { if (!warned) { warned = true; LOGGER.warn(message); } }

    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final Binding binding;
        Layer(Binding binding) { super(binding.renderer()); this.binding = binding; }
        @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light,
                AvatarRenderState state, float yRot, float xRot) {
            Frame frame = find(state);
            if (state.isSpectator || frame == null || frame.binding() != binding) return;
            // 原版 AvatarRenderer 的 tint=-1、whiteOverlay=0；非原版 Renderer 未绑定。
            int color = frame.visibility() == Visibility.OBSERVER ? 0x26ffffff : -1;
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
            for (OutfitPart part : OutfitPart.values()) {
                ModelPart tree = frame.outers().get(part);
                if (tree == null) continue;
                RenderType type = frame.types().get(part);
                collector.submitModel(new Model.Simple(binding.snapshots().copyBodyTree(tree, part, false, true), ignored -> type), Unit.INSTANCE,
                        OutfitPartSnapshots.copyPose(pose), type, light, overlay, color, null, state.outlineColor, null);
            }
        }
    }
}
