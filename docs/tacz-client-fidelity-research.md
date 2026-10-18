# TACZ 七类武器的客户端表现研究

核验日期：2026-10-13，Asia/Hong_Kong。范围沿用 [选型研究](tacz-selection-research.md)，固定官方仓库提交 `b482eff8c94a733ac8d0910193fca3893954027c`，不混入第三方枪包或后续版本。读取七份公开枪械 data、七份 display 和七份动画的**名称、循环标志、时长**，以及展示字段默认值；新取得的 display / animation 元信息均比对固定 Git tree 的 blob SHA-1。没有移植模型点数组、纹理、动画关键帧、Lua 控制流程或音效。

本文件区分原包事实、当前原创实现和后续动作建议。七枪在正常对局中仅从空投获取；管理员可在 `/tlb dev` 领取独立生成的满弹武器用于开发测试，见[开发者物品目录](dev-item-menu.md)。枪械没有合成配方，全部无限备弹；普通枪保留有限弹匣，原生模组 Minigun 使用无限供弹和热量限制。模组默认 **左键开火、右键 ADS、R 换弹**，Paper 默认 **右键开火、潜行提高精度、F 换弹**，按键都可更改。原包伤害和全部动画时长不直接作为本项目战斗数值；当前精确规则见 [枪械玩法](airdrop-weapons.md)。

模组的枪械使用条件独立于 Paper：没有活动比赛时，存活非观察者玩家可在大厅用有效枪械测试，无需分队；进入投票或倒计时后暂停枪械使用，正式比赛仅存活参赛者可用。Paper 保留比赛内使用限制。客户端只按瞄准键提交瞄准状态，开火状态来自独立的攻击键。

短按攻击会在原版键位处理之前记录，服务端保留尚未结算的单次攻击，避免同 tick 内按下与松开、或 ADS 时原版消耗点击后漏射。这个修复保留原有左右键语义，右键不会代替攻击键发射；没有活动比赛时的服务器枪械时钟也继续推进，使大厅换弹和冷却能够完成。

## 外形辨识与动作特征

官方中文名称把 Taurus500 定义为重型手炮、SCAR-H 定义为战斗步枪、M95 定义为 `.50` 反器材步枪、Minigun 定义为 M134 转管机枪，其余为冲锋枪、多功能霰弹枪和火箭筒。这能确定类别与气质，不能证明本项目模型的具体尺寸或配色。[官方中文资源](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/lang/zh_cn.json)

| 参考枪 → 本项目 ID | 公开数据与动作名称能证明的辨识特征 | 原创模型与动作建议；不表示已经视觉核对 TACZ 原网格 |
| --- | --- | --- |
| Taurus500 → `battle_pistol` | 5 发、单发；有弹巢伸出、击锤和蓄力动作名称，适合明显的左轮机构。[数据][taurus-data]、[动作][taurus-anim] | 清晰的五孔弹巢、长厚枪管、后方击锤；射击抬口较大，换弹侧开弹巢再归位。 |
| Vector45 → `tactical_smg` | 20 发、闭膛、极快开火与 ADS；有单发、两发点射及自动模式的动作名称。[数据][vector-data]、[动作][vector-anim] | 紧凑枪身、厚实下机匣和直弹匣，保持独立轮廓；短促密集回弹，快速提枪。 |
| SCAR-H → `assault_rifle` | 20 发、闭膛、单发和自动；空仓与战术换弹有不同动作。[数据][scar-data]、[动作][scar-anim] | 较粗枪管、独立盒式弹匣、可辨识枪托与导轨；换弹顺序体现取匣、插匣，空仓再补一个上膛动作。 |
| SPAS-12 → `breach_shotgun` | 手动动作；存在泵动 / 半自动切换、独立拉动动作和装填进入 / 循环 / 退出动作。[数据][spas-data]、[动作][spas-anim] | 单根主枪管、平行供弹管、可滑动前护木和上方折叠架；把泵动回程与下一发就绪感做清楚。 |
| M95 → `marksman_rifle` | `.50` 反器材定位、5 发、手动拉栓；拉栓动作独立于射击与换弹。[数据][m95-data]、[动作][m95-anim] | 长粗枪管、后置弹匣的无托布局、可见拉栓把手；开火抬口后做往返拉栓，避免普通自动步枪的连续回弹感。 |
| Minigun → `support_machine_gun` | 转管枪，有持续 `spin`、进入 / 保持 / 退出瞄准和过热动作；第三人称指定 `minigun` 并锁定手臂。[展示][minigun-display]、[动作][minigun-anim] | 独立设计多管枪口、环架、电机壳和侧供弹结构，握姿偏持握重武器；转管状态、停转惯性和过热姿态应能被看见。 |
| RPG7 → `siege_launcher` | 单发火箭筒；空仓换弹、空仓检查与普通检查动作分开。[数据][rpg-data]、[动作][rpg-anim] | 管状主体、侧握把和外露火箭头；开火后隐藏装填头，重新装填完成再显示。 |

