<img width="1024" height="500" alt="cm_teamlifebind" src="https://github.com/user-attachments/assets/13d8c04d-9582-46cb-9576-8a81c67ee8f2" />

# TeamLifeBind

TeamLifeBind 是一个面向 Minecraft 的团队生存对抗玩法，核心规则是“队友共享生死，一人死亡，全队连坐”。  
TeamLifeBind is a Minecraft team survival PvP mode built around shared lives: when one teammate dies, the whole team is wiped.

最低两个人，推荐四个人及以上人数游玩，后续会添加更多的道具以及机制  
A minimum of two people, recommended for four or more players. More items and mechanics will be added later.

这个仓库同时维护 `server (Bukkit/Spigot/Paper)`、`Fabric`、`Forge`、`NeoForge` 四个平台实现，并通过 `common` 模块共享核心规则、语言文本和部分通用逻辑。  
This repository ships synchronized implementations for `server (Bukkit/Spigot/Paper)`, `Fabric`, `Forge`, and `NeoForge`, with a `common` module for the core rules, language assets, and shared logic.

当前游戏内与四端元数据的显示版本固定为 **0.1.2**；文件名与文档中的 **0.7.0** 是本批更新的内部构建编号。
The displayed plugin/mod version is **0.1.2**. **0.7.0** identifies this content build in filenames and documentation.

## 特性速览 / Feature Highlights

- 新增 **280 种普通魔法 + 20 种超位魔法**：世界自然箱与空投获取法术书，潜行右键学习，每人最多三种。300 条显式规则分别定义首次/重复命中、目标状态、落点、延迟与结束动作，复用 24 种动作及 20 种基础机制；超位具备有序阶段窗口与开收动作。吟唱/飞行/命中/结束使用不同几何与运动配置，包含魔力、冷却、五件伴生物品和随机魔典开局模式。模组使用 **V 施法 / G 轮盘 / Alt + 滚轮**，支持改键；Paper 使用总魔典右键施法、F 切槽。八阶书与伴生物品使用原创透明材质。见[魔法玩法](docs/magic-system.md)、[300 本目录](docs/magic-catalogue.md)和[验证范围](docs/magic-validation.md)。
  **280 ordinary spells and 20 super-tier rituals** add learnable chest/air-drop books, three learned slots, mana, five companion items and a random starter-book mode. Each of the 300 explicit profiles has distinct trigger/condition/target/action combinations built from 24 actions and 20 base mechanics, plus cast/travel/impact/finish geometry. Super-tier stages have ordered time windows and opening/closing actions. Native controls are rebindable **V / G / Alt + scroll**; Paper uses the grimoire with right-click to cast and F to cycle. See the [magic guide](docs/magic-system.md) and [complete catalogue](docs/magic-catalogue.md).
- 空投军械覆盖七类参考枪型：Taurus500、Vector45、SCAR-H、SPAS-12、M95、Minigun 和 RPG7。每个成功开启的补给箱仅生成一把随机枪械，没有合成方式，所有武器无限备弹。普通枪保留弹匣和装填时间，模组 Minigun 使用无限供弹与热量限制。Paper 保留断誓手炮等原创名称和既有弹匣数值。见[枪械玩法](docs/airdrop-weapons.md)。
  Seven independently implemented air-drop guns are inspired by Taurus500, Vector45, SCAR-H, SPAS-12, M95, Minigun and RPG7. Each claimed crate contains one random gun, with infinite reserves and no crafting recipe. Ordinary guns retain magazines and reload times; native Minigun uses continuous feed and heat limits. Paper retains its original names and magazine tuning.
- 模组端魔法改为 **GLSL 着色器视觉，无 Minecraft 魔法粒子**：世界空间法阵、光束、格盾、旋涡和超位星域，使用深度遮挡、独立 HDR 发光与辉光合成；无需安装额外光影或参考模组。Paper 保留粒子表现。见[实现与 GPU 预览](docs/magic-shader-rendering.md)。
  Native magic uses **GLSL world-space effects without Minecraft magic particles**, with depth occlusion, separate HDR radiance and bloom. No extra shader pack or reference mod is required; Paper retains particles. See the [renderer and GPU previews](docs/magic-shader-rendering.md).
