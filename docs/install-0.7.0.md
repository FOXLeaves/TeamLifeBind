# 安装 0.1.2（构建 0.7.0）

**2026-11-02 RG 合作体验更新**：四端路线选择增加可改票的团队投票和首票后 20 秒超时；新增战斗中可看的远征／遗物面板。用 `/tlb rg` 打开，`/tlb rg 1|2|3` 投票。替换本批对应 JAR，模组客户端和服务端同时更新；文件名、显示版本 0.1.2、Java 26 与默认 SUR 保持。Paper 资源包继续使用原 SHA-1，详见[投票规则与面板](roguelike.md)。

**2026-11-01 RG 肉鸽更新**：四端新增 `/tlb mode rg`，十层随机路线、八种团队遗物和局内重生额度。普通参赛者通过 `/tlb rg` 或 `/tlb rg 1|2|3` 选路。替换本批对应 JAR，模组客户端及服务端同时更新；显示版本仍为 0.1.2，默认 SUR 不变，Paper 资源包沿用下方 SHA-1。详见[RG 玩法与清理规则](roguelike.md)。

**2026-11-01 闯关体验更新**：PVE 新增休闲／标准／困难三档、首领实时血条、关卡预告与通关统计。在大厅用 `/tlb pve easy|normal|hard` 或赛制菜单选择，缺少新配置时使用标准档。显示版本和文件名继续沿用，需替换本批对应 JAR；模组客户端与服务端同时更新。本批未修改资源包，已使用下方 SHA-1 对应资源包的 Paper 服务器可继续使用。详见[三模式玩法与验证](play-modes.md)。

**2026-10-26 三模式更新**：默认改为 `SUR` 合作生存，支持单人、持久世界、独立背包／经验／位置记录与中途加入；新增 `PVE` 十关挑战，第 5、10 关有首领，通关后停留 20 秒再回大厅；`PVP` 原比赛保留。替换四端对应 JAR，使用 `/tlb mode sur|pve|pvp` 或 `/tlb campaign` 选择。旧配置缺少 `play-mode` 时使用 SUR，`match-mode` 继续表示屠龙／限命／随机魔典子赛制。详情见[三模式玩法与保存规则](play-modes.md)。

**2026-10-20 魔力恢复修复**：三模组补上开发测试前保存、登录恢复与 `/tlb magic teststop`。四端魔力苹果成长仅本局有效，同局重连保留，结束／重开对局或重载探索记录后上限回到 100；旧探索成长同步清除。单人存档／本地开房替换自己客户端对应加载器的模组 JAR 并重启，其中包含本地服务端逻辑；独立服务器替换对应模组或 Paper 插件 JAR。道具说明已更新。详情见[修复说明](mana-test-reset-fix.md)。

**2026-10-20 模组 HUD 批次**：三个模组更新魔力、三槽冷却、吟唱、枪械弹量/换弹/热量、魔法轮盘与对局侧栏。替换模组服务端及所有玩家客户端 JAR，以同时应用客户端卡片和常驻枪械行动栏去重；协议保持兼容。提示读取实际键位，小窗口侧栏按住玩家列表键展开，见[HUD 说明与布局预览](native-hud.md)。Paper JAR 与资源包不含模组 HUD。

**2026-10-20 建筑与平台调整批次**：四端主世界类世界的新区块加入边境要塞、星辉高塔、远行补给仓，每座六个物资点。Paper 已移除枪械、开发领取与枪械资源；三个模组继续保留枪械。需同时替换本批 JAR 与 Paper 资源包，详见[更新说明与建筑预览](landmarks-and-paper-update.md)。

**2026-10-20 跟随修复批次**更新三个模组客户端的随身魔法定位，解决吟唱、环绕和护盾随移动拖尾的问题；持续引导的视觉起点跟随眼位。请替换对应加载器 JAR，显示编号仍为 0.1.2，详见[修复说明](magic-follow-fix.md)。

**2026-10-18 修复批次**包含三模组着色器不可见修复、14 学派几何和领域脉冲。显示版本与文件名沿用原编号，更新时请替换本批 JAR；客户端与模组服务端同时更新。管理员可用 `/tlb dev magic` 选择法术测试，见[修复记录](magic-fixes-2026-10-18.md)。

游戏内与四端元数据的显示版本均为 **0.1.2**，发行文件保留内部构建编号 **0.7.0**。当前面向 Minecraft **26.3**、**Java 26**，平台 JAR 使用 Java 26 字节码。Fabric Loader 0.19.5 / API 0.162.0+26.3、Forge 26.3-66.0.10、NeoForge 26.3.0.71-beta 保持已有固定版本；Paper 运行目标为 26.3 stable build 175。游戏与加载器依赖沿用原组合，来源见[升级研究](upgrade-research.md)。本轮 JDK 与重跑结果见 [Java 26 验证](java26-validation.md)，迁移前的玩法检查见 [原验证记录](gameplay-validation-0.7.0.md)。

