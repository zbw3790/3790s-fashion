package dev.zbw3790.fashion.outfit;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.outfit.OutfitTestSupport.*;

class OutfitPngInferenceTest {
    @TempDir Path temp;
    byte[] pixel(int x,int y,int alpha) throws IOException {
        var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB); image.setRGB(x,y,(alpha<<24)|0x3790ff);
        var output=new ByteArrayOutputStream(); ImageIO.write(image,"PNG",output);image.flush();return output.toByteArray();
    }
    Path folder(String name) throws IOException { return Files.createDirectories(temp.resolve(name)); }
    OutfitRegistryLoadResult load() { return new OutfitRegistryLoader(4096).loadExisting(temp); }
    @ParameterizedTest @EnumSource(OutfitModel.class)
    void eachFaceAndSingleAlphaPixelIndependentlyProvidesExactlyItsPart(OutfitModel model) {
        for (var part:OutfitPart.values()) for (var face:OutfitOuterUv.faces(part,model)) {
            var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            image.setRGB(face.u(),face.v(),0x013790ff);
            assertEquals(Set.of(part),OutfitOuterUv.infer(image,model),part+" "+face); image.flush();
        }
    }
    @ParameterizedTest @EnumSource(OutfitModel.class)
    void baseAndUnusedPixelsDoNotSupplyOuterParts(OutfitModel model) {
        var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for (int[] uv:new int[][]{{8,8},{20,20},{44,20},{4,20},{20,52},{36,52},{0,0}}) image.setRGB(uv[0],uv[1],0xffabcdef);
        assertTrue(OutfitOuterUv.infer(image,model).isEmpty());image.flush();
    }
    @ParameterizedTest @EnumSource(OutfitModel.class)
    void pngOnlyKeepsExactBytesHashAndDoesNotInventOtherModel(OutfitModel model) throws Exception {
        byte[] bytes=pixel(40,8,1);Files.write(folder("hat").resolve(model.fileName()),bytes);
        var result=load();var entry=result.registry().find(new OutfitId("hat")).orElseThrow();
        assertEquals(Set.of(OutfitPart.HEAD),entry.metadata().parts());assertEquals(Set.of(model),entry.metadata().models());
        var asset=((OutfitModelResource.Valid)entry.model(model)).asset();assertArrayEquals(bytes,asset.bytes());
        assertEquals(dev.zbw3790.fashion.cape.CapeAssetHash.sha256(bytes),asset.sha256());
    }
    @Test void matchingDualModelsKeepBothIndependentHashes() throws Exception {
        Path folder=folder("hat");Files.write(folder.resolve("wide.png"),pixel(40,8,255));Files.write(folder.resolve("slim.png"),pixel(41,8,128));
        var loaded=load();assertEquals(2,loaded.assets().size());assertEquals(Set.of(OutfitModel.WIDE,OutfitModel.SLIM),loaded.registry().entries().getFirst().metadata().models());
    }
    @Test void dualModelMismatchIsExistingInvalidAndNeverSilentlyMerged() throws Exception {
        Path folder=folder("mismatch");Files.write(folder.resolve("wide.png"),pixel(40,8,255));Files.write(folder.resolve("slim.png"),pixel(20,36,255));
        var loaded=load();assertEquals(0,loaded.registry().size());assertTrue(loaded.knowledge().knownExisting(new OutfitId("mismatch")));
        assertTrue(loaded.diagnostics().stream().anyMatch(d->d.code()==OutfitDiagnostic.Code.INFERRED_PARTS_MISMATCH));
    }
    @Test void invalidJsonNeverFallsBackAndExplicitTransparencyRemainsLegal() throws Exception {
        Path bad=folder("invalid");Files.write(bad.resolve("wide.png"),pixel(40,8,255));Files.writeString(bad.resolve("outfit.json"),"{}");
        Path transparent=directory(temp,"explicit","\"wide\"");Files.write(transparent.resolve("wide.png"),png(64,64,0x003790ff));
        var loaded=load();assertFalse(loaded.knowledge().metadataTrusted(new OutfitId("invalid")));
        assertTrue(loaded.knowledge().modelValid(new OutfitId("explicit"),OutfitModel.WIDE));
        assertEquals(Set.of(OutfitPart.HEAD,OutfitPart.BODY),loaded.registry().entries().getFirst().metadata().parts());
    }
    @Test void automaticTransparentBaseOnlyAndEmptyFoldersAreInvalid() throws Exception {
        Files.write(folder("empty").resolve("wide.png"),png(64,64,0x00ffffff));
        Files.write(folder("base").resolve("wide.png"),pixel(8,8,255));folder("none");
        var loaded=load();assertEquals(0,loaded.registry().size());assertEquals(3,loaded.knowledge().knownExistingIds().size());
    }
    @Test void invalidCandidateModelRequiresExplicitMetadataInsteadOfGuessingSharedParts() throws Exception {
        Path folder=folder("damaged");Files.write(folder.resolve("wide.png"),pixel(40,8,255));Files.writeString(folder.resolve("slim.png"),"坏 PNG");
        var loaded=load();assertEquals(0,loaded.registry().size());assertTrue(loaded.knowledge().knownExisting(new OutfitId("damaged")));
    }
    @Test void looseFilesUnknownNamesAndNestedDirectoriesAreNotOutfits() throws Exception {
        Files.write(temp.resolve("wide.png"),pixel(40,8,255));Files.write(folder("unknown").resolve("classic.png"),pixel(40,8,255));
        Files.write(Files.createDirectories(folder("container").resolve("inner")).resolve("wide.png"),pixel(40,8,255));
        assertEquals(0,load().registry().size());
    }
    @Test void manualMissingOrFileRootNeverCreatesTrustedEmptyDirectory() throws Exception {
        Path absent=temp.resolve("missing");var loader=new OutfitRegistryLoader(10);
        assertFalse(loader.loadExisting(absent).knowledge().trustworthy());assertFalse(Files.exists(absent));
        Path file=Files.writeString(temp.resolve("file"),"不是目录");assertFalse(loader.loadExisting(file).knowledge().trustworthy());
    }
    @Test void pngOnlyUsesSameSizeDimensionsAndCrcValidation() throws Exception {
        Files.write(folder("size").resolve("wide.png"),new byte[65537]);
        Files.write(folder("dimensions").resolve("wide.png"),png(64,32,0xffffffff));
        byte[] bad=png();bad[bad.length-1]^=1;Files.write(folder("crc").resolve("wide.png"),bad);
        var loaded=load();assertEquals(0,loaded.registry().size());
        var codes=loaded.diagnostics().stream().map(OutfitDiagnostic::code).collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.containsAll(Set.of(OutfitDiagnostic.Code.TOO_LARGE,OutfitDiagnostic.Code.INVALID_DIMENSIONS,OutfitDiagnostic.Code.INVALID_PNG)));
    }
    @Test void unreadableManualRootIsUntrustedAndDoesNotInvokeStartupCreation() {
        var access=new OutfitFileAccess(){
            @Override Stamp root(Path path) {fail("手动重载不能走创建根路径。");return null;}
            @Override List<Path> children(Stamp directory,int limit) throws IOException {throw new java.nio.file.AccessDeniedException("受控测试拒绝访问");}
        };
        assertFalse(new OutfitRegistryLoader(4096,access).loadExisting(temp).knowledge().trustworthy());
    }
    @Test void autoCandidatesStopBeforeReading257thModelAndDoNotExceed512Assets() throws Exception {
        byte[] bytes=pixel(40,8,255);
        for(int i=0;i<257;i++) {Path folder=folder("auto_%03d".formatted(i));Files.write(folder.resolve("wide.png"),bytes);Files.write(folder.resolve("slim.png"),bytes);}
        int[] reads={0};var access=new OutfitFileAccess(){@Override void beforeOpen(Path path){if(path.toString().endsWith(".png"))reads[0]++;}};
        var loaded=new OutfitRegistryLoader(4096,access).loadExisting(temp);assertFalse(loaded.knowledge().trustworthy());
        assertEquals(512,reads[0]);assertEquals(0,loaded.assets().size());
    }
}
