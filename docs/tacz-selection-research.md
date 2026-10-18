# TACZ 默认枪包选型研究

核验日期：2026-10-12。研究对象是用户指定的 MCModderAnchor/TACZ 官方仓库 `1.20.1` 分支快照，提交 `b482eff8c94a733ac8d0910193fca3893954027c`。这里只提取枪械分类、参数事实和玩法定位；产品实现、模型、材质、音效由 TeamLifeBind 独立制作。TACZ 的 README 指定 Forge 1.20.1，不能作为本项目 Minecraft 26.3 / Java 26 的直接依赖。[官方 README](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/readme.md)

## 分类与范围

官方 `GunTabType` 定义七类：`pistol`、`sniper`、`rifle`、`shotgun`、`smg`、`rpg`、`mg`。精确射手枪和战斗步枪包含在 `rifle` 中，榴弹发射器包含在 `rpg` 中，没有另外两个独立分类。[分类枚举](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/api/item/GunTabType.java)

本次读取默认枪包全部 54 个枪械 index 及对应 data，数量为：手枪 14、狙击枪 6、步枪 17、霰弹枪 6、冲锋枪 5、发射器 2、机枪 4。这不包含第三方枪包或配件改装。[默认索引目录](https://github.com/MCModderAnchor/TACZ/tree/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/guns)

`deagle_golden` 与 `lonetrail` 均在正式默认 index 中，分别属于 `pistol` 并有正常排序；索引没有调试专用标记。本次把它们计入基础数据比较，二者均未超过 Taurus500 的单发伤害和穿甲数值。[金色 Deagle 索引](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/guns/deagle_golden.json)、[Lonetrail 索引](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/index/guns/lonetrail.json)

## “最强”的评价口径

本项目采用“同类中最鲜明的高威力空投定位”，对精确武器优先单发、爆头和穿甲；对自动武器优先基础近距输出；对发射器优先单次爆炸伤害。这是根据参数作出的设计选择，不表示这些枪在所有距离、装甲、配件、操作水平和持续射击场景中都获胜。

下表是参考枪包的原始参数，用于确定定位，不是 TeamLifeBind 的最终伤害数值。伤害单位为 Minecraft health point（2 点为一颗心）；穿甲列为 `armor_ignore` 参数，不代表无视全部防御机制。默认服务器倍率、实际命中和动画会影响实战。

| 分类 | 推荐参考 | 基础近距整发伤害 | 弹丸数 | 面板 RPM | 爆头倍率 | 穿甲参数 | 默认容量 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 手枪 | [taurus500](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/taurus500_data.json) | 40 | 1 | 120 | 2 | 0.5 | 5 |
| 狙击枪 | [m95](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m95_data.json) | 75 | 1 | 151 | 2.5 | 0.75 | 5 |
| 步枪 | [scar_h](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/scar_h_data.json) | 14 | 1 | 570 | 1.5 | 0.5 | 20 |
| 霰弹枪 | [spas_12](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/spas_12_data.json) | 64 | 8 | 200 | 1.33 | 0.25 | 5 |
| 冲锋枪 | [vector45](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/vector45_data.json) | 7 | 1 | 1200 | 1.25 | 0.2 | 20 |
| 机枪 | [minigun](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/minigun_data.json) | 8 | 1 | 1200 | 1.5 | 0.5 | 库存供弹，无 ammo_amount |
| 发射器 | [rpg7](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/rpg7_data.json) | 20 | 1 | 150 | 1 | 0 | 1 |

## 七个选择及主要对手

- **手枪：Taurus500。** 40 点基础近距伤害、2 倍爆头、0.5 穿甲和 5 发容量，单发威力明显高于 Deagle 的 16 点和 Lonetrail 距离曲线的 24 点。它不是射速冠军：B93R 的 900 RPM 是高上限，但三发点射另有 0.4 秒间隔；Taurus500 每发还有蓄力，进度 0.5 / 每 tick 增加 0.1，理论 5 tick，并且射击冷却期间不能蓄力。选择重型手炮定位，而不是宣称它持续 DPS 第一。其伤害阶段为 40 / 55 / 75 格对应 40 / 30 / 20 点，之后为 15 点，空仓 reload cooldown 为 2.72 秒。[Taurus500](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/taurus500_data.json)、[Deagle](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/deagle_data.json)、[Lonetrail](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/lonetrail_data.json)、[B93R](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/b93r_data.json)
- **狙击枪：M95。** 75 点、2.5 倍爆头、0.75 穿甲均为本组最高，75 / 150 格阶段伤害为 75 / 65 点，之后为 50 点。M107 的近距曲线为 50 点，面板 RPM 400，比 M95 的 151 高，因此 M107 的 RPM 伤害上限更高。M95 有 1.43 秒 `bolt_action_time` 和 5.23 秒空仓 cooldown，不能把面板 RPM 当作实际拉栓射速。选择远距精准单发上限和反装甲定位。[M95](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m95_data.json)、[M107](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m107_data.json)
- **步枪：SCAR-H。** 14 点 × 570 / 60 = **133 点/秒**基础近距 RPM 上限，是默认步枪组最高；20 发、0.5 穿甲，35 / 85 格阶段伤害为 14 / 11 点，之后为 9 点。MK14 单发 16 点、1.75 倍爆头更高，但只有 300 RPM；HK416D 射速 943 RPM 更高，但伤害为 6.5。SCAR-H 用 2.53 秒空仓 cooldown 计算的无限重复弹匣理想循环约 60.4 点/秒；这忽略瞄准、后坐力和命中损失。[SCAR-H](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/scar_h_data.json)、[MK14](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/mk14_data.json)、[HK416D](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/hk416d_data.json)
- **霰弹枪：SPAS-12。** 基础 64 点是**整发总伤害**，由 8 枚散弹分摊，不是每枚 64 点。15 / 30 格阶段伤害为 64 / 40 点，之后为 24 点；200 RPM 的无动作约束上限为 213.3 点/秒。AA12 为总伤害 30 / 350 RPM，理想近距上限 175 点/秒，但支持全自动。SPAS12 的脚本参数含 0.53 秒拉栓、0.65 秒逐发装填循环和进出装填阶段，不能把 `.67` cooldown 误当整匣换弹时间；其另一 fire mode 还降低伤害和穿甲。本项目选重型泵动爆发定位。[SPAS-12](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/spas_12_data.json)、[AA12](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/aa12_data.json)
- **冲锋枪：Vector45。** 7 点 × 1200 / 60 = **140 点/秒**基础近距上限，20 发容量，2.47 秒空仓 cooldown；15 / 36 / 50 格阶段为 7 / 5.5 / 4.5 点，之后为 3.5 点。无限重复弹匣理想循环约 40.3 点/秒；UMP45 的 25 发、9 点、660 RPM、2.87 秒 cooldown 对应约 43.8 点/秒，持续输出更好，P90 的 0.7 穿甲也更高。本项目选近距快速突击定位。[Vector45](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/vector45_data.json)、[UMP45](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/ump45_data.json)、[P90](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/p90_data.json)
- **机枪：Minigun。** 8 点 × 1200 / 60 = **160 点/秒**基础近距上限，0.5 穿甲，比 FN EVOLYS 的 12 点 × 750 / 60 = 150 点/秒略高；50 / 95 格阶段为 8 / 7 点，之后为 6 点。它采用库存供弹和 heat，未配置普通 `ammo_amount`，因此不存在可直接套公式的默认弹匣容量。heat 上限 500、每发 2、冷却倍率 15、过热等待 2500 ms，射速和散布还会受热量修正；不可把基线 160 当作恒定持续 DPS。FN EVOLYS 的 75 发和 6.69 秒 cooldown 理想循环约 70.9 点/秒，但与热量枪并非同一供弹模型。本项目采用独立弹匣规则保留转管压制定位。[Minigun](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/minigun_data.json)、[FN EVOLYS](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/fn_evolys_data.json)
- **发射器：RPG7。** 原数据为 20 点直击、120 点爆炸、半径 3；M320 是 10 点直击、50 点爆炸、半径 6。选 RPG7 的单次高伤爆发，承认 M320 覆盖更广。RPG7 为 1 发、3.2 秒空仓 cooldown，150 RPM 不代表它能每秒稳定射出 2.5 发。爆炸伤害并非所有半径内目标恒定 120 点，也不能简单把直击和爆炸相加当作每次命中的保证伤害。[RPG7](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/rpg7_data.json)、[M320](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m320_data.json)

## 数值比较方法与局限

`damage_adjust` 提供距离伤害时，比较近距值使用首段伤害，不能只读 `bullet.damage`；例如 M107 为面板 55、首段 50，M249 为面板 7.5、首段 7，M16A1 为面板 8、首段 8.5。官方子弹契约将一发霰弹伤害分摊给各弹丸，因此 RPM 上限应为“整发近距伤害 × RPM / 60”，**不再乘弹丸数**。这仅用来校准数据字段语义，本项目射击流程独立编写。[M107](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m107_data.json)、[M249](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m249_data.json)、[M16A1](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m16a1_data.json)、[官方霰弹伤害语义](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/entity/EntityKineticBullet.java)

简单的全自动普通弹匣循环估计采用：`整发近距伤害 × 弹匣数 / (弹匣数 × 60 / RPM + 空仓 reload cooldown)`。这按每个完整循环包含首发到下一匣首发间的所有间隔计算，是理想无操作损失估计。点射间隔、蓄力、拉栓、Lua 参数化逐发装填、热量和库存供弹均不能直接套用此公式。本研究不读取或移植 Lua 控制流程，也不复用子弹实体实现。

距离曲线末段的 `infinite` 不是枪械保证能射到无限远；子弹 life、speed、gravity、friction 与遮挡决定实际飞行结果。表中的第一距离阶段用于比较威力定位，不作为本项目最终射程。

## TeamLifeBind 的落地约束

以下为用户确认的本项目要求及独立设计边界：

1. 每类实现一把，共七把；采用新的 `teamlifebind` 物品 ID、原创命名、独立战斗状态机。
2. 正常游戏获取仅来自空投，不添加 crafting / smithing / 工作台枪械配方，不放进据点奖励、初始装备或公开调试物品菜单。
3. 无限备弹保留有限弹匣及换弹时间；无需子弹物品，不消耗背包材料。Minigun 也适配本项目的有限弹匣，而不照搬原库存供弹和 heat 实现。
4. 伤害、射速、爆头、散布、换弹、射程等按团队共命 PvP 重新配置；参考表并不要求复制原始数值。范围伤害遵从已有队友保护、旁观者/淘汰者保护和比赛状态。
5. 模型、材质和声音独立制作；使用项目原生 Minecraft JSON 模型和自己的造型语言，保留分类辨识度。本项目不打包 TACZ 的代码、Lua、模型、贴图、动画或声音。

原仓库 README 标明代码 GPL 3.0、assets CC BY-NC-ND 4.0。本项目选择独立编写和原创资源，外部名称只用于此研究的来源识别，不声称 TACZ 原作者制作或参与本项目。[官方资源声明](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/readme.md)

## 完整基础比较表

下表按分类及首段近距 RPM 上限排序，供后续复核；它不把爆炸伤害、点射间隔、动作时间、热量、配件和命中率折算进“上限”。发射器的上限列只计算直击基线，不用于 RPG7 / M320 的最终选型。每行链接指向固定提交中的对应原数据。

| 分类 | 枪械数据 | 近距整发伤害 | RPM | RPM 伤害上限/秒 | 爆头 | 穿甲 | 默认容量 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 机枪 | [minigun](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/minigun_data.json) | 8 | 1200 | 160.0 | 1.5 | 0.5 | — |
| 机枪 | [fn_evolys](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/fn_evolys_data.json) | 12 | 750 | 150.0 | 1.6 | 0.4 | 75 |
| 机枪 | [rpk](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/rpk_data.json) | 10 | 630 | 105.0 | 1.5 | 0.3 | 40 |
| 机枪 | [m249](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m249_data.json) | 7 | 750 | 87.5 | 1.5 | 0.3 | 75 |
| 手枪 | [b93r](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/b93r_data.json) | 7.5 | 900 | 112.5 | 1.25 | 0.2 | 20 |
| 手枪 | [deagle](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/deagle_data.json) | 16 | 300 | 80.0 | 1.75 | 0.25 | 7 |
| 手枪 | [taurus500](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/taurus500_data.json) | 40 | 120 | 80.0 | 2 | 0.5 | 5 |
| 手枪 | [timeless50](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/timeless50_data.json) | 16 | 300 | 80.0 | 1.5 | 0.25 | 8 |
| 手枪 | [cz75](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/cz75_data.json) | 5 | 900 | 75.0 | 1.3 | 0.2 | 16 |
| 手枪 | [p320](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/p320_data.json) | 10 | 450 | 75.0 | 1.75 | 0.2 | 12 |
| 手枪 | [deagle_golden](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/deagle_golden_data.json) | 12 | 350 | 70.0 | 1.8 | 0.2 | 9 |
| 手枪 | [m1911](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m1911_data.json) | 11 | 350 | 64.2 | 1.5 | 0.2 | 7 |
| 手枪 | [glock_17](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/glock_17_data.json) | 7 | 400 | 46.7 | 1.5 | 0 | 17 |
| 手枪 | [m9a4](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m9a4_data.json) | 7 | 400 | 46.7 | 1.5 | 0 | 17 |
| 手枪 | [lonetrail](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/lonetrail_data.json) | 24 | 90 | 36.0 | 1.75 | 0.4 | 1 |
| 手枪 | [rhino357](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/rhino357_data.json) | 10.5 | 200 | 35.0 | 1.75 | 0.3 | 6 |
| 手枪 | [taurus943](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/taurus943_data.json) | 6 | 180 | 18.0 | 1.5 | 0.25 | 8 |
| 手枪 | [hk_mk23](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/hk_mk23_data.json) | 12 | 50 | 10.0 | 1.75 | 0.2 | 12 |
| 步枪 | [scar_h](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/scar_h_data.json) | 14 | 570 | 133.0 | 1.5 | 0.5 | 20 |
| 步枪 | [spr15hb](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/spr15hb_data.json) | 10 | 700 | 116.7 | 1.75 | 0.3 | 15 |
| 步枪 | [m16a1](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m16a1_data.json) | 8.5 | 750 | 106.2 | 1.5 | 0.25 | 20 |
| 步枪 | [hk416d](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/hk416d_data.json) | 6.5 | 943 | 102.2 | 1.5 | 0.2 | 30 |
| 步枪 | [type_81](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/type_81_data.json) | 9 | 630 | 94.5 | 1.5 | 0.2 | 30 |
| 步枪 | [qbz_191](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/qbz_191_data.json) | 7.5 | 750 | 93.8 | 1.5 | 0.4 | 30 |
| 步枪 | [sks_tactical](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/sks_tactical_data.json) | 11 | 510 | 93.5 | 2 | 0.25 | 10 |
| 步枪 | [g36k](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/g36k_data.json) | 7 | 780 | 91.0 | 1.5 | 0.2 | 30 |
| 步枪 | [ak47](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/ak47_data.json) | 9 | 600 | 90.0 | 1.5 | 0.25 | 30 |
| 步枪 | [m4a1](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m4a1_data.json) | 6.5 | 810 | 87.8 | 1.5 | 0.2 | 30 |
| 步枪 | [aug](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/aug_data.json) | 7 | 710 | 82.8 | 1.5 | 0.25 | 30 |
| 步枪 | [qbz_95](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/qbz_95_data.json) | 7.5 | 660 | 82.5 | 1.5 | 0.4 | 30 |
| 步枪 | [scar_l](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/scar_l_data.json) | 7.5 | 650 | 81.2 | 1.75 | 0.25 | 30 |
| 步枪 | [mk14](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/mk14_data.json) | 16 | 300 | 80.0 | 1.75 | 0.5 | 10 |
| 步枪 | [fn_fal](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/fn_fal_data.json) | 13 | 350 | 75.8 | 1.8 | 0.4 | 20 |
| 步枪 | [hk_g3](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/hk_g3_data.json) | 12 | 350 | 70.0 | 1.5 | 0.5 | 20 |
| 步枪 | [m16a4](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m16a4_data.json) | 8 | 400 | 53.3 | 1.35 | 0.25 | 30 |
| 发射器 | [rpg7](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/rpg7_data.json) | 20 | 150 | 50.0 | 1 | 0 | 1 |
| 发射器 | [m320](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m320_data.json) | 10 | 150 | 25.0 | 1 | 0 | 1 |
| 霰弹枪 | [spas_12](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/spas_12_data.json) | 64 | 200 | 213.3 | 1.33 | 0.25 | 5 |
| 霰弹枪 | [aa12](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/aa12_data.json) | 30 | 350 | 175.0 | 1.33 | 0 | 8 |
| 霰弹枪 | [m1014](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m1014_data.json) | 40 | 200 | 133.3 | 1.33 | 0.25 | 6 |
| 霰弹枪 | [m870](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m870_data.json) | 36 | 180 | 108.0 | 1.33 | 0.25 | 5 |
| 霰弹枪 | [db_short](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/db_short_data.json) | 24 | 150 | 60.0 | 1.25 | 0.33 | 2 |
| 霰弹枪 | [db_long](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/db_long_data.json) | 30 | 100 | 50.0 | 1.2 | 0.33 | 2 |
| 冲锋枪 | [vector45](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/vector45_data.json) | 7 | 1200 | 140.0 | 1.25 | 0.2 | 20 |
| 冲锋枪 | [ump45](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/ump45_data.json) | 9 | 660 | 99.0 | 1.5 | 0.2 | 25 |
| 冲锋枪 | [hk_mp5a5](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/hk_mp5a5_data.json) | 6 | 820 | 82.0 | 1.25 | 0.15 | 30 |
| 冲锋枪 | [p90](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/p90_data.json) | 5.5 | 810 | 74.2 | 1.25 | 0.7 | 50 |
| 冲锋枪 | [uzi](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/uzi_data.json) | 6.5 | 600 | 65.0 | 1.25 | 0.15 | 20 |
| 狙击枪 | [m107](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m107_data.json) | 50 | 400 | 333.3 | 1.5 | 0.5 | 10 |
| 狙击枪 | [m95](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m95_data.json) | 75 | 151 | 188.8 | 2.5 | 0.75 | 5 |
| 狙击枪 | [ai_awp](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/ai_awp_data.json) | 42 | 171 | 119.7 | 2 | 0.6 | 5 |
| 狙击枪 | [kar98](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/kar98_data.json) | 26 | 250 | 108.3 | 1.85 | 0.4 | 4 |
| 狙击枪 | [m700](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m700_data.json) | 24 | 180 | 72.0 | 2 | 0.5 | 5 |
| 狙击枪 | [springfield1873](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/springfield1873_data.json) | 35 | 90 | 52.5 | 1.5 | 0.25 | 1 |

研究缓存保存在忽略目录 `.tools/tacz-reference/`：固定 tree、原始 index/data、字段摘要、计算脚本。缓存用于研究复核，不进入四端 JAR、服务端资源包或发布 ZIP。
