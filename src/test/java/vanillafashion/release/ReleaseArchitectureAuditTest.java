package vanillafashion.release;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.SemanticVersion;
import org.junit.jupiter.api.Test;

class ReleaseArchitectureAuditTest {
	private static final Set<String> S2C = Set.of(
			"OpenWardrobePayload", "WardrobeAvailablePayload", "CapeRegistrySnapshotPayload",
			"CapeAssetDataPayload", "PlayerFashionSnapshotPayload", "PlayerFashionUpdatePayload",
			"PlayerFashionRemovePayload", "CapeSelectionResultPayload",
            "FullPlayerFashionSnapshotPayload", "FullPlayerFashionUpdatePayload", "FullPlayerFashionRemovePayload",
            "FullFashionSelectionResultPayload", "OutfitRegistrySnapshotPayload", "OutfitAssetDataPayload", "OutfitRegistryRefreshPayload");

	@Test
	void mainSourceSetContainsNoClientOnlyReferences() throws IOException {
		String main = readTree(root().resolve("src/main/java"));
		for (String value : List.of("net.minecraft.client", "net.fabricmc.fabric.api.client",
				"WardrobeScreen", "DynamicTexture", "Minecraft.getInstance()")) {
			assertFalse(main.contains(value), () -> "main 源集出现客户端专用引用：" + value);
		}
	}

	@Test
	void allServerSendsUseSingleCapabilityGate() throws IOException {
		Path javaRoot = root().resolve("src/main/java");
		List<Path> senders;
		try (var files = Files.walk(javaRoot)) {
			senders = files.filter(path -> path.toString().endsWith(".java"))
					.filter(path -> read(path).contains("ServerPlayNetworking.send("))
					.toList();
		}
		assertEquals(List.of(javaRoot.resolve("vanillafashion/network/ServerPayloadSender.java")), senders);
		String sender = read(senders.getFirst());
		assertTrue(sender.indexOf("if (!canSend(handler, payload.type()))")
				< sender.indexOf("ServerPlayNetworking.send(handler.getPlayer(), payload)"));
	}

	@Test
	void allFifteenClientReceiversUseCurrentConnectionGate() {
		String network = read(root().resolve(
				"src/client/java/vanillafashion/client/network/VanillaFashionClientNetworking.java"))
                + read(root().resolve("src/client/java/vanillafashion/client/network/ClientFullFashionNetworking.java"));
		var matcher = Pattern.compile("registerCurrentConnectionReceiver\\(\\s*(\\w+Payload)\\.TYPE")
				.matcher(network);
		Set<String> registered = new HashSet<>();
		while (matcher.find()) {
			registered.add(matcher.group(1));
		}
		assertEquals(S2C, registered);
		assertEquals(1, count(network, "ClientPlayNetworking.registerGlobalReceiver("));
		assertTrue(network.contains("ClientConnectionIdentity.runIfCurrent("));
	}

	@Test
	void productionContainsNoFixedFixturesOrRemoteCapeSources() throws IOException {
		String production = readTree(root().resolve("src/main/java"))
				+ readTree(root().resolve("src/client/java"));
		for (String value : List.of("m4c_full", "m5a_", "m6b_shared_01", "http://", "https://",
				"java.net.http", "HttpClient", "TODO", "FIXME")) {
			assertFalse(production.contains(value), () -> "生产源码包含发布禁用标记：" + value);
		}
	}

