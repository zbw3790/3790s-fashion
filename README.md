# 3790's Vanilla Style Fashion

为 Minecraft Java Edition 26.2 提供服务器本地披风、Outfit 外层装束与 Creative Inventory 风格衣柜：服主准备外观资源，玩家在游戏内预览和选择，保留原版基础皮肤、模型与动作。

![项目 Logo](branding/logo-128.png)

## 安装需求

- 当前已发布稳定版本：`v0.3.0`
- Minecraft：`26.2`
- Fabric Loader：`0.19.3` 或更高兼容版本
- Fabric API：`0.158.0+26.2` 或更高兼容版本
- Java：`25`；从源码构建需要 JDK 25

客户端与服务器都需安装 Fabric Loader、Fabric API 和本 Mod，才能使用完整功能。将 `vanilla-fashion-0.3.0.jar` 与对应 Minecraft 版本的 Fabric API 放入双方的 `mods/` 目录，再启动游戏和服务器。单人游戏只需在客户端安装。

## 这个 Mod 能做什么

- 把完全空置的原版盔甲架当作衣柜入口，不添加新方块或物品。
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
config/vanilla-fashion/capes/<cape-id>/
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

装束由服主放在服务器本地的 `config/vanilla-fashion/outfits/<outfit-id>/`。每个目录包含 `outfit.json`，并按声明提供 `wide.png`、`slim.png` 中的一种或两种；不会自动把一种臂宽转换成另一种。

整套、两种模型的 metadata 示例：

```json
{
  "schema_version": 1,
  "parts": ["head", "body", "left_arm", "right_arm", "left_leg", "right_leg"],
  "models": ["wide", "slim"]
}
```

局部装束只在 `parts` 列出提供的部位。图片须是遵循对应原版皮肤 UV 的 **64×64 PNG**，每张不超过 **65536 字节**；渲染只使用声明部位的外层，保留透明度，不替换基础皮肤。`outfit.json` 为 UTF-8，最多 **4096 字节**，使用上述三个字段；`parts` 和 `models` 必须非空，不重复、不使用未知值。添加或修改资源后需要重启服务器；不要在服务器扫描过程中修改资源目录。

未提供当前玩家模型的条目会显示为不适用，不能新选；暂不可用的已保存选择会保留并回退。衣柜不提供资源上传或编辑功能。

## 玩家：使用衣柜

1. 找到一个头部、身体、腿、脚、主手和副手都没有物品的原版盔甲架。
2. 保持玩家主手为空，使用主手右键盔甲架。
3. 在 Cape／Outfit 页签中浏览和选择；装束可设置整套、分组或详细部位，并使用“原版／无外层”。两页签的修改汇入同一草稿。右上按钮可切换披风／鞘翅，仅影响预览，不改变真实装备。
4. 点击“应用”提交选择；服务器确认成功后保持同一衣柜窗口，可以继续选择和应用。
5. 按 ESC 或当前物品栏绑定键（默认 E）关闭衣柜，会丢弃尚未应用的草稿。

披风的“原版”（Original）表示由 Minecraft 决定原有 Cape/Elytra 外观；装束的“原版”保留对应皮肤外层，“无外层”（None）仅隐藏该外层，不隐藏基础皮肤。选择与已应用状态相同时，“应用”按钮禁用。旁观者模式不会打开衣柜。

## 与原版兼容

- 未安装本 Mod 的原版客户端可以进入安装了本 Mod 的服务器，但不能使用衣柜或显示自定义披风／装束。
- 安装了本 Mod 的客户端可以进入原版服务器；此时不提供衣柜和服务器时装功能。

v0.3.0 可读取 v0.2.1 的 schema v1 保存，仅发生实际保存修改时写入 schema v2；不要用旧版 Mod 反向读取 v2 保存。当前客户端连接 v0.2.1 服务器时，Cape 正常可用，Outfit 页签提示不支持；v0.2.1 客户端连接当前服务器时，其 Cape 修改不会清除已有装束。

资源定义在服务器启动时加载。当前不提供资源上传／编辑、权限／entitlement 或产品级热重载管理界面，不包含 Elytra Slot 附属或 Armor Visual。

详细的双向兼容行为见[兼容性说明](docs/compatibility.md)。

## 开发者构建

Windows PowerShell：

```powershell
.\gradlew.bat clean build --console=plain
```

正式 Mod JAR 输出到 `build/libs/vanilla-fashion-0.3.0.jar`。

如需重建包含 README、许可证和 Cape 模板的完整发布 ZIP：

```powershell
python -B tools/package_release.py
```

## 许可证

项目代码采用 [MIT License](LICENSE)；Logo 所含第三方素材见 [品牌素材来源说明](branding/NOTICE.md)。
