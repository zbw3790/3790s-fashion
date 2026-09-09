package vanillafashion.outfit;

import java.util.List;
import java.util.Objects;

public record OutfitRegistryLoadResult(OutfitRegistry registry, OutfitRegistryKnowledge knowledge,
		OutfitAssetIndex assets, List<OutfitDiagnostic> diagnostics) {
	public OutfitRegistryLoadResult {
		Objects.requireNonNull(registry); Objects.requireNonNull(knowledge); Objects.requireNonNull(assets);
		diagnostics = List.copyOf(diagnostics);
	}
}
