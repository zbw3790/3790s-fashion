package dev.zbw3790.fashion.outfit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.outfit.OutfitDiagnostic.Code.*;
import static dev.zbw3790.fashion.outfit.OutfitTestSupport.*;

class OutfitFileAccessTest {
	@TempDir Path temp;
	@Test void ordinaryReadsAreBoundedAndRejectNonFilesAndExternalTargets() throws Exception {
		OutfitFileAccess access = new OutfitFileAccess();
		var root = access.root(temp.resolve("root"));
		Path child = Files.createDirectories(root.real().resolve("child"));
		Path file = Files.write(child.resolve("wide.png"),png());
		Path nonFile = Files.createDirectory(child.resolve("folder"));
		var directory = access.directory(child,root);
		assertArrayEquals(png(),access.read(file,directory,root,65536));
		assertEquals(TOO_LARGE,assertThrows(OutfitFileAccess.Failure.class, () -> access.read(file,directory,root,1)).code);
		assertEquals(NOT_REGULAR_FILE,assertThrows(OutfitFileAccess.Failure.class, () -> access.read(nonFile,directory,root,65536)).code);
		Path external = Files.write(temp.resolve("outside.png"),png());
		assertEquals(UNSAFE_PATH,assertThrows(OutfitFileAccess.Failure.class, () -> access.read(external,directory,root,65536)).code);
	}
	@Test void replacementBeforeOpenIsRejectedEvenWhenFileTimestampsAreReused() throws Exception {
		OutfitFileAccess access = new OutfitFileAccess() {
			@Override void beforeOpen(Path path) throws IOException {
				FileTime time = Files.getLastModifiedTime(path);
				Files.move(path,path.resolveSibling("old.png"));
				Files.write(path,png()); Files.setLastModifiedTime(path,time);
			}
		};
		assertReadChanged(access);
	}
	@Test void contentGrowthAfterReadIsRejected() throws Exception {
		OutfitFileAccess access = new OutfitFileAccess() {
			@Override void afterRead(Path path) throws IOException { Files.write(path,new byte[65537]); }
		};
		assertReadChanged(access);
	}
	@Test void sameSizeModificationAfterReadIsRejected() throws Exception {
		OutfitFileAccess access = new OutfitFileAccess() {
			@Override void afterRead(Path path) throws IOException {
				byte[] bytes = Files.readAllBytes(path); bytes[0] ^= 1;
				FileTime old = Files.getLastModifiedTime(path); Files.write(path,bytes);
				Files.setLastModifiedTime(path,FileTime.fromMillis(old.toMillis()+5000));
			}
		};
		assertReadChanged(access);
	}
	private void assertReadChanged(OutfitFileAccess access) throws Exception {
		var root = access.root(temp.resolve("root"));
		Path child = Files.createDirectory(root.real().resolve("child"));
		Path file = Files.write(child.resolve("wide.png"),png());
		var directory = access.directory(child,root);
		assertEquals(CHANGED_DURING_READ,assertThrows(OutfitFileAccess.Failure.class, () -> access.read(file,directory,root,65536)).code);
	}
	@Test void rootReplacementDuringReadInvalidatesWholeScan() throws Exception {
		Path root = temp.resolve("root"), child = directory(root,"example","\"wide\"");
		Files.write(child.resolve("wide.png"),png());
		OutfitFileAccess access = new OutfitFileAccess() {
			private boolean changed;
			@Override void afterRead(Path path) throws IOException {
				if (!changed) { changed=true; Files.move(root,temp.resolve("old_root")); Files.createDirectory(root); }
			}
		};
		var result = new OutfitRegistryLoader(100,access).load(root);
		assertFalse(result.knowledge().trustworthy()); assertEquals(0,result.registry().size());
		assertFalse(result.knowledge().isDefinitelyAbsent(new OutfitId("anything")));
	}
	@Test void directoryLinksInsideRootWorkAndExternalLinksAreRejected() throws Exception {
		Path root = Files.createDirectory(temp.resolve("root"));
		Path internal = directory(root,"inside","\"wide\""); Files.write(internal.resolve("wide.png"),png());
		Path external = directory(temp.resolve("outside"),"external","\"wide\""); Files.write(external.resolve("wide.png"),png());
		Path insideLink = root.resolve("inside_link"), outsideLink = root.resolve("outside_link"), rootLink = root.resolve("self_link");
		try {
			linkDirectory(insideLink,internal); linkDirectory(outsideLink,external); linkDirectory(rootLink,root);
			var result = new OutfitRegistryLoader(100).load(root);
			assertTrue(result.knowledge().modelValid(new OutfitId("inside_link"),OutfitModel.WIDE));
			for (String name : List.of("outside_link","self_link")) {
				assertTrue(result.knowledge().knownExisting(new OutfitId(name)));
				assertFalse(result.knowledge().metadataTrusted(new OutfitId(name)));
				assertTrue(result.diagnostics().stream().anyMatch(d -> d.entryName().equals(name) && d.code()==UNSAFE_PATH));
			}
		} finally { deleteLink(insideLink); deleteLink(outsideLink); deleteLink(rootLink); }
	}
	@Test void configuredRootAliasUsesResolvedAnchorAndDanglingChildIsNotDeletion() throws Exception {
		Path target = Files.createDirectory(temp.resolve("target"));
		Path root = temp.resolve("root_link"), gone = Files.createDirectory(temp.resolve("gone")), child = target.resolve("dangling");
		try {
			linkDirectory(root,target); linkDirectory(child,gone); Files.delete(gone);
			var result = new OutfitRegistryLoader(100).load(root);
			assertTrue(result.knowledge().trustworthy());
			assertTrue(result.knowledge().knownExisting(new OutfitId("dangling")));
			assertFalse(result.knowledge().metadataTrusted(new OutfitId("dangling")));
			assertFalse(result.knowledge().isDefinitelyAbsent(new OutfitId("dangling")));
		} finally { deleteLink(child); deleteLink(root); }
	}
	@Test void recognizedFilesThroughAliasCannotEscapeOutfitDirectory() throws Exception {
		OutfitFileAccess access = new OutfitFileAccess();
		var root = access.root(temp.resolve("root"));
		Path child = Files.createDirectory(root.real().resolve("child"));
		Path internal = Files.createDirectory(child.resolve("nested"));
		Path external = Files.createDirectory(temp.resolve("outside"));
		for (Path dir : List.of(internal,external)) {
			Files.writeString(dir.resolve("outfit.json"),metadata("\"wide\"")); Files.write(dir.resolve("wide.png"),png());
		}
		Path link = child.resolve("alias");
		try {
			linkDirectory(link,internal); var directory = access.directory(child,root);
			for (String role : List.of("outfit.json","wide.png")) assertArrayEquals(Files.readAllBytes(internal.resolve(role)),access.read(link.resolve(role),directory,root,65536));
			deleteLink(link); linkDirectory(link,external); directory = access.directory(child,root);
			var currentDirectory = directory;
			for (String role : List.of("outfit.json","wide.png")) assertEquals(UNSAFE_PATH,
				assertThrows(OutfitFileAccess.Failure.class, () -> access.read(link.resolve(role),currentDirectory,root,65536)).code);
		} finally { deleteLink(link); }
	}
	private static void linkDirectory(Path link, Path target) throws Exception {
		if (!System.getProperty("os.name").startsWith("Windows")) { Files.createSymbolicLink(link,target); return; }
		// Windows junction 不要求文件 symlink 权限；仅创建本测试临时目录中的链接。
		String command = "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path '"
			+ link.toAbsolutePath().toString().replace("'","''") + "' -Target '"
			+ target.toAbsolutePath().toString().replace("'","''") + "' | Out-Null";
		String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE));
		Process process = new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",encoded)
			.redirectErrorStream(true).start();
		assertTrue(process.waitFor(20,TimeUnit.SECONDS),"测试 junction 创建必须及时完成。");
		assertEquals(0,process.exitValue(),"测试 junction 创建失败："+new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
	}
	private static void deleteLink(Path link) throws IOException {
		// 只删除该链接本身；不递归遍历或删除目标。
		Files.deleteIfExists(link);
	}
}
