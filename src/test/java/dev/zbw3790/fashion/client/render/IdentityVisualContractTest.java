package dev.zbw3790.fashion.client.render;

import com.google.gson.JsonParser;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.Test;
import vanillafashion.elytraslot.api.client.ElytraVisualCompatibility;
import static org.junit.jupiter.api.Assertions.*;

class IdentityVisualContractTest {
    @Test void actualFabricDiscoveryFindsMovedProviderUnderUnchangedExternalKey() {
        var loader=FabricLoader.getInstance();
        var entries=loader.getEntrypointContainers(ElytraVisualCompatibility.ENTRYPOINT,ElytraVisualCompatibility.class);
        var own=entries.stream().filter(e->e.getProvider().getMetadata().getId().equals("fashion_3790")).toList();
        assertEquals(1,own.size());assertInstanceOf(ElytraSlotVisualProvider.class,own.getFirst().getEntrypoint());
        assertTrue(loader.isModLoaded("vanilla_fashion"));
        assertEquals("fashion_3790",loader.getModContainer("vanilla_fashion").orElseThrow().getMetadata().getId());
        assertEquals(1,ElytraVisualCompatibility.VERSION);
        assertEquals("elytra_slot_3790:visual_contract",ElytraVisualCompatibility.CAPABILITY);
        assertEquals("elytra_slot_3790:visual_compatibility",ElytraVisualCompatibility.ENTRYPOINT);
    }
    @Test void expandedMetadataHasCanonicalIdentityAndOnlyDependencyAlias() throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(!Files.exists(root.resolve("settings.gradle")))root=root.getParent();
        var meta=JsonParser.parseString(Files.readString(root.resolve("build/resources/main/fabric.mod.json"))).getAsJsonObject();
        assertEquals("fashion_3790",meta.get("id").getAsString());var properties=new java.util.Properties();
        properties.load(new java.io.StringReader(Files.readString(root.resolve("gradle.properties"))));
        assertEquals(properties.getProperty("mod_version"),meta.get("version").getAsString());
        assertEquals("3790's Fashion",meta.get("name").getAsString());assertEquals("assets/fashion_3790/icon.png",meta.get("icon").getAsString());
        assertEquals(1,meta.getAsJsonArray("provides").size());assertEquals("vanilla_fashion",meta.getAsJsonArray("provides").get(0).getAsString());
        var entries=meta.getAsJsonObject("entrypoints").getAsJsonArray(ElytraVisualCompatibility.ENTRYPOINT);
        assertEquals(1,entries.size());assertEquals(ElytraSlotVisualProvider.class.getName(),entries.get(0).getAsString());
        assertEquals("1",meta.getAsJsonObject("custom").get(ElytraVisualCompatibility.CAPABILITY).getAsString());
        assertFalse(meta.getAsJsonObject("depends").has("elytra_slot_3790"));
    }
}
