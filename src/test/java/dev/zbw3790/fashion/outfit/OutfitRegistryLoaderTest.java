package dev.zbw3790.fashion.outfit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.outfit.OutfitTestSupport.*;
import static dev.zbw3790.fashion.outfit.OutfitDiagnostic.Code.*;

class OutfitRegistryLoaderTest {
	@TempDir Path temp;
	private final OutfitRegistryLoader loader = new OutfitRegistryLoader(100);
	private static OutfitId id(String text) { return new OutfitId(text); }
	private Path root() { return temp.resolve("outfits"); }
	private OutfitRegistryEntry entry(OutfitRegistryLoadResult result, String name) {
		return result.registry().find(id(name)).orElseThrow();
	}
	@Test void rootLocationAndMissingRootFollowExistingCreateSemantics() {
		assertEquals(temp.resolve("3790s-fashion/outfits"),OutfitRegistryLoader.rootUnder(temp));
		var result = loader.load(root());
		assertTrue(Files.isDirectory(root())); assertTrue(result.knowledge().trustworthy());
		assertTrue(result.knowledge().isDefinitelyAbsent(id("missing")));
		assertEquals(0,result.registry().size()); assertTrue(result.diagnostics().isEmpty());
	}
	@ParameterizedTest @ValueSource(strings={"wide","slim"})
	void oneModelDoesNotInferOtherModel(String modelName) throws Exception {
		OutfitModel model = OutfitModel.fromName(modelName);
		Path dir = directory(root(),"single","\""+modelName+"\"");
		Files.write(dir.resolve(model.fileName()),png());
		var result = loader.load(root());
		var entry = entry(result,"single");
		assertEquals(Set.of(model),entry.metadata().models());
		assertInstanceOf(OutfitModelResource.Valid.class,entry.model(model));
		assertSame(OutfitModelResource.Unsupported.INSTANCE,entry.model(model==OutfitModel.WIDE?OutfitModel.SLIM:OutfitModel.WIDE));
		assertEquals(1,result.assets().references().size());
	}
	@Test void bothModelsSameBytesKeepTwoRolesAndContentDeduplicatesAcrossIds() throws Exception {
		byte[] bytes = png();
		for (String name : List.of("zeta","alpha")) {
			Path dir = directory(root(),name,"\"slim\",\"wide\"");
			Files.write(dir.resolve("wide.png"),bytes); Files.write(dir.resolve("slim.png"),bytes);
		}
		var first = loader.load(root()); var second = loader.load(root());
		assertEquals(List.of(id("alpha"),id("zeta")),first.registry().entries().stream().map(OutfitRegistryEntry::id).toList());
		assertEquals(first.registry().entries(),second.registry().entries());
		assertEquals(first.assets().references(),second.assets().references());
		assertEquals(first.diagnostics(),second.diagnostics());
		assertEquals(1,first.assets().size()); assertEquals(4,first.assets().references().size());
		assertEquals(List.of(new OutfitAssetIndex.Reference(id("alpha"),OutfitModel.WIDE),
			new OutfitAssetIndex.Reference(id("alpha"),OutfitModel.SLIM),
			new OutfitAssetIndex.Reference(id("zeta"),OutfitModel.WIDE),
			new OutfitAssetIndex.Reference(id("zeta"),OutfitModel.SLIM)),new ArrayList<>(first.assets().references().keySet()));
		String hash = first.assets().references().values().iterator().next();
		assertArrayEquals(bytes,first.assets().find(hash).orElseThrow().bytes());
		assertThrows(UnsupportedOperationException.class, () -> first.assets().references().clear());
		assertThrows(UnsupportedOperationException.class, () -> entry(first,"alpha").resources().clear());
	}
	@Test void undeclaredModelAndExtraFilesAreNeverRead() throws Exception {
		Path dir = directory(root(),"wide_only","\"wide\"");
		Files.write(dir.resolve("wide.png"),png()); Files.write(dir.resolve("slim.png"),png());
		Files.writeString(dir.resolve("README.txt"),"仅供管理员阅读。");
		Files.writeString(dir.resolve("preview.png"),"不是资产。");
		Files.writeString(dir.resolve("classic.png"),"不是模型别名。");
		List<String> opened = new ArrayList<>();
		OutfitFileAccess access = new OutfitFileAccess() {
			@Override void beforeOpen(Path path) { opened.add(path.getFileName().toString()); }
		};
		var result = new OutfitRegistryLoader(100,access).load(root());
		assertEquals(List.of("outfit.json","wide.png"),opened);
		assertTrue(result.knowledge().modelValid(id("wide_only"),OutfitModel.WIDE));
		assertFalse(result.knowledge().modelDeclared(id("wide_only"),OutfitModel.SLIM));
		assertEquals(List.of(new OutfitDiagnostic("wide_only","slim.png",UNDECLARED_MODEL_FILE)),result.diagnostics());
	}
	@Test void oneBadModelKeepsMetadataAndValidSibling() throws Exception {
		Path dir = directory(root(),"mixed","\"wide\",\"slim\"");
		Files.write(dir.resolve("wide.png"),png()); Files.writeString(dir.resolve("slim.png"),"坏 PNG");
		var result = loader.load(root()); var knowledge = result.knowledge();
		assertTrue(knowledge.knownExisting(id("mixed"))); assertTrue(knowledge.metadataTrusted(id("mixed")));
		assertTrue(knowledge.partDeclared(id("mixed"),OutfitPart.HEAD));
		assertFalse(knowledge.partDeclared(id("mixed"),OutfitPart.LEFT_LEG));
		assertTrue(knowledge.modelDeclared(id("mixed"),OutfitModel.SLIM));
		assertFalse(knowledge.modelValid(id("mixed"),OutfitModel.SLIM));
		assertTrue(knowledge.modelValid(id("mixed"),OutfitModel.WIDE));
		assertEquals(new OutfitModelResource.Invalid(INVALID_PNG),entry(result,"mixed").model(OutfitModel.SLIM));
	}
	@Test void allBadModelsKeepTrustedPartKnowledgeAndReasons() throws Exception {
		Path dir = directory(root(),"broken_models","\"wide\",\"slim\"");
		Files.write(dir.resolve("wide.png"),png(64,32,0xff123456));
		var result = loader.load(root());
		assertTrue(result.knowledge().metadataTrusted(id("broken_models")));
		assertEquals(Set.of(OutfitPart.HEAD,OutfitPart.BODY),entry(result,"broken_models").metadata().parts());
		assertEquals(new OutfitModelResource.Invalid(INVALID_DIMENSIONS),entry(result,"broken_models").model(OutfitModel.WIDE));
		assertEquals(new OutfitModelResource.Invalid(MISSING_FILE),entry(result,"broken_models").model(OutfitModel.SLIM));
		assertEquals(0,result.assets().size());
	}
	@Test void badChildrenKeepExistenceWithoutGuessingMetadataOrAffectingSibling() throws Exception {
		Path good = directory(root(),"good","\"wide\""); Files.write(good.resolve("wide.png"),png());
		Path bad = directory(root(),"bad","\"wide\""); Files.writeString(bad.resolve("outfit.json"),"{}");
		Files.write(bad.resolve("wide.png"),png());
		Files.createDirectories(root().resolve("missing")); Files.createDirectories(root().resolve("BadId"));
		Files.writeString(root().resolve("occupied"),"普通文件占位");
		var result = loader.load(root());
		assertEquals(List.of(id("good")),result.registry().entries().stream().map(OutfitRegistryEntry::id).toList());
		for (String name : List.of("bad","missing","occupied")) {
			assertTrue(result.knowledge().knownExisting(id(name)));
			assertFalse(result.knowledge().metadataTrusted(id(name)));
			assertFalse(result.knowledge().isDefinitelyAbsent(id(name)));
		}
		assertTrue(result.diagnostics().stream().anyMatch(d -> d.code()==INVALID_ID));
		assertEquals(1,result.assets().size());
	}
	@Test void neverRecursesOrFindsArbitraryPngAndNamesAreExact() throws Exception {
		Path container = Files.createDirectories(root().resolve("container"));
		Path nested = directory(container,"nested","\"wide\""); Files.write(nested.resolve("wide.png"),png());
		Path cases = directory(root(),"case_test","\"wide\""); Files.write(cases.resolve("Wide.png"),png());
		Path metaCase = Files.createDirectories(root().resolve("meta_case"));
		Files.writeString(metaCase.resolve("Outfit.json"),metadata("\"wide\""));
		Files.write(metaCase.resolve("wide.png"),png());
		var result = loader.load(root());
		assertFalse(result.knowledge().knownExisting(id("nested")));
		assertFalse(result.knowledge().metadataTrusted(id("container")));
		assertFalse(result.knowledge().metadataTrusted(id("meta_case")));
		assertEquals(new OutfitModelResource.Invalid(MISSING_FILE),entry(result,"case_test").model(OutfitModel.WIDE));
	}
	@Test void childEnumerationFailureIsIsolatedAndNeverReadsItsModels() throws Exception {
		for (String name : List.of("bad","good")) {
			Path dir = directory(root(),name,"\"wide\""); Files.write(dir.resolve("wide.png"),png());
		}
		OutfitFileAccess access = new OutfitFileAccess() {
			@Override List<Path> children(Stamp directory,int limit) throws IOException {
				if (directory.real().getFileName().toString().equals("bad")) throw new IOException("测试单目录枚举失败。");
				return super.children(directory,limit);
			}
		};
		var result = new OutfitRegistryLoader(100,access).load(root());
		assertTrue(result.knowledge().trustworthy());
		assertTrue(result.knowledge().modelValid(id("good"),OutfitModel.WIDE));
		assertTrue(result.knowledge().knownExisting(id("bad")));
		assertFalse(result.knowledge().metadataTrusted(id("bad")));
		assertFalse(result.knowledge().isDefinitelyAbsent(id("bad")));
	}
	@Test void nonRegularDeclaredModelIsFailureWithoutDiscardingMetadata() throws Exception {
		Path dir = directory(root(),"folder_model","\"wide\"");
		Files.createDirectory(dir.resolve("wide.png"));
		var result = loader.load(root());
		assertTrue(result.knowledge().metadataTrusted(id("folder_model")));
		assertEquals(new OutfitModelResource.Invalid(NOT_REGULAR_FILE),entry(result,"folder_model").model(OutfitModel.WIDE));
	}
	@Test void rootFailureAndEnumerationFailureAreNotDeletionKnowledge() throws Exception {
		Files.writeString(root(),"根被文件占位。");
		var fileRoot = loader.load(root());
		assertFalse(fileRoot.knowledge().trustworthy()); assertFalse(fileRoot.knowledge().isDefinitelyAbsent(id("anything")));
		OutfitFileAccess failing = new OutfitFileAccess() {
			@Override List<Path> children(Stamp directory,int limit) throws IOException { throw new IOException("测试枚举失败。"); }
		};
		var failed = new OutfitRegistryLoader(100,failing).load(temp.resolve("other"));
		assertFalse(failed.knowledge().trustworthy()); assertEquals(0,failed.registry().size());
		assertFalse(failed.knowledge().isDefinitelyAbsent(id("anything")));
	}
	@Test void scanBudgetsBoundWorkAndFailureDoesNotInventAbsence() throws Exception {
		Files.createDirectories(root().resolve("one")); Files.createDirectories(root().resolve("two"));
		var result = new OutfitRegistryLoader(1).load(root());
		assertFalse(result.knowledge().trustworthy());
		assertEquals(SCAN_LIMIT,result.diagnostics().getFirst().code());
		assertFalse(result.knowledge().isDefinitelyAbsent(id("three")));
		assertThrows(IllegalArgumentException.class, () -> new OutfitRegistryLoader(0));
		assertThrows(IllegalArgumentException.class, () -> new OutfitRegistryLoader(Integer.MAX_VALUE));
	}
	@Test void oversizeMetadataIsUntrustedWhileOversizeModelIsLocalFailure() throws Exception {
		Path meta = directory(root(),"huge_meta","\"wide\"");
		Files.write(meta.resolve("outfit.json"),new byte[4097]);
		Path image = directory(root(),"huge_png","\"wide\""); Files.write(image.resolve("wide.png"),new byte[65537]);
		var result = loader.load(root());
		assertFalse(result.knowledge().metadataTrusted(id("huge_meta")));
		assertTrue(result.knowledge().metadataTrusted(id("huge_png")));
		assertEquals(new OutfitModelResource.Invalid(TOO_LARGE),entry(result,"huge_png").model(OutfitModel.WIDE));
	}
	@Test void deletionRequiresFreshAbsenceAndStableRootIdentity() throws Exception {
		var before = loader.load(root());
		assertTrue(before.knowledge().isDefinitelyAbsent(id("later")));
		Files.writeString(root().resolve("later"),"新占位");
		assertFalse(before.knowledge().isDefinitelyAbsent(id("later")));
		Files.move(root(),temp.resolve("previous")); Files.createDirectories(root());
		assertFalse(before.knowledge().isDefinitelyAbsent(id("missing")));
	}
	@Test void publishedAssetsSurviveSourceChangesWithoutReopeningPaths() throws Exception {
		Path dir = directory(root(),"snapshot","\"wide\""); byte[] original = png();
		Files.write(dir.resolve("wide.png"),original);
		var result = loader.load(root()); String hash = result.assets().references().values().iterator().next();
		Files.writeString(dir.resolve("wide.png"),"扫描完成后管理员可以修改，旧快照仍持有旧字节。");
		assertArrayEquals(original,result.assets().find(hash).orElseThrow().bytes());
	}
	@Test void diagnosticsAreBoundedAndContainNoPrivatePath() throws Exception {
		Files.createDirectories(root().resolve("Invalid-"+"x".repeat(100)));
		var result = loader.load(root());
		assertEquals(1,result.diagnostics().size());
		assertEquals(64,result.diagnostics().getFirst().entryName().codePointCount(0,result.diagnostics().getFirst().entryName().length()));
		assertFalse(result.diagnostics().toString().contains(temp.toString()));
		assertFalse(new OutfitDiagnostic("a\nb","directory",INVALID_ID).entryName().contains("\n"));
	}
}
