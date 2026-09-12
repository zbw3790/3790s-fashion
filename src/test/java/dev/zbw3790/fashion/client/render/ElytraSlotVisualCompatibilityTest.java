package dev.zbw3790.fashion.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ElytraSlotVisualCompatibilityTest {
    static final UUID LOCAL = new UUID(0, 1), REMOTE = new UUID(0, 2);
    static final Identifier CAPE = Identifier.fromNamespaceAndPath("fashion_3790", "test/cape");
    static final Identifier WINGS = Identifier.fromNamespaceAndPath("fashion_3790", "test/elytra");
    static final AtomicBoolean BODY = new AtomicBoolean();
    static EntityModelSet models;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
            .forEach(DataComponentInitializers.PendingComponents::apply);
        models = EntityModelSet.vanilla();
        assertFalse(ElytraSlotVisualCooperation.blocksCape(new AvatarRenderState()));
        ElytraSlotVisualCooperation.connect(state -> BODY.get());
    }
    @BeforeEach void resetFact() { BODY.set(false); }

    @ParameterizedTest @ValueSource(ints={0, 1, 2, 3})
    void existingThreeStateTextureDecisionsReachDedicatedWings(int mode) {
        var appearance = switch(mode) {
            case 0 -> PlayerFashionRenderAppearance.unknown();
            case 1 -> PlayerFashionRenderAppearance.vanilla();
            case 2 -> PlayerFashionRenderAppearance.serverCosmetic(Optional.of(CAPE), ElytraTextureDecision.customTexture(WINGS));
            default -> PlayerFashionRenderAppearance.serverCosmetic(Optional.of(CAPE), ElytraTextureDecision.vanillaDefault());
        };
        var provider = new ElytraSlotVisualProvider(id -> appearance, query -> {});
        var dedicated = new AvatarRenderState();
        var chest = new ItemStack(Items.ELYTRA); dedicated.chestEquipment = chest;
        dedicated.elytraRotX = .4f; dedicated.showCape = true;
        provider.applyAppearance(LOCAL, dedicated);
        assertSame(chest, dedicated.chestEquipment);
        assertEquals(.4f, dedicated.elytraRotX);
        assertTrue(dedicated.showCape);
        assertEquals(appearance.elytraDecision(), ElytraTextureOverrideResolver.resolve(dedicated));
        assertFalse(provider.isPreview(dedicated));
    }

    @Test void localAndRemoteResolveTheirOwnFashionWithoutHoldingLivePlayers() {
        var selected = new HashMap<UUID,PlayerFashionRenderAppearance>();
        selected.put(LOCAL, PlayerFashionRenderAppearance.vanilla());
        selected.put(REMOTE, PlayerFashionRenderAppearance.serverCosmetic(Optional.of(CAPE), ElytraTextureDecision.customTexture(WINGS)));
        var provider = new ElytraSlotVisualProvider(selected::get, query -> {});
        var local = new AvatarRenderState(); var remote = new AvatarRenderState();
        provider.applyAppearance(LOCAL,local);provider.applyAppearance(REMOTE,remote);
        selected.put(REMOTE,PlayerFashionRenderAppearance.vanilla());
        assertEquals(ElytraTextureDecision.passThrough(),ElytraTextureOverrideResolver.resolve(local));
        assertEquals(ElytraTextureDecision.customTexture(WINGS),ElytraTextureOverrideResolver.resolve(remote));
        var next = new AvatarRenderState();provider.applyAppearance(REMOTE,next);
        assertEquals(ElytraTextureDecision.passThrough(),ElytraTextureOverrideResolver.resolve(next));
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5})
    void customCapeConsumesEquipmentVetoSeparatelyFromCosmeticTakeover(int scenario) throws Exception {
        var state = new AvatarRenderState();state.showCape=true;
        state.chestEquipment = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var original=state.chestEquipment;
        PlayerFashionRenderState.attach(state, scenario==0?PlayerFashionRenderAppearance.vanilla():
            PlayerFashionRenderAppearance.serverCosmetic(Optional.of(CAPE),ElytraTextureDecision.vanillaDefault()));
        BODY.set(scenario==2 || scenario==3);
        if(scenario==3)WardrobePreviewRenderState.attach(state,
            WardrobePreviewAppearance.serverCosmetic(Optional.of(CAPE),ElytraTextureDecision.vanillaDefault()));
        if(scenario==4)state.chestEquipment=new ItemStack(Items.ELYTRA);
        if(scenario==5)state.isInvisible=true;
        var layer=new Fashion3790CapeLayer(null,models,assets());
        var submits=new ArrayList<Object[]>();
        var collector=(SubmitNodeCollector)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},
            (proxy,method,args)->{
                if(method.getName().equals("order"))return proxy;
                if(method.getName().equals("submitModel"))submits.add(args);
                return null;
            });
        layer.submit(new PoseStack(),collector,0,state,0,0);
        assertEquals(scenario==1 || scenario==3?1:0,submits.size());
        assertEquals(scenario==0,PlayerFashionRenderDecisions.allowVanillaCape(state));
        assertTrue(state.showCape);
        if(scenario!=4)assertSame(original,state.chestEquipment);
    }

    @Test void existingPreviewMarkerRecognizedWithoutModeOrRealEquipmentQueries() {
        var provider=new ElytraSlotVisualProvider(id->{throw new AssertionError();},query->{});
        var state=new AvatarRenderState();assertFalse(provider.isPreview(state));
        WardrobePreviewRenderState.attach(state,WardrobePreviewAppearance.vanilla());
        BODY.set(true);assertTrue(provider.isPreview(state));assertFalse(ElytraSlotVisualCooperation.blocksCape(state));
    }

    @Test void readonlyCapeQueryIsHandedThroughAtInitialization() {
        var supplied=new java.util.concurrent.atomic.AtomicReference<java.util.function.Predicate<AvatarRenderState>>();
        var provider=new ElytraSlotVisualProvider(id->PlayerFashionRenderAppearance.unknown(),supplied::set);
        java.util.function.Predicate<AvatarRenderState> fact=state->state.id==7;
        provider.connect(fact);assertSame(fact,supplied.get());
        var state=new AvatarRenderState();state.id=7;assertTrue(supplied.get().test(state));
    }

    static EquipmentAssetManager assets() throws Exception {
        var manager=new EquipmentAssetManager();
        var field=EquipmentAssetManager.class.getDeclaredField("equipmentAssets");field.setAccessible(true);
        var info=new net.minecraft.client.resources.model.EquipmentClientInfo(Map.of(
            net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.WINGS,List.of(
                new net.minecraft.client.resources.model.EquipmentClientInfo.Layer(Identifier.withDefaultNamespace("elytra"),Optional.empty(),false))));
        field.set(manager,Map.of(net.minecraft.world.item.equipment.EquipmentAssets.ELYTRA,info));
        return manager;
    }
}
