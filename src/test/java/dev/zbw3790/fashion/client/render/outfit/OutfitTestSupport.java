package dev.zbw3790.fashion.client.render.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.outfit.*;
import static dev.zbw3790.fashion.outfit.OutfitPartSelection.*;
import static org.junit.jupiter.api.Assertions.*;

final class OutfitTestSupport {
    static final OutfitId TEST_ID = new OutfitId("test_outfit");
    static final OutfitPartSelection TEST_OUTFIT = OutfitPartSelection.outfit(TEST_ID);
    static final Identifier TEXTURE = Identifier.withDefaultNamespace("test_outfit");
    static java.util.stream.Stream<OutfitPartSelection> selections() { return java.util.stream.Stream.of(ORIGINAL,NONE,TEST_OUTFIT); }
    static java.util.Map<OutfitPart,Boolean> visible(boolean value) {
        var map = new java.util.EnumMap<OutfitPart,Boolean>(OutfitPart.class);
        for (OutfitPart part:OutfitPart.CANONICAL_ORDER) map.put(part,value);
        return map;
    }
    static OutfitRenderAppearance appearance(AvatarRenderState state, OutfitRenderAppearance.Scene scene,
            OutfitSelections selections, java.util.Map<OutfitId,OutfitRenderAppearance.ResolvedAsset> assets) {
        return new OutfitRenderAppearance(new java.util.UUID(0,1),selections,OutfitRenderAppearance.modelOf(state.skin.model()),
                assets,OutfitRendering.visibilitySnapshot(state),scene);
    }
    static OutfitRenderAppearance.ResolvedAsset asset(OutfitModel model,boolean ready) {
        return new OutfitRenderAppearance.ResolvedAsset(new OutfitMetadata(OutfitPart.ALL,java.util.Set.of(model)),model,
                ready?java.util.Optional.of(TEXTURE):java.util.Optional.empty());
    }
    static OutfitRenderAppearance all(AvatarRenderState state,OutfitRenderAppearance.Scene scene,
            OutfitPartSelection selection,OutfitModel expected,boolean ready) {
        var assets = selection instanceof OutfitPartSelection.Outfit selected
                ? java.util.Map.of(selected.id(),asset(expected,ready)) : java.util.Map.<OutfitId,OutfitRenderAppearance.ResolvedAsset>of();
        return appearance(state,scene,OutfitSelections.original().set(OutfitPart.ALL,selection).selections(),assets);
    }
    static OutfitRenderDecisions.Decision decision(OutfitPartSelection selection, boolean visible, boolean ready,
            OutfitModel expected,OutfitModel actual) {
        var assets = selection instanceof OutfitPartSelection.Outfit selected
                ? java.util.Map.of(selected.id(),asset(expected,ready)) : java.util.Map.<OutfitId,OutfitRenderAppearance.ResolvedAsset>of();
        var input = new OutfitRenderAppearance(new java.util.UUID(0,1),OutfitSelections.original().with(OutfitPart.RIGHT_ARM,selection),
                actual,assets,visible(visible),OutfitRenderAppearance.Scene.FIRST_PERSON);
        return OutfitRenderDecisions.decide(input,OutfitPart.RIGHT_ARM);
    }
    static EntityModelSet models() { return EntityModelSet.vanilla(); }
    static PlayerModel model(EntityModelSet models, OutfitModel type) {
        return new PlayerModel(models.bakeLayer(type == OutfitModel.SLIM ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), type == OutfitModel.SLIM);
    }
    static AvatarRenderState state(OutfitModel type) {
        AvatarRenderState state = new AvatarRenderState();
        for (int i=0;i<1000;i++) {
            var skin = DefaultPlayerSkin.get(new java.util.UUID(0,i));
            if (OutfitRenderAppearance.modelOf(skin.model()) == type) { state.skin = skin; break; }
        }
        assertNotNull(state.skin);
        state.showHat = state.showJacket = state.showLeftSleeve = state.showRightSleeve = state.showLeftPants = state.showRightPants = true;
        state.scale = state.ageScale = 1;
        return state;
    }
    static List<String> consume(ModelPart part, PoseStack pose) {
        List<String> values = new ArrayList<>();
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    values.add(method.getName()+Arrays.toString(args));
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        part.render(pose, consumer, 31, 47, 0xffaabbcc);
        return values;
    }
    record Call(String method, Object[] args, int order) {
        ModelPart tree() { return method.equals("submitModel") ? ((Model<?>)args[0]).root() : (ModelPart)args[0]; }
        PoseStack pose() { return (PoseStack)args[method.equals("submitModel") ? 2 : 1]; }
    }
    static final class Queue {
        final List<Call> calls = new ArrayList<>();
        int failAt = -1;
        SubmitNodeCollector collector() { return collector(0); }
        private SubmitNodeCollector collector(int order) {
            return (SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class, FabricOrderedSubmitNodeCollector.class}, (proxy, method, args) -> {
                        if (method.getName().equals("order")) return collector((int)args[0]);
                        if (method.getDeclaringClass() == Object.class) return switch(method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0]; default -> "测试提交队列";
                        };
                        calls.add(new Call(method.getName(),args.clone(),order));
                        if (calls.size() == failAt) throw new IllegalStateException("测试：队列记录后抛出异常。");
                        return null;
                    });
        }
    }
}
