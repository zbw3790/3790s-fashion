package dev.zbw3790.fashion.outfit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 只有可信 metadata 能形成条目；每个声明模型独立记录成功或失败。 */
public record OutfitRegistryEntry(OutfitId id, OutfitMetadata metadata,
		Map<OutfitModel, OutfitModelResource> resources) {
	public OutfitRegistryEntry {
		Objects.requireNonNull(id, "条目必须有 ID。");
		Objects.requireNonNull(metadata, "条目必须有可信 metadata。");
		Objects.requireNonNull(resources, "条目必须有逐模型结果。");
		if (!resources.keySet().equals(metadata.models())) {
			throw new IllegalArgumentException("模型结果必须恰好覆盖声明模型。");
		}
		EnumMap<OutfitModel, OutfitModelResource> copy = new EnumMap<>(OutfitModel.class);
		for (OutfitModel model : metadata.models()) {
			OutfitModelResource value = Objects.requireNonNull(resources.get(model), "模型结果不能为 null。");
			if (value instanceof OutfitModelResource.Unsupported) {
				throw new IllegalArgumentException("已声明模型不能记录为未支持。");
			}
			copy.put(model, value);
		}
		resources = Collections.unmodifiableMap(copy);
	}
	public OutfitModelResource model(OutfitModel model) {
		return resources.getOrDefault(Objects.requireNonNull(model), OutfitModelResource.Unsupported.INSTANCE);
	}
}