官方仓库提供七个库存预览图，可作为人工视觉复核入口：[Taurus500][taurus-image]、[Vector45][vector-image]、[SCAR-H][scar-image]、[SPAS-12][spas-image]、[M95][m95-image]、[Minigun][minigun-image]、[RPG7][rpg-image]。本次网页工具能确认图片页面，但图片获取返回超时 / robots 拒绝，因此**未声称实际看过这些图片或核对其精确配色、长度比例**。模型已按公开机构信息独立细化，具体尺寸与配色由本项目制作，见 [模型说明与预览](gun-models.md)。表中精细零件动作仍属于设计依据，不能视为全部已经完成的动画。

## ADS 与镜头

以下是未另装瞄具时的 display 参数和 gun data 的 `aim_time`。展示类默认 `iron_zoom = 1.2`、`zoom_model_fov = 70`、`show_crosshair = false`。`zoom_model_fov` 是**枪械模型的视场角**，不能把 33 或 45 当成镜片倍率，也不能直接替代世界镜头的 FOV。表中的进入时间是公开配置目标，不是已经测过真人客户端的实际过渡。[展示字段契约](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/client/resource/pojo/display/gun/GunDisplay.java)

| 枪 | 基础机瞄倍率 | 模型 FOV | 进入 ADS / 秒 | ADS 时保留准星 | 来源 |
| --- | ---: | ---: | ---: | --- | --- |
| Taurus500 | 2 | 45 | 0.18 | 否，默认 | [展示][taurus-display]、[数据][taurus-data] |
| Vector45 | 1.2，默认 | 70，默认 | 0.08 | 否，默认 | [展示][vector-display]、[数据][vector-data] |
| SCAR-H | 1.33 | 45 | 0.21 | 否，默认 | [展示][scar-display]、[数据][scar-data] |
| SPAS-12 | 1.5 | 70，默认 | 0.21 | 是 | [展示][spas-display]、[数据][spas-data] |
| M95 | 1.5 | 33 | 0.23 | 否，默认 | [展示][m95-display]、[数据][m95-data] |
| Minigun | 2 | 55 | 0.25 | 是 | [展示][minigun-display]、[数据][minigun-data] |
| RPG7 | 1.2，默认 | 70，默认 | 0.20 | 否，默认 | [展示][rpg-display]、[数据][rpg-data] |

M95 的此份基础 display 并没有写高倍率瞄准镜；不能以“狙击枪”类别自行声称默认 8× 或 12×。本轮原生模组已经把开火和 ADS 分为原版攻击、使用两个可重绑输入。独立的程序姿态系统以上表倍率、模型 FOV 和进入时间为输入，平滑改变持枪位置及两种视场角；M95 保留 1.5 倍铁瞄与配件导轨，没有默认镜片或高倍率遮罩。服务端接收开火与瞄准状态，决定实际散布、弹药及伤害；Paper 继续使用潜行精度适配。

## 射击与换弹节奏

`feed` 是数据中的补入时点，`cooldown` 是数据中的动作约束时长，动画文件的 `animation_length` 又是独立的视觉长度。它们并不总相等。客户端原创动作应跟随本项目服务器的开火、弹匣和换弹状态；视觉动作可以有准备和收尾，但不能提前在客户端补弹或允许射击。