- 2026-10-18 修复三端魔法不可见，补入 14 学派主体几何、领域脉冲和多目标附加效果修复；各端生产渲染器已检查 300/300 法术像素输出。显示编号仍为 0.1.2，详情见[本轮修复](docs/magic-fixes-2026-10-18.md)。

- 四端 `/tlb dev` 主目录列出 19 类基础测试物品：八件基础道具、七把枪械和四件辅助工具。魔法入口或 `/tlb dev magic` 先选 16 分类，再选具体物品和详情；可只领取一件，背包满时拒绝且不掉落。法术还可装入临时测试槽 1–3，给予 300 魔力并清临时冷却，不用囤书；结束、退出、撤权或开局恢复原知识、魔力与随时间衰减的冷却。正常对局枪械只通过空投获得，所有开发领取持续检查权限。见[开发者物品目录](docs/dev-item-menu.md)。
  The admin-only `/tlb dev` catalog lists 19 base test items. Its magic entry or `/tlb dev magic` opens 16 categories, then an item detail view. Claim one item, or load a spell into one of three temporary test slots with 300 mana and cleared temporary cooldowns. Full inventories reject magic claims without dropping items. Ending a test, disconnecting, losing permission or starting a round restores original knowledge, mana and elapsed cooldowns. Normal matches still obtain guns only from air drops. See the [developer item catalog](docs/dev-item-menu.md).
- 模组默认 **左键开火、右键 ADS、R 换弹**；Paper 使用 **右键开火、潜行提高精度、F 换弹**。开火、瞄准与换弹均可改键。模组增加平滑 ADS、镜头和持枪后坐力、换弹与拉栓姿态、SPAS-12 可打断逐颗装填、Minigun 16 相位转管及惯性停转；射击与弹药由服务器确认。火箭受墙体阻挡，不破坏方块。
  Native mods use **left-click to fire, right-click to aim and R to reload**; Paper uses **right-click to fire, sneaking for accuracy and F to reload**. Bindings are configurable. Native presentation adds smooth ADS, recoil, reload and bolt poses, interruptible SPAS-12 shell loading and a 16-phase Minigun rotor. The server authorizes shots and ammunition; rockets respect walls and never destroy blocks.
- 模组在没有活动比赛时允许存活、非观察者玩家持有效枪械在大厅测试，无需分队或开局；进入投票或倒计时后暂停枪械使用，正式比赛仅存活参赛者可用。Paper 保留比赛内使用限制。管理员 `/tlb dev` 发放权限继续单独检查，正常对局仍只通过空投获得枪械。
  Without an active match, native mods allow living non-spectator players to test initialized guns in the lobby without joining a team. Gun use pauses during voting and countdowns; running matches require eligible living participants. Paper retains its match-only restriction. Admin claims remain permission-checked, and normal matches still obtain guns only from air drops.
- 七把原创基础模型共 479 个几何方块，使用独立生成的 `256×256` 战术材质，并配有 13 个独立合成音效。Paper 玩家须安装本次资源包以显示外观及播放原创声音；动态持枪与转管表现由模组客户端提供。见[原创模型与预览](docs/gun-models.md)、[音效记录](docs/gun-sounds.md)与[验证范围](docs/gun-validation.md)。真人客户端的键位、持枪和声音混音仍待验收。
  Seven original base models contain 479 cuboids and share a generated `256×256` tactical atlas, with 13 independently synthesized sounds. Paper clients need the updated resource pack for models and sounds; animated gun presentation is provided by native mods. See [models](docs/gun-models.md), [sounds](docs/gun-sounds.md) and [validation](docs/gun-validation.md). Real-client controls, handling and audio mix checks remain pending.
- 构建 `0.7.0` 已整合床/药水状态、30 秒战斗断线宽限、原创静默符与侦察火箭，以及可选限命、60 秒屠龙贡献、45 秒据点、阶段任务和终局收圈。默认保留经典屠龙、最后一击，据点和收圈默认关闭。见[玩法说明](docs/gameplay-update-0.7.0.md)；当前 Java 26 检查见[迁移验证](docs/java26-validation.md)，真人多人对局待验收。
  Content build `0.7.0` combines clearer HUDs, reconnect handling, two original items and optional limited lives, contribution scoring, control points and shrinking borders. Builds, isolated boots and automated checks pass; real-client multiplayer playtesting remains pending. Classic defaults remain. See [the gameplay guide](docs/gameplay-update-0.7.0.md).
