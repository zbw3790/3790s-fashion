# Cape Cosmetic 资产布局

## 根目录与 ID

每个 Cape Cosmetic 是服务器目录中的一个直接子目录：

```text
config/vanilla-fashion/capes/<id>/
```

`<id>` 长度为 1～64。首字符必须匹配 `[a-z0-9]`，其余字符只能使用小写英文字母、数字、下划线、连字符或点，即整体匹配：

```text
[a-z0-9][a-z0-9_.-]{0,63}
```

## 三种合法布局

只识别目录中的以下精确小写文件名：

- `cape.png`
- `elytra.png`
- `cape_elytra.png`

| 布局 | 文件 | Cape | Elytra |
| --- | --- | --- | --- |
| 仅披风 | `cape.png` | 使用该文件 | 使用 Minecraft 默认 Elytra 纹理 |
| 分离 | `cape.png` + `elytra.png` | 使用 `cape.png` | 使用 `elytra.png` |
| 共享 | `cape_elytra.png` | 使用该文件 | 使用同一文件 |

共享布局只是让 Cape 与 Elytra 引用同一份符合 Vanilla UV 的 64×32 PNG；两种模型仍使用各自的 UV 区域。

以下组合无效：

- 只有 `elytra.png`
- `cape.png` 与 `cape_elytra.png` 同时存在
- `elytra.png` 与 `cape_elytra.png` 同时存在
- 三个识别文件同时存在

普通说明文件或预览图片会被忽略，子目录不会递归扫描。错误大小写的文件名不会被识别。

## PNG 要求

每个识别文件必须：

- 是可正常解码的真实 PNG，而不是只修改扩展名的其他格式；
- 尺寸严格为 `64×32`；
- 文件大小不超过 `65536` 字节；
- 遵循 Minecraft Vanilla Cape/Elytra UV；
- 自行保留所需透明区域。

Mod 不会自动缩放、裁剪或转换不合格纹理。

## 安装步骤

1. 从 [`templates/capes/`](../templates/capes/) 选择一个完整模板目录。
2. 将目录复制到服务器的 `config/vanilla-fashion/capes/`。
3. 如需重命名目录，确保新名称符合 ID 规则。
4. 编辑 recognized PNG，同时保持文件名、尺寸、格式、大小和 Vanilla UV 合法。
5. 启动或重启服务器。

`blue-migrator` 展示共享单文件布局；`rose-red-migrator` 展示分离布局。模板不会由 Runtime JAR 自动安装。

## 错误与回退

单个损坏条目采用 fail-soft：服务器拒绝该条目，但其他合法条目和服务器启动不受影响。

- 条目目录仍存在但资产暂时无效时，玩家保存的选择会保留为休眠状态，世界外观临时回退为原版；修复资产并重启后可以恢复。
- 在可正常读取的根目录中明确删除整个 `<id>/` 目录后，服务器会永久清除对应的已保存选择。
- Cape 根目录整体不可读取时，系统不会把全部条目误判为明确删除，而会使用空 Registry 安全回退并保护已有状态。

修改或删除服务器资产前请自行备份。v0.1 不提供热重载、管理 GUI 或撤销工具。