| 枪 | 原包开火模式与节奏事实 | 基础 data 的战术 / 空仓换弹：feed；cooldown（秒） | 原包对应动画长度（秒） | 来源 |
| --- | --- | --- | --- | --- |
| Taurus500 | `semi`；120 RPM；蓄力上限与阈值均 0.5，每 tick 加 0.1，理论 5 tick；冷却期间不能蓄力。 | 两种均 2.28；2.72 | 两种均 3.0833；蓄力进入 / 退出各 0.25；击锤 0.4833。 | [数据][taurus-data]、[动作][taurus-anim] |
| Vector45 | `auto`、`burst`、`semi`；自动 1200 RPM；点射 2 发、900 BPM、最短间隔 0.3。 | 战术 1.67；2.2。空仓 2.03；2.47。另有公开 `script_param` 基础 cooldown 2.53 / 2.76。 | 战术 2.5333、空仓 2.7667；与上述 script 参数接近，不能只用旧基础 cooldown 推断视觉结束。 | [数据][vector-data]、[动作][vector-anim] |
| SCAR-H | `semi`、`auto`；570 RPM。 | 战术 1.2；2.03。空仓 2.05；2.53。 | 战术 2.3333、空仓 2.8667。 | [数据][scar-data]、[动作][scar-anim] |
| SPAS-12 | 数据枚举为 `semi`、`burst`；后者 count=1、180 BPM、最短间隔 0.3，不能当成多发点射。200 RPM 之外还有 0.53 秒手动动作参数。 | 本枪采用分阶段参数：进入 0.6、逐发循环 0.65、退出 0.6；空仓进入 2.43，另一个姿态空仓进入 1.9。基础 cooldown 的 0.67 / 0.23 不能代表装满整枪时间。 | 进入 0.7；普通 / 半自动装填循环 0.6667 / 0.8667；退出 0.8667；空仓进入 2.4333 / 1.8667；拉动 0.6。 | [数据][spas-data]、[动作][spas-anim] |
| M95 | `semi`；151 RPM 面板外另有 1.43 秒拉栓，不宜把 RPM 当实际重复射速。 | 战术 2.8；4.07。空仓 4.93；5.23。 | 战术 4.5、空仓 5.6；拉栓 1.7；公开展示参数的拉栓抛壳时点 0.5。 | [数据][m95-data]、[展示][m95-display]、[动作][m95-anim] |
| Minigun | `auto`、`burst`；1200 RPM；点射为可持续 6 发、1200 BPM、最短间隔 0.5。 | `inventory` 供弹，没有 `ammo_amount`。data 留有战术 1.55；2.0、空仓 2.25；2.6 参数，不能据此声称原包有普通弹匣换弹流程。 | 没有普通 `reload_tactical` / `reload_empty` 动画；循环转管 1.0、过热 2.6、进入 / 退出瞄准各 0.1。 | [数据][minigun-data]、[动作][minigun-anim] |
| RPG7 | `semi`；单发容量；150 RPM 不覆盖 3.2 秒装填约束。 | 两种均 2.63；3.2。 | 空仓换弹 3.53333；射击 0.76667。 | [数据][rpg-data]、[动作][rpg-anim] |

表格仅包含基础枪型。Vector45 和 SCAR-H 还有扩容弹匣专用时长，本轮没有配件系统。SPAS-12 的物理模式有独立泵动 / 半自动动作命名，但仅凭 data 的枚举与动画名字不能完整证明脚本如何映射两者，因此当前采用独立的单发泵动规则。Vector45 与 SCAR-H 使用自动模式，没有加入原包的模式切换。

本项目原生规则以 20 tick/s 量化这些节奏：Vector45 为 20 发、战术 / 空仓换弹 51 / 56 tick；SCAR-H 为 41 / 51 tick；M95 为 29 tick 射击间隔、82 / 105 tick 换弹；RPG7 换弹 64 tick。Taurus500 保留 5 tick 蓄力，射后冷却 10 tick，冷却期间不能提前蓄力，换弹 55 tick。原生 SPAS-12 改为五发逐颗装填：有弹 / 空仓进入 12 / 49 tick，每颗 13 tick，收尾 12 tick；有已装入弹药时能打断并开火，余弹由服务器保存。这些是当前项目实现，不是上表原包参数逐字一致的声明。Paper 的数值保留在 [枪械玩法](airdrop-weapons.md) 的独立表格中。

## Minigun 的起转与热量