构建时将 `JAVA_HOME` 与 IDE 的 Gradle JVM 设为 JDK 26；运行服务端和所有模组客户端时也选择 Java 26。启动器自带的 Java 25 不满足当前平台 JAR 的字节码要求。`common` 不需要单独安装；其 Java 21 字节码只是共享模块的编译目标，不代表平台可用 Java 21 启动。

## Bukkit / Spigot / Paper

1. 停止服务端，备份配置和需要保留的存档。替换为发行包内 `plugins/TeamLifeBind-0.7.0-server.jar`，移走旧 TeamLifeBind JAR，保留 `plugins/TeamLifeBind/`。
2. 用 Java 26 启动，确认 TeamLifeBind 显示版本为 **0.1.2**，查看 `/tlb rules`。游玩模式默认 SUR；需要原团队对抗时选择 `/tlb mode pvp`。经典屠龙、最后一击、经典节奏和图腾仍是子赛制／规则默认值，限命、贡献、据点与收圈按需在大厅启用。
3. 替换资源包为 `resourcepack/TeamLifeBind-resourcepack-0.7.0.zip`，托管新 ZIP，更新 `resource-pack.url`、ZIP 的 SHA-1 与 `resource-pack.enabled`。本批资源包保留八阶魔法书、五件魔法伴生物品与协作道具，移除枪械模型、材质及音效。文件名相同也需替换。
4. 管理员用 `/tlb dev` 查看 **12 类**基础测试物品，通过魔法入口或 `/tlb dev magic` 选择分类、具体物品及详情。“领取一件”只发选中的魔法物品，背包满则拒绝；法术可直接装入临时测试槽。Paper 不再提供枪械，旧枪在玩家背包、末影箱、光标或打开的容器中转换为普通铁马铠。主手持总魔典时右键施法、F 切槽、潜行右键选择菜单；其他物品保留原版副手交换，改键跟随原版设置。见[开发者目录](dev-item-menu.md)、[魔法玩法](magic-system.md)与[玩法说明](gameplay-update-0.7.0.md)。

本次资源包 `TeamLifeBind-resourcepack-0.7.0.zip` 的 SHA-1 为 `d37e438f3eb64307c7f0e63f6068785c60f01631`，填写到 `resource-pack.sha1`。若自行修改或重新打包资源，使用修改后 ZIP 的 SHA-1。

新赛制设置在开局保存快照；在大厅调整后再开下一局。配置示例（把新键并入已有 `gameplay`，不要重复创建同名段）：

```yaml
gameplay:
  play-mode: SUR
  pve-difficulty: NORMAL
  match-pace: CLASSIC
  totem-balance: CLASSIC
  match-mode: DRAGON_HUNT
  team-lives: 3
  dragon-victory: LAST_HIT
  control-point-enabled: false
  border-enabled: false
```

`play-mode: SUR|PVE|RG|PVP` 控制游玩模式；`pve-difficulty: EASY|NORMAL|HARD` 控制闯关怪物强度，不修改原版世界难度和奖励概率。启用限命设 `match-mode: LIMITED_LIVES`；PVP 贡献屠龙设 `dragon-victory: CONTRIBUTION`；据点与收圈设相应布尔值为 `true`。`team-lives: 3` 表示三次重生额度，第三次普通团灭仍可复活，第四次才淘汰。默认据点和收圈关闭。文件修改应在大厅完成，再使用 `/tlb reload`。

随机魔典开局设 `match-mode: RANDOM_GRIMOIRE`，或在大厅执行 `/tlb mode RANDOM_GRIMOIRE`；下一局每人获得一本一阶书，仍需潜行右键学习。新老世界只有尚未生成战利品的自然箱会抽取魔法物资，已经开过的箱子不会重新填充。探索魔法记录保存在 `plugins/TeamLifeBind/magic-players.properties`，比赛学习与其分离。

大型建筑默认启用，仅在插件启动后生成的主世界类新区块中出现，配置的大厅世界除外；已生成区块不补建，物资取走后不补货。每个 512×512 区域有一个候选位置，不合适的水域、陡坡或已有建筑会跳过；并非每个区域都能生成。可在配置中设 `world-landmarks.enabled: false` 关闭后续生成，重启生效，已有建筑保留。新版本每局创建的比赛世界会自动采用该生成器。

管理员也可使用大厅赛制菜单，或 `/tlb mode LIMITED_LIVES`、`/tlb dragon CONTRIBUTION`、`/tlb objectives on`、`/tlb border on`。比赛准备、倒计时和运行时会拒绝修改。

## Fabric / Forge / NeoForge

服务端与所有玩家客户端安装对应加载器的同版本 JAR：

