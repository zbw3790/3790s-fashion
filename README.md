# 3790's Vanilla Style Fashion

为 Minecraft Java Edition 26.2 服务器提供披风衣柜：服主准备披风，玩家在游戏内预览并选择，外观仍使用 Minecraft 原版模型与动作。

![项目 Logo](branding/logo-128.png)

## 安装需求

- 版本：`0.1.0`
- Minecraft：`26.2`
- Fabric Loader：`0.19.3` 或更高兼容版本
- Fabric API：`0.158.0+26.2` 或更高兼容版本
- Java：游戏按 Minecraft 26.2 的正常要求运行；从源码构建需要 JDK 25

完整功能需要客户端和服务器都安装 Fabric Loader、Fabric API 与本 Mod。没有安装本 Mod 的原版客户端仍能进入服务器，只是不能使用衣柜或显示本 Mod 的披风。

## 这个 Mod 能做什么

- 把完全空置的原版盔甲架当作衣柜入口，不添加新方块或物品。
- 在衣柜中浏览服务器提供的披风，并直接在玩家模型上预览。
- 玩家点击“完成”后由服务器保存选择，并同步给其他在线玩家。
- 支持普通披风、独立 Elytra 纹理以及 Cape/Elytra 共用一张纹理。
- 资产暂时损坏时安全回退为原版外观，修复后可以恢复。

## 服主：添加披风

服务器资产放置在：

```text
config/vanilla-fashion/capes/<id>/
```

每个 `<id>` 目录代表一个可选择的披风。放入以下三种 64×32 PNG 布局之一：

| 布局 | 文件 |
| --- | --- |
| 仅披风 | `cape.png` |
| 分离披风与 Elytra | `cape.png` + `elytra.png` |
| 共享单文件 | `cape_elytra.png` |

可从 [`templates/capes/`](templates/capes/) 复制示例目录，再修改图案和目录名。完整命名、校验及回退规则见 [Cape Cosmetic 资产布局](docs/cape-cosmetic-asset-layout.md)。添加或修改资产后需要重启服务器。

## 玩家：使用衣柜

1. 找到一个头部、身体、腿、脚、主手和副手都没有物品的原版盔甲架。
2. 保持玩家主手为空，使用主手右键盔甲架。
3. 在衣柜里点击“原版”或一个披风条目进行预览。
4. 点击“完成”提交选择；点击“取消”或按 ESC 会放弃尚未提交的预览。

“原版”表示不使用服务器披风，继续由 Minecraft 决定玩家原有的 Cape/Elytra 外观。旁观者模式不会打开衣柜。

详细的双向兼容行为见[兼容性说明](docs/compatibility.md)。

## 开发者构建

Windows PowerShell：

```powershell
.\gradlew.bat clean build --console=plain
```

正式 Mod JAR 输出到 `build/libs/vanilla-fashion-0.1.0.jar`。

如需重建包含 README、许可证和 Cape 模板的完整发布 ZIP：

```powershell
python tools/package_release.py
```

## 许可证

本项目采用 [MIT License](LICENSE)。
