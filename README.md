<img width="1024" height="500" alt="cm_teamlifebind" src="https://github.com/user-attachments/assets/13d8c04d-9582-46cb-9576-8a81c67ee8f2" />

# TeamLifeBind

TeamLifeBind 是一个面向 Minecraft 的团队生存对抗玩法，核心规则是“队友共享生死，一人死亡，全队连坐”。  
TeamLifeBind is a Minecraft team survival PvP mode built around shared lives: when one teammate dies, the whole team is wiped.

最低两个人，推荐四个人及以上人数游玩，后续会添加更多的道具以及机制  
A minimum of two people, recommended for four or more players. More items and mechanics will be added later.

这个仓库同时维护 `server (Bukkit/Spigot/Paper)`、`Fabric`、`Forge`、`NeoForge` 四个平台实现，并通过 `common` 模块共享核心规则、语言文本和部分通用逻辑。  
This repository ships synchronized implementations for `server (Bukkit/Spigot/Paper)`, `Fabric`, `Forge`, and `NeoForge`, with a `common` module for the core rules, language assets, and shared logic.

## 特性速览 / Feature Highlights

- 支持 `2-32` 队，开局会为每支队伍分配独立出生区。  
  Supports `2-32` teams, with a dedicated spawn area assigned to each team at match start.
- 生命绑定规则：任意一名队员死亡，会触发整队淘汰。  
  Shared-life rule: a single teammate death wipes the entire team.
- 大厅准备流、内置大厅菜单、计分板、Tab 信息、队伍通报等基础 UI 已同步到四个平台。  
  The lobby-ready flow, built-in lobby menu, scoreboard, tab display, and team announcements are kept aligned across all four platforms.
- 进入比赛场地后会有 `10` 秒观察阶段，此阶段只能转视角、不能移动。  
  After entering the battle area, players get a `10` second observation phase where they can look around but cannot move.
- 默认启用禁复活维度机制，并支持血量同步、队伍床、以及“首支击杀末影龙获胜”的终局目标。  
  No-respawn dimensions, health sync, team beds, and the “first team to kill the Ender Dragon wins” goal are enabled as core match systems.
- 三个模组端都已同步新的自定义道具：追踪轮盘、死亡豁免图腾、生命诅咒药水，以及开发调试物品箱。  
  The three mod loaders now share the new custom items: Tracking Wheel, Death Exemption Totem, Life Curse Potion, and the developer test crate.

## 对局流程 / Match Flow

1. 玩家进入大厅并使用大厅菜单或 `/tlb ready` 准备。  
   Players enter the lobby and ready up through the lobby menu or `/tlb ready`.
2. 所有人准备完成后，系统开始开局倒计时。  
   Once everyone is ready, the start countdown begins.
3. 倒计时结束后，所有玩家被传送到比赛区域。  
   When the countdown finishes, all players are teleported into the battle area.
4. 进场后先进入 `10` 秒观察阶段，方便确认地形和周边环境。  
   Players then enter a `10` second observation phase to inspect terrain and surroundings.
5. 观察阶段结束后，正式开放移动与战斗。  
   When the observation phase ends, movement and combat are fully enabled.
6. 推进资源与战斗，直到只剩最后赢家，或有队伍击杀末影龙。  
   Teams gather resources and fight until one winner remains or a team kills the Ender Dragon.

## 平台与目录 / Platforms and Layout

- `common`  
  公共规则层，包含 `TeamLifeBindEngine`、血量预设、语言文本等共享逻辑。  
  Shared rules layer containing `TeamLifeBindEngine`, health presets, language assets, and other common logic.
- `server`
  Paper 插件实现，使用插件配置和每局独立比赛世界。  
  Paper plugin implementation using plugin config and a fresh match world per round.
- `fabric`  
  Fabric 模组实现，使用专用大厅/战斗维度和模组配置文件。  
  Fabric mod implementation using dedicated lobby/battle dimensions and a mod config file.
- `forge`  
  Forge 模组实现，结构与 Fabric/NeoForge 基本同步。  
  Forge mod implementation, kept largely in sync with Fabric and NeoForge.