- 支持 `2-32` 队，开局会为每支队伍分配独立出生区。  
  Supports `2-32` teams, with a dedicated spawn area assigned to each team at match start.
- 经典赛制中，任意一名队员死亡会触发全队连锁死亡；普通维度可重生，禁复活维度进入旁观。可选限命赛另按团队重生额度决定淘汰。
  In classic matches, one teammate death triggers a team wipe; respawning remains possible outside no-respawn dimensions. Limited-lives matches also apply the team's respawn credits.
- 大厅准备流、内置大厅菜单、计分板、Tab 信息、队伍通报等基础 UI 已同步到四个平台。  
  The lobby-ready flow, built-in lobby menu, scoreboard, tab display, and team announcements are kept aligned across all four platforms.
- 进入比赛场地后会有 `10` 秒观察阶段，此阶段只能转视角、不能移动。  
  After entering the battle area, players get a `10` second observation phase where they can look around but cannot move.
- 默认启用禁复活维度机制，并支持血量同步、队伍床、以及“首支击杀末影龙获胜”的终局目标。  
  No-respawn dimensions, health sync, team beds, and the “first team to kill the Ender Dragon wins” goal are enabled as core match systems.
- 自定义道具同步四端：追踪轮盘、死亡豁免图腾、生命诅咒药水、共鸣哨、应急绷带、静默符、侦察火箭，并提供开发调试物品箱。插件端通过资源包显示物品外观。
  All platforms share the Tracking Wheel, Death Exemption Totem, Life Curse Potion, Resonance Whistle, Emergency Bandage, Silent Sigil, Scout Rocket and developer test crate. Plugin clients use the resource pack for custom models.
- `0.3.0` 已实现四端大厅规则卡、可选快速节奏和强化图腾平衡档；默认仍为 `CLASSIC` / `CLASSIC`，经典档保留原设计的时间与救援目标，并修复强化图腾吸收容量和致死伤害判定。四端构建、隔离启动及自动检查通过，真人多人对局待验收；配置与数值见 [0.3.0 玩法说明](docs/gameplay-update-0.3.0.md)，测试范围见 [验证记录](docs/gameplay-validation-0.3.0.md)。
  The `0.3.0` update adds a lobby rules card, optional fast pacing and balanced upgraded totems across all platforms. Both defaults remain `CLASSIC`; builds, isolated server boots and automated checks pass. Real-client multiplayer playtesting remains pending.
- `0.4.0` 新增使用原创透明像素材质的共鸣哨与应急绷带、按资格队伍数量分配的动态补给、8 秒开箱争夺和终局目标预告。四端构建、隔离启动与自动检查已通过，真人多人对局待验收；交互、配方和开关见 [0.4.0 玩法说明](docs/gameplay-update-0.4.0.md)。插件玩家须重新下载本次更新的资源包以显示新外观。
  Version `0.4.0` adds a Resonance Whistle and Emergency Bandage with original transparent pixel-art textures, dynamic supply drops, contested opening and advance objective notices. Builds, isolated server boots and automated checks pass; real-client multiplayer playtesting remains pending. Plugin players must download the updated resource pack for the new item appearances. See [the gameplay guide](docs/gameplay-update-0.4.0.md).

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
6. 推进资源与战斗，默认由击杀末影龙的队伍获胜；可选近 60 秒屠龙贡献，或限命赛最后存活队伍结算。
   Teams gather resources and fight; dragon kills decide the default mode. Optional modes add contribution scoring and limited lives.

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
- server 端的主要配置入口是 [`server/src/main/resources/config.yml`](server/src/main/resources/config.yml)。
The server module is primarily configured through [`server/src/main/resources/config.yml`](server/src/main/resources/config.yml).
- 模组端运行后会把设置写入 `config/teamlifebind/settings.properties`。  
  Mod loaders persist settings into `config/teamlifebind/settings.properties`.

## 命令 / Commands

所有平台通用命令 / Commands available on all platforms:

