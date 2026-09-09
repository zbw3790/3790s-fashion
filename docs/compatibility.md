# 兼容性说明

## 支持矩阵

| 客户端 | 服务器 | 行为 |
| --- | --- | --- |
| v0.3.0 | v0.3.0 | 提供 Cape＋六部位 Outfit 衣柜、统一应用、资产与多人外观同步 |
| v0.3.0 | v0.2.1 | 正常加入并使用旧 Cape 路由；Outfit 页签显示不支持，不能编辑装束 |
| v0.2.1 | v0.3.0 | 正常加入并使用 Cape；不显示或编辑 Outfit，Cape 修改保留服务器已有装束 |
| 安装 Vanilla Fashion | 原版 Minecraft 26.2 | 正常加入；不打开衣柜、不发送本 Mod 请求、不覆盖原版外观 |
| 原版 Minecraft 26.2 | 安装 Vanilla Fashion | 正常加入；服务器不会向该连接发送不支持的自定义 Payload |
| 原版 Minecraft 26.2 | 原版 Minecraft 26.2 | 与本项目无关 |

“原版”表示实际客户端或服务器进程没有加载 Fabric Loader、Fabric API、Vanilla Fashion 或其他 Mod Loader。

## 双端安装

完整功能需要客户端和服务器同时安装：

- Fabric Loader
- Fabric API
- Vanilla Fashion

服务器不会强制所有连接安装本 Mod。未安装的玩家不能打开衣柜或提交新选择，但其 UUID 对应的既有服务器权威状态不会仅因客户端缺少 Mod 而自动删除；其他已安装客户端仍可看到有效外观。

## 连接隔离

客户端在每次新连接开始和断开时清理当前连接的临时 Registry、资源请求、纹理和玩家状态。磁盘内容缓存可以复用，但不能自行授权或激活旧服务器内容。

连接到不支持本 Mod 的服务器时，盔甲架保持原版交互，世界渲染保持原版 pass-through。服务器只会向明确支持对应频道的客户端发送 Vanilla Fashion Payload。

## 已知限制

- Cape／Outfit 定义在服务器启动时加载；资产变化需要重启服务器。
- 不提供资源上传、编辑、权限或 entitlement 系统，不包含 Elytra Slot 附属或 Armor Visual。
- Elytra 纹理属于 Cape Cosmetic 的绑定资源，不是独立时装槽。

## 保存兼容

v0.3.0 读取 v0.2.1 的 schema v1 保存；实际修改时写入同时包含 Cape 和六部位 Outfit 的 schema v2。仅加载旧保存不会立即迁移。旧版 Mod 不能反向读取 v2 保存，升级前应备份世界。
