# 3790's Vanilla Style Fashion

适用于 Minecraft Java Edition 26.2 + Fabric 的服务器权威披风时装 Mod，保持原版视觉与交互风格。

![项目 Logo](branding/logo-128.png)

## 支持环境

- 版本：`0.1.0`
- Minecraft：`26.2`
- Fabric Loader：`0.19.3` 或更高兼容版本
- Fabric API：`0.158.0+26.2` 或更高兼容版本
- 构建 JDK：`25`

完整功能需要客户端与服务器同时安装本 Mod 和 Fabric API。未安装本 Mod 的原版客户端仍可加入安装了本 Mod 的服务器；安装了本 Mod 的客户端也可以连接原版服务器，详见[兼容性说明](docs/compatibility.md)。

## 基本功能

- 服务器本地管理 Cape Cosmetic 资产和玩家选择。
- 空手右键完全空的原版盔甲架打开原版风格衣柜。
- 衣柜提供披风网格、玩家模型预览以及服务器确认后的正式选择。
- 支持披风与 Elytra 的原版模型、姿态和动画。

## 资产与模板

服务器资产放置在：

```text
config/vanilla-fashion/capes/<id>/
```

支持三种 64×32 PNG 布局：

| 布局 | 文件 |
| --- | --- |
| 仅披风 | `cape.png` |
| 分离披风与 Elytra | `cape.png` + `elytra.png` |
| 共享单文件 | `cape_elytra.png` |

可从 [`templates/capes/`](templates/capes/) 复制正式示例。完整命名、校验和回退规则见 [Cape Cosmetic 资产布局](docs/cape-cosmetic-asset-layout.md)。修改资产后需要重启服务器。

## 安装与使用

1. 在客户端和服务器安装 Minecraft 26.2、Fabric Loader、Fabric API 与本 Mod。
2. 把 Cape Cosmetic 目录放入服务器的 `config/vanilla-fashion/capes/`。
3. 启动服务器；玩家主手为空时，右键六个装备槽和手持槽均为空的原版盔甲架。
4. 在衣柜中预览条目并点击“完成”，由服务器批准并保存选择。

## 构建

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