- `/tlb help`
- `/tlb rules`
- `/tlb menu`
- `/tlb dev` — 管理员完整测试物品目录 / Admin test item catalog
- `/tlb dev magic` — 按分类选择魔法物品，再单件领取或临时测试 / Choose a magic item, then claim one or test a spell
- `/tlb dev magic <1–7>` — 兼容七页平铺浏览，点击进入详情 / Optional flat pages opening item details
- `/tlb magic list|select <1–3>|cast|focus` — 已学法术、选槽、施法与总魔典补领 / Learned spells, selection, casting and grimoire recovery
- `/tlb magic give <id>` — 管理员领取指定魔法物品 / Admin magic item grant
- `/tlb magic test <id> [1–3]` — 管理员临时替换测试法术槽 / Admin temporary spell test slot
- `/tlb magic teststop` — 结束测试并恢复原知识、魔力与冷却 / Restore original magic state
- `/tlb mode RANDOM_GRIMOIRE` — 下局每人随机一本一阶书 / Random tier-one starter book next round
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

### 新增赛制与地图目标 / Competition Settings

Paper 使用 `gameplay.match-mode`、`gameplay.team-lives`、`gameplay.dragon-victory`、`gameplay.control-point-enabled` 与 `gameplay.border-enabled`；模组配置使用相同键去掉 `gameplay.`。默认 `DRAGON_HUNT` / `3` / `LAST_HIT` / `false` / `false`。限命的 3 是重生额度：前三次普通团灭仍可复活，第四次才淘汰；禁复活维度立即淘汰。设置开局锁定，示例与边界见 [0.7.0 玩法说明](docs/gameplay-update-0.7.0.md)。
Paper stores competition settings under `gameplay`; mod settings use the same unprefixed keys. Defaults preserve dragon hunting and last-hit scoring. Three respawn credits permit three ordinary wipes; the fourth eliminates the team.

### 0.4.0 新物品与补给 / Items and Supplies

共鸣哨与应急绷带默认可合成使用，独立于 `CLASSIC` / `FAST` 节奏和图腾预设。共鸣哨用 2 铜锭 + 1 红石无序合成，右键给本队标记准星位置 12 秒，团队冷却 15 秒且不消耗。应急绷带用 2 纸 + 2 线无序合成，产出 1 个；右键开始 4 秒读条，成功治疗 2 HP、消耗 1 个并进入 30 秒团队冷却。再次右键、受伤、攻击、换掉触发手槽物品、离线、换维度或死亡会打断；无需持续按住右键。
Both new items are craftable by default. The whistle marks a position for the team; a bandage starts a four-second channel on right-click and heals 2 HP on success. Channels do not require holding the button.

补给默认按仍有对局资格的队伍数计算 `clamp(teams, 2, 8)`，提前 60 秒锁定并公布落点；用空主手右键一次补给桶开始 8 秒开箱，离开 3 格、受伤、攻击或换维度会打断，成功后一次性填入战利品。首次要塞提示前 60 秒增加终局目标预告；屠龙胜利归属沿用现有规则。
Supply drops scale from two to eight by eligible team count and announce positions one minute ahead. Right-click once with an empty main hand, then stay within three blocks for the uninterrupted eight-second opening channel. The first stronghold hint receives a one-minute advance notice.

- Paper：`timed-events.supply-drops.dynamic-count: true`；原有 `count: 0` 仍关闭补给，设为 `false` 时采用原固定数量。
- Fabric / Forge / NeoForge：`supply-drops-dynamic-count=true`，关闭后采用原固定数量设置。

完整数值、同步治疗与争夺边界见 [0.4.0 玩法说明](docs/gameplay-update-0.4.0.md)；资源包需要同步更新至 0.4.0。

### 0.3.0 玩法预设 / Gameplay Presets

大厅规则卡与 `/tlb rules` 向所有玩家显示屠龙胜利条件、普通维度约 5 秒复活、禁复活维度观战、队伍共享项目、一队一床限制，以及实际事件开关和末地普通图腾限制。管理员可以在大厅切换比赛节奏与强化图腾平衡；开局会锁定本局配置快照。
The lobby rules card and `/tlb rules` explain victory, respawning, shared team state, the one-bed limit, active events and End totem restrictions. Administrators select presets in the lobby; each match keeps its starting settings.

