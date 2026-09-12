package dev.zbw3790.fashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Unit;
import static dev.zbw3790.fashion.client.render.outfit.OutfitRenderDecisions.*;
import dev.zbw3790.fashion.outfit.OutfitPart;
import dev.zbw3790.fashion.outfit.OutfitPartSelection;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;

/** 一个 hand 调用独占一个上下文；最多暂存一个目标，不重放已尝试发出的节点。 */
public final class OutfitHandCollector {
    private final SubmitNodeCollector root;
    private final PlayerModel source;
    private final ModelPart target;
    private final OutfitPartSnapshots snapshots;
    private final OutfitPart part;
    private final Decision decision;
    private final RenderType skinType;
    private final RenderType outfitType;
    private Pending pending;
    private boolean passthrough;
    private boolean closed;

    public OutfitHandCollector(SubmitNodeCollector root, PlayerModel source, OutfitPartSnapshots snapshots,
            OutfitPart part, Decision decision, RenderType skinType, RenderType outfitType) {
        this.root = Objects.requireNonNull(root);
        this.source = Objects.requireNonNull(source);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.part = Objects.requireNonNull(part);
        this.target = source.root().getChild(OutfitPartSnapshots.baseName(part));
        this.decision = Objects.requireNonNull(decision);
        this.skinType = skinType;
        this.outfitType = outfitType;
    }

    public void run(Consumer<SubmitNodeCollector> original) {
        if (closed) throw new IllegalStateException("手部调用上下文不能复用。");
        try {
            original.accept(new Bridge(root));
            emit(false);
        } finally {
            // 原操作抛出时不提交尚未发布的目标，也不重跑原操作。
            pending = null;
            closed = true;
        }
    }

    private record Draw(ModelPart tree, RenderType type) { }
    private record Pending(OrderedSubmitNodeCollector delegate, PoseStack pose, int light, int overlay,
            TextureAtlasSprite sprite, int color, CrumblingOverlay crumbling, int outline,
            Draw original, List<Draw> replacement) {
        void send(Draw draw) {
            delegate.submitModel(new Model.Simple(draw.tree(), ignored -> draw.type()), Unit.INSTANCE,
                    OutfitPartSnapshots.copyPose(pose), draw.type(), light, overlay, color, sprite, outline, crumbling);
        }
    }

    private void emit(boolean originalOnly) {
        Pending value = pending;
        pending = null; // 在首次 delegate 调用之前转为不可重放。
        if (value == null) return;
        if (originalOnly) value.send(value.original());
        else for (Draw draw : value.replacement()) value.send(draw);
    }

    private void forward(Runnable call) {
        passthrough = true;
        emit(true);
        call.run();
    }

    private void capture(OrderedSubmitNodeCollector delegate, ModelPart modelPart, PoseStack pose,
            RenderType type, int light, int overlay, TextureAtlasSprite sprite, int color,
            CrumblingOverlay crumbling, int outline, Runnable raw) {
        if (closed || passthrough || pending != null || modelPart != target || type != skinType || sprite != null) {
            forward(raw);
            return;
        }
        boolean matches;
        try { matches = snapshots.matches(source); }
        catch (RuntimeException exception) { matches = false; }
        if (!matches) { forward(raw); return; }
        Pending fallback;
        try {
            PoseStack copiedPose = OutfitPartSnapshots.copyPose(pose);
            Draw original = new Draw(snapshots.hand(source, part, true, true), type);
            fallback = new Pending(delegate, copiedPose, light, overlay, sprite, color, crumbling, outline,
                    original, List.of(original));
        } catch (RuntimeException exception) {
            forward(raw);
            return;
        }
        // 原始退路先完整准备；后续构造失败仅使用这一份独立原版快照。
        pending = fallback;
        try {
            List<Draw> draws;
            if (decision.effective().equals(ORIGINAL)) draws = fallback.replacement();
            else if (decision.effective().equals(NONE) || !decision.outfitVisible())
                draws = List.of(new Draw(snapshots.hand(source, part, true, false), type));
            else draws = List.of(new Draw(snapshots.hand(source, part, true, false), type),
                    new Draw(snapshots.hand(source, part, false, true), Objects.requireNonNull(outfitType)));
            pending = new Pending(delegate, fallback.pose(), light, overlay, sprite, color, crumbling, outline,
                    fallback.original(), draws);
        } catch (RuntimeException exception) {
            passthrough = true;
        }
    }

    private final class Bridge implements SubmitNodeCollector, FabricOrderedSubmitNodeCollector {
        private final OrderedSubmitNodeCollector delegate;
        private Bridge(OrderedSubmitNodeCollector delegate) { this.delegate = delegate; }

        @Override public OrderedSubmitNodeCollector order(int order) {
            return new Bridge(root.order(order));
        }

