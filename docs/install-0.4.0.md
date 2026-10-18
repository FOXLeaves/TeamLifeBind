# 安装 0.4.0

0.4.0 继续面向 Minecraft **26.3**、**Java 25**。2026-10-12 重核官方版本后，NeoForge 更新至 **26.3.0.71-beta**，Paper 运行验证目标更新至已进入稳定通道的 **build 175**；其余兼容依赖沿用，详情见[上游复核](upgrade-research.md)。本次新增物品、动态补给和预告默认可用，`CLASSIC` 节奏与图腾默认档保留。四端构建、隔离启动、自动检查和发行包核验已通过；真实客户端与多人对局待验收，实际范围由 [0.4.0 验证记录](gameplay-validation-0.4.0.md)单独记录。

## Bukkit / Spigot / Paper 插件

1. 停止服务端，备份现有配置与需要保留的存档。将 `plugins/TeamLifeBind-0.4.0-server.jar` 放入 26.3 服务端 `plugins/`，移走旧 TeamLifeBind JAR，保留 `plugins/TeamLifeBind/`。
2. 用 Java 25 启动，确认 TeamLifeBind 0.4.0 启用。新增动态补给键缺省为 `true`，已有的 `timed-events.supply-drops.count: 0` 仍关闭补给。
3. 将资源 ZIP 换成本次重建的 `resourcepack/TeamLifeBind-resourcepack-0.4.0.zip`，托管新文件并更新 `resource-pack.url`、该 ZIP 的 SHA-1 与 `resource-pack.enabled`。旧版本 ZIP 或此前发布的 0.4.0 ZIP 未包含本次原创 32px 材质；插件玩家须重新下载。原稿不进入资源包，详见[材质规范](item-textures-0.4.0.md)。
4. 在大厅使用 `/tlb rules` 查看规则，合成共鸣哨与绷带；管理员通过 `/tlb dev` 做物品检查，通过 `/tlb menu` 的“动态补给”按钮切换数量模式。完整配方和交互见[玩法说明](gameplay-update-0.4.0.md)。至少两位玩家准备后才开始对局。

补给默认示例：

```yaml
timed-events:
  supply-drops:
    count: 5
    dynamic-count: true
```

动态模式中 `count` 的正值表示补给开启，实际数量按资格队伍数计算；设 `dynamic-count: false` 后采用固定 `count`。不要在已有配置里重复添加整个 `timed-events` 段，应把新键并入现有 `supply-drops`。文件修改应在大厅完成，再使用 `/tlb reload`；正在进行的对局应结束后再调整。

server 仍以 Spigot API 构建，供 Bukkit / Spigot / Paper 使用；本次隔离运行目标为 Paper 26.3 build **175**，官方通道为 **STABLE**。独立 Bukkit/Spigot 与真人客户端的实际兼容范围由验证记录说明。

## Fabric / Forge / NeoForge 模组

服务端与每位参与玩家的客户端安装相同版本、对应加载器的 JAR：

| 平台 | 运行依赖 | 发行包内文件 |
| --- | --- | --- |
| Fabric | Loader 0.19.5；另装 Fabric API 0.162.0+26.3 | `mods/fabric/TeamLifeBind-0.4.0-fabric.jar` |
| Forge | Forge 26.3-66.0.10 | `mods/forge/TeamLifeBind-0.4.0-forge.jar` |
| NeoForge | NeoForge 26.3.0.71-beta | `mods/neoforge/TeamLifeBind-0.4.0-neoforge.jar` |

停止实例后替换旧 TeamLifeBind JAR，保留 `config/teamlifebind/settings.properties`。模组包含共享规则和物品资源，无需另装 common JAR 或插件资源 ZIP。NeoForge 对应 26.3 的当前版本仍为 **beta**。

新增设置：

```properties
supply-drops-dynamic-count=true
```

默认允许合成与使用共鸣哨、应急绷带；无需新增开关来启用这两件物品。实例关闭后再编辑模组配置，现有 `match-pace=CLASSIC`、`totem-balance=CLASSIC` 继续生效。补给关闭设置和其他事件开关继续生效。

## 验收与历史

先检查 `/tlb rules`、新配方、模型与语言，再检查两人组队的哨声标记、绷带读条、受伤/攻击打断和空主手右键补给争夺。代理或自动检查不能替代两人全流程与四人对抗试玩。

[0.3.0 安装说明](install-0.3.0.md)、[0.3.0 验证记录](gameplay-validation-0.3.0.md)与[0.2.0 平台验证](upgrade-validation.md)保留为历史资料；0.4.0 的新玩法须单独验收。
