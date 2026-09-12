package dev.zbw3790.fashion.wardrobe;

public final class WardrobeServerAvailability {
	private volatile boolean available;

	public boolean isAvailable() {
		return available;
	}

	public void markAvailable() {
		available = true;
	}

	public void reset() {
		available = false;
	}
}
