package dev.zbw3790.fashion.armor;

import com.google.gson.Strictness;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** 明确字段类型，拒绝重复键、未知字段与隐式转换。 */
public final class ArmorMetadataParser {
    public static final int MAX_BYTES=4096;
    private ArmorMetadataParser() { }
    public static ArmorMetadata parse(byte[] bytes) {
        if(bytes.length>MAX_BYTES) throw invalid();
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try(var r=new JsonReader(new StringReader(text))) {
                r.setStrictness(Strictness.STRICT); require(r,JsonToken.BEGIN_OBJECT); r.beginObject();
                var keys=new HashSet<String>(); var slots=EnumSet.noneOf(ArmorSlot.class); var textures=new EnumMap<ArmorGeometry,String>(ArmorGeometry.class);
                ArmorStyleId id=null; String name=null;
                while(r.hasNext()) {
                    String key=r.nextName(); if(!keys.add(key)) throw invalid();
                    switch(key) {
                        case "format_version" -> { require(r,JsonToken.NUMBER); if(!r.nextString().equals("1")) throw invalid(); }
                        case "id" -> id=new ArmorStyleId(string(r));
                        case "name" -> name=string(r);
                        case "slots" -> { require(r,JsonToken.BEGIN_ARRAY); r.beginArray(); while(r.hasNext()) if(!slots.add(ArmorSlot.fromName(string(r)))) throw invalid(); r.endArray(); }
                        case "textures" -> {
                            require(r,JsonToken.BEGIN_OBJECT); r.beginObject();
                            while(r.hasNext()) if(textures.put(ArmorGeometry.fromName(r.nextName()),string(r))!=null) throw invalid();
                            r.endObject();
                        }
                        default -> throw invalid();
                    }
                }
                r.endObject(); require(r,JsonToken.END_DOCUMENT);
                if(!keys.equals(Set.of("format_version","id","name","slots","textures"))) throw invalid();
                return new ArmorMetadata(id,name,slots,textures);
            }
        } catch(IOException | IllegalArgumentException exception) { throw new IllegalArgumentException("盔甲 metadata 不符合严格 v1 契约。",exception); }
    }
    private static String string(JsonReader r) throws IOException { require(r,JsonToken.STRING); return r.nextString(); }
    private static void require(JsonReader r,JsonToken token) throws IOException { if(r.peek()!=token) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("盔甲 metadata 结构、版本或大小不合法。"); }
}