- `neoforge`  
  NeoForge 模组实现，和 Forge/Fabric 一起维护模组端行为。  
  NeoForge mod implementation maintained alongside the other mod loaders.

## 平台差异 / Platform Differences

- Paper 每局会创建新的比赛世界，并在对局结束后清理旧世界。  
  Paper creates a fresh match world for each round and cleans it up after the game ends.
- Paper 目前还额外提供服务端资源包分发、每玩家语言覆盖、`/tlb zd` 组队模式、以及特殊对局投票流程。  
  Paper currently also provides server-side resource-pack delivery, per-player language overrides, `/tlb zd` team-mode grouping, and special-match voting flow.
- Fabric / Forge / NeoForge 使用固定维度组来承载大厅和战斗。  
  Fabric / Forge / NeoForge use fixed dimensions for lobby and battle flow.
- 模组端当前使用的关键维度为：  
  The main mod-side dimensions currently used are:
  - `teamlifebind:lobby_overworld`
  - `teamlifebind:battle_overworld`
  - `teamlifebind:battle_nether`
  - `teamlifebind:battle_end`
- server 端的主要配置入口是 [`server/src/main/resources/config.yml`](/D:/2.mc%20dev/TeamLifeBind/server/src/main/resources/config.yml)。
The server module is primarily configured through [`server/src/main/resources/config.yml`](/D:/2.mc%20dev/TeamLifeBind/server/src/main/resources/config.yml).
- 模组端运行后会把设置写入 `config/teamlifebind/settings.properties`。  
  Mod loaders persist settings into `config/teamlifebind/settings.properties`.

## 命令 / Commands

所有平台通用命令 / Commands available on all platforms:

- `/tlb help`
- `/tlb menu`
- `/tlb dev`
- `/tlb ready`
- `/tlb unready`
- `/tlb start`
- `/tlb stop`
- `/tlb status`
- `/tlb teams <2-32>`
- `/tlb health <ONE_HEART|HALF_ROW|ONE_ROW>`
- `/tlb healthsync`
- `/tlb healthsync on`
- `/tlb healthsync off`
- `/tlb norespawn`
- `/tlb norespawn on`
- `/tlb norespawn off`
- `/tlb norespawn add <namespace:path>`
- `/tlb norespawn remove <namespace:path>`
- `/tlb norespawn clear`

Paper 额外命令 / Paper-only extra commands:

- `/tlb language`
- `/tlb lang`
- `/tlb zd`
- `/tlb setspawn <teamId>`
- `/tlb clearspawns`
- `/tlb reload`

## 配置 / Configuration

### Paper

- 队伍数量：`team-count`  
  Team count: `team-count`
- 血量预设：`health-preset`  
  Health preset: `health-preset`
- 随机出生范围：`random-spawn-radius`  
  Random spawn radius: `random-spawn-radius`
- 队伍最小间距：`min-team-distance`  
  Minimum team distance: `min-team-distance`
- 队内出生点扩散半径：`team-spread-radius`  
  Per-team spawn spread radius: `team-spread-radius`
- 开局准备倒计时：`ready-countdown-seconds`  
  Ready countdown seconds: `ready-countdown-seconds`
- 计分板 / Tab / 成就播报开关：  
  Scoreboard / tab / advancement toggles:
  - `scoreboard.enabled`
  - `tab.enabled`
  - `advancements.enabled`
- 服务端资源包配置：  
  Server resource-pack settings:
  - `resource-pack.enabled`
  - `resource-pack.url`
  - `resource-pack.sha1`
- 血量同步开关：`health-sync.enabled`  
  Health-sync toggle: `health-sync.enabled`
- 定时事件配置：  
  Timed-event settings:
  - `timed-events.auto-nether-portals.enabled`
  - `timed-events.anonymous-stronghold-hints.enabled`
  - `timed-events.supply-drops.count`
- 末地图腾限制：`end-totem-restriction.enabled`  
  End-totem restriction: `end-totem-restriction.enabled`
