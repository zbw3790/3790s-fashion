package dev.zbw3790.fashion;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.cape.CapeCosmeticValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserGuideExamplesTest {
    private static Path root() {
        Path path=Path.of("").toAbsolutePath();
        while(path!=null && !Files.isRegularFile(path.resolve("gradle.properties"))) path=path.getParent();
        return java.util.Objects.requireNonNull(path,"找不到测试工程根。");
    }
    @ParameterizedTest @ValueSource(strings={"zh_cn","en_us"})
    void actualGuideJsonPassesTheProductionMetadataParsers(String locale) throws Exception {
        Path docs=Files.isDirectory(root().resolve("public/docs"))?root().resolve("public/docs"):root().resolve("docs");
        String text=Files.readString(docs.resolve("getting-started-"+locale+".md"));
        var blocks=Pattern.compile("```json\\s*(.*?)```",Pattern.DOTALL).matcher(text);
        assertTrue(blocks.find());var outfit=OutfitMetadataParser.parse(blocks.group(1).strip().getBytes(StandardCharsets.UTF_8));
        assertEquals(Set.of(OutfitPart.HEAD),outfit.parts());assertEquals(Set.of(OutfitModel.WIDE),outfit.models());
        assertTrue(blocks.find());var armor=ArmorMetadataParser.parse(blocks.group(1).strip().getBytes(StandardCharsets.UTF_8));
        assertEquals(new ArmorStyleId("demo_helmet"),armor.id());assertEquals(Set.of(ArmorSlot.HEAD),armor.slots());
        assertFalse(blocks.find());
    }
    @Test void existingCapeTemplatesRemainRealValidatedResources() throws Exception {
        Path capes=Files.isDirectory(root().resolve("dev-assets/capes"))?root().resolve("dev-assets/capes"):root().resolve("templates/capes");
        try(var directories=Files.list(capes)) {
            var entries=directories.filter(Files::isDirectory).toList();assertFalse(entries.isEmpty());
            for(var directory:entries) {
                var result=new CapeCosmeticValidator().validate(directory);
                assertTrue(result.isSuccess(),directory+"："+result.issues());
            }
        }
    }
}
