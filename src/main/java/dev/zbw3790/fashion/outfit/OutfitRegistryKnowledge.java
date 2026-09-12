package dev.zbw3790.fashion.outfit;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** 当前扫描知识供后续服务使用，本身不修改任何玩家选择。 */
public final class OutfitRegistryKnowledge {
	private final OutfitFileAccess access;
	private final OutfitFileAccess.Stamp root;
	private final boolean trustworthy;
	private final Set<OutfitId> knownExisting;
	private final OutfitRegistry registry;

	OutfitRegistryKnowledge(OutfitFileAccess access, OutfitFileAccess.Stamp root, boolean trustworthy,
			Set<OutfitId> knownExisting, OutfitRegistry registry) {
		this.access = access;
		this.root = root;
		this.trustworthy = trustworthy;
		this.knownExisting = Collections.unmodifiableSet(new TreeSet<>(knownExisting));
		this.registry = registry;
	}
	public boolean trustworthy() { return trustworthy; }
	public boolean knownExisting(OutfitId id) { return knownExisting.contains(id); }
	public Set<OutfitId> knownExistingIds() { return knownExisting; }
	public boolean metadataTrusted(OutfitId id) { return registry.find(id).isPresent(); }
	public boolean partDeclared(OutfitId id, OutfitPart part) {
		return registry.find(id).map(entry -> entry.metadata().parts().contains(part)).orElse(false);
	}
	public boolean modelDeclared(OutfitId id, OutfitModel model) {
		return registry.find(id).map(entry -> entry.metadata().models().contains(model)).orElse(false);
	}
	public boolean modelValid(OutfitId id, OutfitModel model) {
		return registry.find(id).map(entry -> entry.model(model) instanceof OutfitModelResource.Valid).orElse(false);
	}
	public boolean isDefinitelyAbsent(OutfitId id) {
		return trustworthy && root != null && !knownExisting(id) && access.definitelyAbsent(root, id);
	}
}
