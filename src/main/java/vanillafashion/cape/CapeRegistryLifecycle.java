package vanillafashion.cape;

import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;

public final class CapeRegistryLifecycle {
	private CapeRegistryLifecycle() {
	}

	public static CapeRegistryKnowledge load(CapeRegistryService service, Path capesRoot, Logger logger) {
		logger.info("Vanilla Fashion Cape Registry 开始加载，资产目录：{}。", capesRoot.toAbsolutePath().normalize());

		try {
			CapeRegistryLoadResult result = new CapeRegistryLoader().load(capesRoot);
			service.replace(result.registry());

			for (CapeRejectedEntry rejectedEntry : result.rejectedEntries()) {
				String entryName = rejectedEntry.directory().getFileName().toString();

				for (CapeValidationIssue issue : rejectedEntry.issues()) {
					logger.warn(
							"拒绝 Cape Cosmetic“{}”：{}，{}",
							entryName,
							issue.code(),
							issue.message()
					);
				}
			}

			logger.info(
					"Vanilla Fashion Cape Registry 加载完成：{} 个有效，{} 个无效。",
					result.registry().size(),
					result.rejectedEntries().size()
			);
			return CapeRegistryKnowledge.loaded(capesRoot, result);
		} catch (IOException | SecurityException exception) {
			service.clear();
			logger.error(
					"Vanilla Fashion Cape Registry 加载失败，当前使用空 Registry。目录：{}。",
					capesRoot.toAbsolutePath().normalize(),
					exception
			);
			return CapeRegistryKnowledge.unavailable(capesRoot);
		}
	}
}
