# TeamLifeBind 升级依据

首次核对日期：2026-10-11（Asia/Hong_Kong）；0.4.0 最后复核：2026-10-12 00:13。下文保留 0.2.0 / 0.3.0 的选型依据，末节记录 0.4.0 的上游变化与当时采用组合。随后按要求切换到 Java 26，见 [Java 26 迁移验证](java26-validation.md)；Minecraft 26.3 官方元数据中的 Java 25 要求作为历史与上游最低要求保留。实际构建和运行结果以各版本验证记录为准；版本号来自官方发布页或实时元数据。

## 0.2.0 / 0.3.0 采用的版本组合

Minecraft Java 最新正式版是 **26.3**，Mojang 清单记录其发布日期为 2026-09-15；`26.4-snapshot-3` 是快照，未作为本次目标。[官方版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)、[26.3 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)

| 组件 | 本次目标 | 官方依据与状态 |
| --- | --- | --- |
| Minecraft Java | `26.3` | Mojang 正式版；四个平台统一游戏版本 |
| Java / 模组目标字节码 | `25` | [26.3 版本 JSON](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json) 的 `javaVersion.majorVersion` 为 25 |
| Gradle Wrapper | `9.8.1` | [官方当前正式版 API](https://services.gradle.org/versions/current)；2026-10-07 构建，无 snapshot / RC 标记 |
| Fabric Loader | `0.19.5` | [Loader 官方元数据](https://meta.fabricmc.net/v2/versions/loader) 标记 `stable: true` |
| Fabric API | `0.162.0+26.3` | [官方 Maven 元数据](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml) 中针对 **26.3** 的最新发布；全局 `latest` 指向 26.4，不适用于正式版 26.3 |
| Fabric Loom | `1.18.4` | [官方 Maven 元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml) 中最新非 alpha / snapshot 发布；`1.19.0-alpha.2` 不采用 |
| Forge | `26.3-66.0.10` | [官方下载页](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.3.html) 的 Download Latest；[官方元数据](https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml) 一致 |
| ForgeGradle | `7.0.40` | [FG 7 官方元数据](https://maven.minecraftforge.net/net/minecraftforge/forgegradle/maven-metadata.xml) |
| Forge eventbus-validator | `7.0.6` | [官方元数据](https://maven.minecraftforge.net/net/minecraftforge/eventbus-validator/maven-metadata.xml)，同时是最新 Forge MDK 的声明版本 |
| NeoForge | `26.3.0.70-beta` | [官方元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)；**beta**，26.3 当前未提供无 beta 后缀版本 |
| ModDevGradle | `2.0.148` | [官方插件元数据](https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml) |
| Spigot API | `26.3-R0.1-SNAPSHOT` | [官方 API 元数据](https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml)；维持 Bukkit / Spigot / Paper 共用插件的编译接口 |
| Paper（运行验证） | `26.3` build `174` | [官方下载 API](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/174)，channel 为 **BETA** |
| Paper API（参考，非 server 必需依赖） | `26.3.build.174-beta` | [官方 Maven 元数据](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)；新格式不再是旧式 `26.3-R0.1-SNAPSHOT` |

NeoForge 原项目已经使用 beta 版本，因此本次继续采用对应最新正式游戏版本的官方 beta loader。若后续发行要求所有 loader 都处于稳定通道，应另建 **26.2** 兼容发行线，NeoForge 稳定版为 `26.2.0.90`；不可给 26.3 模组直接替换成 26.2 loader 后宣称兼容。[NeoForge 官方元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)

## 构建工具的兼容证据

- Gradle 从 `9.1.0` 开始支持运行于 Java 25，`9.8.1` 满足要求。推荐 Gradle JVM 也使用 Java 25，避免“toolchain 是 25，但运行 Gradle 的 JVM 是 21”的插件解析失败。[Gradle Java 兼容矩阵](https://docs.gradle.org/current/userguide/compatibility.html)
- Loom `1.18.4` 的发布 module 声明 `org.gradle.jvm.version: 25`、`org.gradle.plugin.api-version: 9.7.0`。Gradle 的官方属性规则允许较低插件 API 版本匹配较高运行版本，因此 9.8.1 满足其声明要求。[Loom 发布属性](https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.18.4/fabric-loom-1.18.4.module)、[Gradle 属性兼容规则](https://docs.gradle.org/current/userguide/variant_attributes.html)
- Forge `26.3-66.0.10` 的官方 MDK 使用 Gradle `9.7.1`，ForgeGradle 范围 `[7.0.17,8)`，Java 25、validator `7.0.6`。FG `7.0.40` 本身由 Gradle `9.7.1` 发布。9.8.1 是同一主版本的新补丁/功能版本组合，但仍需实际配置和编译验证，不把 producer 字段解释成完整兼容保证。[官方 MDK](https://maven.minecraftforge.net/net/minecraftforge/forge/26.3-66.0.10/forge-26.3-66.0.10-mdk.zip)、[ForgeGradle 发布属性](https://maven.minecraftforge.net/net/minecraftforge/forgegradle/7.0.40/forgegradle-7.0.40.module)
- ModDevGradle `2.0.148` 的发布 module 未声明 Gradle 9.8.1 上界，其 producer 为 8.14.5。跨主版本 API 使用是否兼容，应由本项目配置/构建检查确认，不能仅凭“最新”推断通过。[ModDevGradle 发布属性](https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/2.0.148/moddev-gradle-2.0.148.module)、[官方仓库文档](https://github.com/neoforged/ModDevGradle)

Gradle `9.8.1` 的 `-bin.zip` SHA-256：

```text
dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f
```

该值由[官方当前版本 API](https://services.gradle.org/versions/current)返回，可填入 wrapper 的 `distributionSha256Sum`。

ForgeGradle 7 的实际 artifact 为 `net.minecraftforge:forgegradle`，插件 marker 为 `net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin`。旧的 `net.minecraftforge.gradle:ForgeGradle` 元数据仍可能返回 `6.0.54`，它不是本项目 FG 7 的版本来源。[FG 7 artifact 元数据](https://maven.minecraftforge.net/net/minecraftforge/forgegradle/maven-metadata.xml)、[插件 marker 元数据](https://maven.minecraftforge.net/net/minecraftforge/gradle/net.minecraftforge.gradle.gradle.plugin/maven-metadata.xml)

## 游戏资源与平台约束

26.3 的数据包格式为 **121.0**，资源包格式为 **97.1**。新 `pack.mcmeta` 使用整数或 `[major, minor]` 数组；`min_format` 的单个整数等于 minor 0，`max_format` 的单个整数则包含该 major 的全部 minor。建议精确写数组，避免把 `97.1` 写成浮点数。[26.3 技术变更](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)、[Mojang 新 pack 元数据格式说明](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w31a)

- 只含客户端贴图、模型的服务端资源 ZIP：`min_format: [97, 1]`、`max_format: [97, 1]`。
- 只含服务器维度、配方的数据包：`min_format: [121, 0]`、`max_format: [121, 0]`。
- 同时含 `assets/` 和 `data/` 的模组 jar：必须检查 loader 的内建包处理。NeoForge 官方 `26.3.0.70-beta-universal.jar` 自身采用数据格式 121.0；本项目两类内容共存时，覆盖 `[97,1]` 至 `[121,0]` 是避免两种 pack 类型冲突的元数据范围。它表达资源类型的技术范围，游戏支持范围仍由 `fabric.mod.json` / `mods.toml` / `neoforge.mods.toml` 限定 26.3。[NeoForge 官方 universal jar](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.3.0.70-beta/neoforge-26.3.0.70-beta-universal.jar)

`plugin.yml` 的 `api-version` 可设为 `'26.3'`。该字段表示最低 API 版本；升级后不应继续声称新 jar 支持未验证的旧服务端。[Paper plugin.yml 文档](https://docs.papermc.io/paper/dev/plugin-yml/)

26.1 起 Minecraft 已去除代码混淆，因此 Fabric 使用 `net.fabricmc.fabric-loom`、普通 `implementation` / `compileOnly` 和 `jar`。26.3 又移除了 Fabric 的旧酿造构建 API，新增的数据组件和原版酿造配方将影响自定义药水；世界生成注册结构的变动需要特别检查本项目的 `RoundSeededNoiseChunkGenerator`。[Fabric 26.1 迁移说明](https://fabricmc.net/2026/03/14/261.html)、[Fabric 26.3 迁移说明](https://fabricmc.net/2026/09/15/263.html)

Paper 26.1+ 运行要求 Java 25。server 使用 Spigot API 编译可保持共用插件形式，但不能据此替代真实 Spigot / Paper 的行为测试；0.2.0 / 0.3.0 验证使用的 Paper 26.3 build 174 为 beta，0.4.0 改用的 build 175 已进入稳定通道。[Paper 运行要求](https://docs.papermc.io/paper/getting-started/)、[build 175 官方记录](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/175)

## 0.2.0 / 0.3.0 隔离运行验证用下载

Paper `26.3` build `174` 官方文件为 `paper-26.3-174.jar`，大小 `55,143,345` 字节，发布于 `2026-10-11T08:11:34Z`（香港时间 16:11:34）。[官方构建记录](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/174)

- [官方 jar 下载](https://fill-data.papermc.io/v1/objects/ab925801c3b7f8fbb98d6fa88a2e9c34687ffe2697199a1c2fa19a98922ed1f9/paper-26.3-174.jar)
- SHA-256：`ab925801c3b7f8fbb98d6fa88a2e9c34687ffe2697199a1c2fa19a98922ed1f9`

建议在新空白测试目录启动，检查插件启用、语言、资源包、开局/结束、维度切换和多轮清理。正常启动与编译通过都不能单独证明四端玩法完全一致。

## Toolchain resolver 与 CI 版本补查

`org.gradle.toolchains.foojay-resolver-convention` 最新稳定版仍为 **1.0.0**，当前 settings 声明无需改变。官方插件页说明该版使用 Java 17 编译，并移除了为 Gradle 9 兼容准备的旧 vendor 引用。[Gradle 插件发布页](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention)

截至核对日，官方 GitHub 最新非预发布 release 及实际 tag commit 如下。建议工作流使用完整 commit SHA，在行末保留版本注释，以保持可复现性。

| GitHub Action | 最新稳定 tag / 主版本 | 已核对的 commit SHA | 发布依据 |
| --- | --- | --- | --- |
| `actions/checkout` | `v7.0.1` / `v7` | `3d3c42e5aac5ba805825da76410c181273ba90b1` | [官方 release](https://github.com/actions/checkout/releases/tag/v7.0.1) |
| `actions/setup-java` | `v6.0.1` / `v6` | `de7274f081f381c8f8158605e0321c36c376e2e6` | [官方 release](https://github.com/actions/setup-java/releases/tag/v6.0.1) |
| `gradle/actions/setup-gradle` | `v6.4.0` / `v6` | `3f5f9adaf7d9fecd50b5935e54106014257a94e6` | [官方 release](https://github.com/gradle/actions/releases/tag/v6.4.0) |

三者均使用 Node 24 action runtime。自托管 runner 至少应支持 Node 24（`v2.327.1` 起）；如果在 Docker container action 内执行需认证的 Git 命令，checkout 的凭据处理还要求 runner `v2.329.0` 起。普通 GitHub 托管 runner 使用当前镜像即可。[checkout 对应版本 README](https://github.com/actions/checkout/blob/v7.0.1/README.md)、[setup-java 对应版本 README](https://github.com/actions/setup-java/blob/v6.0.1/README.md)、[setup-gradle 对应版本 action.yml](https://github.com/gradle/actions/blob/v6.4.0/setup-gradle/action.yml)

## 三端维度、配方与模型的迁移检查

这些是结合仓库现有 JSON 与官方 26.2 / 26.3 发布说明得到的检查项；修改 pack 格式声明不等于已经验证内容加载成功。

| 内容 | 官方变更与本项目判断 | 运行验收 |
| --- | --- | --- |
| `dimension_type/match_arena.json` | 26.2 的 `infiniburn` 新增接受 ID / ID 列表，原有 `#minecraft:infiniburn_overworld` 仍属于有效形式。现有 `default_clock`、`timelines` 形式与 26.3 官方主世界定义一致。[26.2 维度说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) | 注册表加载无 codec 错误；进入对应维度，昼夜、怪物光照与传送比例正确 |
| 床环境属性 | 26.3 的 `minecraft:gameplay/bed_rule` 将 `explodes` 改为 `destroy_on_use`，并增加草床专用规则。当前自定义维度未写旧 `explodes`，无该键的批量迁移；仍要验证新增草床不会绕过队伍床/个人复活限制。[26.3 环境属性说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) | 普通床、草床、队伍床分别在大厅、主世界、地狱、末地测试；不得改写被规则禁止的复活点 |
| `dimension/*.json` 噪声配置 | 26.3 重构了 noise settings 与世界生成注册表。项目维度使用原版 settings ID，而未内联旧版噪声定义，所以应保留这些引用；需检查自定义 `teamlifebind:round_seeded_noise` codec、`seed_offset`、每轮 seed 状态和生成器 Java 适配。[26.3 世界生成说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) | 首次创建三种战斗维度均成功；同 seed 可复现，换 seed 后新区域不同；末地有龙战与有效出生平台 |
| 三个已有物品配方 | 当前 `crafting_shaped` 与 `smithing_transform` 使用字符串 / `#tag` ingredient、`result.id`。与 Mojang 26.3 jar 内的 `white_bed.json`、`netherite_sword_smithing.json` 对比，核心形式仍一致，无依据将其改回旧式 ingredient 对象。[26.3 官方客户端下载依据](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json) | 三端启动与 `/reload` 无配方错误；工作台合成轮盘、队伍床以及锻造强化图腾均获得正确注册物品 |
| 生命诅咒药水 | 26.3 增加 `minecraft:brewing` 数据配方，旧 Fabric brewing builder 已移除。现有项目按自定义 Item 的消费事件实现效果，未使用该 builder，因此不需为了编译添加新酿造 API；数据酿造配方属于后续玩法扩展。[Fabric 26.3 迁移说明](https://fabricmc.net/2026/09/15/263.html) | 物品模型与药水颜色正确；消费后效果、时长、牛奶清除与共享模式一致 |

用于对比的 Mojang 原始 `minecraft-client.jar` 已核对 SHA-1 为 `e877b6a07acd633fb3bb475002175cec036e7b87`，与[官方 26.3 版本 JSON](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json) 一致。这里只确认相关 schema 的静态依据，最终加载与合成效果仍以运行测试为准。

## 0.4.0 上游版本再次核验

2026-10-12 00:13（Asia/Hong_Kong）重新读取官方 JSON、Maven 元数据和发布页。Minecraft 最新正式版仍为 **26.3**；本次将 NeoForge 从 `26.3.0.70-beta` 更新至 **`26.3.0.71-beta`**，Paper 运行验证目标从 beta build 174 更新至已进入 **STABLE** 通道的 **build 175**。其余兼容依赖与 0.3.0 固定组合一致，项目版本号升至 0.4.0。这里记录官方版本与选型；下载校验、构建和实际运行由 [0.4.0 验证记录](gameplay-validation-0.4.0.md)单独说明。

| 范围 | 再次读取的结果 | 官方依据 |
| --- | --- | --- |
| Minecraft / Java | `latest.release=26.3`，`latest.snapshot=26.4-snapshot-3`；26.3 的 `javaVersion.majorVersion=25` | [Mojang 清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)、[26.3 JSON](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json) |
| Fabric | Loader `0.19.5` 标记稳定；针对 26.3 的 API 最新 `0.162.0+26.3`；Loom 最新非预发布 `1.18.4` | [Loader](https://meta.fabricmc.net/v2/versions/loader)、[API Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml)、[Loom Maven](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml) |
| Forge | 26.3 最新 `66.0.10`；ForgeGradle `7.0.40`；eventbus-validator `7.0.6` | [下载页](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.3.html)、[ForgeGradle](https://maven.minecraftforge.net/net/minecraftforge/forgegradle/maven-metadata.xml)、[validator](https://maven.minecraftforge.net/net/minecraftforge/eventbus-validator/maven-metadata.xml) |
| NeoForge | 26.3 最新 `26.3.0.71-beta`，仍未列出无 beta 后缀的 26.3 版本；ModDevGradle `2.0.148` | [NeoForge](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)、[ModDevGradle](https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml) |
| 插件 API / Paper | Spigot `26.3-R0.1-SNAPSHOT`；Paper 26.3 最新 build `175`，通道已为 `STABLE` | [Spigot](https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml)、[build 175 官方记录](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/175) |
| Gradle / toolchain resolver | Gradle 当前正式版 `9.8.1`，`snapshot=false`，bin SHA-256 未变；Foojay resolver 最新 `1.0.0` | [Gradle 当前版本](https://services.gradle.org/versions/current)、[Foojay 发布页](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention) |
| GitHub CI actions | 官方 latest release 仍为 checkout `v7.0.1`、setup-java `v6.0.1`、gradle/actions `v6.4.0`；当前工作流已固定这些版本对应 SHA | [checkout](https://github.com/actions/checkout/releases/tag/v7.0.1)、[setup-java](https://github.com/actions/setup-java/releases/tag/v6.0.1)、[gradle/actions](https://github.com/gradle/actions/releases/tag/v6.4.0) |

筛选适用版本时，Fabric API 全局 `latest=0.162.2+26.4` 与 Loom `latest=1.19.0-alpha.2` 均未纳入 26.3 组合。Minecraft 26.4 当前只有快照，四端继续面向正式版 26.3。Paper 已进入稳定通道，但 NeoForge 仍为 beta，不能将整个组合标为“所有组件稳定”。目标游戏版本不变，因此资源包与数据包格式仍分别为 97.1 / 121.0；新配方、模型和交互须独立验收。

Paper build 175 发布于 `2026-10-11T15:32:14Z`，即香港时间 **2026-10-11 23:32:14**。官方文件 `paper-26.3-175.jar` 大小为 **55,142,601 字节**，官方 SHA-256 为下值：[build 175 官方记录](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/175)

```text
ece5afb6d0cdf0c930489618bf92341e33d2ebcd4f21e6d365daff6ed26dafb0
```

[官方 jar 下载](https://fill-data.papermc.io/v1/objects/ece5afb6d0cdf0c930489618bf92341e33d2ebcd4f21e6d365daff6ed26dafb0/paper-26.3-175.jar)。0.4.0 隔离测试下载的本地文件已核对长度与 SHA-256，均与该官方记录一致；实际启服与玩法测试另见 0.4.0 验证记录。0.2.0 / 0.3.0 的 build 174 历史结果继续保留。
