# 300 种魔法的参考源码研究

核验日期：2026-10-14，Asia/Hong_Kong。研究直接读取用户指定仓库的源码，不运行外部 Gradle、Python、数据包或安装脚本。浅克隆保存在 `.tools/reference-magic/`，只作为只读参考；本项目不增加这些模组/插件依赖，也不移入外部代码、材质、图标、模型或声音。

| 官方仓库 | 本次固定提交 | 阅读范围 |
| --- | --- | --- |
| [lullaby6/data-packs 的 26.3 目录](https://github.com/lullaby6/data-packs/tree/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3) | `ccea5be14428777d27f0c1516a34cd214f46d1c2` | Magicraft 的注册、学习、施法、魔力、自然箱及具体法术；Meteors、Warpstones、相关附魔的效果表达 |
| [PrimordialMoros/Bending](https://github.com/PrimordialMoros/Bending/tree/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5) | `b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5` | 能力生命周期、激活、世界管理、几何碰撞、元素碰撞、粒子适配、暂态方块、具体元素能力 |
| [iron431/Irons-Spells-n-Spellbooks](https://github.com/iron431/Irons-Spells-n-Spellbooks/tree/e4056af90302d37eb1739f5ff05020b020e6e252) | `e4056af90302d37eb1739f5ff05020b020e6e252` | 原生客户端按键、轮盘、快捷栏、法力 HUD、书与法术容器、学习、服务端施法、法力恢复 |

Bending 的固定源码标明 AGPL-3.0-or-later；Iron 的官方说明保留其代码/资源的使用条件。本次参考的是公开交互与架构，产物是本项目独立实现的设计，不是移植这两个项目。[Bending 许可](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/LICENSE)、[Iron 官方说明](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/README.MD)

本文的“参考事实”已经按固定源码核对；“本项目方案”是独立实现建议，不表示对应功能已经在游戏内完成或经过真人试玩。明确目标为 **280 种普通魔法 + 20 种超位魔法，共 300 种**；每人总共最多学习 3 种，不是每本书另有 3 槽。

## 26.3 数据包：具体能学什么

固定提交的 `26.3` 有 83 个顶层包目录。直接相关的主包是 **Magicraft**，另有 Meteors、Teleport Book、Warpstones、Dimensional Potions、Recall Potion、Thunder Strike / Freezing / Gravity / Projectile Deflection 等附魔包。Magicraft 没有独立 README，因此下面以函数和 JSON 为证据。其 `pack.mcmeta` 声明数据包格式 121、最小/最大 `[121,1]`；这里只确认目录与元数据，不将它当成对 TeamLifeBind 四个平台的运行兼容证明。[固定目录](https://github.com/lullaby6/data-packs/tree/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3)、[Magicraft 元数据](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/pack.mcmeta)

### 注册、学习与魔力

Magicraft 的注册函数实际列出 **59 个法术**，每项有稳定 ID、分类、颜色、显示名、物品和函数路径；分类表有 13 类：空气、植物、生命、箭、火、冰、雷、毒、蛛网、尖牙、凋零、幽匿、末影。这个“注册表描述与执行函数分开”的结构适合大量内容，但不同名字本身并不等于不同机制。[59 项注册](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/load/spells.mcfunction)、[13 类描述](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/scripts/categories.json)

卷轴用来随机学习未掌握的法术，已学内容以玩家 tag 表示；全部学完时还给卷轴。它没有用户要求的统一 3 种上限，也没有按普通/超位划分。TeamLifeBind 应保留“获得书 → 学习 → 可释放”的清楚反馈，改成书明确携带一个法术 ID，服务器检查重复与 3 槽上限，拒绝时不吞书。[学习入口](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/spell/learn/learn.mcfunction)、[学习具体法术](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/spell/learn/spell.mcfunction)

Magicraft 用 scoreboard 保存当前/最大魔力，初始化为 10/10；每个法术自己验证并扣费，魔力药水增加 8 并限制到 10；持书 HUD 用物品 atlas 的紫水晶/煤图标显示魔力格。TeamLifeBind 应以共享魔力账户统一扣费与上限，避免 300 个处理器各自修改账户，且 HUD 只显示服务器同步结果。[账户初始化](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/load.mcfunction)、[药水](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/consume/mana_potion.mcfunction)、[魔力图标](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/grimoire/held/mana.mcfunction)

Magicraft 的施法入口检查已学、非零魔力和短暂施法锁，再按选中 ID 分派；对应附魔利用 `post_piercing_attack` 调用施法函数。成功播放附魔台与唤魔者声音。原生模组不能照搬这个借附魔捕获攻击的桥接，尤其本项目已有独立左右键枪械控制。[施法入口](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/grimoire/left_click.mcfunction)、[成功声音/施法锁](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/player/grimoire/left_click/spell.mcfunction)、[附魔触发桥](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/enchantment/grimoire.json)

### 具体效果与自然战利品

| 源码例子 | 真实实现 | 可独立借鉴的表达 |
| --- | --- | --- |
| [强冰球发射](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/ice/strong_iceball.mcfunction)、[飞行](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/ice/strong_iceball/tick.mcfunction)、[命中](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/ice/strong_iceball/hit.mcfunction) | 无重力雪球携带 marker，tick 雪花，命中有雪块碎片/声音/范围伤害与减速 | 连续轨迹、明确命中爆发、冰元素控制；本项目用有界投射物记录替代为每发生成辅助实体 |
| [生命治疗领域](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/life/healing_area.mcfunction) | 范围即时治疗与再生，心形粒子 | 治疗不必复用伤害弹道，按队友/自己过滤，并以环形边缘显示有效范围 |
| [尖牙环](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/fangs/fangs_circle.mcfunction) | 16 个地面尖牙分布在周围 | 使用空间形状与延迟触发区分“环、线、墙、尾迹”；本项目可用地面预警后分段脉冲 |
| [幽匿射线](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/sculk/sonic_boom/raycast.mcfunction)、[命中](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/sculk/sonic_boom/hit.mcfunction) | 前向递归步进，每步音爆粒子，命中伤害和黑暗 | 把射线碰撞和视觉采样分开；明确是否穿墙，而非靠粒子推断 |
| [Meteors 飞行](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Meteors/data/meteors/function/meteor/tick.mcfunction)、[爆发](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Meteors/data/meteors/function/meteor/explosion.mcfunction) | 下降/旋转，火焰+两类烟雾，碰地后大爆炸 | 超位法术的远景轮廓、降临预警、核心和外圈分层；不采用外部破坏规模或无范围广播 |
| [Warpstones 分频 tick](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Warpstones/data/warpstones/function/warpstone/tick.mcfunction)、[组合展示实体](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Warpstones/data/warpstones/function/warpstone/place/summon.mcfunction) | portal/end_rod 分别计时到 5/15 后触发；interaction 与 block/text display 组合 | 长驻符阵降低更新频率，交互体和视觉体分开；本项目暂态实体必须统一清理 |

Magicraft 在四种自然箱战利品表增加卷轴池；末地城例子每次 1 roll，`random_chance=0.05`。这些是**覆盖完整原版表**的文件，不是通用安全注入接口。TeamLifeBind 要在真实结构战利品首次生成时追加自己的池，保留原有物品；玩家放置、管理员填充、空投和已经生成过的箱子不可误判为自然箱，更不能每次打开重抽。[末地城池](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/minecraft/loot_table/chests/end_city_treasure.json)、[其余自然箱](https://github.com/lullaby6/data-packs/tree/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/minecraft/loot_table/chests)

### 不能把参考源码当成正确性模板

发现以下代码事实，未在外部数据包中运行验证：

- 5 秒回蓝函数 `execute as @a` 后使用 `scoreboard players add @a`，从命令语义推断，多名满足条件者会重复给全体玩家加蓝。独立实现必须按 UUID 单独回蓝、对最大值截断，并测试多人不同蓝量。[回蓝函数](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/schedule/5s.mcfunction)
- 幽匿射线的方块阻挡检查已注释，命中函数残留调试聊天输出。TeamLifeBind 的非穿墙法术必须执行真实方块 raycast，正式路径不发送调试聊天。[射线](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/sculk/sonic_boom/raycast.mcfunction)、[命中](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/sculk/sonic_boom/hit.mcfunction)
- 多个范围技能使用广泛实体选择器，不能表达本项目的存活参赛、队伤、来源归属、观察者和 PvP 策略。范围判定必须先过统一目标过滤，再调用正常伤害管线。[强冰球范围](https://github.com/lullaby6/data-packs/blob/ccea5be14428777d27f0c1516a34cd214f46d1c2/26.3/Magicraft/data/magicraft/function/spells/ice/strong_iceball/hit.mcfunction)

## Bending：能力生命周期与元素生态

### 生命周期与世界隔离

`Ability` 的契约明确拆开激活、更新、配置、碰撞体、碰撞回调、销毁和易主。激活成功后才登记实例；每个世界各有能力/碰撞管理器，每个玩家 UUID 有自己的实例集合。更新返回 REMOVE 或抛异常会移除并调用 `onDestroy`；换世界和卸载世界清理旧实例。[能力契约](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/api/src/main/java/me/moros/bending/api/ability/Ability.java)、[激活入口](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/game/ActivationControllerImpl.java)、[实例更新/清理](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/game/AbilityManagerImpl.java)、[世界生命周期](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/game/WorldManagerImpl.java)

本项目可独立采用 `PREPARING → ACTIVE → FINISHING → CLOSED` 的有界状态，保存施法 UUID、世界、来源法术、开始 tick、截止 tick、命中过的目标集合；死亡、淘汰、退出、换世界、比赛重置都走同一关闭操作。不能让“先完成华丽粒子，再补清理”成为能力接口。

### 碰撞不是粒子命中

Bending 每 tick 汇集有碰撞体的能力，合并成 AABB 并计算 Morton 编码，以 LBVH 筛候选；然后检查实际 Collider 相交。相同施法者的两能力跳过，未注册的碰撞对也跳过；规则给出默认双方移除结果，两边回调可以更改结果，已经被移除的实例不再重复处理。[真实碰撞流程](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/game/CollisionManager.java)、[LBVH](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/collision/LBVH.java)

它的几何库有 AABB、OBB、球、圆盘和射线。ParticleStream 对一 tick 的移动区间建射线及扩张包围盒，先找附近实体，再以区间交点精检；同时检查方块与对角方向，而不是只在末端找实体。本项目快弹道也应检查上一位置到下一位置的完整扫掠，避免穿过细目标；可先用空间网格/AABB候选，不必复制其树算法。[几何类型](https://github.com/PrimordialMoros/Bending/tree/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/api/src/main/java/me/moros/bending/api/collision/geometry)、[区间碰撞](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/api/src/main/java/me/moros/bending/api/ability/common/basic/ParticleStream.java)

### 具体生态机制

| 能力/规则 | 实际行为 | 本项目独立机制方向 |
| --- | --- | --- |
| [碰撞注册](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/ability/AbilityInitializer.java) | 分层弹道对消、护盾挡低层弹道、冰息/火息对消，另有雷与金属索等特例 | 以元素 tag 和机制 tag 表达反制；每个反制写明消耗、持续和上限，避免 300×300 硬编码表 |
| [FireBlast](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/ability/fire/FireBlast.java) | 蓄力改变弹道大小/强度/速度；满蓄力与护盾、其他爆炸技能有专门碰撞结果，水/熔岩导致取消 | 蓄力球、过载爆炸、护盾消耗与弹道贯穿是不同机制；不要只提高伤害数字 |
| [WaterManipulation](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/ability/water/WaterManipulation.java) | 先选自然水/冰源再发射；允许瞄准重定向并转移所有者；重定向收紧到有限次数 | 受视线约束的“夺取敌方弹道”、可重新定向的召回弹、材料条件；伤害归属随易主变化 |
| [Lightning](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/ability/fire/Lightning.java) | 分叉线段、有受击 UUID 去重、范围电击/重定向；湿水目标伤害因子 2，落地金属甲目标因子 0.5 | 湿润导电、链式跳跃、同一施法一次命中、有限分叉；本项目数值独立平衡 |
| [AirShield](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/ability/air/AirShield.java) | 与冰息相撞在可修改表面形成暂态冰；与火能力相撞播放火/熄灭反馈 | 盾面凝霜、风盾消散火弹、有可见破盾爆发；预警和命中结果对应同一几何体 |
| [暂态方块](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/api/src/main/java/me/moros/bending/api/temporal/TempBlock.java) | 保存叠加快照、按计时器逆序恢复、维护重力方块缓存 | 默认采用视觉屏障+逻辑碰撞；若真正改地形，必须对区块卸载、重叠技能和比赛恢复建立日志 |

Bending 的 StaminaBar 是带延迟恢复、持续消耗和 BossBar 的专用状态机，不能据此宣称它为每一种元素能力提供统一魔力账户。可借鉴“消耗后停一下再恢复”的节奏，TeamLifeBind 自己实现用户要求的全法术账户。[StaminaBar](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/util/stamina/StaminaBar.java)、[截断与扣费](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/common/src/main/java/me/moros/bending/common/util/stamina/StaminaGauge.java)

### 粒子预算的证据边界

Bending 有统一 ParticleBuilder，统一空气 dust、火/灵魂火和颜色/大小/数量/偏移；Paper 与 Fabric 再分别映射粒子数据。FireBlast 的粒子数会随蓄力因子四次方增长；所读 builder、world adapter 和实例管理路径没有显示总粒子令牌预算。因此可以确认“统一效果参数与跨平台映射”，不能把“全局粒子预算完善”当成该源码事实。[统一粒子参数](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/api/src/main/java/me/moros/bending/api/platform/particle/ParticleBuilder.java)、[Paper 适配](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/paper/src/main/java/me/moros/bending/paper/platform/world/BukkitWorld.java)、[Fabric 适配](https://github.com/PrimordialMoros/Bending/blob/b1e1a3a3d6656b6db9eb7b9f9aefb3b3fd0ff8f5/fabric/src/main/java/me/moros/bending/fabric/platform/world/FabricWorld.java)

## Iron：模组操作与 HUD 对照

本次 Iron 固定提交所在默认分支为 `1.21`；这里只参考交互，不能将其 NeoForge 事件、渲染 API 或依赖直接当成 Minecraft 26.3 接口。[固定源树](https://github.com/iron431/Irons-Spells-n-Spellbooks/tree/e4056af90302d37eb1739f5ff05020b020e6e252)

### 实际按键与轮盘行为

| 操作 | Iron 官方默认 | 本项目建议 |
| --- | --- | --- |
| 释放选中法术 | `V` | `V`，通过独立 KeyMapping 改键 |
| 按住轮盘 | `R` | `G`，避免与已有 `R` 枪械换弹重复；也允许用户重绑 |
| 轮盘开/关切换 | 默认未绑定 | 可选未绑定 |
| 快捷栏切术修饰键 | 左 `Alt` + 滚轮 | 保留该习惯，只在已学槽之间循环并消费本次滚轮 |
| 单槽快捷释放 | 15 个默认未绑定 | 只建立 3 个默认未绑定的快捷释放，与学习上限一致 |

以上官方默认来自真实 KeyMappings；不要把 `R` 写成 Iron 的默认施法键，也不要把鼠标右键当成所有书的统一施法键。[官方默认键](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/player/KeyMappings.java)

Iron 在按住轮盘时释放鼠标，松开时关闭并**仅选中法术，不自动释放**；另按 `V` 才请求施法。`Alt` + 滚轮会取消原版切换物品，只改法术选择。轮盘显示名称、等级、法耗、冷却和法术独有说明；中心死区保留当前选择。键位处理采用按下边沿，避免长按连续提交即时术。[输入事件](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/player/ClientInputEvents.java)、[轮盘选择/关闭](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/gui/overlays/SpellWheelOverlay.java)、[键位边沿](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/player/ExtendedKeyMapping.java)

本项目应做原创 3 扇区轮盘和独立法力条，沿用“按住选择、松开确认、`V` 施法”的行为。打开 GUI、失焦、换世界、死亡和轮盘开启都要清除攻击输入；持枪 `R` 换弹维持既有行为。轮盘和施法键可在 Minecraft 控制设置改为用户喜欢的组合，tooltip 必须读取真实绑定，而非写死字母。

Iron 的法力 HUD 有 7 种锚点与偏移设置，可配置总显示/情景显示/不显示；情景条件包括持施法载体或法力未满，显示同步法力与玩家最大法力属性。TeamLifeBind 的对应条不应覆盖队伍复活/比赛 HUD；可以在已学法术或魔力未满时显示。[ManaBarOverlay](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/gui/overlays/ManaBarOverlay.java)

### 书、学习与施法不能混成同一概念

Iron 法术书是有容量的法术容器，通常要装备，选择管理器从装备和手持物品搜集允许加入轮盘的法术，重复术保留较高等级。它不是“所有人最多学 3 种”模型。[SpellBook](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/item/SpellBook.java)、[选择汇集](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/magic/SpellSelectionManager.java)

Iron 的通用学习 packet 只在手里有 Eldritch Page、目标未学且法术有效时消耗页面并解锁；其 learned ID 集合会保存。这个特定解锁机制不能推广成全部原作卷轴都要学习。TeamLifeBind 300 种书统一先学后用、学习总上限 3，是本项目用户规则。[学习验证](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/network/spells/LearnSpellPacket.java)、[已学持久数据](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/magic/LearnedSpellData.java)

客户端施法 packet 不传任意伤害/魔力，排队到服务端发起；服务端验证未学、冷却、法耗和施法条件。能力区分瞬发、长吟唱和持续释放；持续释放固定间隔结算。施法状态、冷却与法力分别同步。[CastPacket](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/network/casting/CastPacket.java)、[施法验证/结果](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/spells/AbstractSpell.java)、[三种生命周期及同步](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/capabilities/magic/MagicManager.java)

Iron 回蓝每 10 tick 按最大法力/回蓝属性与服务器倍率增长并截断，药水是固定量加最大量比例，变化同步给玩家。TeamLifeBind 可借鉴“服务端账户+属性上限+视觉同步”，数值要适配本项目比赛时长与超位消耗。[回蓝](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/capabilities/magic/MagicManager.java)、[法力写入](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/api/magic/MagicData.java)、[瞬时回蓝药水](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/effect/InstantManaEffect.java)

## TeamLifeBind 的独立执行方案

以下全部是本项目方案，数值是可验证的设计起点，不是外部项目参数，也不是已发布行为。

### 300 法术怎样保留不同机制

描述表应记录：稳定 ID、中文名/说明、学派、阶级、魔力、冷却、吟唱/持续 tick、获取权重、目标策略、机制 ID、形状、元素状态、触发条件、特殊参数、视觉脚本和声音脚本。300 份条目必须能说明 **触发 → 空间行为 → 命中/辅助结果 → 清理**；只改元素颜色、名称、伤害或半径的条目不足以满足“每个魔法都有不同机制”。

建议先提供这些真正执行的原语，再为每本书组合不同的条件和阶段：

| 执行原语 | 区别与必需状态 |
| --- | --- |
| 扫掠飞弹 | 区间碰撞，墙体停止，命中一次，最大寿命/距离 |
| 穿刺飞弹 | 最多穿刺数、已命中 UUID、每段衰减、不能重复刷同一实体 |
| 追踪/召回 | 合法目标锁定、视线/转向上限、返回时不同结果 |
| 弹射 | 最多反弹次数、方块法线、同位置循环终止 |
| 瞬时射线/持续光束 | 一次精检与分频持续结算分开，累计法耗/每目标命中间隔 |
| 扇形/冲击波 | 前向角度或增长半径，遮挡检查，每个波一次命中 |
| 链跃/分叉雷 | 目标集合去重、跳数、最大跳距、湿润/标记条件 |
| 固定领域/跟随领域 | 锚点不同，周期结算，出入事件，最多存活区数量 |
| 延迟符雷/地面裂隙 | 激活延迟、接近触发、预警形状、不能永久占用实例 |
| 护盾/反射 | 逻辑体、承伤预算、反射次数、易主后来源归属 |
| 墙/牢笼/冰环 | 不同几何体的阻挡/禁锢，保持可逃路线和期限 |
| 位移/交换/回溯锚点 | 找可站位置、世界边界/障碍过滤、位置记录寿命 |
| 拉拽/抛升/冲刺斩 | 正常速度接口、最大位移、目标过滤、冲刺期间轨迹判定 |
| 治疗/净化/守护转移 | 队友与自己策略、有效恢复上限、负面状态白名单 |
| 吸蓝/燃蓝/返蓝 | 同步修改两个服务端账户、截断、每次施法预算 |
| 标记引爆/条件反应 | 状态所有者、期限、堆叠上限、消耗状态后触发第二阶段 |
| 幻象/召唤 | 所有者、行为周期、数量上限、退出/比赛清理，不能以大量无主实体凑特效 |
| 二次施放/回声 | 第一次记录坐标或目标，第二次在期限内转换机制、独立收尾 |
| 环境适应 | 湿润、燃烧、接地、昼夜等输入只由服务器读取；存在不满足条件时的明确结果 |
| 超位多阶段阵式 | 长预警、多锚点、分批落点、阶段推进、可中断/结束；不可直接变成无限全屏伤害 |

组合是减少重复基础设施，不是允许对 300 个法术用同一个“附近实体伤害 + 粒子”处理器。机制条目需要审计相同签名；研发菜单应显示法术自身说明，便于逐项试玩辨别。

### 获取、学习、模式与伴生物品

- 自然箱在真实战利品生成时只追加一次；用“是否出书”的总体概率与“出哪阶”的条件权重分开。高阶条件权重单调下降，超位非常低；空投另有更高高阶/超位权重，不能把自然箱表原样复用。
- 书明确携带法术 ID，右键学习；重复、非法 ID、3 槽满均不给予施法资格、不消耗。允许替换的机制要通过明确学习管理界面，不隐式忘掉已有术。
- 新模式只在开局给每位有效参赛者 1 本随机可学习书，优先普通低阶池，不直接绕过学习；比赛重置不能重复叠书或带入上场已学状态。
- 管理员 `/tlb dev` 能分页调出全部 300 本和药水/上限苹果等伴生物品。菜单预览不分配有效消耗凭据，领取生成有效实例；保持当前管理权限、满背包和点击防转移规则。
- 魔力药水恢复当前值，上限苹果提高最大值并有硬上限/比赛恢复策略；可补充清明药剂、遗忘墨水、魔力结晶、施法稳定护符等，但每种需有独立用途和实际服务器处理。

### 华丽效果仍要读得懂战斗

分四层制作：**吟唱符阵 → 运动核心/轨迹 → 命中层 → 收尾消散**。高阶增加原创几何、阶段和色彩变化，不只是增加同种粒子数量。普通术以清晰轮廓为主；超位用多环阵、螺旋、分叉、地面预警与延迟落点形成规模。视觉形状不得暗示超过真实碰撞/伤害范围。

建议效果事件携带世界、法术 ID、阶段、位置、方向、范围、开始 tick、随机种子；原生客户端据此生成插值与纹理表现，Paper 由服务器生成必要采样点。逻辑不从客户端粒子数量反推命中，超位特效降低客户端画质也不能改变结算。

预算起点可以是世界最多 128 个活动实例、每观察者每 tick 最多 128 个粒子采样、世界每 tick 最多 1024 个样本；这些只是待压测的配置建议。装饰特效先降频/远处 LOD，预警、核心和命中反馈优先保留；限定同世界和可见距离，普通烟雾不全服强制广播。音效也分施法/持续/命中阶段限频，持续法术不每 tick 播响一次。

### 验证必须覆盖真实链路

1. 目录校验恰好 280 普通 + 20 超位，ID/机制签名唯一、阶级权重单调、300 本都能开发者领取、无缺失资源。
2. 状态测试未学习拒绝、3 槽上限、重复不吞书、魔力不足不扣、各类冷却、药水截断、苹果硬上限、比赛重置与断线/死亡/换世界清理。
3. 世界测试真实自然战利品仅注入一次，人工箱不注入，空投高阶分布、模式开局每人一书、队伤/观察者/非目标不误伤。
4. 每个机制做墙体、极端距离、重复目标、过期与资源上限场景；射线、快飞弹和高阶多阶段必须测试实际原生/Paper伤害管线。
5. 原生按键必须在客户端测短按/长按、轮盘松开仅选、`V` 一次施法、`Alt`滚轮不换物品、改键及 `R` 换弹无误触；截图/录像核对预警、华丽程度和 HUD 遮挡。编译或服务端无报错不能替代这项真人客户端视觉验证。
