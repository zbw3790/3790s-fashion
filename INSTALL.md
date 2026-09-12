# 3790's Fashion v0.4.0 安装说明

本包包含主 Mod JAR、MIT 许可证、两个 Cape 模板与 SHA256SUMS.txt，不包含 Fabric API、Elytra Slot 或开发接口 JAR。

## 安装

1. 使用 Minecraft Java Edition 26.2、Java 25、Fabric Loader 0.19.3 或更高兼容版本。
2. 在客户端与服务器的 mods 目录安装本包的 `3790s-fashion-0.4.0.jar` 以及适用于 26.2 的 Fabric API 0.158.0+26.2 或更高兼容版本。单人只需客户端安装。
3. 升级前正常停服并备份配置和世界；移除 mods 中旧主 Mod JAR，避免新旧两个主 Mod 同时加载，再安装新版。
4. 原版盔甲架的装备交互优先；没有原版动作时右键打开衣柜，预览 Cape／Outfit 并应用。成功应用后窗口保持打开。

## 资源与迁移

- Cape：`config/3790s-fashion/capes/<id>/`；复制本包 `templates/capes/` 下的示例。PNG 为 64×32，每张不超过 65536 bytes；支持 cape.png、cape.png＋elytra.png 或 cape_elytra.png。修改后重启服务器。
- Outfit：`config/3790s-fashion/outfits/<id>/`；PNG-only 可用 wide.png／slim.png，64×64，每张不超过 65536 bytes；部位由外层像素识别。严格 JSON、全透明声明与臂宽规则见在线说明。
- 管理员执行 `/fashion3790 reload` 只重载 Outfit；无需重新 Apply。不要在扫描期间修改资源。
- 旧 `config/vanilla-fashion` 在新配置根不存在时自动完整复制；已有新根优先，不合并，旧文件保留。
- 旧世界 `data/vanilla_fashion/player_fashion.dat` 验证后自动迁移至 `data/fashion_3790/player_fashion.dat`，只写新位置；schema 2、Cape、六部位、Original／None 和休眠引用保持。
- 新位置损坏不自动回退旧副本；迁移失败不会以空默认值覆盖旧文件。旧副本不会持续同步，降级应恢复正确备份。
- Mod ID 为 `fashion_3790`；旧 `vanilla_fashion` 是依赖别名，不是旧网络桥接。v0.4.0 不承诺与 v0.3.x 跨版本 Fashion 联网，双端请一起升级；旧 `/vanillafashion` 命令不再注册。

## 可选兼容与许可证

已联合验证 3790's Elytra Slot 0.1.0、Visual Contract v1。它负责真实专用槽位和额外翼，主 Mod 负责外观与预览；附属单独获取、独立安装。主 Mod 单装不需要附属或 Visual API helper。

代码采用 MIT；Logo 为原创 Outfit 衬衫与 #3790FF 品牌色。没有新增 Armor 系统、玩法或 Visual Contract v2。

完整说明与源码：[3790's Fashion](https://github.com/zbw3790/3790s-fashion)。问题反馈：[Issues](https://github.com/zbw3790/3790s-fashion/issues)。
