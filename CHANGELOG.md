# 更新记录

## v0.5.0 — 盔甲外观与衣柜改进

- 新增头盔、胸甲、护腿、靴子四槽 Original／Hidden／Custom 外观，保留真实装备属性、耐久、染色、饰纹和附魔语义。
- Armor 页采用与 Outfit 一致的部位入口、固定动作、4×2 正面纹理样片和完整三维预览；Cape／Outfit／Armor 共用草稿和一次 Apply。
- 盔甲资源由服务器同步，支持同 ID 换图、手动 reload、缺失回退和恢复；保存失效引用，无需为恢复纹理重新应用。
- 修复原版物品栏中 Outfit 与 Base 的动态姿态错位；Survival 与 Creative 自身页按实际 GUI 最终姿态绘制，每次提交保持独立快照。
- 盔甲架原版动作优先，次要使用绕过衣柜；主动 ELYTRA 试穿可保留胸甲外观，Preview 与真实装备隔离。

## 安装与升级

Minecraft 26.2、Fabric、Java 25；双端一起升级。停止游戏／服务器并备份配置和世界，再替换旧主 JAR。schema 1/2 默认盔甲原版，schema 3 隐藏位保留，实际修改后写 schema 4。旧版不能读取已升级保存，降级应恢复升级前备份。

完整 Fashion 使用 Full v4；与 v0.4.0 只按能力降级为 Cape，不提供跨版本完整 Outfit／Armor 编辑。不承诺 v0.3.x 旧 namespace 桥接。管理员 `/fashion3790 reload` 共同重载 Outfit＋Armor，Cape 仍需重启。

## 已知限制

对于本次固定验证的 Elytra Slot 组合，世界既有支持保持；原版物品栏尚未同步额外 BODY 翼及对应披风遮挡，真实 BODY 驱动的衣柜组合预览未纳入本次发行。新的附属 GUI 合作已延期，不承诺版本或日期。此限制不影响主 Mod 单装、原版真实胸槽 Elytra 和主动 CAPE／ELYTRA 试穿。

只替换标准盔甲基础纹理，不提供自定义 3D 几何、资源上传或热重载管理 GUI。开发构建仍需接口 Owner 提供固定 Visual Contract v1 compile-only JAR；它不随玩家安装包分发，也不应放入 mods。

## v0.4.0 — 3790's Fashion

## 主要变化

- 项目正式统一为 **3790's Fashion**，沿用原创 Outfit 衬衫 Logo 与 #3790FF 品牌色。
- 主 Mod ID 从 `vanilla_fashion` 迁移为 `fashion_3790`；Java package、资源／网络 namespace 和构建制品统一使用新技术身份。
- 为 v0.3.x 用户提供配置和 Fashion 世界保存的安全迁移，保留 Cape、六部位 Outfit、自定义／Original／None 与休眠状态。
- Visual Contract v1 进入发行线，已与 **3790's Elytra Slot 0.1.0** 联合验证：胸甲与 BODY Elytra 共存，单份绑定翼外观、披风遮挡／恢复、Owner／Observer、保存／重连及两种 Preview 隔离。
- 保留现有 Cape、绑定 Elytra 外观、Outfit、统一草稿衣柜、PNG-only 装束和手动 Outfit 热重载。

## 升级说明

正常停服、备份配置和世界后，将双方旧主 Mod JAR 替换为 `3790s-fashion-0.4.0.jar`；不要同时安装新旧主 Mod。

新配置根为 `config/3790s-fashion`。只有新根不存在时，才自动从旧 `config/vanilla-fashion` 完整复制；已有新根优先，不合并资产。旧文件不自动删除，迁移时不要并发修改目录。

旧 Fashion SavedData 验证后自动复制到新位置 `data/fashion_3790/player_fashion.dat`，随后只写新位置；业务 schema 保持 2。新位置损坏不回退旧副本，也不以默认值覆盖坏文件。旧副本不是最新镜像，降级须恢复正确备份。普通用户无需手动编辑 NBT 或重选外观。

当前管理员命令为 `/fashion3790 reload`，仅重载 Outfit；旧 `/vanillafashion` 不再注册。Cape 资源变化仍需重启服务器。

## 技术兼容边界

网络 namespace 已迁移。**v0.4.0 不承诺与 v0.3.x 客户端／服务器跨版本 Fashion 联网，双端应一起升级。** `provides: vanilla_fashion` 仅是 Fabric 依赖别名。

Elytra Slot 是可选附属，不包含在主 Mod 安装包中，也不是主 Mod 硬运行依赖。Visual Contract 仍为 v1；本版不包含 Armor 系统、Visual Contract v2、BadOptimizations／WaveyCapes 新适配、新玩法、新网络功能或 schema 3。

## 安装环境

Minecraft Java Edition 26.2，Fabric Loader >=0.19.3，Fabric API 0.158.0+26.2 或更高兼容版本，Java 25，Client＋Server，MIT。GitHub 安装 ZIP 只包含主 JAR、说明、许可证与 Cape 模板；Modrinth／CurseForge 使用同一主 JAR。

## 历史发行

v0.3.2 及更早版本使用旧名称 3790's Vanilla Style Fashion、旧 Mod ID `vanilla_fashion` 和旧 artifact 前缀。历史 Tag、Release、制品与证据保持不变；[v0.3.2 历史 Release](https://github.com/zbw3790/3790s-vanilla-style-fashion/releases/tag/v0.3.2)。
