# 兼容性说明

## 支持矩阵

| 客户端 | 服务器 | 行为 |
| --- | --- | --- |
| 安装 Vanilla Fashion | 安装 Vanilla Fashion | 提供完整衣柜、资产同步、玩家选择与多人外观同步 |
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

- v0.1 不支持 Cape Registry 热重载；资产变化需要重启服务器。
- v0.1 不包含披风上传、审核、权限分配、云服务、HTTP API 或数据库。
- Elytra 纹理属于 Cape Cosmetic 的绑定资源，不是独立时装槽。
