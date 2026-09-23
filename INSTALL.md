# 3790's Fashion v1.0.0 安装说明

**未发布的 1.0.0 本地候选。** [中文入门](docs/getting-started-zh_cn.md)／[English](docs/getting-started-en_us.md)。

本包包含主 Mod JAR、MIT 许可证、中英文入门和资源说明、两个 Cape 模板与 SHA256SUMS.txt，不包含 Fabric API、Elytra Slot 或开发接口 JAR。

## 安装

1. 使用 Minecraft Java Edition 26.2、Java 25、Fabric Loader 0.19.3 或更高兼容版本。
2. 在客户端与服务器的 mods 目录安装本包的 `3790s-fashion-1.0.0.jar` 以及适用于 26.2 的 Fabric API 0.158.0+26.2 或更高兼容版本。单人只需客户端安装。
3. 升级前正常停服并备份配置和世界；移除 mods 中旧主 Mod JAR，避免新旧两个主 Mod 同时加载，再安装新版。
4. 原版盔甲架的装备交互优先；没有原版动作时右键打开衣柜，预览 Cape／Outfit／Armor 并统一应用。成功应用后窗口保持打开。

## 资源与迁移

- Cape：`config/3790s-fashion/capes/<id>/`；复制本包 `templates/capes/` 下的示例。PNG 为 64×32，每张不超过 65536 bytes；支持 cape.png、cape.png＋elytra.png 或 cape_elytra.png。修改后重启服务器。
- Outfit：`config/3790s-fashion/outfits/<id>/`；PNG-only 可用 wide.png／slim.png，64×64，每张不超过 65536 bytes；部位由外层像素识别。严格 JSON、全透明声明与臂宽规则见在线说明。
- 管理员执行 `/fashion3790 reload` 重载 Outfit 和 Armor；无需重新 Apply。不要在扫描期间修改资源。
- 旧 `config/vanilla-fashion` 在新配置根不存在时自动完整复制；已有新根优先，不合并，旧文件保留。
- 旧世界 `data/vanilla_fashion/player_fashion.dat` 验证后自动迁移至 `data/fashion_3790/player_fashion.dat`，只写新位置；旧 Cape、六部位、Original／None 和休眠引用保持。读取 schema 1/2 默认盔甲原版，schema 3 保留 Hidden；实际修改后写入 schema 4。
- 新位置损坏不自动回退旧副本；迁移失败不会以空默认值覆盖旧文件。旧副本不会持续同步，降级应恢复正确备份。
- Mod ID 为 `fashion_3790`；旧 `vanilla_fashion` 是依赖别名，不是旧网络桥接。v0.4.0 不承诺与 v0.3.x 跨版本 Fashion 联网，双端请一起升级；旧 `/vanillafashion` 命令不再注册。

## 盔甲资源与交互

Armor 页先选头盔／胸甲／护腿／靴子，再选原版、隐藏或网格纹理。资源目录为 `config/3790s-fashion/armor/<id>/`，含严格 `armor.json` 及声明的 64×32 RGBA `outer.png`／`inner.png`，仅不透明或全透明像素。完整示例与限额见项目 README 的盔甲资源说明；本安装 ZIP 不捆绑自定义盔甲资源。无资源也可选择原版／隐藏。

盔甲架完整原版动作优先；按住次要使用可绕过衣柜。三类草稿统一 Apply，取消或 E／ESC 丢弃未提交选择。主动 ELYTRA 试穿保留胸甲外观，不修改真实装备。

请双端一起升级到 1.0.0 才能使用完整 Cape／Outfit／Armor；旧身份同名频道不冒充新 Full v4。v0.4.0 与本版只按能力协商降级为 Cape，不提供跨版本完整 Outfit／Armor 编辑。0.5.0 与 1.0.0 均为 schema 4，本次升级不引入新格式。直接降级未作为发行承诺，回退请恢复升级前完整备份。

对于本次固定验证的 Elytra Slot 组合，世界既有支持保持；原版物品栏尚未同步额外 BODY 翼及对应披风遮挡，真实 BODY 驱动的衣柜组合预览未纳入本次发行。主 Mod 单装、真实胸槽 Elytra 及主动 CAPE／ELYTRA 试穿仍可用。

## 可选兼容与许可证

已联合验证 3790's Elytra Slot 0.1.0、Visual Contract v1。它负责真实专用槽位和额外翼，主 Mod 负责外观与预览；附属单独获取、独立安装。主 Mod 单装不需要附属或 Visual API helper。

代码采用 MIT；Logo 为原创 Outfit 衬衫与 #3790FF 品牌色。盔甲外观只改变绘制，不改变物品与玩法；没有 Visual Contract v2。

完整说明与源码：[3790's Fashion](https://github.com/zbw3790/3790s-fashion)。问题反馈：[Issues](https://github.com/zbw3790/3790s-fashion/issues)。

## 从正式 0.5.0 升级 / Upgrade from 0.5.0

正常关闭游戏和服务器，备份整个世界、config/3790s-fashion 和实例配置。只更换主 JAR，保留配置、资源与玩家缓存；不要同时加载两个版本。首次连接先核对已保存的三域外观，无需 Apply 来恢复。确认后再编辑。

Stop the game and server normally. Back up the complete world, config/3790s-fashion and instance settings. Replace only the main Mod JAR, keeping resources and caches. Both versions use schema 4; this is not a new schema migration. Check saved appearance before editing. To roll back, restore the complete pre-upgrade backup; direct downgrade has not been promised.
