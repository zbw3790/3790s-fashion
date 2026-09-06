package vanillafashion.client.cape;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import vanillafashion.cape.CapeCosmeticMetadata;
import vanillafashion.cape.CapeId;
import vanillafashion.cape.CapeRegistrySnapshot;

public final class ClientCapeRegistry {
	private final AtomicReference<CapeRegistrySnapshot> current =
			new AtomicReference<>(CapeRegistrySnapshot.empty());

	public void replace(CapeRegistrySnapshot snapshot) {
		current.set(Objects.requireNonNull(snapshot, "客户端 Cape Registry Snapshot 不能为 null。"));
	}

	public void clear() {
		current.set(CapeRegistrySnapshot.empty());
	}

	public Optional<CapeCosmeticMetadata> find(CapeId id) {
		return current.get().find(id);
	}

	public List<CapeCosmeticMetadata> entries() {
		return current.get().entries();
	}

	public int size() {
		return current.get().size();
	}

	public boolean isEmpty() {
		return current.get().isEmpty();
	}
}
