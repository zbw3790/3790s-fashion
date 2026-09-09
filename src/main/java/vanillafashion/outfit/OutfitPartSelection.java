package vanillafashion.outfit;

import java.util.Objects;

/** 选择种类由类型表达，未知和未就绪不是选择值。 */
public sealed interface OutfitPartSelection {
	OutfitPartSelection ORIGINAL = Builtin.ORIGINAL;
	OutfitPartSelection NONE = Builtin.NONE;

	enum Builtin implements OutfitPartSelection { ORIGINAL, NONE }
	record Outfit(OutfitId id) implements OutfitPartSelection {
		public Outfit { Objects.requireNonNull(id, "装束选择必须携带 ID。"); }
	}
	static OutfitPartSelection outfit(OutfitId id) { return new Outfit(id); }
}
