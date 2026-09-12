# 3790's Fashion

为 Minecraft Java Edition 26.2 提供服务器本地披风、Outfit 外层装束与 Creative Inventory 风格衣柜：服主准备外观资源，玩家在游戏内预览和选择，保留原版基础皮肤、模型与动作。

![原创 Outfit 衬衫 Logo](branding/logo-128.png)

项目源码与问题反馈的 canonical 地址为 [GitHub](https://github.com/zbw3790/3790s-fashion)、[Issues](https://github.com/zbw3790/3790s-fashion/issues)；版本变化见 [CHANGELOG](CHANGELOG.md)，独立安装包说明见 [INSTALL](INSTALL.md)。

## 安装需求

- 版本：`0.4.0`
- Mod ID：`fashion_3790`
- Minecraft：`26.2`
- Fabric Loader：`0.19.3` 或更高兼容版本
- Fabric API：`0.158.0+26.2` 或更高兼容版本
- Java：`25`；从源码构建需要 JDK 25

客户端与服务器都需安装 Fabric Loader、Fabric API 和本 Mod，才能使用完整功能。将 `3790s-fashion-0.4.0.jar` 与对应 Minecraft 版本的 Fabric API 放入双方的 `mods/` 目录，再启动游戏和服务器。单人游戏只需在客户端安装。

v0.4.0 将技术身份统一为 `fashion_3790`，保留现有披风、装束和衣柜行为，并纳入已联合验证的 Elytra Slot Visual Contract v1。继续使用原创 Outfit 衬衫 Logo、`#3790FF` 主色和 Outfit Tab 配色。当前 Logo 不再嵌入 Minecraft 原版盔甲架 sprite，图像由确定性脚本生成，未使用 AI。

## 这个 Mod 能做什么

- 把原版盔甲架当作衣柜入口：完整原版交互优先，没有原版动作时才打开衣柜，不要求空手或空架。
- Outfit 支持 PNG-only 资源；服主可执行 `/fashion3790 reload` 手动热重载装束，新端无需重连或重新应用。
- Creative Inventory 风格衣柜，支持 Standard / Compact 自适应布局、Cape／Outfit 两页签、4×3 披风列表和 4×2 装束列表。
- Outfit 可替换头部、身体、左右袖和左右裤腿六个原版外层部位，支持 WIDE／SLIM；可按整套、分组或单部位选择，保留原版、隐藏外层或形成混搭。
- 在带边框的深灰区域居中预览玩家，切换披风／鞘翅模型无需更换真实装备；支持“原版”选项和分页浏览，垂直跟随更平缓。
- Cape 与 Outfit 共用一份草稿和一次“应用”，由服务器确认、保存并同步给其他在线玩家；成功后保持衣柜打开，可以继续调整，等待期间图标保持亮度。
- 装束显示于世界第三人称、第一人称手臂和衣柜 Preview；网格使用正面二维样片，顶部 Cape／Outfit 页签分别将预览转向背面／正面，之后仍可自由旋转。
- 支持 Cape-only、Split、Shared 三种资产布局；自定义 Elytra 外观跟随所选披风，不单独选择。
- 资产暂时损坏时安全回退为原版外观，修复后可以恢复。

## 服主：添加披风

披风文件由服主保存在服务器本地：

```text
config/3790s-fashion/capes/<cape-id>/
```

每个 `<cape-id>` 目录代表一个可选择的披风。使用以下三种布局之一：

| 布局 | 文件 |
| --- | --- |
| Cape-only：仅披风 | `cape.png` |
| Split：分离披风与 Elytra | `cape.png` + `elytra.png` |
| Shared：共享单文件 | `cape_elytra.png` |

每张图片必须是真实、可解码的 PNG，尺寸为 **64×32**，文件大小 **不超过 65536 字节**；PNG 的 Alpha 透明度会保留。

可从 [`templates/capes/`](templates/capes/) 复制示例目录，再修改图案和目录名。完整命名、校验及回退规则见 [Cape Cosmetic 资产布局](docs/cape-cosmetic-asset-layout.md)。添加或修改披风文件后，当前版本需要**重启服务器**才能重新加载披风列表。

## 服主：添加装束

装束由服主放在服务器本地的 `config/3790s-fashion/outfits/<outfit-id>/`。v0.3.1 起 `outfit.json` 可选：最简单的目录只需 `wide.png`、`slim.png` 中的一种或两种；不会自动把一种臂宽转换成另一种。

PNG-only 模式从六个原版外层部位的全部 UV 面识别非透明像素，自动确定提供的部位；Base Skin 像素不参与识别。双模型的识别部位必须一致。存在 JSON 时仍使用严格显式模式，错误 JSON 不会改走自动模式；需要显式提供全透明部位时仍须 JSON。

整套、两种模型的显式 metadata 示例：

```json
{
  "schema_version": 1,
  "parts": ["head", "body", "left_arm", "right_arm", "left_leg", "right_leg"],
  "models": ["wide", "slim"]
}
```

局部装束只在 `parts` 列出提供的部位。图片须是遵循对应原版皮肤 UV 的 **64×64 PNG**，每张不超过 **65536 字节**；渲染只使用声明部位的外层，保留透明度，不替换基础皮肤。`outfit.json` 为 UTF-8，最多 **4096 字节**，使用上述三个字段；`parts` 和 `models` 必须非空，不重复、不使用未知值。不要在服务器扫描过程中修改资源目录。

未提供当前玩家模型的条目会显示为不适用，不能新选；暂不可用的已保存选择会保留并回退。衣柜不提供资源上传或编辑功能。

添加、修改或删除 Outfit 后，管理员或服务器控制台可执行：

```text
/fashion3790 reload
```

当前客户端自动更新世界、衣柜 Preview 和网格缩略，无需重新 Apply。可信删除装束目录只将引用它的部位恢复为 Original，Cape 和其他部位保持；根目录不可用时保留现有资源与选择，目录仍存在但无效时保留保存引用并回退显示。此命令仅重载 Outfit，不重载 Cape，也不提供实时文件监听。

## 玩家：使用衣柜

1. 找到一个原版盔甲架，架子已有装备也可以使用。
2. 右键盔甲架：先执行完整原版交互，没有原版动作时打开衣柜；普通无交互物品或无可执行原版动作的空手均可使用。放上、取下装备仍由原版优先处理，不会同时开衣柜。
3. 在 Cape／Outfit 页签中浏览和选择；装束可设置整套、分组或详细部位，并使用“原版／无外层”。两页签的修改汇入同一草稿。右上按钮可切换披风／鞘翅，仅影响预览，不改变真实装备。
4. 点击“应用”提交选择；服务器确认成功后保持同一衣柜窗口，可以继续选择和应用。
5. 按 ESC 或当前物品栏绑定键（默认 E）关闭衣柜，会丢弃尚未应用的草稿。

披风的“原版”（Original）表示由 Minecraft 决定原有 Cape/Elytra 外观；装束的“原版”保留对应皮肤外层，“无外层”（None）仅隐藏该外层，不隐藏基础皮肤。选择与已应用状态相同时，“应用”按钮禁用。旁观者模式不会打开衣柜。

## 与原版兼容

- 未安装本 Mod 的原版客户端可以进入安装了本 Mod 的服务器，但不能使用衣柜或显示自定义披风／装束。
- 安装了本 Mod 的客户端可以进入原版服务器；此时不提供衣柜和服务器时装功能。

v0.4.0 保持业务 schema 2。停服并备份后升级：新配置根 `config/3790s-fashion/` 不存在时，从 `config/vanilla-fashion/` 完整复制，保留全部旧文件；新目录已存在则只使用新目录，不合并旧资产。迁移时不要并发修改目录；遇到链接、访问失败或不完整复制会停止启动，避免空 Registry 错误清除选择。

世界保存由 `data/vanilla_fashion/player_fashion.dat` 验证后复制到 `data/fashion_3790/player_fashion.dat`，只注册新位置的一份权威状态，保留旧文件。坏文件不会被默认状态覆盖，新位置优先且不会因坏文件回退旧副本。Cape／Outfit ID、Original、None、dormant 与玩家记录原样保留。旧 schema v1 继续在实际修改后才写成 v2，不增加 schema 3。新路径使用后旧副本不再同步，降级前必须自行恢复正确备份，不能直接把旧副本当成最新存档。

客户端缓存改用 `3790s-fashion/cache/`，由服务器提供的内容重新建立，旧缓存不删除。所有网络频道使用 `fashion_3790:*`，不提供 v0.4.0 与 v0.3.x 的跨版本 Fashion 联网；客户端与服务器应一起升级。`provides: vanilla_fashion` 只是 Fabric 依赖别名，不是旧协议桥接。

可选兼容 **3790's Elytra Slot 0.1.0**（Visual Contract v1）。附属需另行安装，不随主 Mod 分发，也不是主 Mod 必需依赖。其 `elytra_slot_3790:visual_contract = 1`、`elytra_slot_3790:visual_compatibility` 及接口语义保持。主 Mod 不负责真实 BODY 槽位或翼部提交，不复制附属功能。

Cape 定义在服务器启动时加载，Outfit 另支持上述手动 reload。当前不提供资源上传／编辑、权限／entitlement 或热重载管理 GUI，不包含 Elytra Slot 附属或 Armor Visual。历史 v0.3.x 功能与制品仍保留在原版本 Tag／Release，不能将旧网络兼容矩阵当作 v0.4.0 的跨版本承诺。

详细的双向兼容行为见[兼容性说明](docs/compatibility.md)。

## 开发者构建

需要已核验的 Visual Contract v1 单接口编译 JAR（仅 compile/test，不随主 Mod 分发）：1410 bytes，SHA-256 `f1722277bd6301cd22c43c01ddc3ce325917cb19a08bc2d91e8bd02ee2dbe096`。从接口 owner 获取已核验的编译制品后显式传入路径；源码导出不携带该 JAR，不会下载未知接口，也不会访问相邻附属工作树。缺少它时构建明确失败。该外部编译输入目前由接口 owner 单独提供；开发者公开分发延期，不影响用户安装主 Mod，也不应安装进游戏 mods。Java package 为 `dev.zbw3790.fashion`，Maven group 为 `dev.zbw3790`。

Windows PowerShell：

```powershell
.\gradlew.bat -PelytraSlotVisualApiJar=C:/path/3790s-elytra-slot-visual-api-1.jar clean build --console=plain
```

正式 Mod JAR 输出到 `build/libs/3790s-fashion-0.4.0.jar`。

如需重建包含 README、许可证和 Cape 模板的完整发布 ZIP：

```powershell
python -B tools/package_release.py
```

## 许可证

项目代码采用 [MIT License](LICENSE)；当前 Logo 使用项目原创 Outfit 衬衫与 `#3790FF` 主色，素材与历史说明见 [品牌说明](branding/NOTICE.md)。
