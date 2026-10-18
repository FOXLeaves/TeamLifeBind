# 魔法更新验证记录

## 2026-10-18 可见性与学派修复批次

本节覆盖最新构建，下方 2026-10-15 的测试数字和哈希保留为历史。Minecraft 26.3、Java 26、显示版本 0.1.2、内部构建 0.7.0 保持。

- 公共回归 **77,655 项断言**通过，日志 `.tools/magic-domains-final-compile.log`；其中触发/仪式/几何 17,842 项，学派轨迹与领域周期 250 项。
- `scripts/audit-magic-distinctness.py --fail-on-suppression` 确认 55 条目标附加规则无同 tick 多目标漏触发；自身回蓝的 1 条全局间隔规则单独保留。20 类投放方式中，没有继续全学派共用同一行进形状和运动的类别。该审计是源码定义与真实 Tracker 调用，不是世界运行或视觉验收。
- 三端生产 `MagicShaderRenderer.render` 各 **927 项检查**通过，分别使用相应加载器修补的 Minecraft 类，每端 **300/300** 法术有最终像素输出。深度遮挡、清理、过期、TTL、资源重载、远坐标稳定和关闭释放通过。旧绘制调用隔离对照稳定产生零像素并失败。入口 `scripts/test-magic-renderpearl.py`，日志 `.tools/magic-renderpearl-production/{platform}/render.log`。
- 生产 GLSL **3,966 项断言**通过，含 1,820 组跨学派同色比较、2,660 组同学派机制比较及 594 组阶段图样比较。日志 `.tools/magic-identity-production-final/gpu-test.log`；新预览、参数契约与性能边界见[渲染说明](magic-shader-rendering.md)。
- Fabric 真实服务器最终 **14,355 项断言**通过，执行 300 种法术，魔法粒子包 **0**、着色器场景/控制包 **9,815**。新增检查得到 **14 种不同的领域实体状态结果**，正常负载下各触发三次脉冲；墙体遮挡、施法者移到相反侧后的落点径向推离、岩领域停止水平移动并保留下压速度均通过。日志 `.tools/fabric-magic-domains-final-2026-10-18.log`，就绪、退出码和错误扫描正常。满动作队列的极限拥塞仍可能丢弃附加动作，这些次数不表示任何负载下保证执行。
- Paper 使用本批生产 JAR 的真实服务器回归 **20,313 项断言**通过，覆盖全部 300 法术，记录 1,045 次伤害事件；它继续使用粒子。日志 `.tools/paper-magic-probe/identity-final-2026-10-18.log`，退出码与项目错误扫描均为 0。测试 JAR SHA-256 为 `447a0b2dd4ff6c52f64a06a11ee1a4dee7410c995eb832c13221508d2e880e9a`，逐类记录 `.tools/paper-magic-probe/identity-final-tested-sha256.json`。Paper 没有重复执行 Fabric 的领域状态专项矩阵。

本次生产渲染测试经过真实 Minecraft 渲染 API 和生产类，仍是可控合成场景；多人游戏画面、其他显卡和外部光影包尚需客户端试玩。旧版裸 GLSL 成功曾漏过 Java 提交零顶点的错误，因此不能再用下方旧离屏结果推断该批游戏内可见。

更新日期：**2026-10-15**。环境为 Minecraft **26.3**、Java **26**，游戏显示 **0.1.2**、内部构建 **0.7.0**。日志文件名保留实际运行日期。本记录只描述已执行的检查；实际客户端画面与真人多人对局单独验收。

## 公共状态与四端构建

最终 Gradle 四端编译与公共回归已通过，公共层共 **76,298 项断言**，其中差异化触发、定时仪式与运动几何 **16,735 项断言**。记录为 `.tools/magic-picker-verified-compile2.log`。检查还覆盖单次命中法术不得配置无法触发的重复命中规则、汲取未命中不发放收尾奖励，以及无命中/条件命中/持续/收尾的实际执行轨迹。

300 条显式法术配置的机制指纹互不重复，比较触发事件、条件、目标、动作与超位阶段开收动作；不把名称、ID、学派或平衡数值算作独特机制。四阶段视觉配置也分别比较形状与运动；采样检查包含有限坐标、点数与半径限制。触发器还检查首次/重复命中、远近/空中/着地/潮湿/燃烧/受伤条件、次数和间隔限制、延迟动作及效果结束只能执行一次。

四端 JAR 均使用同一批共享规则和定义；发行包核对会比较所有实现类与当前构建结果。前批构建日志保留历史，不用旧哈希替代本批文件。

## Paper 真实服务器夹具

