# 3790's Vanilla Style Fashion

为 Minecraft Java Edition 26.2 提供服务器本地披风与 Creative Inventory 风格衣柜：服主准备披风，玩家在游戏内预览并选择，外观继续使用 Minecraft 原版模型与动作。

![项目 Logo](branding/logo-128.png)

## 安装需求

- 当前候选版本：`0.2.1`（内部发布准备，尚未公开发布）
- Minecraft：`26.2`
- Fabric Loader：`0.19.3` 或更高兼容版本
- Fabric API：`0.158.0+26.2` 或更高兼容版本
- Java：`25`；从源码构建需要 JDK 25

客户端与服务器都需安装 Fabric Loader、Fabric API 和本 Mod，才能使用完整功能。将 `vanilla-fashion-0.2.1.jar` 与对应 Minecraft 版本的 Fabric API 放入双方的 `mods/` 目录，再启动游戏和服务器。单人游戏只需在客户端安装。

## 这个 Mod 能做什么

- 把完全空置的原版盔甲架当作衣柜入口，不添加新方块或物品。
- Creative Inventory 风格衣柜，支持 Standard / Compact 自适应布局、32×32 披风分类页签与 4×3 披风列表。
- 在带边框的深灰区域居中预览玩家，切换披风／鞘翅模型无需更换真实装备；支持“原版”选项和分页浏览，垂直跟随更平缓。
- 点击“应用”后由服务器确认并保存选择，同步给其他在线玩家；成功后保持衣柜打开，可以继续调整，等待期间图标保持亮度。
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

## 玩家：使用衣柜

1. 找到一个头部、身体、腿、脚、主手和副手都没有物品的原版盔甲架。
2. 保持玩家主手为空，使用主手右键盔甲架。
3. 在衣柜中翻页浏览，点击“原版”或披风条目查看预览；右上按钮可切换披风／鞘翅，仅影响预览，不改变真实装备。
4. 点击“应用”提交选择；服务器确认成功后保持同一衣柜窗口，可以继续选择和应用。
5. 按 ESC 或当前物品栏绑定键（默认 E）关闭衣柜，会丢弃尚未应用的草稿。

“原版”（Original）表示不使用服务器披风，继续由 Minecraft 决定玩家原有的 Cape/Elytra 外观。选择与已应用状态相同时，“应用”按钮禁用。旁观者模式不会打开衣柜。

## 与原版兼容

- 未安装本 Mod 的原版客户端可以进入安装了本 Mod 的服务器，但不能使用衣柜或显示自定义披风。
- 安装了本 Mod 的客户端可以进入原版服务器；此时不提供衣柜和服务器自定义披风功能。

从 0.2.0 升级可继续使用现有披风文件和玩家选择数据，资产格式和保存格式不变，无需数据迁移。独立 Elytra 时装槽、Outfit、Armor、权限系统和热重载仍未实现。

详细的双向兼容行为见[兼容性说明](docs/compatibility.md)。

## 开发者构建

Windows PowerShell：

```powershell
.\gradlew.bat clean build --console=plain
```

正式 Mod JAR 输出到 `build/libs/vanilla-fashion-0.2.1.jar`。

如需重建包含 README、许可证和 Cape 模板的完整发布 ZIP：

```powershell
python -B tools/package_release.py
```

## 许可证

本项目采用 [MIT License](LICENSE)。
