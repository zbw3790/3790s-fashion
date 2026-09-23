package dev.zbw3790.fashion.client.screen;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** 测试使用正式 classpath 语言文件与 Vanilla ClientLanguage，不内置另一份翻译表。 */
public final class WardrobeLanguageTestSupport implements BeforeEachCallback, AfterEachCallback {
    private Language previous;
    @Override public void beforeEach(ExtensionContext context) { previous=Language.getInstance();use("zh_cn"); }
    @Override public void afterEach(ExtensionContext context) { Language.inject(previous); }
    public static void use(String locale) { Language.inject(load(locale)); }
    public static ClientLanguage load(String locale) {
        return ClientLanguage.loadFrom(new ClasspathResources(),locale.equals("en_us")?List.of("en_us"):List.of("en_us",locale),false);
    }
    private static final class ClasspathResources implements ResourceManager {
        public Set<String> getNamespaces() { return Set.of("minecraft","fashion_3790"); }
        public Optional<Resource> getResource(Identifier id) {
            String path="/assets/"+id.getNamespace()+"/"+id.getPath();
            var url=WardrobeLanguageTestSupport.class.getResource(path);
            return url==null?Optional.empty():Optional.of(new Resource(null,url::openStream));
        }
        public List<Resource> getResourceStack(Identifier id) { return getResource(id).stream().toList(); }
        public Map<Identifier,Resource> listResources(String prefix,Predicate<Identifier> filter) { return Map.of(); }
        public Map<Identifier,List<Resource>> listResourceStacks(String prefix,Predicate<Identifier> filter) { return Map.of(); }
        public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