最新 Paper 26.3 build 175 实际玩家、Bukkit/NMS 实体与事件夹具通过 **20,307 项断言**。对全部 **300 种法术**运行世界执行路径，捕获 **1,039 次带攻击来源的伤害事件**与 **2,195,688 次粒子发送调用**。这些是测试场景累计数量，不是游戏每秒负载，也不能替代玩家看到的渲染结果。

覆盖内容包括：

- 300 本书与五件伴生物品的模型/PDC/学派颜色、16 分类到详情浏览、兼容七页目录、逐项领取、预览不可学习、拖拽与快捷移动保护、权限变化。
- 浏览不发物品、明确领取才发一件、背包满拒绝且没有地面掉落，临时三槽替换与 300 魔力、原知识/魔力/冷却恢复、临时记录不进入探索存档、退出/撤权/开局恢复。
- 三槽学习与消耗、重复与满槽处理、总魔典施法、右键/F/选择菜单、遗失总魔典补领、两种药水和上限苹果。
- 自然箱初次生成追加且不重复抽取，空投保留书/枪并必有药水，30 次随机开局仅发一阶书且不自动学习。
- 探索记录持久化、重连与对局隔离，待完成施法不复活；切槽或移动只停止当前吟唱/引导，已释放领域继续运行。
- 大厅玩家 PvP 拒绝、观察者清理效果、墙体与目标资格、300 项运行时、显式条件动作、超位开收动作与有序窗口、位移/护盾/学派互动及四阶段几何发送。
- 引导魔力耗尽会撤销整次仪式的后续阶段与收尾攻击，同时保留其他已释放领域；临时吸收护盾经原生效果 tick 后按时消失。

本轮使用最终 Gradle 生产 JAR 的日志：`.tools/paper-magic-probe/final-repairs-2026-10-15.log`，结果行含 `TLB_PAPER_MAGIC_PROBE PASS assertions=20307`，实例就绪、正常退出、退出码 0，项目错误扫描为 0。JAR 与所有实现类的校验记录位于 `.tools/paper-magic-probe/final-repairs-tested-sha256.json`。前一批生产 JAR 的夹具日志 `.tools/paper-magic-probe/production-2026-10-14.log`（20,158 项断言）及对应 `production-tested-sha256.json` 保留为历史；其中的旧文件哈希不代表本轮发行包。最终发行文件与实现类须重新核对。

## 原生加载器注入检查

Fabric、Forge、NeoForge 均使用对应平台真实 Minecraft 26.3 类加载与 Mixin 变换，验证自然战利品 `RandomizableContainer`、滚轮 `MouseHandler` 和 HUD 提取 `Hud` 及持书姿态 `FirstPersonHandsAndItemsRenderer` 四个目标。Fabric 28、Forge 26、NeoForge 26，合计 **80 项断言**通过，既检查目标签名也检查注入后的方法形态。日志位于 `.tools/magic-mixin-probe/transform-{fabric,forge,neoforge}.log`。

这些检查验证客户端代码能连接对应 26.3 挂点，不代表已经打开图形客户端观察轮盘和世界特效。

Fabric **着色器改造前**的实际服务器世界夹具通过 **11,830 项断言**，执行全部 **300 种法术**，累计记录 **794,152 次粒子发送调用**。覆盖 305 项开发物品、16 分类与详情、临时三槽及恢复、满背包拒领、200 个自然箱只抽一次、50 个空投、知识与对局隔离、墙体遮挡、穿透射线、飞矢、延迟回响、护盾反击、显式触发规则与四阶段几何，以及超位阶段共用瞄准区域。日志 `.tools/fabric-magic-picker-final-2026-10-15.log` 含 `TLB_FABRIC_MAGIC_PROBE PASS 11830`，实例正常退出。该日志保留为历史；当前模组魔法已经移除这条粒子路径，最新验证见下节。

Forge 与 NeoForge 使用各自正式服务端环境实际隔离启动，均到达就绪状态、正常退出且退出码为 0，项目启动/Mixin 错误扫描为 0。记录分别为 `.tools/magic-forge-boot.log` 与 `.tools/magic-neoforge-boot.log`（本批启动摘要 `.tools/magic-picker-{forge,neoforge}-boot-summary.log`）；这是实际加载与生命周期检查，没有把重复的世界战斗场景当作已经在这两个平台重跑。

Minecraft 26.3 实际物品客户端 Codec 通过 **171 项断言**，解码三个加载器的 18 个魔法物品入口并检查 24 条阶位分发路径，覆盖层叠符文与染色配置。日志 `.tools/client-magic-input-probe/model-codec.log` 含 `MAGIC_MODEL_CODEC_PASS`；资源解码成功仍不等同于屏幕渲染验收。

