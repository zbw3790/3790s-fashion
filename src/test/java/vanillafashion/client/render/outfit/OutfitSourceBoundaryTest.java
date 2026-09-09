package vanillafashion.client.render.outfit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutfitSourceBoundaryTest {
    @Test void renderingCoreDoesNotDependOnDevelopmentOrServerAssetSources() throws Exception {
        try(var paths=Files.list(root().resolve("src/client/java/vanillafashion/client/render/outfit"))){
            for(Path path:paths.filter(p->p.toString().endsWith(".java")).toList()){
                String source=Files.readString(path);
                for(String forbidden:List.of("OutfitSpike","client.dev.spike","DynamicTexture","NativeImage","java.nio.file",
                        "System.getProperty","Boolean.getBoolean","OutfitRegistryLoader","SavedData","literal(\"outfitspike\""))
                    assertFalse(source.contains(forbidden),path.getFileName()+" 不得依赖 "+forbidden);
                assertFalse(source.contains("enum Part"));assertFalse(source.contains("enum Choice"));
            }
        }
    }
    @Test void wrappersAndSceneWiringUseFormalConsumerOnly() throws Exception {
        String hand=Files.readString(root().resolve("src/client/java/vanillafashion/client/mixin/ItemInHandRendererMixin.java"));
        assertTrue(hand.contains("vanillafashion.outfit.OutfitPart"));assertTrue(hand.contains("OutfitRendering.hand("));
        assertFalse(hand.contains("Spike"));
        for(String path:List.of("src/client/java/vanillafashion/client/render/PlayerFashionWorldRendering.java",
                "src/client/java/vanillafashion/client/screen/WardrobePlayerPreviewRenderer.java")){
            String source=Files.readString(root().resolve(path));assertFalse(source.contains("Spike"));assertTrue(source.contains("OutfitRendering.prepare"));
        }
        String initializer=Files.readString(root().resolve("src/client/java/vanillafashion/client/VanillaFashionClient.java"));
        assertTrue(initializer.contains("NetworkOutfitAppearanceProvider(PLAYER_FASHIONS"));
        assertTrue(initializer.contains("NetworkOutfitAppearanceProvider.exclusive("));
        assertTrue(initializer.contains("production, vanillafashion.client.dev.spike.OutfitSpikeHarness.register()"));
    }
    private static Path root(){for(Path path=Path.of(System.getProperty("user.dir")).toAbsolutePath();path!=null;path=path.getParent())
        if(Files.exists(path.resolve("settings.gradle")))return path;throw new IllegalStateException("找不到项目根目录。");}
}