原包具有单独转管循环，瞄准进入 / 退出动作、转管启动 / 停止的公开音效资源名称、过热动作和专用第三人称姿态。这支持还原“启动后持续旋转、松开后减速、过热时中断并冷却”的可见状态。**1 秒 `spin` 是循环动画长度，0.25 秒 `aim_time` 是瞄准时间，均不等同于已核实的起转开火延迟**；此研究不读取并复刻 Lua 转管控制流程。[展示][minigun-display]、[动作][minigun-anim]、[公开音效目录](https://github.com/MCModderAnchor/TACZ/tree/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/tacz_sounds/minigun)

热量数据为上限 500、每发增加 2、冷却倍率 15、冷却延迟 5 ms、完全过热等待 2500 ms；热量进度还影响不准确度倍率 1→1.2 和 RPM 修正倍率 1→1.2。它不是本项目现有热量刻度与固定射速的同一规则。客户端应显示**服务器给出的本项目热量百分比**，并以此驱动原创警示色与姿态，不能擅自按原包热量重算是否开火。[热量数据][minigun-data]

本轮原生 Minigun 改为无限供弹，不扣减有限弹匣，也不进入普通换弹；换弹键提示无需换弹。服务端仍保留本项目的 8 tick 起转、每发 2 点热量、100 点过热、非射击 tick 冷却 0.7 点及 40 点解锁阈值。客户端根据服务端状态驱动转速、停转惯性和热量姿态，渲染时从独立生成的 16 个转管相位中选择一个；固定枪体保持静止。转管相位仅应用于本地玩家持枪，物品栏、掉落物和 Paper 回退静态基础模型。Paper 保留 120 发弹仓与 96 tick 换弹，这是本项目的平台适配规则，不是 TACZ 原包的 Minigun 换弹流程。

## 本轮实现与后续检查

1. **服务端确认输入与反馈。** 原生客户端提交当前枪械序号、开火与瞄准状态，R 只提交换弹请求；服务端检查持枪、玩家资格及输入时效，返回射击序号、弹匣、换弹剩余时间和热量。只有确认的新发射序号会触发一次镜头及持枪后坐力。换枪、离线、失去资格和停止比赛会清除相应状态。
2. **独立程序化持枪。** 七枪分别使用平滑提枪、两种视场角变化、呼吸晃动、不同强度的后坐力和换弹姿态；SPAS-12 与 M95 加入泵动 / 拉栓节奏。姿态通过整把枪及手部变换计算，没有复用 TACZ 的曲线或关键帧，也没有把这些变换描述为逐零件拆装动画。
3. **特殊供弹和装填。** 原生 SPAS-12 已实现服务器逐颗装弹、保留部分余弹及开火打断；原生 Minigun 已实现无限供弹、热量、16 相位转管与惯性停转。普通枪区分相应战术和空仓装填时间，所有弹药结算均由服务端决定。
4. **原创资源与声音。** 七把基础模型、战术材质及转管帧独立制作；七种射击音色和六种机构音效独立合成，没有导入第三方录音。模组按确认射击和装填 / 拉栓 / 转管状态播放；Paper 使用同一资源包的模型、射击声与换弹开始 / 完成声。制作记录见 [原创模型](gun-models.md)与[原创音效](gun-sounds.md)。
5. **精细动作后续项。** Taurus 弹巢侧开和击锤零件运动、SCAR / Vector 取匣与插匣、SPAS 护木零件滑动、M95 枪栓零件移动，以及 RPG 装填头随弹药显示 / 隐藏仍可追加。配件系统、火力模式切换、专用第三人称骨骼姿态和镜片渲染也尚未加入。这些需要独立建模与状态接线。
6. **逐端真人检查。** Fabric、Forge、NeoForge 分别检查左键按住 / 松开、只按右键 ADS、R 重绑、第一 / 第三人称与左右手、GUI 打开、死亡和断线取消；Paper 检查右键、潜行及原版副手键重绑。复核武器占屏、镜头晕动、转管渲染、连续射击混音和网络延迟。检查结果见 [枪械验证](gun-validation.md)，服务器夹具和原生 JSON 解析不能代替真人客户端验收。

前四项描述本轮代码与资源实现，第五项为后续精细化工作，第六项为待进行的真人客户端验收。七模型、镜头、机构姿态与声音的体验及还原度需要在原生客户端实际显示、操作和试听后评估；没有声称与 TACZ 全部功能或原网格一比一相同。

[taurus-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/taurus500_data.json
[vector-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/vector45_data.json
[scar-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/scar_h_data.json
[spas-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/spas_12_data.json
[m95-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m95_data.json
[minigun-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/minigun_data.json
[rpg-data]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/rpg7_data.json
[taurus-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/taurus500_display.json
[vector-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/vector45_display.json
[scar-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/scar_h_display.json
[spas-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/spas_12_display.json
[m95-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/m95_display.json
[minigun-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/minigun_display.json
[rpg-display]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/rpg7_display.json
[taurus-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/taurus500.animation.json
[vector-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/vector45.animation.json
[scar-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/scar_h.animation.json
[spas-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/spas_12.animation.json
[m95-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/m95.animation.json
[minigun-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/minigun.animation.json
[rpg-anim]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/rpg7.animation.json
[taurus-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/taurus500.png
[vector-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/vector45.png
[scar-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/scar_h.png
[spas-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/spas_12.png
[m95-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/m95.png
[minigun-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/minigun.png
[rpg-image]: https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/textures/gun/slot/rpg7.png
