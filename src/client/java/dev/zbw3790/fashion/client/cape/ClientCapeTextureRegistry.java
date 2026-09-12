package dev.zbw3790.fashion.client.cape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.cape.CapeAssetHash;

public final class ClientCapeTextureRegistry {
	private final Map<String, Identifier> identifiersByHash = new LinkedHashMap<>();

	public boolean register(String sha256, Identifier identifier) {
		CapeAssetHash.requireValid(sha256, "运行时纹理 SHA-256 ");
		Objects.requireNonNull(identifier, "运行时纹理 Identifier 不能为 null。");
		return identifiersByHash.putIfAbsent(sha256, identifier) == null;
	}

	public Optional<Identifier> find(String sha256) {
		return Optional.ofNullable(identifiersByHash.get(
				CapeAssetHash.requireValid(sha256, "运行时纹理 SHA-256 ")
		));
	}

	public List<Identifier> retain(Set<String> requiredHashes) {
		Objects.requireNonNull(requiredHashes, "运行时纹理保留集合不能为 null。");
		List<Identifier> released = new ArrayList<>();
		Iterator<Map.Entry<String, Identifier>> iterator = identifiersByHash.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, Identifier> entry = iterator.next();

			if (!requiredHashes.contains(entry.getKey())) {
				released.add(entry.getValue());
				iterator.remove();
			}
		}

		return List.copyOf(released);
	}

	public List<Identifier> clear() {
		List<Identifier> released = List.copyOf(identifiersByHash.values());
		identifiersByHash.clear();
		return released;
	}

	public Set<String> hashes() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(identifiersByHash.keySet()));
	}

	public int size() {
		return identifiersByHash.size();
	}
}
