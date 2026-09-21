package dev.zbw3790.fashion.armor;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.outfit.ValidatedAssetFiles;
import static dev.zbw3790.fashion.armor.ArmorTextureFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorRegistryTest {
    @TempDir Path root;
    @Test void completeAndPartialStylesOwnImmutableBytesAndHashes() throws Exception {
        var first=style(root,"blue",0xff3790ff);var partial=root.resolve("head");Files.createDirectory(partial);
        Files.writeString(partial.resolve("armor.json"),metadata("head","[\"head\"]","{\"outer\":\"head.png\"}"));Files.write(partial.resolve("head.png"),png(0xffcc8800));
        var loaded=new ArmorRegistryLoader().loadExisting(root);assertTrue(loaded.snapshot().available());assertEquals(2,loaded.snapshot().entries().size());
        assertTrue(loaded.snapshot().supports(new ArmorStyleId("head"),ArmorSlot.HEAD));assertFalse(loaded.snapshot().supports(new ArmorStyleId("head"),ArmorSlot.LEGS));
        String expected=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(first.resolve("outer.png"))));
        assertEquals(expected,loaded.snapshot().find(new ArmorStyleId("blue")).orElseThrow().hashes().get(ArmorGeometry.OUTER));
        byte[] copy=loaded.assets().get(expected).bytes();copy[0]=0;assertNotEquals(0,loaded.assets().get(expected).bytes()[0]);
    }
    @ParameterizedTest @ValueSource(strings={"missing","dimension","alpha","crc","metadata","duplicate","unknown","escape","undeclared","wrong_id","declared_legs"})
    void badStyleQuarantinedWithoutHurtingGood(String problem) throws Exception {
        style(root,"good",0xff00ccff);var bad=style(root,"bad",0xff8844cc);
        switch(problem) {
            case "missing"->Files.delete(bad.resolve("inner.png"));case "dimension"->Files.write(bad.resolve("outer.png"),png(64,64,0xffffffff));
            case "alpha"->Files.write(bad.resolve("outer.png"),png(0x7f444444));case "crc"->{byte[] data=Files.readAllBytes(bad.resolve("outer.png"));data[29]^=1;Files.write(bad.resolve("outer.png"),data);}
            case "metadata"->Files.writeString(bad.resolve("armor.json"),"{}");
            case "duplicate"->Files.writeString(bad.resolve("armor.json"),Files.readString(bad.resolve("armor.json")).replace("{\"format_version\":1","{\"format_version\":1,\"format_version\":1"));
            case "unknown"->Files.writeString(bad.resolve("armor.json"),Files.readString(bad.resolve("armor.json")).replace("{\"format_version\":1","{\"extra\":true,\"format_version\":1"));
            case "escape"->Files.writeString(bad.resolve("armor.json"),metadata("bad","[\"head\"]","{\"outer\":\"../outer.png\"}"));
            case "undeclared"->Files.writeString(bad.resolve("extra.txt"),"x");
            case "wrong_id"->Files.writeString(bad.resolve("armor.json"),metadata("good","[\"head\"]","{\"outer\":\"outer.png\"}"));
            case "declared_legs"->Files.writeString(bad.resolve("armor.json"),metadata("bad","[\"legs\"]","{\"outer\":\"outer.png\"}"));
            default->throw new AssertionError();
        }
        var loaded=new ArmorRegistryLoader().loadExisting(root);assertTrue(loaded.snapshot().available());assertEquals(List.of(new ArmorStyleId("good")),loaded.snapshot().entries().stream().map(ArmorRegistrySnapshot.Entry::id).toList());assertFalse(loaded.diagnostics().isEmpty());
    }
    @Test void finalRecheckRejectsMixedRead() throws Exception {
        var dir=style(root,"blue",0xff3790ff);
        var access=new ValidatedAssetFiles(){int count;@Override public byte[] read(Path path,Directory d,Directory r,int max)throws IOException {
            var bytes=super.read(path,d,r,max);if(++count==3)Files.write(dir.resolve("outer.png"),png(0xff883311));return bytes;
        }};
        var loaded=new ArmorRegistryLoader(access).loadExisting(root);assertFalse(loaded.snapshot().available());assertTrue(loaded.assets().isEmpty());
    }
    @Test void absentRootIsNotTrustedEmptyAndDoesNotCreateOnReload() {Path missing=root.resolve("missing");assertFalse(new ArmorRegistryLoader().loadExisting(missing).snapshot().available());assertFalse(Files.exists(missing));}
    @Test void tooManyStylesRejectWholeSnapshot() throws Exception {for(int i=0;i<129;i++)style(root,"s"+i,0xff000000+i);var loaded=new ArmorRegistryLoader().loadExisting(root);assertFalse(loaded.snapshot().available());assertTrue(loaded.assets().isEmpty());}
    @Test void storedDormantIsDistinctFromEffective() {var stored=ArmorSelections.original().with(ArmorSlot.HEAD,ArmorSelection.custom(new ArmorStyleId("missing"))).with(ArmorSlot.CHEST,ArmorSelection.HIDDEN);var effective=ArmorRegistrySnapshot.empty().effective(stored);assertEquals(ArmorSelection.ORIGINAL,effective.get(ArmorSlot.HEAD));assertEquals(ArmorSelection.HIDDEN,effective.get(ArmorSlot.CHEST));assertInstanceOf(ArmorSelection.Custom.class,stored.get(ArmorSlot.HEAD));}
    @Test void oversizedAndHashMismatchRejected() throws Exception {assertThrows(IllegalArgumentException.class,()->ArmorAsset.fromBytes(new byte[16385]));byte[] bytes=png(0xff00aaff);assertThrows(IllegalArgumentException.class,()->ArmorAsset.verified("0".repeat(64),bytes));}
    @ParameterizedTest @ValueSource(strings={"absolute","url","name_long","control","unicode_bytes","text_bytes","file_bytes","directory_file","truncated","bad_utf8"})
    void explicitResourceBoundsRejectMalformedStyle(String failure) throws Exception {
        style(root,"good",0xff112233);var bad=style(root,"bad",0xff334455);var json=bad.resolve("armor.json");
        switch(failure){
            case "absolute" -> Files.writeString(json,metadata("bad","[\"head\"]","{\"outer\":\"C:/outside.png\"}"));
            case "url" -> Files.writeString(json,metadata("bad","[\"head\"]","{\"outer\":\"https://example.test/a.png\"}"));
            case "name_long" -> {var object=com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject();object.addProperty("name","x".repeat(81));Files.writeString(json,object.toString());}
            case "control" -> { var text=Files.readString(json);var object=com.google.gson.JsonParser.parseString(text).getAsJsonObject();object.addProperty("name","bad\nname");Files.writeString(json,object.toString()); }
            case "unicode_bytes" -> {var object=com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject();object.addProperty("name","😀".repeat(80));Files.writeString(json,object.toString());}
            case "text_bytes" -> Files.write(json,new byte[4097]);
            case "file_bytes" -> Files.write(bad.resolve("outer.png"),new byte[16385]);
            case "directory_file" -> {Files.delete(bad.resolve("outer.png"));Files.createDirectory(bad.resolve("outer.png"));}
            case "truncated" -> Files.write(bad.resolve("outer.png"),java.util.Arrays.copyOf(png(0xff334455),30));
            case "bad_utf8" -> Files.write(json,new byte[]{(byte)0xc3,(byte)0x28});
            default -> throw new AssertionError();
        }
        var loaded=new ArmorRegistryLoader().loadExisting(root);
        assertTrue(loaded.snapshot().available());assertEquals(1,loaded.snapshot().entries().size(),failure);assertEquals("good",loaded.snapshot().entries().getFirst().id().value());
    }
    @Test void excessiveRootEntriesRejectWholeSnapshot() throws Exception {
        for(int i=0;i<4097;i++)Files.createFile(root.resolve("invalid-"+i));
        assertFalse(new ArmorRegistryLoader().loadExisting(root).snapshot().available());
    }
    @Test void redirectedRootAndExternalStyleAreRejectedWithoutReadingTargetAsAuthority() throws Exception {
        var actual=root.resolve("actual");Files.createDirectory(actual);style(actual,"blue",0xff3790ff);
        var alias=root.resolve("alias");directoryLink(alias,actual);
        try {assertFalse(new ArmorRegistryLoader().loadExisting(alias).snapshot().available());}
        finally {Files.delete(alias);}
        var resources=root.resolve("resources");Files.createDirectory(resources);style(resources,"good",0xff448899);
        var external=resources.resolve("blue");directoryLink(external,actual.resolve("blue"));
        try {var loaded=new ArmorRegistryLoader().loadExisting(resources);assertTrue(loaded.snapshot().available());assertEquals(List.of("good"),loaded.snapshot().entries().stream().map(e->e.id().value()).toList());}
        finally {Files.delete(external);}
        assertTrue(Files.isRegularFile(actual.resolve("blue/outer.png")));
    }
    private static void directoryLink(Path link,Path target) throws Exception {
        if(!System.getProperty("os.name").startsWith("Windows")){Files.createSymbolicLink(link,target);return;}
        String script="$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path '"+link.toAbsolutePath().toString().replace("'","''")+"' -Target '"+target.toAbsolutePath().toString().replace("'","''")+"' | Out-Null";
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
        var process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",encoded).redirectErrorStream(true).start();
        assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
    }
}
