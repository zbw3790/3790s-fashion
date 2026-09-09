package vanillafashion.outfit;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public enum OutfitPart {
	HEAD("head"), BODY("body"), LEFT_ARM("left_arm"), RIGHT_ARM("right_arm"),
	LEFT_LEG("left_leg"), RIGHT_LEG("right_leg");

	public static final List<OutfitPart> CANONICAL_ORDER = List.of(values());
	public static final Set<OutfitPart> ALL = immutableSet(EnumSet.allOf(OutfitPart.class));
	private final String serializedName;

	OutfitPart(String serializedName) { this.serializedName = serializedName; }
	public String serializedName() { return serializedName; }
	public static OutfitPart fromName(String value) {
		return CANONICAL_ORDER.stream().filter(part -> part.serializedName.equals(value)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("未知的装束部位。"));
	}

	public static Set<OutfitPart> immutableSet(Set<OutfitPart> values) {
		Objects.requireNonNull(values, "部位集合不能为 null。");
		EnumSet<OutfitPart> copy = EnumSet.noneOf(OutfitPart.class);
		for (OutfitPart part : values) copy.add(Objects.requireNonNull(part, "部位不能为 null。"));
		return Collections.unmodifiableSet(copy);
	}
}
