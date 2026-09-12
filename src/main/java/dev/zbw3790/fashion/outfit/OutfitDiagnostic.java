package dev.zbw3790.fashion.outfit;

import java.util.Objects;

/** 只暴露有界目录名和固定角色，不携带异常消息或绝对路径。 */
public record OutfitDiagnostic(String entryName, String role, Code code) {
	public OutfitDiagnostic {
		Objects.requireNonNull(entryName, "诊断目录名不能为 null。");
		Objects.requireNonNull(role, "诊断角色不能为 null。");
		Objects.requireNonNull(code, "诊断原因不能为 null。");
		entryName = entryName.codePoints().limit(64)
				.collect(StringBuilder::new, (b, c) -> b.appendCodePoint(Character.isISOControl(c) ? '?' : c), StringBuilder::append).toString();
		if (!role.equals("root") && !role.equals("directory") && !role.equals("outfit.json")
				&& !role.equals("wide.png") && !role.equals("slim.png")) {
			throw new IllegalArgumentException("诊断角色必须为已知固定文件角色。");
		}
	}
    public static void report(java.util.List<OutfitDiagnostic> diagnostics, org.slf4j.Logger logger) {
        diagnostics.stream().limit(32).forEach(issue -> logger.warn("装束资源诊断：目录={}，角色={}，原因={}。{}",
                issue.entryName(),issue.role(),issue.code(),issue.code().message()));
        if (diagnostics.size()>32) logger.warn("其余装束资源诊断未逐项展开：{} 条。",diagnostics.size()-32);
    }
	public enum Code {
		ROOT_UNAVAILABLE("装束根目录不可用。"), SCAN_LIMIT("目录条目超过本次明确的扫描预算。"),
		INVALID_ID("目录名称不是合法装束 ID。"), NOT_DIRECTORY("装束路径不是目录。"),
		UNSAFE_PATH("资产真实路径不属于已核验的装束目录。"), CHANGED_DURING_READ("扫描期间路径或文件发生变化。"),
		MISSING_FILE("缺少声明要求的固定文件。"), NOT_REGULAR_FILE("资产目标不是普通文件。"),
		IO_ERROR("读取资产时发生错误。"), TOO_LARGE("文件超过对应字节上限。"),
		INVALID_METADATA("metadata 不符合严格 v1 契约。"), INVALID_PNG("资产不是完整可解码 PNG。"),
		INVALID_DIMENSIONS("装束 PNG 尺寸必须为 64×64。"), UNDECLARED_MODEL_FILE("忽略未声明模型文件。"),
        INFERRED_PARTS_MISMATCH("WIDE／SLIM 推导部位不一致，请使用 outfit.json 显式声明。"),
        NO_INFERRED_PARTS("自动装束至少需要一个可见外层部位；全透明部位请使用 outfit.json 显式声明。");
		private final String message;
		Code(String message) { this.message = message; }
		public String message() { return message; }
	}
}
