package dev.zbw3790.fashion.outfit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.outfit.OutfitTestSupport.metadata;

class OutfitMetadataParserTest {
	private static OutfitMetadata parse(String json) { return OutfitMetadataParser.parse(json.getBytes(StandardCharsets.UTF_8)); }
	static Stream<String> invalidMetadata() {
		String valid = metadata("\"wide\"");
		return Stream.of("", "null", "[]", "1", "true", "{}", "{", valid + "{}",
			valid.replace("1,", "2,"), valid.replace("1,", "1.01,"), valid.replace("1,", "true,"),
			valid.replace("1,", "\"1\","), valid.replace("1,", "null,"), valid.replace("1,", "NaN,"),
			valid.replace("1,", "01,"), valid.replace("1,", "+1,"),
			valid.replace("\"schema_version\":1,", ""), valid.replace("\"parts\":[\"head\",\"body\"],", ""),
			"{\"schema_version\":1,\"parts\":[\"head\"]}",
			valid.replace("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1"),
			valid.replace("\"schema_version\":1", "\"schema_version\":1,\"extra\":1"),
			valid.replace("\"parts\":", "\"parts\":[\"head\"],\"parts\":"),
			valid.replace("\"models\":", "\"models\":[\"wide\"],\"models\":"),
			valid.replace("[\"head\",\"body\"]", "\"head\""),
			valid.replace("[\"head\",\"body\"]", "null"), valid.replace("[\"wide\"]", "{}"),
			metadata("", "\"wide\""), metadata("\"head\"", ""),
			metadata("\"head\",\"head\"", "\"wide\""), metadata("\"HEAD\"", "\"wide\""),
			metadata("\"cape\"", "\"wide\""), metadata("1", "\"wide\""), metadata("null", "\"wide\""),
			metadata("{}", "\"wide\""), metadata("\"head\"", "\"classic\""),
			metadata("\"head\"", "\"WIDE\""), metadata("\"head\"", "\"wide\",\"wide\""),
			metadata("\"head\"", "1"), metadata("\"head\"", "null"),
			valid.replace("1,", "1,/* 注释 */"), valid.replace("1,", "1,// 注释\n"),
			valid.replace("\"wide\"]", "\"wide\",]"), valid.replace("1,", "1,}"),
			valid.replace("\"parts\":", "\"part\":"),
			valid.replace("1,", "1,\"display_name\":\"帽子\","),
			valid.replace("1,", "1,\"wide_parts\":[\"head\"],"),
			valid.replace("1,", "1,\"path\":\"../other.png\","),
			valid.replace("1,", "1,\"hash\":\"" + "0".repeat(64) + "\","),
			valid.replace("\"wide\"", "'wide'"), valid.replace("\"wide\"", "wide"),
			valid.replace("\"head\"", "\"he\nad\""));
	}
	@ParameterizedTest @MethodSource("invalidMetadata")
	void rejectsWithoutCoercion(String json) { assertThrows(IllegalArgumentException.class, () -> parse(json)); }
	@Test void canonicalOrderDoesNotDependOnJsonOrder() {
		var reversed = parse("{\"models\":[\"slim\",\"wide\"],\"parts\":[\"right_leg\",\"left_leg\",\"right_arm\",\"left_arm\",\"body\",\"head\"],\"schema_version\":1}");
		assertEquals(OutfitPart.CANONICAL_ORDER, List.copyOf(reversed.parts()));
		assertEquals(OutfitModel.CANONICAL_ORDER, List.copyOf(reversed.models()));
		assertEquals(new OutfitMetadata(OutfitPart.ALL, Set.of(OutfitModel.WIDE, OutfitModel.SLIM)), reversed);
		assertThrows(UnsupportedOperationException.class, () -> reversed.parts().clear());
		assertThrows(UnsupportedOperationException.class, () -> reversed.models().clear());
	}
	@Test void numericOneMatchesSchemaIntegerSemantics() {
		for (String number : List.of("1", "1.0", "1e0", "10e-1"))
			assertEquals(parse(metadata("\"wide\"")), parse(metadata("\"wide\"").replace("1,", number + ",")));
	}
	@Test void utf8AndMetadataBudgetAreStrict() {
		assertThrows(IllegalArgumentException.class, () -> OutfitMetadataParser.parse(new byte[]{(byte)0xc0,(byte)0xaf}));
		String valid = metadata("\"wide\"");
		assertEquals(parse(valid), parse(valid + " ".repeat(4096 - valid.length())));
		assertThrows(IllegalArgumentException.class, () -> parse(valid + " ".repeat(4097 - valid.length())));
		assertThrows(IllegalArgumentException.class, () -> parse(valid.replace("1,", "1e999999999999,")));
	}
	@Test void documentedExamplesAreSelfContained() {
		// 将冻结示例作为独立测试输入，不依赖内部文档随公开测试分发。
		assertEquals(OutfitPart.ALL, parse(metadata("\"head\",\"body\",\"left_arm\",\"right_arm\",\"left_leg\",\"right_leg\"", "\"wide\",\"slim\"")).parts());
		assertEquals(Set.of(OutfitPart.HEAD), parse(metadata("\"head\"", "\"wide\",\"slim\"")).parts());
		assertEquals(OutfitGroup.UPPER_GROUP.parts(), parse(metadata("\"body\",\"left_arm\",\"right_arm\"", "\"wide\"")).parts());
		assertEquals(Set.of(OutfitModel.SLIM), parse(metadata("\"left_arm\"", "\"slim\"")).models());
	}
}
