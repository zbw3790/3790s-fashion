package vanillafashion.release;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReleaseArchitectureAuditTest {
	private static final Set<String> S2C = Set.of(
			"OpenWardrobePayload", "WardrobeAvailablePayload", "CapeRegistrySnapshotPayload",
			"CapeAssetDataPayload", "PlayerFashionSnapshotPayload", "PlayerFashionUpdatePayload",
			"PlayerFashionRemovePayload", "CapeSelectionResultPayload");

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
	void allEightClientReceiversUseCurrentConnectionGate() {
		String network = read(root().resolve(
				"src/client/java/vanillafashion/client/network/VanillaFashionClientNetworking.java"));
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
	void releaseMetadataRemainsConsistent() {
		Path project = root();
		String properties = read(project.resolve("gradle.properties"));
		String metadata = read(project.resolve("src/main/resources/fabric.mod.json"));
		assertTrue(properties.contains("mod_version=0.2.0"));
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
		assertEquals(1, mixins.size());
		assertEquals("WingsLayerMixin.java", mixins.getFirst().getFileName().toString());
		String config = read(project.resolve("src/client/resources/vanilla_fashion.client.mixins.json"));
		String mixin = read(mixins.getFirst());
		assertTrue(config.contains("WingsLayerMixin"));
		assertTrue(mixin.contains("@Inject("));
		assertTrue(mixin.contains("HEAD"));
		assertTrue(mixin.contains("cancellable = true"));
		assertFalse(mixin.contains("@Overwrite"));
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
