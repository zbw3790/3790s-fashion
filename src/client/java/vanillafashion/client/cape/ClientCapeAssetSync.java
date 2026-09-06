package vanillafashion.client.cape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import vanillafashion.cape.CapeAssetLimits;
import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeRegistrySnapshot;
import vanillafashion.network.CapeAssetDataPayload;
import vanillafashion.network.CapeAssetRequestPayload;

public final class ClientCapeAssetSync {
	private final ClientCapeAssetStore assetStore;
	private final ClientCapeAssetCache assetCache;
	private final Set<String> requiredHashes = new LinkedHashSet<>();
	private final Set<String> pendingHashes = new LinkedHashSet<>();
	private int lastCacheHitCount;
	private ClientCapeAssetCache.StoreResult lastCacheStoreResult;

	public ClientCapeAssetSync(ClientCapeAssetStore assetStore, ClientCapeAssetCache assetCache) {
		this.assetStore = Objects.requireNonNull(assetStore, "客户端 Cape Asset Store 不能为 null。");
		this.assetCache = Objects.requireNonNull(assetCache, "客户端 Cape Asset Cache 不能为 null。");
	}

	public List<CapeAssetRequestPayload> plan(CapeRegistrySnapshot snapshot) {
		Objects.requireNonNull(snapshot, "Cape Registry Snapshot 不能为 null。");

		requiredHashes.clear();

		for (CapeCosmeticMetadata metadata : snapshot.entries()) {
			requiredHashes.add(metadata.capeSha256());
			metadata.elytraSha256().ifPresent(requiredHashes::add);
		}

		assetStore.retain(requiredHashes);
		pendingHashes.retainAll(requiredHashes);
		lastCacheHitCount = 0;

		for (String hash : requiredHashes) {
			if (assetStore.contains(hash)) {
				continue;
			}

			assetCache.findValidated(hash).ifPresent(bytes -> {
				if (assetStore.store(hash, bytes) == ClientCapeAssetStore.StoreResult.STORED) {
					lastCacheHitCount++;
				}
			});
		}

		pendingHashes.removeIf(assetStore::contains);

		List<String> missingHashes = requiredHashes.stream()
				.filter(hash -> !assetStore.contains(hash))
				.filter(hash -> !pendingHashes.contains(hash))
				.toList();
		List<CapeAssetRequestPayload> requests = new ArrayList<>();

		for (int fromIndex = 0; fromIndex < missingHashes.size(); fromIndex += CapeAssetLimits.MAX_REQUEST_HASHES) {
			int toIndex = Math.min(fromIndex + CapeAssetLimits.MAX_REQUEST_HASHES, missingHashes.size());
			requests.add(new CapeAssetRequestPayload(missingHashes.subList(fromIndex, toIndex)));
		}

		return List.copyOf(requests);
	}

	public void markRequested(CapeAssetRequestPayload request) {
		for (String hash : request.sha256Hashes()) {
			if (requiredHashes.contains(hash) && !assetStore.contains(hash)) {
				pendingHashes.add(hash);
			}
		}
	}

	public ReceiveResult receive(CapeAssetDataPayload payload) {
		Objects.requireNonNull(payload, "Cape Asset Data payload 不能为 null。");
		String hash = payload.sha256();
		lastCacheStoreResult = null;

		if (!requiredHashes.contains(hash)) {
			return ReceiveResult.NOT_REQUIRED;
		}

		if (!pendingHashes.contains(hash)) {
			return ReceiveResult.NOT_PENDING;
		}

		ClientCapeAssetStore.StoreResult storeResult = assetStore.store(hash, payload.pngBytes());

		if (storeResult == ClientCapeAssetStore.StoreResult.STORED) {
			pendingHashes.remove(hash);
			lastCacheStoreResult = assetCache.storeValidated(hash, payload.pngBytes());
			return ReceiveResult.STORED;
		}

		return switch (storeResult) {
			case INVALID_SIZE -> ReceiveResult.INVALID_SIZE;
			case HASH_MISMATCH -> ReceiveResult.HASH_MISMATCH;
			case INVALID_PNG -> ReceiveResult.INVALID_PNG;
			case INVALID_DIMENSIONS -> ReceiveResult.INVALID_DIMENSIONS;
			case STORED -> throw new IllegalStateException("已存储结果不应进入失败分支。");
		};
	}

	public Set<String> requiredHashes() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(requiredHashes));
	}

	public Set<String> pendingHashes() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(pendingHashes));
	}

	public int lastCacheHitCount() {
		return lastCacheHitCount;
	}

	public Optional<ClientCapeAssetCache.StoreResult> lastCacheStoreResult() {
		return Optional.ofNullable(lastCacheStoreResult);
	}

	public void clear() {
		requiredHashes.clear();
		pendingHashes.clear();
		lastCacheHitCount = 0;
		lastCacheStoreResult = null;
	}

	public enum ReceiveResult {
		STORED,
		NOT_REQUIRED,
		NOT_PENDING,
		INVALID_SIZE,
		HASH_MISMATCH,
		INVALID_PNG,
		INVALID_DIMENSIONS
	}
}
