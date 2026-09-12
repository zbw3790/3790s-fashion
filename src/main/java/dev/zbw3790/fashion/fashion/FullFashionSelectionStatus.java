package dev.zbw3790.fashion.fashion;
/** 有界结果；序号是已冻结协议，不使用枚举 ordinal。 */
public enum FullFashionSelectionStatus {
    SUCCESS(0), CONFLICT(1), INVALID_CAPE(2), INVALID_OUTFIT_SELECTION(3), SERVICE_UNAVAILABLE(4),
    READ_ONLY_PERSISTENCE(5), PROTOCOL_REJECT(6), STORAGE_LIMIT(7), NOT_ALLOWED(8);
    private final int wireId;
    FullFashionSelectionStatus(int wireId) { this.wireId = wireId; }
    public int wireId() { return wireId; }
    public static FullFashionSelectionStatus fromWire(int value) {
        for (var status : values()) if (status.wireId == value) return status;
        throw new IllegalArgumentException("未知的完整选择结果。");
    }
}