- 禁复活维度列表：`no-respawn.blocked-dimensions`  
  No-respawn dimension list: `no-respawn.blocked-dimensions`

### Fabric / Forge / NeoForge

模组端会把常用设置保存到 `settings.properties`，目前主要包括：  
The mod loaders save their common settings to `settings.properties`, including:

- `team-count`
- `health-preset`
- `announce-team-assignment`
- `health-sync-enabled`
- `no-respawn-enabled`
- `scoreboard-enabled`
- `tab-enabled`
- `advancements-enabled`
- `end-totem-restriction-enabled`
- `battle-seed`

## 构建 / Build

当前 `26.1` 平台组合建议直接使用 `Java 25`。`common` 与 `server` 仍以 Java 21 目标字节码构建，但 Fabric / Forge / NeoForge 的构建与本地运行任务需要 Java 25 toolchain。  
For the current `26.1` platform set, `Java 25` is the recommended default. `common` and `server` still target Java 21 bytecode, but the Fabric / Forge / NeoForge builds and local run tasks require a Java 25 toolchain.

构建全部模块 / Build all modules:

```bash
./gradlew build
```

只构建某个平台 / Build a single platform:

```bash
./gradlew :server:build
./gradlew :fabric:build
./gradlew :forge:build
./gradlew :neoforge:build
```

## 本地运行 / Local Development Runs

根项目已经提供了便捷任务。  
The root project already provides convenience tasks.

启动本地 Paper 测试服 / Start the local Paper test server:

```bash
./gradlew runPaperServer
```

启动各平台客户端 / Start platform clients:

```bash
./gradlew runFabricClient
./gradlew runForgeClient
./gradlew runNeoForgeClient
```

启动各平台服务端 / Start platform servers:

```bash
./gradlew runFabricServer
./gradlew runForgeServer
./gradlew runNeoForgeServer
```

补充说明 / Notes:

- `runServer` 会自动准备 `run/server`，并复制最新的通用服务端插件到 `plugins/`；`runPaperServer` 只是兼容别名。
`runServer` prepares `run/server` and copies the latest shared server plugin into `plugins/`; `runPaperServer` remains only as a compatibility alias.
- `run*Client` 现在会正常启动到客户端，而不会再自动连接本地服务器。  
  `run*Client` now starts the client normally and no longer auto-connects to a local server.
- 各平台开发运行目录分别位于 `run/server`、`run/fabric-client`、`run/fabric-server`、`run/forge-client`、`run/forge-server`、`run/neoforge-client`、`run/neoforge-server`。
Development run directories live under `run/server`, `run/fabric-client`, `run/fabric-server`, `run/forge-client`, `run/forge-server`, `run/neoforge-client`, and `run/neoforge-server`.

## 开发说明 / Development Notes

- 这个项目虽然有 `common` 公共层，但大量完整玩法逻辑仍然分别存在于 `server`、`fabric`、`forge`、`neoforge` 四端。
Even though the project has a `common` layer, a large portion of the gameplay logic still lives separately in `server`, `fabric`, `forge`, and `neoforge`.
- 如果要改规则枚举、文本、血量预设这类基础内容，通常先看 `common`。  
  For core enums, text assets, and health preset changes, `common` is usually the first place to update.
- 如果要改 UI、计分板、Tab、菜单、开局流程、重生、床逻辑、维度流转，通常需要同步四个平台。  
  For UI, scoreboard, tab, menu flow, match start flow, respawn, bed logic, or dimension flow, expect to sync changes across all four platforms.
- 当前仓库没有完整的自动化玩法测试，平台同步主要依赖人工比对和编译验证。  
  The repository does not yet have full gameplay automation tests, so platform sync still relies mostly on manual comparison and compile verification.
- 部分代码由AI辅助完成。  
  Some of the code was assisted by AI

## 许可证 / License

本项目使用 [MIT License](/D:/2.mc%20dev/TeamLifeBind/LICENSE)。  
This project is licensed under the [MIT License](/D:/2.mc%20dev/TeamLifeBind/LICENSE).
