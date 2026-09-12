package dev.zbw3790.fashion.network;

public enum CapeSelectionReason {
	APPLIED(0, true),
	NO_CHANGE(1, true),
	CAPE_NOT_AVAILABLE(2, false),
	NOT_ALLOWED(3, false),
	SERVICE_UNAVAILABLE(4, false),
	STORAGE_LIMIT(5, false);

	private final int wireId;
	private final boolean accepted;

	CapeSelectionReason(int wireId, boolean accepted) {
		this.wireId = wireId;
		this.accepted = accepted;
	}

	public int wireId() { return wireId; }
	public boolean accepted() { return accepted; }

	static CapeSelectionReason fromWire(int id) {
		for (var reason : values()) {
			if (reason.wireId == id) {
				return reason;
			}
		}
		throw new IllegalArgumentException("未知的时装选择结果编码。");
	}
}
