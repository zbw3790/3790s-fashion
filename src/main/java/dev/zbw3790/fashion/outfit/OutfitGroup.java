package dev.zbw3790.fashion.outfit;

import java.util.Set;

public enum OutfitGroup {
	HEAD_GROUP(Set.of(OutfitPart.HEAD)),
	UPPER_GROUP(Set.of(OutfitPart.BODY, OutfitPart.LEFT_ARM, OutfitPart.RIGHT_ARM)),
	LEGS_GROUP(Set.of(OutfitPart.LEFT_LEG, OutfitPart.RIGHT_LEG));

	private final Set<OutfitPart> parts;
	OutfitGroup(Set<OutfitPart> parts) { this.parts = OutfitPart.immutableSet(parts); }
	public Set<OutfitPart> parts() { return parts; }
}
