package dev.zbw3790.fashion.cape;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CapeRegistryService {
	private final AtomicReference<CapeRegistry> current = new AtomicReference<>(CapeRegistry.empty());

	public CapeRegistry current() {
		return current.get();
	}

	void replace(CapeRegistry registry) {
		current.set(Objects.requireNonNull(registry, "当前 Cape Registry 不能为 null。"));
	}

	public void clear() {
		current.set(CapeRegistry.empty());
	}
}
