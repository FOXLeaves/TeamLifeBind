<img width="1024" height="500" alt="cm_teamlifebind" src="https://github.com/user-attachments/assets/13d8c04d-9582-46cb-9576-8a81c67ee8f2" />

# TeamLifeBind

TeamLifeBind 是一个面向 Minecraft 1.21.11 的团队生存对抗玩法，核心规则是“队友共享生死，一人死亡，全队连坐”。  
TeamLifeBind is a Minecraft 1.21.11 team survival PvP mode built around shared lives: when one teammate dies, the whole team is wiped.

最低两个人，推荐四个人及以上人数游玩，后续会添加更多的道具以及机制
A minimum of two people, recommended for four or more players. More items and mechanics will be added later.

这个仓库同时维护 `Paper`、`Fabric`、`Forge`、`NeoForge` 四个平台实现，并通过 `common` 模块共享核心规则、语言文本和部分通用逻辑。  
This repository ships synchronized implementations for `Paper`, `Fabric`, `Forge`, and `NeoForge`, with a `common` module for the core rules, language assets, and shared logic.

## 特性速览 / Feature Highlights

- 支持 `2-32` 队，开局会为每支队伍分配独立出生区。  
  Supports `2-32` teams, with a dedicated spawn area assigned to each team at match start.
- 生命绑定规则：任意一名队员死亡，会触发整队淘汰。  
  Shared-life rule: a single teammate death wipes the entire team.
- 大厅准备流：玩家在大厅待命，使用 `/tlb ready` 后进入自动开局倒计时。  
  Lobby-ready flow: players wait in the lobby, use `/tlb ready`, and then trigger an automatic start countdown.
- 进入比赛场地后会有 `10` 秒观察阶段，此阶段只能转视角、不能移动。  
  After entering the battle area, players get a `10` second observation phase where they can look around but cannot move.
- 默认启用禁复活维度机制，在配置的维度中死亡会直接变为旁观者淘汰。  
  No-respawn dimensions are enabled by default: dying there eliminates the player into spectator mode.
- 自带大厅菜单、计分板、Tab 信息、队伍通报等 UI，并已对齐四个平台表现。  
  Includes a built-in lobby menu, scoreboard, tab display, and team announcements, aligned across all four platforms.
- 胜利条件默认为“首支击杀末影龙的队伍获胜”。  
  The default win condition is “the first team to kill the Ender Dragon wins.”

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
- `paper`  
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
- Fabric / Forge / NeoForge 使用固定维度组来承载大厅和战斗。  
  Fabric / Forge / NeoForge use fixed dimensions for lobby and battle flow.
- 模组端当前使用的关键维度为：  
  The main mod-side dimensions currently used are:
  - `teamlifebind:lobby_overworld`
  - `teamlifebind:battle_overworld`
  - `teamlifebind:battle_nether`
  - `teamlifebind:battle_end`
- Paper 的主要配置入口是 [`paper/src/main/resources/config.yml`](/D:/2.mc%20dev/TeamLifeBind/paper/src/main/resources/config.yml)。  
  Paper is primarily configured through [`paper/src/main/resources/config.yml`](/D:/2.mc%20dev/TeamLifeBind/paper/src/main/resources/config.yml).
- 模组端运行后会把设置写入 `config/teamlifebind/settings.properties`。  
  Mod loaders persist settings into `config/teamlifebind/settings.properties`.

## 命令 / Commands

所有平台通用命令 / Commands available on all platforms:

- `/tlb help`
- `/tlb menu`
- `/tlb ready`
- `/tlb unready`
- `/tlb start`
- `/tlb stop`
- `/tlb status`
- `/tlb teams <2-32>`
- `/tlb health <ONE_HEART|HALF_ROW|ONE_ROW>`
- `/tlb norespawn`
- `/tlb norespawn on`
- `/tlb norespawn off`
- `/tlb norespawn add <namespace:path>`
- `/tlb norespawn remove <namespace:path>`
- `/tlb norespawn clear`

Paper 额外命令 / Paper-only extra commands:

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
- 开局准备倒计时：`ready-countdown-seconds`  
  Ready countdown seconds: `ready-countdown-seconds`
- 计分板 / Tab / 成就播报开关：  
  Scoreboard / tab / advancement toggles:
  - `scoreboard.enabled`
  - `tab.enabled`
  - `advancements.enabled`
- 禁复活维度列表：`no-respawn.blocked-dimensions`  
  No-respawn dimension list: `no-respawn.blocked-dimensions`

### Fabric / Forge / NeoForge

模组端会把常用设置保存到 `settings.properties`，目前主要包括：  
The mod loaders save their common settings to `settings.properties`, including:

- `team-count`
- `health-preset`
- `announce-team-assignment`
- `scoreboard-enabled`
- `tab-enabled`
- `advancements-enabled`

## 构建 / Build

推荐使用 `Java 21`。  
`Java 21` is recommended.

构建全部模块 / Build all modules:

```bash
./gradlew build
```

只构建某个平台 / Build a single platform:

```bash
./gradlew :paper:build
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

- `runPaperServer` 会自动准备 `run/paper-server`，下载对应版本的 Paper 服务端，并复制最新插件到 `plugins/`。  
  `runPaperServer` prepares `run/paper-server`, downloads the matching Paper server jar, and copies the latest plugin into `plugins/`.
- `run*Client` 现在会正常启动到客户端，而不会再自动连接本地服务器。  
  `run*Client` now starts the client normally and no longer auto-connects to a local server.
- 各平台开发运行目录分别位于 `run/paper-server`、`run/fabric-client`、`run/fabric-server`、`run/forge-client`、`run/forge-server`、`run/neoforge-client`、`run/neoforge-server`。  
  Development run directories live under `run/paper-server`, `run/fabric-client`, `run/fabric-server`, `run/forge-client`, `run/forge-server`, `run/neoforge-client`, and `run/neoforge-server`.

## 开发说明 / Development Notes

- 这个项目虽然有 `common` 公共层，但大量完整玩法逻辑仍然分别存在于 `paper`、`fabric`、`forge`、`neoforge` 四端。  
  Even though the project has a `common` layer, a large portion of the gameplay logic still lives separately in `paper`, `fabric`, `forge`, and `neoforge`.
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
