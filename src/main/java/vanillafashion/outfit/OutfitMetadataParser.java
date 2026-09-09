package vanillafashion.outfit;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 使用既有 Gson 流式解析，拒绝类型转换、重复键和未知字段。 */
public final class OutfitMetadataParser {
	public static final int MAX_METADATA_BYTES = 4096;
	private OutfitMetadataParser() {}

	public static OutfitMetadata parse(byte[] bytes) {
		Objects.requireNonNull(bytes, "metadata 字节不能为 null。");
		if (bytes.length > MAX_METADATA_BYTES) throw invalid();
		try {
			String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
			try (JsonReader reader = new JsonReader(new StringReader(text))) {
				reader.setStrictness(Strictness.STRICT);
				require(reader, JsonToken.BEGIN_OBJECT);
				reader.beginObject();
				Set<String> keys = new HashSet<>();
				Set<OutfitPart> parts = EnumSet.noneOf(OutfitPart.class);
				Set<OutfitModel> models = EnumSet.noneOf(OutfitModel.class);
				while (reader.hasNext()) {
					String key = reader.nextName();
					if (!keys.add(key)) throw invalid();
					switch (key) {
						case "schema_version" -> {
							require(reader, JsonToken.NUMBER);
							if (new BigDecimal(reader.nextString()).compareTo(BigDecimal.ONE) != 0) throw invalid();
						}
						case "parts" -> {
							require(reader, JsonToken.BEGIN_ARRAY); reader.beginArray();
							while (reader.hasNext()) {
								require(reader, JsonToken.STRING);
								if (!parts.add(OutfitPart.fromName(reader.nextString()))) throw invalid();
							}
							reader.endArray();
						}
						case "models" -> {
							require(reader, JsonToken.BEGIN_ARRAY); reader.beginArray();
							while (reader.hasNext()) {
								require(reader, JsonToken.STRING);
								if (!models.add(OutfitModel.fromName(reader.nextString()))) throw invalid();
							}
							reader.endArray();
						}
						default -> throw invalid();
					}
				}
				reader.endObject(); require(reader, JsonToken.END_DOCUMENT);
				if (!keys.equals(Set.of("schema_version", "parts", "models"))) throw invalid();
				return new OutfitMetadata(parts, models);
			}
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("装束 metadata 不符合严格 v1 契约。", exception);
		}
	}
	private static void require(JsonReader reader, JsonToken token) throws IOException {
		if (reader.peek() != token) throw invalid();
	}
	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("装束 metadata 结构、版本或大小不合法。");
	}
}
