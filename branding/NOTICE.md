# 品牌素材来源与使用说明

当前 Logo 使用 Minecraft Java Edition 26.2 的盔甲架物品素材和默认像素字体。项目不是 Mojang／Microsoft 官方项目，图标不代表官方批准，也不是完全原创图像。

项目代码的 MIT 声明不授予第三方素材的权利；这份来源声明不代表取得新的授权。第三方素材的公开使用许可尚未确认解决。

## 来源与处理

| 来源 | SHA-256 |
| --- | --- |
| Minecraft 26.2 官方客户端 JAR | `40896ee9f1e2bec3c934daac7e93d41e9e3d9c2f8ae0ca366d52ffbfd1afa290` |
| `assets/minecraft/textures/item/armor_stand.png` | `54d95c69cf2bb9e566c6a013c8258bd44e998de5d0578fc79acb8d16b38b1c8a` |
| `assets/minecraft/textures/font/ascii.png` | `e8646f1ed1f4bfd597d262cca3d8fac88ceaaa62807bbd5e607b2e3fe41c928a` |

物品引用经 `assets/minecraft/items/armor_stand.json` 和 `assets/minecraft/models/item/armor_stand.json` 指向上述物品 PNG；默认字体经 `assets/minecraft/font/default.json` 和 `assets/minecraft/font/include/default.json` 指向上述字体贴图。

处理方式为脚本像素处理，未使用 AI 图像生成：保留盔甲架原色及 Alpha 轮廓，向外增加一原始像素的纯白描边；使用原版数字字形，按 37／90 两行置于深炭灰背景，以低对比颜色绘制。128 像素基础画布使用整数倍最近邻放大，不嵌入独立原材质、字体贴图或客户端 JAR。

## 校验与完整再生

普通构建直接使用仓库内 PNG，不需要游戏资源或 Pillow。以下命令只检查现有图像，省略参数时也是这个行为：

```powershell
python -B tools/branding/generate_logo.py --check
```

完整再生需要自行提供上述版本及摘要匹配的本地官方客户端 JAR，并具备 Pillow（冻结编码环境为 10.3.0）。只向新的空目录写入，缺少资源时明确失败，不自动下载或替换字体：

```powershell
python -B tools/branding/generate_logo.py --regenerate --client-jar "<本地客户端JAR>" --output-dir run/branding-regenerated
```

生成器会检查三份再生 PNG 与批准摘要一致；现有 PNG 校验不等于完整再生验证。

| 正式文件 | SHA-256 |
| --- | --- |
| `logo.png`（512×512） | `6301eebeda43cc47623cd3886fb47b21f77cf2abab3a3e2a36970b279618c6ce` |
| `logo-256.png`（256×256） | `31bc223b080557920154886092d16e26b35927fff914d5692aa82822c1e17eed` |
| `logo-128.png`（128×128） | `52efa02194417c62039ae011db3afabf867a52bb8ec64a9a03ff9404baa5e8d6` |

本次仓库品牌更新未替换已经发布的 v0.2.1 JAR／ZIP 附件。
