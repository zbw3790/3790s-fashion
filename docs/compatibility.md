# 兼容性说明

## 支持矩阵

| 客户端 | 服务器 | 行为 |
| --- | --- | --- |
| v0.3.1 | v0.3.1 | 完整 Cape／Outfit 功能；手动 Outfit reload 后新端同连接刷新，无需 Apply |
| v0.3.0 | v0.3.1 | Cape／Outfit 可用；在线固定加入时的资源视图，不接收新 Refresh，重连后获得最新资源 |
| v0.3.1 | v0.3.0 | Cape／Outfit 可用；旧服务器不提供 v0.3.1 的手动热重载能力 |
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

## Outfit 手动重载与旧客户端

v0.3.1 服务器支持管理员／控制台 `/vanillafashion reload`。支持 Refresh 的 v0.3.1 客户端在同一连接中更新资源目录、世界外观、Preview 与二维缩略，不需要重新应用选择。PNG-only Outfit 在服务器解析后沿用正式装束定义与资产传输。

冻结 v0.3.0 客户端不会收到其不支持的 Refresh Payload；同连接继续使用加入时的定义与资产视图，不支持在线换图。正常重连后才取得最新目录和纹理。不要将服务器 reload 成功误认为旧端也已刷新。

可信删除只清除引用已删除装束的部位，保留 Cape 与其他部位；存在但无效的资源保留 stored 引用并回退。资源根目录不可用时保留原有资源和状态。

## 已知限制

- Cape 定义仍在服务器启动时加载；Cape 变化需要重启服务器。Outfit 支持 v0.3.1 手动 reload，不支持文件实时监听或热重载 GUI。
- 不提供资源上传、编辑、权限或 entitlement 系统，不包含 Elytra Slot 附属或 Armor Visual。
- Elytra 纹理属于 Cape Cosmetic 的绑定资源，不是独立时装槽。

## 保存兼容

v0.3.1 继续使用 schema 2，直接兼容 v0.3.0 保存，没有新增保存 schema。v0.3.0 起读取 v0.2.1 的 schema v1 保存；实际修改时写入同时包含 Cape 和六部位 Outfit 的 schema v2。仅加载旧保存不会立即迁移。旧版 Mod 不能反向读取 v2 保存，升级前应备份世界。