| 平台 | 依赖 | 发行包内文件 |
| --- | --- | --- |
| Fabric | Loader 0.19.5；Fabric API 0.162.0+26.3 | `mods/fabric/TeamLifeBind-0.7.0-fabric.jar` |
| Forge | Forge 26.3-66.0.10 | `mods/forge/TeamLifeBind-0.7.0-forge.jar` |
| NeoForge | NeoForge 26.3.0.71-beta | `mods/neoforge/TeamLifeBind-0.7.0-neoforge.jar` |

停止实例后替换旧 TeamLifeBind JAR，保留 `config/teamlifebind/settings.properties`；不用另装 common JAR 或插件资源包，模组已经内置生产材质与模型。NeoForge 当前固定发行仍为 beta。Fabric、Forge、NeoForge 的模组元数据均声明最低 Java 26，服务端 JVM 与所有玩家启动器都应选择 Java 26。

大型建筑同时接入普通主世界与每局种子主世界生成流程，专用大厅维度排除。请探索新区块；旧区块不追补建筑。每座建筑六个物资点，使用自然箱的魔法书稀有度，建筑物资不含枪械。

模组端默认左键开火、右键瞄准、R 换弹。在 **选项 → 控制 → 按键绑定 → TeamLifeBind → 枪械换弹** 中修改换弹键；开火和瞄准跟随原版攻击、使用物品键。F 保留原版副手交换。正常对局枪械只从空投获取，管理员可用 `/tlb dev` 领取七把测试枪。没有活动比赛时，存活非观察者玩家无需分队即可在大厅测试；进入投票或倒计时后暂停枪械使用，正式比赛仅存活参赛者可用。专用大厅的玩家 PvP 保护保留，可用生物测试命中。备弹无限，Minigun 使用无限供弹与过热限制，其他枪械仍需装填弹匣。目录见[开发者物品目录](dev-item-menu.md)，规则详见[空投枪械说明](airdrop-weapons.md)。

魔法书通过潜行右键学习，首次学习赠送总魔典。主手总魔典默认 **V 施法 / 按住 G 轮盘 / 左 Alt + 滚轮切槽**，三个键均可在 TeamLifeBind 魔法分类修改；G 松开只选槽。服务端与客户端须同时更新，避免缺少魔法物品、同步包或输入注册。模组魔法现在使用内置 GLSL 着色器，包含法阵、连续光束、球形格盾、旋涡和超位星环，依世界深度遮挡并叠加独立辉光，不创建 Minecraft 魔法粒子；无需额外光影包或 Shooting Star JAR，Paper 保留粒子。实现与离屏预览见[着色器说明](magic-shader-rendering.md)。探索记录保存为服务端 `config/teamlifebind-magic.properties`，更新时保留该文件；总魔典遗失可用 `/tlb magic focus` 按条件补领。

四端管理员魔法测试统一使用 `/tlb dev magic` 的分类与详情选择器，也可直接 `/tlb magic test <法术ID> [1–3]`。临时槽最多三种、300 魔力上限，每次装入清理临时冷却；需要总魔典时仅在空格加入一本临时载体。`/tlb magic teststop` 或详情内结束按钮恢复原知识、魔力与随经过时间衰减的冷却，并清理临时载体；退出、撤权或开局也会恢复。临时测试不改变探索存档；测试普通学习或消耗流程前先结束临时会话。完整步骤见[开发者目录](dev-item-menu.md)。

**Forge 26.3-66.0.10 使用 Java 26 时，服务端和所有玩家客户端都需追加以下 JVM 参数：**

```text
--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming
```

Forge 服务端将参数追加到安装器生成的 `user_jvm_args.txt`；自定义 Java 启动命令时放在 `-jar` 或主类之前。客户端在启动器的 JVM 参数栏追加。项目内 `runForgeServer` / `runForgeClient` 已包含此参数。它只向 `java.naming` 开放指定 DNS provider 包，解决该 Forge 版本在 Java 26 下的 DNS 工厂访问失败；不会替换加载器。实际初始化检查与上游日志说明见 [Java 26 验证](java26-validation.md)。

经典默认与已有 `match-pace=CLASSIC`、`totem-balance=CLASSIC`、`supply-drops-dynamic-count=true` 保留。新增玩法设置在本局开始时锁定；只有下一局采用修改后的参数。

模组新增默认键：

```properties
match-mode=DRAGON_HUNT
team-lives=3
dragon-victory=LAST_HIT
control-point-enabled=false
border-enabled=false
```

与插件使用相同枚举及数值，在实例停止后编辑。无独立阶段任务资源奖励开关：占点、夺补给和抵达末地的首次完成公告按本局实际目标发生。

## 安装后检查

先核对 `/tlb rules` 的赛制、额度、屠龙归属、据点与边界状态，再测试床/药水 HUD、两件新配方、团队冷却、战斗断线重连和普通维度第 4 次团灭淘汰。最后进行至少两人全流程与四人对抗测试。

[0.4.0 说明](install-0.4.0.md)和[验证](gameplay-validation-0.4.0.md)保留历史结论；只有 0.7.0 的实际构建、运行与试玩记录才能证明本版本行为。
