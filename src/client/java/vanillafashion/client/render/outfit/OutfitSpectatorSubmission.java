package vanillafashion.client.render.outfit;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Unit;
import static vanillafashion.client.render.outfit.OutfitRenderDecisions.*;
import vanillafashion.outfit.OutfitPart;
import static vanillafashion.outfit.OutfitPartSelection.ORIGINAL;

/** 只处理已准备场景的 spectator 主模型 HEAD，不打开普通 Layer 门禁。 */
public final class OutfitSpectatorSubmission {
    private OutfitSpectatorSubmission() { }

    public static void submit(Object renderer, SubmitNodeCollector collector, Model<?> model, Object state,
            PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite,
            int outline, CrumblingOverlay crumbling, Operation<Void> original, LivingEntityRenderState outerState) {
        Runnable vanilla = () -> original.call(collector, model, state, pose, type, light, overlay, color, sprite, outline, crumbling);
        if (!(renderer instanceof AvatarRenderer<?>)
                || !(state instanceof AvatarRenderState avatar) || outerState != state || !avatar.isSpectator) {
            vanilla.run(); return;
        }
        Prepared prepared;
        try {
            var frame = OutfitRendering.find(avatar);
            prepared = OutfitRendering.current(frame, renderer, model)
                    ? prepare(frame, avatar, pose, type, sprite) : null;
        } catch (RuntimeException exception) { prepared = null; }
        if (prepared == null) { vanilla.run(); return; }
        emit(prepared, collector, light, overlay, color, sprite, outline, crumbling, original);
    }

    record Prepared(Model.Simple base, Model.Simple hat, PoseStack pose, RenderType baseType, RenderType hatType) { }

    static Prepared prepare(OutfitRendering.Frame frame, AvatarRenderState state, PoseStack pose,
            RenderType type, TextureAtlasSprite sprite) {
        if (!state.isSpectator || frame == null || frame.baseHead() == null || sprite != null
                || frame.decisions().get(OutfitPart.HEAD).effective().equals(ORIGINAL)) return null;
        var skin = state.skin.body().texturePath();
        RenderType expected = switch (frame.visibility()) {
            case NORMAL -> frame.binding().source().renderType(skin);
            case OBSERVER -> RenderTypes.entityTranslucentCullItemTarget(skin);
            case OUTLINE -> RenderTypes.outline(skin);
            case HIDDEN -> null;
        };
        if (type == null || type != expected) return null;
        RenderType hatType = frame.types().get(OutfitPart.HEAD);
        var tree = frame.outers().get(OutfitPart.HEAD);
        return new Prepared(new Model.Simple(frame.binding().snapshots().copyBodyTree(frame.baseHead(), OutfitPart.HEAD, true, false), ignored -> type),
                tree == null ? null : new Model.Simple(frame.binding().snapshots().copyBodyTree(tree, OutfitPart.HEAD, false, true), ignored -> hatType),
                OutfitPartSnapshots.copyPose(pose), type, hatType);
    }

    static void emit(Prepared prepared, SubmitNodeCollector collector, int light, int overlay, int color,
            TextureAtlasSprite sprite, int outline, CrumblingOverlay crumbling, Operation<Void> original) {
        // 全部准备已经结束；任何一次真实提交异常直接传播，绝不补跑主模型。
        original.call(collector, prepared.base(), Unit.INSTANCE, OutfitPartSnapshots.copyPose(prepared.pose()), prepared.baseType(),
                light, overlay, color, sprite, outline, crumbling);
        if (prepared.hat() != null) collector.submitModel(prepared.hat(), Unit.INSTANCE,
                OutfitPartSnapshots.copyPose(prepared.pose()), prepared.hatType(), light, overlay, color, sprite, outline, crumbling);
    }
}
