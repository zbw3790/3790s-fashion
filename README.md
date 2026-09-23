# 3790's Fashion

![3790's Fashion Logo](branding/logo-128.png)

**面向 Minecraft Java Edition 的服务器权威外观模组：披风、装束与盔甲外观，共用一个原版风格衣柜。**

本文以正式 **v1.0.0** 为基线，面向源码阅读者、贡献者、资源作者和兼容模组开发者。普通玩家可先阅读[中文入门](docs/getting-started-zh_cn.md)或 [English Getting Started](docs/getting-started-en_us.md)。

[正式发行](https://github.com/zbw3790/3790s-fashion/releases/tag/v1.0.0) · [安装说明](INSTALL.md) · [版本记录](CHANGELOG.md) · [问题反馈](https://github.com/zbw3790/3790s-fashion/issues) · [MIT License](LICENSE)

## 项目概览

3790's Fashion 从世界或服务器的本地资源库加载外观定义，向客户端同步所需纹理，并由服务器验证、保存和同步玩家选择。客户端负责衣柜、试穿和实际渲染，不以本地预览替代服务器保存。

| 系统 | v1.0.0 提供的能力 |
| --- | --- |
| Cape | 自定义披风、绑定的 Elytra 纹理、Original 回退，以及 Cape-only／Split／Shared 三种资源布局。 |
| Outfit | 六个皮肤外层部位：头部、身体、左右袖、左右裤腿；支持 WIDE／SLIM、整套／分组／单部位选择与混搭。 |
| Armor | 头盔、胸甲、护腿、靴子独立选择 Original／Hidden／Custom；使用真实装备的原版模型。 |
| Wardrobe | 三页签共用草稿与一次 Apply；纹理样片、可旋转预览、Standard／Compact 布局、键盘操作、中英文界面与长 Tooltip 分页。 |

模组改变外观，不改变基础皮肤、真实装备、属性、耐久、附魔数据或生存机制。Outfit 的 `NONE` 仅隐藏皮肤外层；Armor 的 `HIDDEN` 隐藏盔甲视觉而不卸下装备。Cape 的 `Original` 不等于隐藏披风，Elytra 外观也不是独立的选择槽。

相对公开版 0.5.0，1.0.0 的主要变化是手绘衣柜图标、中英文本地化、Tooltip 避让与分页，以及双语入门资料。盔甲系统、统一 Apply、同步和资源重载不是 1.0 首次新增的功能。

## 版本与开发环境

| 项目 | 1.0.0 基线 |
| --- | --- |
| Minecraft | Java Edition `26.2` |
| Java | 编译使用 **JDK 25**；正式验收使用 JDK `25.0.4.1+1` |
| Fabric Loader | 构建基线 `0.19.3`；运行 metadata 要求 `>=0.19.3` |
| Fabric API | 构建基线 `0.158.0+26.2`；运行时须匹配 Minecraft 26.2 |
| Gradle Wrapper | `9.5.1` |
| Fabric Loom | 配置为 `1.17-SNAPSHOT`；正式构建记录的解析版本为 `1.17.21` |
| JUnit | JUnit 5，BOM `5.14.4` |
| Mod ID／资源与网络 namespace | `fashion_3790` |
| Java 主包 | `dev.zbw3790.fashion` |
| Maven group／制品前缀 | `dev.zbw3790`／`3790s-fashion` |

配置来源为 [gradle.properties](gradle.properties)、[build.gradle](build.gradle)和 [fabric.mod.json](src/main/resources/fabric.mod.json)。`mod_version` 是构建版本的唯一来源，资源处理时将其注入 metadata。

运行时允许的依赖范围不等于所有组合都已验证。重现正式构建时，应记录实际 JDK、Loom 解析结果和外部编译输入；不要把 `1.17-SNAPSHOT` 理解为永久固定到同一插件字节。

## 开发者快速开始

### 1. 获取正式版本源码

```bash
git clone --branch v1.0.0 --depth 1 https://github.com/zbw3790/3790s-fashion.git
cd 3790s-fashion
```

`v1.0.0` 对应公开提交 `111cf2c3d37bd9999a3d53fd9f3e17ffac6a930c`。按 Tag 克隆便于研究已发布行为；需要修改时再创建自己的工作分支，不移动正式 Tag。

使用仓库自带的 Gradle Wrapper，并将 IDE 的 Gradle JVM 和命令行构建环境设为 JDK 25。

### 2. 准备 Visual Contract v1 编译接口

> **运行主模组不需要安装 Elytra Slot，但从本仓源码构建需要一份额外的、已冻结的 Visual Contract v1 接口 JAR。**

该 JAR 由接口维护者单独提供。v1.0.0 的公开源码与玩家发行附件不包含它，也没有在构建中声明可自动下载的公开接口坐标。尚未取得接口文件时，应先联系维护者；仅克隆仓库不足以完成客户端编译与测试。

```text
参考文件名：3790s-elytra-slot-visual-api-1.jar
大小：1410 bytes
SHA-256：
f1722277bd6301cd22c43c01ddc3ce325917cb19a08bc2d91e8bd02ee2dbe096
```

Gradle 通过 `elytraSlotVisualApiJar` 属性读取它；未显式设置时查找：

```text
run/dependencies/3790s-elytra-slot-visual-api-1.jar
```

[`verifyVisualContractApi`](build.gradle) 会检查文件存在性、大小和完整 SHA-256，客户端与测试编译依赖这项检查。缺失或指纹不符会明确失败。不要用完整附属 Mod JAR、自己重编的近似接口或空桩绕过校验。

该依赖只进入 `clientCompileOnly` 和测试 classpath，不打入主 JAR，也不构成主模组的硬运行依赖。不要将独立接口 JAR 放入玩家或测试游戏的 `mods/`。

### 3. 构建与测试

Windows PowerShell：

```powershell
$api = "C:/deps/3790s-elytra-slot-visual-api-1.jar"

.\gradlew.bat "-PelytraSlotVisualApiJar=$api" verifyVisualContractApi clean test build sourcesJar --no-build-cache --console=plain
```

POSIX shell：

```bash
api="/absolute/path/3790s-elytra-slot-visual-api-1.jar"

bash ./gradlew "-PelytraSlotVisualApiJar=$api" verifyVisualContractApi clean test build sourcesJar --no-build-cache --console=plain
```

将示例路径替换为本机已经核验的接口文件。PowerShell 中保留整个 `-P...` 参数的引号，以支持带空格的路径。以上是命令用法，不代表所有操作系统都经过同等的游戏实机验收。

v1.0.0 的主要输出：

```text
build/libs/3790s-fashion-1.0.0.jar
build/libs/3790s-fashion-1.0.0-sources.jar
build/reports/tests/test/index.html
build/test-results/test/
```

开发中的新版本文件名随 `mod_version` 派生。测试通过不等于视觉和多人实机验收通过；发布前还需核对实际运行、保存和最终制品。

### 4. 在隔离游戏环境验证

将构建的主 JAR 与对应 Fabric API 放入独立 Fabric 实例。多人改动应在独立服务器和至少两个客户端中检查；存档升级只使用备份副本，不直接测试正式世界。

衣柜入口是原版盔甲架：原版放取装备优先，没有原版动作时才打开衣柜；次要使用绕过衣柜。三页签编辑仅影响草稿，Apply 经服务器确认后生效，关闭只丢弃尚未提交的修改。

公开仓包含普通产品测试，但不包含内部完整的自动实机 harness 和编排环境。`build.gradle` 保留的 harness 源集或任务名，不代表该公开快照已经提供可直接复现内部矩阵的完整测试 Mod。

## 源码导航

使用 `main`／`client` 分离源集。下面是主要目录和阅读入口，而不是对所有 Java 类型的稳定 API 承诺。

| 位置 | 职责 |
| --- | --- |
| [`src/main/java/dev/zbw3790/fashion/`](src/main/java/dev/zbw3790/fashion/) | 通用与服务端实现；含领域模型、资源加载、保存、同步和交互入口。 |
| `…/fashion/` | 聚合玩家外观、Stored／Effective、服务与持久化。 |
| `…/cape/`、`…/outfit/`、`…/armor/` | 三类资源、选择类型、校验、Registry 与领域规则。 |
| `…/asset/` | 资源内容与校验等基础能力。 |
| `…/network/` | 自定义消息、能力判断、事务与资源同步。 |
| `…/identity/`、`…/wardrobe/`、`…/mixin/` | 旧身份迁移、衣柜交互及通用侧局部接入。 |
| [`src/client/java/dev/zbw3790/fashion/client/`](src/client/java/dev/zbw3790/fashion/client/) | 客户端权威视图、资源就绪、衣柜、渲染与可选兼容提供者。 |
| [`src/main/resources/`](src/main/resources/) | Fabric metadata、语言、图标及通用资源。 |
| [`src/test/`](src/test/) | 普通产品测试与测试资源。 |
| [`docs/`](docs/) | 中英文入门、Cape／Armor 资源说明和兼容记录。 |
| [`templates/capes/`](templates/capes/) | 可复制安装的披风模板。 |
| [`tools/`](tools/) | 公开的打包与相关校验工具；不是完整内部发行系统。 |

建议先从 [PlayerFashionService](src/main/java/dev/zbw3790/fashion/fashion/PlayerFashionService.java) 阅读服务器状态和 Apply，再沿 `network/` 查看消息流；研究客户端时，将衣柜草稿、已同步权威和渲染快照分开阅读。

## 架构概览

### 单一外观权威，三类状态

Cape、Outfit 和 Armor 属于同一份玩家外观状态，由服务器上的聚合服务统一处理，不是三个各自保存、独立提交的系统。

| 层次 | 含义 |
| --- | --- |
| Stored | 玩家已经保存的选择意图，包括仍需保留的失效资源引用。 |
| Server Effective | 根据服务器当前可信资源和支持范围解析出的有效外观。 |
| Render Readiness | 客户端下载、校验、GPU 纹理就绪及实际装备适用性；不是新的服务器选择。 |

客户端下载失败或纹理尚未就绪只影响本地显示，不应自动改写 Stored、发送一次 Apply，或把失效引用清成 Original。

`PlayerFashionService` 的调用限定在服务器线程。在线 revision 随当前 membership 存在：新连接建立自己的起点，断开释放对应状态；连接身份与 UUID 不是可以互相替代的校验条件。

### 草稿与事务

```text
衣柜输入
  → 本地三领域草稿与独立试穿
  → Apply：请求标识 + expectedRevision + 完整选择
  → 服务器验证连接、能力、资源准入和当前 revision
  → 原子提交聚合外观
  → 状态同步与请求结果
  → 客户端更新已应用外观和衣柜基线
```

客户端的草稿合并、冲突表达和 pending 状态不能替代服务器的版本检查。关闭窗口不取消已经发送的请求，也不应因迟到确认自动重开衣柜。资源故障、可信空列表、不支持、下载中和失效引用是不同状态，不能全部归为“没有外观”。

### 渲染与生命周期

世界、第一人称、原版物品栏和衣柜使用明确的场景与本帧输入。世界和原版物品栏消费已应用状态；衣柜消费当前草稿，并与真实装备隔离。

渲染使用独立、不可变的外观与部位快照，不通过临时更换真实装备、修改基础皮肤或共享可变模型实现试穿。原版物品栏的 Outfit 使用该次 GUI 绘制最终采用的模型姿态，不能只在打开窗口时复制一次，也不能仅按 tick 缓存。

Armor Custom 只替换受支持装备结构的基础层纹理，保留真实模型、染色、覆盖层、饰纹与附魔路径。渲染消费已就绪资源，不在绘制帧中扫描服务器目录或解码 PNG。

### 协议与持久化基线

v1.0.0 使用 **17 条 S2C／5 条 C2S、8 个生产 Mixin、SavedData schema 4、Full v4、Visual Contract v1**。这些是该发行版本的实现基线，不表示产品版本号变化时必须一并升号。

`fashion_3790` 是当前 Mod ID 与网络 namespace。metadata 中的 `provides: vanilla_fashion` 仅用于旧 Fabric 依赖识别，不是旧 Java 包、旧网络 namespace 或任意历史协议的兼容桥。

0.5.0 与 1.0.0 的双向组合经过有限真实验收，均使用 Full v4／schema 4；部署仍建议双方使用同版。历史或原版端按实际能力处理，不能从某个兼容测试推出所有旧版本、所有附属和所有渲染 Mod 都受支持。

## 外观资源与存储

服务器或单人实例的资源根为：

```text
config/3790s-fashion/
├── capes/<cape-id>/
├── outfits/<outfit-id>/
└── armor/<style-id>/
```

| 领域 | 定义与纹理 | 更新方式 |
| --- | --- | --- |
| Cape | `cape.png`，或 `cape.png`＋`elytra.png`，或 `cape_elytra.png`；64×32 PNG。 | 正常重启服务器。 |
| Outfit | `wide.png`／`slim.png`；64×64 PNG；`outfit.json` 可选。 | `/fashion3790 reload`。 |
| Armor | 必需 `armor.json`；声明相应 `outer`／`inner`；64×32、8 位 RGBA、非交错 PNG，Alpha 仅 0／255。 | `/fashion3790 reload`。 |

Outfit 无 JSON 时，根据六个外层部位的全部 UV 面推导提供部位；WIDE／SLIM 同时存在时需要一致的部位集合。JSON 一旦存在就严格按显式定义解析，解析错误不回退 PNG-only。

Armor 的 outer 服务 HEAD／CHEST／FEET，inner 服务 LEGS。Custom 不生成空槽装备，不替换物品图标；透明基础纹理也不会取消原版的独立覆盖层和饰纹。需要完整隐藏时使用 Hidden。

资源 ID、作者名称、文件名和 hash 是数据，不随界面语言改变。客户端从服务器获取所需纹理；磁盘缓存可复用，但不构成对当前服务器资源的授权。

玩家选择位于：

```text
<世界目录>/data/fashion_3790/player_fashion.dat
```

客户端缓存根为 `3790s-fashion/cache/`。原始资源、世界选择与缓存用途不同；备份应保留世界及原始资源，不能只保留客户端缓存。

**删除策略按领域区分：**可信扫描确认 Cape／Outfit 目录被明确删除时，会清除对应选择或将引用部位恢复 Original；Armor 缺失或无效时保留 Custom 引用并回退，恢复资源后重新生效。暂时无效、根不可读和明确删除不能混为一谈。完成文件编辑后再执行重载，不在扫描期间并发修改资源目录。

完整格式、文件预算及使用步骤见[中文入门](docs/getting-started-zh_cn.md)、[Cape 资源布局](docs/cape-cosmetic-asset-layout.md)与[盔甲资源说明](docs/armor-resources.md)。

## Elytra Slot 兼容开发

3790's Elytra Slot 是独立可选模组。主模组拥有外观选择、资源同步、纹理与预览；附属拥有真实 BODY 槽位、物品存取、飞行、耐久、装备保存及真实额外翼提交。

v1.0.0 的正式接入点：

| 项目 | 值 |
| --- | --- |
| 接口 | `vanillafashion.elytraslot.api.client.ElytraVisualCompatibility` |
| 契约版本 | `1` |
| capability | `elytra_slot_3790:visual_contract`，整数 `1` |
| Fabric 自定义 entrypoint | `elytra_slot_3790:visual_compatibility` |
| 主模组实现 | [`ElytraSlotVisualProvider`](src/client/java/dev/zbw3790/fashion/client/render/ElytraSlotVisualProvider.java) |

现有入口连接只读披风遮挡谓词、识别独立 Preview，并向专用翼状态附着主模组外观。接口保留其原有包名；它不应随主 Mod 的品牌或包名统一而擅自迁移。

这是一份已有消费者的特定视觉协作契约，不是通用槽位 API，也不承诺所有内部 Java 包都是稳定 ABI。兼容开发应先明确状态与渲染所有者，再讨论接口变化；不要通过反射附属私有装备、复制资源下载器或建立第二份外观保存解决问题。

**已知限制：**固定附属组合在世界和主动试穿中的已有支持保持；额外 BODY 翼及其披风遮挡在原版物品栏中仍有缺口，按真实 BODY 装备驱动的衣柜组合预览尚未实现。主动“胸甲＋翼”试穿不代表这两项已经完成。不要把 `isPreview` 改为 false 或伪装 WORLD 来绕过原有隔离规则。

## 本地化与界面资源

语言资源位于 `src/main/resources/assets/fashion_3790/lang/`，本版支持 `zh_cn` 和 `en_us`，跟随 Minecraft 的语言与回退机制。客户端解析界面表达，Dedicated 代码不依赖客户端 I18n；管理员命令通过已有可翻译 Component 表达结果。

翻译使用原版 Component 参数语法，保留参数对应关系。资源 ID、作者填写的名称、路径和命令保持字面数据；不要将它们再次当作格式模板或翻译键。更改术语时应区分 Original、Outfit No overlay、Armor Hidden，以及 Reload selection／Reload resources。

四张正式衣柜图标位于 `src/main/resources/assets/fashion_3790/textures/gui/icons/`。它们来自人工绘制的 16×16 PNG，Cape 页签和预览按钮披风状态共用同一资源。图标接入保持原始像素与透明边距；查看用放大图与正式资源分开，不以自动平滑、改色或重绘替代原件。

## 测试、打包与发行

### 测试范围

v1.0.0 正式公开源码独立构建记录为 **118 suites／1630 tests，零失败、错误、跳过**。这是发行点的历史验证结果，不是当前分支的实时 CI 状态，也不是每次克隆后自动成立的结论。

测试覆盖资源解析、选择与事务、保存、能力边界、客户端状态、渲染决定和 GUI 等产品逻辑。实际游戏的视觉、正常输入、多人同步和保存仍需单独验证，截图也不能替代事务和存档检查。

### 本地安装包

完成构建后，可使用公开打包工具：

```bash
python -B tools/package_release.py --help
python -B tools/package_release.py
```

打包入口为 [tools/package_release.py](tools/package_release.py)，v1.0.0 的 ZIP 输出到 `build/release/3790s-fashion-1.0.0-release.zip`。包内 README 来自 `INSTALL.md`，不是本页的开发者介绍。正式安装 ZIP 包含主 JAR、安装说明、许可证、五份用户文档、两个 Cape 模板和校验文件；不包含 Fabric API、sources、测试 harness、接口 JAR 或附属模组。

安装说明、包内文档、模板和换行都可能影响 ZIP 字节；源码文件字节及构建环境也需要纳入制品复现记录。是否复现正式文件应以完整哈希比较为准，不能只看版本或文件名。

正式下载以 [v1.0.0 Release](https://github.com/zbw3790/3790s-fashion/releases/tag/v1.0.0) 为准。根目录 README 的文档更新不要求重发 1.0.0；不要覆盖已发布文件或移动发行 Tag。

## 贡献与问题反馈

提交修改时，请说明目标版本、复现步骤、影响范围和实际验证结果。资源／协议／保存／兼容接口变更应先说明语义和迁移影响；已有不变字段、失效引用、连接生命周期与 Preview 隔离不能为了通过测试而削弱。

普通修复保持改动集中，并补相应回归。涉及 GUI 时分别说明自动检查与实际观察；涉及多人或保存时给出客户端／服务端版本和正常退出后的结果。不要提交运行世界、缓存、凭据、外部编译接口或不具备明确来源的素材。

反馈入口：[GitHub Issues](https://github.com/zbw3790/3790s-fashion/issues)。建议附上 Minecraft、Loader、Fabric API、主模组及相关附属版本，操作系统、模组列表、资源结构，以及最小复现日志或截图。

## 文档与许可

| 文档 | 内容 |
| --- | --- |
| [INSTALL.md](INSTALL.md) | 玩家安装与安装 ZIP 使用。 |
| [中文入门](docs/getting-started-zh_cn.md)／[English](docs/getting-started-en_us.md) | 首次使用、衣柜、资源与备份。 |
| [Cape 资源布局](docs/cape-cosmetic-asset-layout.md) | 披风／绑定鞘翅文件布局、校验与回退。 |
| [Armor 资源说明](docs/armor-resources.md) | 盔甲定义、PNG、槽位、效果与资源生命周期。 |
| [兼容性记录](docs/compatibility.md) | 版本组合和历史边界；阅读时区分历史版本与当前基线。 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变化。 |
| [branding/NOTICE.md](branding/NOTICE.md) | 品牌资产说明。 |

项目代码采用 [MIT License](LICENSE)。新增外观素材请保留作者、来源及相应许可说明，不因被放进资源目录就将外部素材视为项目原创。