	@Test
	void releaseMetadataRemainsConsistent() throws IOException {
		Path project = root();
		Properties properties = new Properties();
		properties.load(new StringReader(read(project.resolve("gradle.properties"))));
		String expectedVersion = properties.getProperty("mod_version");
		assertNotNull(expectedVersion, "唯一版本来源 mod_version 缺失。");
		assertFalse(expectedVersion.isBlank(), "mod_version 不能为空。");
		assertDoesNotThrow(() -> SemanticVersion.parse(expectedVersion), "mod_version 必须是合法的语义版本。");

		String build = read(project.resolve("build.gradle"));
		assertTrue(build.contains("version = project.mod_version"));
		assertTrue(build.contains("def modVersion = project.version"));
		assertTrue(build.contains("inputs.property \"version\", modVersion"));
		assertTrue(build.contains("filesMatching(\"fabric.mod.json\")"));
		assertTrue(build.contains("expand \"version\": modVersion"));
		String metadata = read(project.resolve("src/main/resources/fabric.mod.json"));
		assertEquals("${version}", JsonParser.parseString(metadata).getAsJsonObject().get("version").getAsString(),
				"源码 metadata 必须继续使用唯一构建版本的替换模板。");
		// test 的资源依赖保证 processResources 已执行；最终 JAR 在构建后另行审计。
		String processedMetadata = read(project.resolve("build/resources/main/fabric.mod.json"));
		assertEquals(expectedVersion,
				JsonParser.parseString(processedMetadata).getAsJsonObject().get("version").getAsString(),
				"处理后的 metadata 版本必须与 mod_version 一致。");
		assertTrue(metadata.contains("vanilla_fashion"));
		assertTrue(metadata.contains("3790's Vanilla Style Fashion"));
		assertTrue(metadata.contains("MIT"));
		assertTrue(metadata.contains("assets/vanilla_fashion/icon.png"));
		assertTrue(metadata.contains("26.2"));
		assertTrue(metadata.contains("fabric-api"));
		assertEquals(2, count(metadata, "environment"));
		assertTrue(metadata.contains("*"));
		assertTrue(metadata.contains("client"));
	}

	@Test
	void releasePackagingIncludesLicenseAndSupportsBothRepositoryLayouts() {
		String packaging = read(root().resolve("tools/package_release.py"));
		assertTrue(packaging.contains("LICENSE_PATH"));
		assertTrue(packaging.contains("shutil.copyfile(LICENSE_PATH"));
		assertTrue(packaging.contains("INTERNAL_TEMPLATE_ROOT"));
		assertTrue(packaging.contains("PUBLIC_TEMPLATE_ROOT"));
	}

	@Test
	void onlyExactClientMixinRemains() throws IOException {
		Path project = root();
		List<Path> mixins;
		try (var files = Files.walk(project.resolve("src/client/java"))) {
			mixins = files.filter(path -> path.getFileName().toString().endsWith("Mixin.java")).toList();
		}
		assertEquals(Set.of("WingsLayerMixin.java", "ItemInHandRendererMixin.java", "LivingEntityRendererMixin.java"),
				mixins.stream().map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
		String config = read(project.resolve("src/client/resources/vanilla_fashion.client.mixins.json"));
		String mixin = read(project.resolve("src/client/java/vanillafashion/client/mixin/WingsLayerMixin.java"));
		assertTrue(config.contains("WingsLayerMixin"));
		assertTrue(mixin.contains("@Inject("));
		assertTrue(mixin.contains("HEAD"));
		assertTrue(mixin.contains("cancellable = true"));
		assertFalse(mixin.contains("@Overwrite"));
	}

    @Test void exactlyOneCommonInteractionMixinAndNoForwardCallback() throws IOException {
        try (var files=Files.walk(root().resolve("src/main/java"))) {
            assertEquals(List.of("PlayerInteractionMixin.java"),files.filter(p->p.toString().endsWith("Mixin.java")).map(p->p.getFileName().toString()).toList());
        }
        var config=JsonParser.parseString(read(root().resolve("src/main/resources/vanilla_fashion.mixins.json"))).getAsJsonObject();
        assertEquals("PlayerInteractionMixin",config.getAsJsonArray("mixins").get(0).getAsString());
        assertEquals(1,config.getAsJsonArray("mixins").size());
        assertTrue(read(root().resolve("src/main/resources/fabric.mod.json")).contains("vanilla_fashion.mixins.json"));
        String mixin=read(root().resolve("src/main/java/vanillafashion/mixin/PlayerInteractionMixin.java"));
        assertTrue(mixin.contains("@At(\"RETURN\")")); assertTrue(mixin.contains("cancellable=true"));
        assertTrue(mixin.contains("interactOn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;"));
        assertFalse(readTree(root().resolve("src/main/java")).contains("UseEntityCallback"));
        assertFalse(mixin.contains("attack")); assertFalse(mixin.contains("@Overwrite"));
    }

	private static Path root() {
		for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath(); path != null;
				path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))) {
				return path;
			}
		}
		return fail("无法从测试工作目录定位项目根目录。");
	}

	private static String readTree(Path root) throws IOException {
		StringBuilder text = new StringBuilder();
		try (var files = Files.walk(root)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
				text.append(read(file));
			}
		}
		return text.toString();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException exception) {
			return fail("无法读取发布审计文件：" + path, exception);
		}
	}

	private static int count(String text, String value) {
		return (text.length() - text.replace(value, "").length()) / value.length();
	}
}
