package dev.zbw3790.fashion.client.render.outfit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** 从类资源读取变换前字节码；这不是 Minecraft Runtime 注入 Gate。 */
class OutfitMixinContractTest {
    private static final String POSE="Lcom/mojang/blaze3d/vertex/PoseStack;";
    private static final String COLLECTOR="Lnet/minecraft/client/renderer/SubmitNodeCollector;";
    private static final String ARM="Lnet/minecraft/world/entity/HumanoidArm;";
    private static final String HAND="("+POSE+COLLECTOR+"ILnet/minecraft/resources/Identifier;Z)V";
    private static final String MAIN="(Lnet/minecraft/client/model/Model;Ljava/lang/Object;"+POSE
            +"Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

    @Test void exactlyFourHandSitesHaveExplicitDescriptorsAndStrictCounts() throws Exception {
        var target=readClass("net/minecraft/client/renderer/ItemInHandRenderer");
        String mixin=Files.readString(root().resolve("src/client/java/dev/zbw3790/fashion/client/mixin/ItemInHandRendererMixin.java"));
        for(String method:List.of("renderPlayerArm","renderMapHand")){
            String descriptor="("+POSE+COLLECTOR+(method.equals("renderPlayerArm")?"IFF":"I")+ARM+")V";
            var node=method(target,method,descriptor);
            assertEquals(0,node.access&Opcodes.ACC_BRIDGE);
            assertTrue(mixin.contains(method+descriptor));
            for(String hand:List.of("renderRightHand","renderLeftHand")) {
                assertEquals(1,calls(node,"net/minecraft/client/renderer/entity/player/AvatarRenderer",hand,HAND));
                assertTrue(mixin.contains("Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"+hand+HAND));
            }
        }
        assertEquals(4,count(mixin,"@WrapOperation("));assertEquals(4,count(mixin,"require = 1, expect = 1, allow = 1"));
        assertFalse(mixin.contains("ordinal"));assertFalse(mixin.contains("require = 0"));
    }
    @Test void exactlyOneNonBridgeSpectatorMainSiteAndNoBridgeDuplicate() throws Exception {
        var target=readClass("net/minecraft/client/renderer/entity/LivingEntityRenderer");
        String descriptor="(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"+POSE+COLLECTOR+"Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
        var node=method(target,"submit",descriptor);assertEquals(0,node.access&Opcodes.ACC_BRIDGE);
        assertEquals(1,calls(node,"net/minecraft/client/renderer/SubmitNodeCollector","submitModel",MAIN));
        for(MethodNode other:target.methods)if((other.access&Opcodes.ACC_BRIDGE)!=0)assertEquals(0,calls(other,"net/minecraft/client/renderer/SubmitNodeCollector","submitModel",MAIN));
        String mixin=Files.readString(root().resolve("src/client/java/dev/zbw3790/fashion/client/mixin/LivingEntityRendererMixin.java"));
        assertTrue(mixin.contains("submit"+descriptor));assertTrue(mixin.contains("Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel"+MAIN));
        assertEquals(1,count(mixin,"@WrapOperation("));assertEquals(1,count(mixin,"require = 1, expect = 1, allow = 1"));
    }
    @Test void jsonContainsOnlyThreeApprovedClientMixins() throws Exception {
        var json=com.google.gson.JsonParser.parseString(Files.readString(root().resolve("src/client/resources/fashion_3790.client.mixins.json"))).getAsJsonObject();
        Set<String> actual=new java.util.HashSet<>();json.getAsJsonArray("client").forEach(v->actual.add(v.getAsString()));
        assertEquals(Set.of("WingsLayerMixin","ItemInHandRendererMixin","LivingEntityRendererMixin"),actual);
        assertTrue(json.get("required").getAsBoolean());assertEquals(1,json.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
    }
    private static ClassNode readClass(String name) throws Exception {
        try(var stream=OutfitMixinContractTest.class.getClassLoader().getResourceAsStream(name+".class")){
            assertNotNull(stream);ClassNode node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    private static MethodNode method(ClassNode node,String name,String desc){return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static long calls(MethodNode method,String owner,String name,String desc){long count=0;for(var instruction:method.instructions)
        if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))count++;return count;}
    private static int count(String text,String value){return (text.length()-text.replace(value,"").length())/value.length();}
    private static Path root(){for(Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();p!=null;p=p.getParent())if(Files.exists(p.resolve("settings.gradle")))return p;throw new IllegalStateException("找不到项目根目录。");}
}
