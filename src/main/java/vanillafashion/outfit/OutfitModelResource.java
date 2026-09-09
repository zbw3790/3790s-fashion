package vanillafashion.outfit;

import java.util.Objects;

public sealed interface OutfitModelResource {
	enum Unsupported implements OutfitModelResource { INSTANCE }
	record Invalid(OutfitDiagnostic.Code reason) implements OutfitModelResource {
		public Invalid { Objects.requireNonNull(reason, "模型失败必须有原因。"); }
	}
	record Valid(OutfitAsset asset) implements OutfitModelResource {
		public Valid { Objects.requireNonNull(asset, "有效模型必须有资产。"); }
	}
}
