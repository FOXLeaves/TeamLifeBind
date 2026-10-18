# 安装 0.2.0

此发行包面向 Minecraft **26.3**，游戏与服务端使用 **Java 25**。各平台版本与发布通道见 [升级依据](upgrade-research.md)，已经执行的测试见 [验证记录](upgrade-validation.md)。

## Bukkit / Spigot / Paper 插件

1. 将 `plugins/TeamLifeBind-0.2.0-server.jar` 放入 26.3 服务端的 `plugins/`，替换旧 TeamLifeBind JAR，保留现有配置目录。
2. 启动服务端，确认 TeamLifeBind 0.2.0 启用。首次安装生成 `plugins/TeamLifeBind/config.yml`，可设置队伍数、共享项目、血量预设与禁复活维度。
3. 自定义外观使用 `resourcepack/TeamLifeBind-resourcepack-0.2.0.zip`。将 ZIP 托管到玩家可下载的地址，在配置中填入 `resource-pack.url` 与 ZIP 的 SHA-1，再启用 `resource-pack.enabled`。
4. 使用 `/tlb help` 和 `/tlb status` 检查状态。至少两名玩家进入大厅并准备后可开局。

本次插件运行检查使用 Paper 26.3 build 174（BETA）；Spigot API 编译通过，未单独完成 Bukkit/Spigot 运行测试。

## Fabric / Forge / NeoForge 模组

选择与你的加载器对应的一套 JAR；服务端与参与玩家客户端都安装该套模组：

| 加载器 | 运行依赖 | 发行包内文件 |
| --- | --- | --- |
| Fabric | Loader 0.19.5，另装 Fabric API 0.162.0+26.3 | `mods/fabric/TeamLifeBind-0.2.0-fabric.jar` |
| Forge | Forge 26.3-66.0.10 | `mods/forge/TeamLifeBind-0.2.0-forge.jar` |
| NeoForge | NeoForge 26.3.0.70-beta | `mods/neoforge/TeamLifeBind-0.2.0-neoforge.jar` |

将对应文件放入实例的 `mods/`，替换旧 TeamLifeBind JAR。模组 JAR 已包含本项目的 `common` 和物品资源，无需额外安装公共模块或服务端资源 ZIP。启动后配置位于 `config/teamlifebind/settings.properties`。

进入后使用 `/tlb help` 查看命令；`/tlb menu` 打开菜单，`/tlb ready` 准备。管理员可通过 `/tlb dev` 领取现有调试物品，验收配方、模型和物品交互。

新物品与新赛制的名称和起始数值见 [玩法更新路线](gameplay-roadmap.md)，它们是后续建议，不在 0.2.0 发行包内。
