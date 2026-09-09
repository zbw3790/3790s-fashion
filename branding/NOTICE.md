# 品牌素材来源与使用说明

## 当前品牌：原创 Outfit 衬衫

当前 Logo 不再嵌入 Mojang Armor Stand sprite 或任何 Minecraft 原版贴图。中央主体直接读取项目 `WardrobeGuiPainter.OUTFIT_ICON` 的原创 16×16 衬衫几何；仅对项目自有图标统一品牌色，仍保持 Minecraft-like 像素风格。项目不是 Mojang／Microsoft 官方项目。

项目品牌主色为 **#3790FF**（RGB 55, 144, 255）。阴影、边缘与高光使用固定整数混色公式：`(channel × (100 - percent) + target × percent + 50) // 100`。边缘混黑 55%、阴影混黑 20%、高光混白 30%；Outfit Tab 与 Logo 使用相同配色，其他 UI／玩家资源颜色不替换。

保留原 128×128 母版中的炭灰背景 `(38,41,43)`、低对比数字色 `(54,58,61)`，以及数字坐标 3=(8,14)、7=(90,14)、9=(8,72)、0=(90,72)，每字占 5×7 格、每格 6 像素。**数字仅由原创七段矩形绘制**，不再读取原版字体；为移除字体资产依赖，具体字形与历史版本有所区别。衬衫保持正中，含外描边为 84×84 母版像素，最长边占 65.625%；一个逻辑像素的纯白外描边为 6 个母版像素。全部尺寸共用母版并最近邻缩放，无抗锯齿或插值色。

未使用 AI 图像生成、生成式编辑、AI 放大、网络绘图或第三方 sprite；生成器不访问网络、不读取 Minecraft JAR／纹理。历史候选脚本仅作内部记录，不属于当前生成链或公开导出。

## 离线生成、产品 icon 与平台上传

复用 `tools/branding/generate_logo.py`，使用当前环境已有的 Pillow；本次验证编码环境为 **Pillow 10.3.0**。只需项目原创 Painter Java 文本，不需要 Minecraft 缓存、客户端 JAR、Blender 或 Gradle 依赖。

```powershell
python -B tools/branding/generate_logo.py --check
python -B tools/branding/generate_logo.py --regenerate --output-dir run/branding-regenerated
```

默认只读检查四份 PNG 和 metadata icon；完整生成只接受新空目录。可用 `--preview` 输出本地审查图，用 `--manifest` 留存确定性来源／参数／SHA 清单。生成器不以旧 PNG 作为输入。

- Canonical／**MODRINTH_ICON_PATH**：`branding/logo.png`，512×512，不透明 RGBA 方形。
- 派生版本：`branding/logo-256.png`、`branding/logo-128.png`、`branding/logo-64.png`。
- 产品 metadata 继续引用 `assets/vanilla_fashion/icon.png`；对应 source tree PNG 与 128 版本逐字节相同。
- 新品牌只影响本次提交后的源码／未来构建；已发布 **v0.3.1** JAR／ZIP、Tag、GitHub Release 与 Release Archive 保持冻结。
- 品牌视觉已获批准；公开源码按正式发布流程同步，Modrinth 仍由用户手动上传。

| 当前文件 | SHA-256 |
| --- | --- |
| `logo.png`（512×512） | `51d51b580197812431141bea66e82546bd902c608f10a61b7cf8b89923c2647a` |
| `logo-256.png`（256×256） | `b591dac8fb7631fdbf985f9bc9d191f60503f7b319c0808ac8474cad4e558966` |
| `logo-128.png`（128×128） | `05d31278fd2e98a28fa1f5879e29ea280af846853ed86e7c03ccf7a2e79f2cfd` |
| `logo-64.png`（64×64） | `59232a4adb2f934d46cd6e28985cf72db29fe04848c1eafdf7ebe873c461ebb5` |

## 历史品牌：原版盔甲架与数字字体

以下保留旧版来源及再生说明作为历史记录，适用于已冻结的旧品牌和包含它的既有发布制品。其“当前”“正式”措辞是当时的历史语境，旧生成命令应从对应历史 Git 版本使用，不能作为现行工具说明。原素材及字体从未因加描边而改变权属；本次不作新的法律授权结论。

<details>
<summary>旧版品牌说明原文</summary>

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

</details>