原生客户端输入与同步逻辑在三端各通过 **203 项断言**，合计 **609 项**，记录为 `.tools/client-magic-input-probe/input-{fabric,forge,neoforge}.log`。验证执行实际生产类与真实 PoseStack/JOML 数学，覆盖 8 种运动、平滑渐入渐出、左右手镜像、主副手、GUI 取消、世界切换及 renderer push/pop 平衡。新增第一人称吟唱抬书姿态由服务端读条状态驱动；这些检查不包含真人鼠标轮盘操作或实际画面展示。

## 原生魔法着色器

最终 Fabric 真实服务器夹具通过 **14,254 项断言**，再次执行全部 **300 种法术**，记录 **0 次魔法粒子发送、8,770 条着色器场景/控制包**。日志 `.tools/fabric-magic-shader-final-probe.log`，实例就绪、正常退出、退出码 0，项目错误扫描为 0。场景只传服务端决定的法术、阶段、起终点、半径与生命期，伤害、碰撞、学习和魔力的原测试继续通过。

新增检查覆盖 300×4 阶段的真实数据包编解码、包大小限制、无效输入、每 tick 96 条更新预算与下一 tick 恢复、同维度距离裁剪、跨维度不发送、取消清理、世界边界外的瞄准坐标裁剪，以及 NaN 视觉请求移除旧场景而不抛入战斗逻辑。中断引导以一条 `REMOVE_RUN` 清理对应仪式，同时保留其他已释放领域。最终生产 source、class 与内部类 SHA-256 记录在 `.tools/magic-shader-final-verification.json`，测试前后核对无变动。

三端真实 `GameRenderer` 再次进行 Mixin 变换与 ASM 校验，合计 **33 项断言**通过，检查实际投影捕获、世界渲染后与手部渲染前的挂点，以及资源重载、切世界、关闭时的生命周期注入。日志 `.tools/magic-shader-mixin-probe/transform-{fabric,forge,neoforge}.log`。Forge 和 NeoForge 的最终启动摘要为 `.tools/magic-shader-{forge,neoforge}-final-boot-summary.log`，均正常就绪/退出，退出码与错误扫描为 0；它们没有重复执行 Fabric 的 300 法术战斗夹具。

独立 GPU 夹具在 NVIDIA GeForce RTX 3050 Laptop GPU 上直接编译四份生产 GLSL，**623 项断言通过**，包含 176-byte uniform 契约、20 机制×4 阶段、12 种法阵、reversed-Z 的两种 clip 范围、全遮挡/半墙遮挡、辉光不涂到前景、相机在体积内外、生命周期与编译错误诊断。可复跑 `python scripts/test-magic-shaders-gpu.py`，日志 `.tools/magic-shader-probe/gpu-test.log`。图像、最终资源 SHA 和压力计时见[着色器说明](magic-shader-rendering.md)与[离屏验证范围](magic-shader-gpu-validation.md)。这些是实际 GPU 离屏结果，尚未替代 Minecraft 图形客户端和多人帧率验收。

## 资源与发行包

`scripts/verify-resources.py` 检查三个加载器的资源同步、双语名称与完整 300 项目录对应关系；魔法专项检查八阶范围分发、学派符文单独着色、五种伴生物品模型、14 张透明 PNG、三个可重绑键位与 HUD 文本，以及学习书没有合成配方。PNG 检查包括 CRC、解压长度、滤波还原及透明/可见像素，避免只验证文件名。本轮检查通过：119 个共享文件（含四份 GLSL）、253 个 JSON、342 个模组语言键、587 个公共语言键，并核对 schema 2 中 300 份行为结构与四阶段图样互不重复、单次命中机制没有不可达的重复命中规则。另检查原生魔法运行器不存在 `sendParticles`、`ParticleTypes` 或 `DustParticleOptions` 路径。

`scripts/verify-release-bundle.py` 进一步比较四端 JAR 中的公共/平台类、Java 字节码版本、Mixin 配置、魔法资源和资源包内容与当前构建；同时保留既有枪械、队伍床及道具检查。`scripts/ExportMagicCatalogue.java` 从实际共享定义导出 300 本目录与 schema 2 JSON，包含触发规则、超位阶段及四阶段视觉配置，避免手工抄写漏项。

## 真人客户端待验收

尚未完成实际图形客户端的三槽轮盘、改键后的松开选择、物品栏透明边缘、色盲/不同画质设置、高阶特效辨识，以及多人同时施法的帧率和网络负载试玩。安装后宜用两位普通玩家与一位管理员核对学习、友伤、空投竞争、吟唱打断和新赛制，再测试超位组合；自动夹具通过不等于平衡性已经定稿。
