# 安装 0.3.0

0.3.0 面向 Minecraft **26.3**，构建与游戏运行使用 **Java 25**。平台依赖沿用 0.2.0；本次新增规则卡、可选快速节奏和强化图腾平衡。两个新配置默认均为 `CLASSIC`，经典档保留原设计的时间与救援目标，并修复强化图腾吸收容量和致死伤害判定。四端构建、隔离启动与自动检查已通过；真人多人验收范围和检查结果记录在 [0.3.0 验证记录](gameplay-validation-0.3.0.md)。

## Bukkit / Spigot / Paper 插件

1. 停止服务端，将发行包内的 `plugins/TeamLifeBind-0.3.0-server.jar` 放入 26.3 服务端的 `plugins/`，移走旧 TeamLifeBind JAR，保留 `plugins/TeamLifeBind/` 配置目录。
2. 启动服务端，确认 TeamLifeBind 0.3.0 启用。首次安装会生成 `plugins/TeamLifeBind/config.yml`；升级安装保留已有队伍、血量、资源包及禁复活维度设置。
3. 如需新玩法配置，在现有配置中添加下列内容；缺省采用经典档。编辑应在大厅进行，完成后使用 `/tlb reload` 加载配置。

```yaml
gameplay:
  match-pace: CLASSIC
  totem-balance: CLASSIC
```

4. 自定义物品外观使用 `resourcepack/TeamLifeBind-resourcepack-0.3.0.zip`。托管到玩家可下载的地址，更新 `resource-pack.url` 和该 ZIP 的 SHA-1，再启用 `resource-pack.enabled`。
5. 使用 `/tlb menu` 查看规则卡，或 `/tlb rules` 阅读规则全文。管理员可在大厅选择 `FAST` 或 `BALANCED`；至少两位玩家进入大厅、准备后开局。本局采用开局时的设置快照。

插件以 Spigot API 构建，沿用 Bukkit / Spigot / Paper 共用形式。0.3.0 在 Paper 26.3 build 174（BETA）完成隔离启动及 168 条集成代理检查；未在独立 Bukkit/Spigot 实例和真人客户端上验收。旧版 [0.2.0 验证记录](upgrade-validation.md) 仅说明此前平台升级结果。

## Fabric / Forge / NeoForge 模组

服务端和参与玩家客户端都安装对应加载器的模组 JAR：

| 加载器 | 运行依赖 | 发行包内文件 |
| --- | --- | --- |
| Fabric | Loader 0.19.5，另装 Fabric API 0.162.0+26.3 | `mods/fabric/TeamLifeBind-0.3.0-fabric.jar` |
| Forge | Forge 26.3-66.0.10 | `mods/forge/TeamLifeBind-0.3.0-forge.jar` |
| NeoForge | NeoForge 26.3.0.70-beta | `mods/neoforge/TeamLifeBind-0.3.0-neoforge.jar` |

停止实例后替换 `mods/` 中旧 TeamLifeBind JAR，保留 `config/teamlifebind/settings.properties`。模组已包含共享模块和物品资源，无需另装 `common` 或插件资源 ZIP。

两个新设置为：

```properties
match-pace=CLASSIC
totem-balance=CLASSIC
```

需要文件配置时，在实例关闭后编辑；启动后可通过管理员大厅菜单切换并保存。普通玩家可以打开 `/tlb menu` 或使用 `/tlb rules` 阅读规则，但无法修改管理员预设。

## 启用快速或平衡档

- 只缩短资源推进阶段：选择 `FAST` + `CLASSIC`。
- 只调整强化死亡豁免图腾：选择 `CLASSIC` + `BALANCED`。
- 同时启用两项：选择 `FAST` + `BALANCED`。

`FAST` 将自动地狱门、补给、首个要塞提示改为第 8 / 10 / 15 分钟；`BALANCED` 将强化图腾再生改为 20 秒、总吸收限制为当前最大 HP 的 50% 且最多 8 HP，并加入 45 秒团队强化救援冷却。普通图腾保持既有行为。

详细数值、权限和快照规则见 [0.3.0 玩法配置说明](gameplay-update-0.3.0.md)。共鸣哨、静默符、应急绷带、侦察火箭以及限命/据点/收圈赛制仍是 [后续建议](gameplay-roadmap.md)，不属于此次新增内容。
