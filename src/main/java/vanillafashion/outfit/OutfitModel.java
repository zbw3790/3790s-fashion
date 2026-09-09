package vanillafashion.outfit;

import java.util.List;

public enum OutfitModel {
	WIDE("wide", "wide.png"), SLIM("slim", "slim.png");

	public static final List<OutfitModel> CANONICAL_ORDER = List.of(values());
	private final String serializedName;
	private final String fileName;
	OutfitModel(String serializedName, String fileName) {
		this.serializedName = serializedName;
		this.fileName = fileName;
	}
	public String serializedName() { return serializedName; }
	public String fileName() { return fileName; }
	public static OutfitModel fromName(String value) {
		return CANONICAL_ORDER.stream().filter(model -> model.serializedName.equals(value)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("未知的装束模型。"));
	}
}
