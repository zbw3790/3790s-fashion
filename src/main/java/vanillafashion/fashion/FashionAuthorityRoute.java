package vanillafashion.fashion;
/** 一条连接只选择一套玩家权威协议；资产协议独立。 */
public enum FashionAuthorityRoute {
    UNDECIDED, LEGACY, V2;
    public static FashionAuthorityRoute server(boolean fullSnapshot, boolean fullUpdate, boolean fullRemove, boolean legacySnapshot) {
        if (fullSnapshot && fullUpdate && fullRemove) return V2;
        return legacySnapshot ? LEGACY : UNDECIDED;
    }
}
