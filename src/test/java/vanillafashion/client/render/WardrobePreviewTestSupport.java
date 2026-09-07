package vanillafashion.client.render;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

public final class WardrobePreviewTestSupport {
	private static boolean componentsBound;

	private WardrobePreviewTestSupport() {
	}

	public static synchronized void bootstrap() {
		if (componentsBound) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// 26.2 在 Registry 装载后单独绑定默认组件；只执行原版 CPU 数据初始化。
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
				.forEach(DataComponentInitializers.PendingComponents::apply);
		componentsBound = true;
	}
}
