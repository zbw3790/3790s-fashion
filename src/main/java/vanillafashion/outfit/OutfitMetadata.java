package vanillafashion.outfit;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record OutfitMetadata(Set<OutfitPart> parts, Set<OutfitModel> models) {
	public static final int SCHEMA_VERSION = 1;

	public OutfitMetadata {
		parts = OutfitPart.immutableSet(parts);
		Objects.requireNonNull(models, "模型集合不能为 null。");
		if (parts.isEmpty() || models.isEmpty()) {
			throw new IllegalArgumentException("提供部位和声明模型均不能为空。");
		}
		EnumSet<OutfitModel> copy = EnumSet.noneOf(OutfitModel.class);
		for (OutfitModel model : models) copy.add(Objects.requireNonNull(model, "模型不能为 null。"));
		models = Collections.unmodifiableSet(copy);
	}
}
