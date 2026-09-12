package dev.zbw3790.fashion.client.cape;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeAssetLimits;
import dev.zbw3790.fashion.cape.CapePngValidator;

public final class ClientCapeAssetStore {
	private final Map<String, byte[]> assetsByHash = new LinkedHashMap<>();

	public StoreResult store(String sha256, byte[] pngBytes) {
		CapeAssetHash.requireValid(sha256, "客户端 Cape 资产 SHA-256 ");
		Objects.requireNonNull(pngBytes, "客户端 Cape 资产字节不能为 null。");

		if (pngBytes.length < 1 || pngBytes.length > CapeAssetLimits.MAX_ASSET_BYTES) {
			return StoreResult.INVALID_SIZE;
		}

		if (!sha256.equals(CapeAssetHash.sha256(pngBytes))) {
			return StoreResult.HASH_MISMATCH;
		}

		CapePngValidator.ValidationResult validation = CapePngValidator.validate(pngBytes);

		if (validation == CapePngValidator.ValidationResult.INVALID_PNG) {
			return StoreResult.INVALID_PNG;
		}

		if (validation == CapePngValidator.ValidationResult.INVALID_DIMENSIONS) {
			return StoreResult.INVALID_DIMENSIONS;
		}

		assetsByHash.put(sha256, pngBytes.clone());
		return StoreResult.STORED;
	}

	public boolean contains(String sha256) {
		return assetsByHash.containsKey(CapeAssetHash.requireValid(sha256, "查询资产 SHA-256 "));
	}

	public Optional<byte[]> find(String sha256) {
		byte[] bytes = assetsByHash.get(CapeAssetHash.requireValid(sha256, "查询资产 SHA-256 "));
		return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
	}

	public int size() {
		return assetsByHash.size();
	}

	public void retain(Set<String> requiredHashes) {
		Objects.requireNonNull(requiredHashes, "保留资产 hash 集合不能为 null。");
		assetsByHash.keySet().removeIf(hash -> !requiredHashes.contains(hash));
	}

	public void clear() {
		assetsByHash.clear();
	}

	public enum StoreResult {
		STORED,
		INVALID_SIZE,
		HASH_MISMATCH,
		INVALID_PNG,
		INVALID_DIMENSIONS
	}
}