| 选项 / Option | 默认 / Default | 可选档位 / Alternative |
| --- | --- | --- |
| 比赛节奏 / Match pace | `CLASSIC`：15 / 20 / 30 分钟事件，3 分钟提示间隔 | `FAST`：8 / 10 / 15 分钟事件，2 分钟提示间隔 |
| 强化图腾 / Upgraded totem | `CLASSIC`：保留原设计的时间与救援目标，无额外团队冷却 | `BALANCED`：20 秒再生，总吸收为当前最大 HP 的 50%、上限 8 HP，45 秒团队强化救援冷却 |

事件依次为自动地狱门、补给、首个要塞提示。普通图腾保留原有行为。完整配置示例和两档数值见 [0.3.0 玩法说明](docs/gameplay-update-0.3.0.md)。
The events are automatic Nether portals, supply drops and the first stronghold hint. Ordinary totems retain their existing behavior; see [the gameplay guide](docs/gameplay-update-0.3.0.md) for exact settings.

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
- 比赛节奏与强化图腾平衡：`gameplay.match-pace`、`gameplay.totem-balance`，默认均为 `CLASSIC`。
  Gameplay presets: `gameplay.match-pace` and `gameplay.totem-balance`, both defaulting to `CLASSIC`.
- 定时事件配置：  
  Timed-event settings:
  - `timed-events.auto-nether-portals.enabled`
  - `timed-events.anonymous-stronghold-hints.enabled`
  - `timed-events.supply-drops.count`
  - `timed-events.supply-drops.dynamic-count`（默认 `true`）
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
- `match-pace`（默认 `CLASSIC`）
- `totem-balance`（默认 `CLASSIC`）
- `supply-drops-dynamic-count`（默认 `true`）

## 构建 / Build

当前四端统一使用 Minecraft **26.3**，显示版本 **0.1.2**、内部构建编号 **0.7.0**。构建与游戏运行使用 **Java 26**；平台类生成 Java 26 字节码，`common` 使用 JDK 26 编译并保留 Java 21 目标字节码。Gradle Wrapper 为 **9.8.1**，Java 版本和平台依赖集中在 [`gradle.properties`](gradle.properties)。NeoForge 固定为 **26.3.0.71-beta**，Paper 运行验证目标为 26.3 稳定通道 **build 175**；游戏与加载器依赖沿用原组合。Java 26 本轮检查见 [迁移验证](docs/java26-validation.md)，此前依赖选择见 [升级依据](docs/upgrade-research.md)。
All four platforms target Minecraft **26.3**, displayed version **0.1.2**, content build **0.7.0**, using **Java 26** for builds and runtime. Platform classes target Java 26; `common` is compiled with JDK 26 and keeps Java 21 bytecode. Java settings and platform dependencies are centralized in [`gradle.properties`](gradle.properties). Gradle **9.8.1**, NeoForge **26.3.0.71-beta**, and Paper 26.3 stable **build 175** remain unchanged. See [Java 26 validation](docs/java26-validation.md).

服务端 API 由官方 Maven 下载，编译不再依赖 `.tools` 内的本机 JAR；请将 `JAVA_HOME` 和 IDE 的 Gradle JVM 指向 JDK 26，客户端启动器也选择 Java 26。Gradle 开发运行任务使用工具链启动器；三种模组元数据都声明最低 Java 26。
The server API is resolved from official Maven; building no longer requires a local API JAR. Set `JAVA_HOME` and the IDE Gradle JVM to JDK 26, and select Java 26 in client launchers. Development tasks use the toolchain launcher, and all three mod loaders receive a Java 26 minimum requirement.

Forge 26.3-66.0.10 的 Java 26 服务端与客户端还需 JVM 参数 `--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming`；项目内 Forge 开发启动已包含，玩家启动器及正式服务端按 [安装说明](docs/install-0.7.0.md) 添加。
Forge 26.3-66.0.10 on Java 26 also needs `--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming`. Forge development tasks include it; add it to production servers and client launchers as described in [installation](docs/install-0.7.0.md).

构建全部模块 / Build all modules:

```bash
./gradlew build
```

