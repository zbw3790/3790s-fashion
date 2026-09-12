package dev.zbw3790.fashion.cape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CapeAssetIndex {
	private final Map<String, CapeTextureAsset> assetsByHash;

	private CapeAssetIndex(Map<String, CapeTextureAsset> assetsByHash) {
		this.assetsByHash = Collections.unmodifiableMap(new LinkedHashMap<>(assetsByHash));
	}

	public static CapeAssetIndex from(CapeRegistry registry) {
		Objects.requireNonNull(registry, "Cape Registry 不能为 null。");

		Map<String, CapeTextureAsset> assetsByHash = new LinkedHashMap<>();

		for (CapeCosmeticDefinition definition : registry.definitions()) {
			addIfAbsent(assetsByHash, definition.cape());
			definition.elytra().ifPresent(asset -> addIfAbsent(assetsByHash, asset));
		}

		return new CapeAssetIndex(assetsByHash);
	}

	public Optional<CapeTextureAsset> find(String sha256) {
		return Optional.ofNullable(assetsByHash.get(
				CapeAssetHash.requireValid(sha256, "查询资产 SHA-256 ")
		));
	}

	public int size() {
		return assetsByHash.size();
	}

	private static void addIfAbsent(Map<String, CapeTextureAsset> assetsByHash, CapeTextureAsset asset) {
		assetsByHash.putIfAbsent(asset.sha256(), asset);
	}
}