        @Override public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light,
                int overlay, TextureAtlasSprite sprite) {
            capture(delegate, part, pose, type, light, overlay, sprite, -1, null, 0,
                    () -> delegate.submitModelPart(part, pose, type, light, overlay, sprite));
        }
        @Override public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light,
                int overlay, TextureAtlasSprite sprite, int color, CrumblingOverlay crumbling) {
            capture(delegate, part, pose, type, light, overlay, sprite, color, crumbling, 0,
                    () -> delegate.submitModelPart(part, pose, type, light, overlay, sprite, color, crumbling));
        }
        @Override public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light,
                int overlay, TextureAtlasSprite sprite, int color, CrumblingOverlay crumbling, int outline) {
            capture(delegate, part, pose, type, light, overlay, sprite, color, crumbling, outline,
                    () -> delegate.submitModelPart(part, pose, type, light, overlay, sprite, color, crumbling, outline));
        }
        @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type,
                int light, int overlay, int color, TextureAtlasSprite sprite, int outline, CrumblingOverlay crumbling) {
            Runnable raw = () -> delegate.submitModel(model, state, pose, type, light, overlay, color, sprite, outline, crumbling);
            if (model.getClass() == Model.Simple.class && state == Unit.INSTANCE) {
                capture(delegate, model.root(), pose, type, light, overlay, sprite, color, crumbling, outline, raw);
            } else forward(raw);
        }
        @Override public <T extends SubmitNode> void submitCustom(SubmitRenderPhase<T> phase, T node) {
            forward(() -> ((FabricOrderedSubmitNodeCollector) delegate).submitCustom(phase, node));
        }
        @Override public void submitShadow(com.mojang.blaze3d.vertex.PoseStack p0, float p1, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> p2) {
            forward(() -> delegate.submitShadow(p0, p1, p2));
        }
        @Override public void submitNameTag(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.world.phys.Vec3 p1, int p2, net.minecraft.network.chat.Component p3, boolean p4, int p5, net.minecraft.client.renderer.state.level.CameraRenderState p6) {
            forward(() -> delegate.submitNameTag(p0, p1, p2, p3, p4, p5, p6));
        }
        @Override public void submitText(com.mojang.blaze3d.vertex.PoseStack p0, float p1, float p2, net.minecraft.util.FormattedCharSequence p3, boolean p4, net.minecraft.client.gui.Font.DisplayMode p5, int p6, int p7, int p8, int p9) {
            forward(() -> delegate.submitText(p0, p1, p2, p3, p4, p5, p6, p7, p8, p9));
        }
        @Override public void submitFlame(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.client.renderer.entity.state.EntityRenderState p1, org.joml.Quaternionf p2) {
            forward(() -> delegate.submitFlame(p0, p1, p2));
        }
        @Override public void submitLeash(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState p1) {
            forward(() -> delegate.submitLeash(p0, p1));
        }
        @Override public void submitMovingBlock(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.client.renderer.block.MovingBlockRenderState p1, int p2) {
            forward(() -> delegate.submitMovingBlock(p0, p1, p2));
        }
        @Override public void submitBlockModel(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.client.renderer.rendertype.RenderType p1, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> p2, int[] p3, int p4, int p5, int p6) {
            forward(() -> delegate.submitBlockModel(p0, p1, p2, p3, p4, p5, p6));
        }
        @Override public void submitBreakingBlockModel(com.mojang.blaze3d.vertex.PoseStack p0, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> p1, int p2) {
            forward(() -> delegate.submitBreakingBlockModel(p0, p1, p2));
        }
        @Override public void submitShapeOutline(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.world.phys.shapes.VoxelShape p1, net.minecraft.client.renderer.rendertype.RenderType p2, int p3, float p4, boolean p5) {
            forward(() -> delegate.submitShapeOutline(p0, p1, p2, p3, p4, p5));
        }
        @Override public void submitItem(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.world.item.ItemDisplayContext p1, int p2, int p3, int p4, int[] p5, java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> p6, net.minecraft.client.renderer.item.ItemStackRenderState.FoilType p7) {
            forward(() -> delegate.submitItem(p0, p1, p2, p3, p4, p5, p6, p7));
        }
        @Override public void submitCustomGeometry(com.mojang.blaze3d.vertex.PoseStack p0, net.minecraft.client.renderer.rendertype.RenderType p1, net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer p2) {
            forward(() -> delegate.submitCustomGeometry(p0, p1, p2));
        }
        @Override public void submitQuadParticleGroup(net.minecraft.client.renderer.state.level.QuadParticleRenderState p0) {
            forward(() -> delegate.submitQuadParticleGroup(p0));
        }
        @Override public void submitGizmoPrimitives(net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group p0, net.minecraft.client.renderer.state.level.CameraRenderState p1, boolean p2) {
            forward(() -> delegate.submitGizmoPrimitives(p0, p1, p2));
        }
    }
}