构建完整分发包（四端 JAR、服务端资源包、更新说明）：`./gradlew bundleRelease`，输出位于 `build/distributions/`。
Build the complete release bundle with `./gradlew bundleRelease`; output is in `build/distributions/`.

`0.7.0` 安装步骤见 [安装说明](docs/install-0.7.0.md)，玩法配置见 [玩法说明](docs/gameplay-update-0.7.0.md)。当前 Java 26 构建与运行记录见 [迁移验证](docs/java26-validation.md)；迁移前的 Java 25 玩法测试保留在 [玩法验证](docs/gameplay-validation-0.7.0.md)，0.2.0、0.3.0、0.4.0 验证作为历史保留。
See [0.7.0 installation](docs/install-0.7.0.md), [gameplay settings](docs/gameplay-update-0.7.0.md), and [current Java 26 validation](docs/java26-validation.md). The [pre-migration gameplay validation](docs/gameplay-validation-0.7.0.md) and earlier versions remain historical evidence.

只构建某个平台 / Build a single platform:

```bash
./gradlew :server:build
./gradlew :fabric:build
./gradlew :forge:build
./gradlew :neoforge:build
```

独立平台构建可加 `-Pplatforms=server`（或 `fabric` / `forge` / `neoforge`），跳过其他 loader 的配置与缓存生成；默认仍构建所有平台。
Use `-Pplatforms=server` (or another loader name) to configure only that platform; all platforms remain the default.

```bash
./gradlew :server:build -Pplatforms=server
python scripts/verify-resources.py
```

## 本地运行 / Local Development Runs

根项目已经提供了便捷任务。  
The root project already provides convenience tasks.

启动本地测试服：默认查找 `.tools/spigot-26.3.jar`，也可指定对应版本的 Bukkit/Spigot/Paper JAR。
Start a local test server with a matching Bukkit/Spigot/Paper JAR:

```bash
./gradlew runServer -Pserver_jar=/absolute/path/to/server-26.3.jar
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
- 可用 `-Pdev_run_root=.tools/smoke-26.3` 另建测试运行目录。`chunk-preloading` 配置限制同步加载、每 tick 异步提交和未完成请求数。
  Use `-Pdev_run_root=.tools/smoke-26.3` for a separate test environment. `chunk-preloading` bounds synchronous loads, asynchronous submissions and unfinished requests.

## 开发说明 / Development Notes

- 这个项目虽然有 `common` 公共层，但大量完整玩法逻辑仍然分别存在于 `server`、`fabric`、`forge`、`neoforge` 四端。
Even though the project has a `common` layer, a large portion of the gameplay logic still lives separately in `server`, `fabric`, `forge`, and `neoforge`.
- 如果要改规则枚举、文本、血量预设这类基础内容，通常先看 `common`。  
  For core enums, text assets, and health preset changes, `common` is usually the first place to update.
- 如果要改 UI、计分板、Tab、菜单、开局流程、重生、床逻辑、维度流转，通常需要同步四个平台。  
  For UI, scoreboard, tab, menu flow, match start flow, respawn, bed logic, or dimension flow, expect to sync changes across all four platforms.
- `:common:regressionTest` 覆盖经验边界、分队/生命周期和语言回退，并自动加入 `build` 的检查流程。CI 同时构建四端与服务端资源包；多人完整对局仍需试玩。
  `:common:regressionTest` covers experience boundaries, assignments, lifecycle and language fallback, and runs during `build`. CI builds all platforms and the resource pack; full multiplayer matches still require playtesting.
- 规则卡、快速节奏与强化图腾平衡已在 `0.3.0` 实现；共鸣哨、绷带与补给争夺已在 `0.4.0` 完成自动检查。本轮构建 `0.7.0` 已实现其余路线内容，四端自动检查通过；后续按真实多人试玩数据继续调平衡，见[玩法更新路线](docs/gameplay-roadmap.md)。
  Rules and presets shipped in `0.3.0`, and utility items in `0.4.0`. The remaining [roadmap](docs/gameplay-roadmap.md) is implemented in content build `0.7.0`, with automated checks passing. Further balancing will use real multiplayer playtesting.
- 部分代码由AI辅助完成。  
  Some of the code was assisted by AI

## 许可证 / License

本项目使用 [MIT License](LICENSE)。
This project is licensed under the [MIT License](LICENSE).
