# Web Motion Showcase 源码学习与 AUI 适配

来源：[展示站](https://holynova.github.io/web-motion-showcase/?lang=zh&theme=slate&mode=dark)、[动效实现](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js)、[详情样式](https://github.com/holynova/web-motion-showcase/blob/master/detail.css)、[沙盒生命周期](https://github.com/holynova/web-motion-showcase/blob/master/detail.js)。抓取和核对日期为 2026-11-14。

目录的实际数据是 **60 项**。本次按每项 `render` 函数及对应预览／详情 CSS 整理触发、状态和可迁移部分，并检查事件与清理路径；没有将源码分析写成 60 次浏览器实测。原始文件的 SHA-256 和静态 API 信号保存在 [机器可读目录](web-motion-catalog.json)。

## 对实现的核对

标签指示器示例的说明提到物理弹簧，实际代码是读取按钮位置／宽度，并采用 380ms 的过冲贝塞尔过渡。独立的 spring-motion 才保存位置与速度并积分弹簧力。项目的标签指示器采用 180ms、无过冲的过渡，避免切换设置时反复晃动。

详情页在重新播放之前分发 cleanup；对应的监听器、观察器、定时器和动画帧需要一并释放。AUI 没有浏览器脚本运行时，因此不能直接搬入 IntersectionObserver、Canvas、requestAnimationFrame 或页面全局监听。交互由 Java 控制，样式过渡由 AUI CSS 处理，流体过场由现有 GPU 渲染器处理。

本轮实际接入：创建世界标签指示线、目录轻抬与按压反馈、侧栏下划线、列表阅读进度及卷动边界提示。原有局部颜色反馈和 UI 纹理过场继续复用。交互立即交给原控制器；关闭使用 180ms UI 纹理退场，开关动效仍由同一配置控制。搜索已按用户要求移除，保留分类与分页。

正文、生命、魔力及真实加工进度不使用打字、乱序或数字追赶效果。初始资源未就绪时保留 GPU 加载进度，原生字库可用后再显示加载文字，HTML 页面还要等待字体和样式发布。

## 逐项学习与落点

以下“可用于／适合”表示适配建议；“已采用／已具备”才表示当前已有实现。背景类效果依照透明游戏画面的要求处理。

| # | 动效 | 触发分类 | 机制与项目落点 |
| --- | --- | --- | --- |
| 1 | [淡入上移](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L31) | 进入 | 透明度与小幅位移联合入场；适合一次性的章节介绍，正文必须先等待字体。 |
| 2 | [滚动显现](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L52) | 进入 | 观察可见区域后切换显现状态；长目录需要按真实滚动容器计算，不能使用游戏镜头。 |
| 3 | [悬停上浮](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L101) | 悬停 | 已采用：主菜单与目录卡片轻微上浮；按键和物品槽保持稳定的点击目标。 |
| 4 | [悬停阴影增强](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L122) | 悬停 | 以阴影变化表达层次；局部面板可以使用，密集物品格避免逐格大面积阴影。 |
| 5 | [颜色过渡](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L142) | 反馈 | 已采用：按钮与分类的背景、边界颜色平滑切换，状态和动作立即生效。 |
| 6 | [下划线展开](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L162) | 布局 | 已采用：侧栏分类用横向缩放的细线表达悬停及选中。 |
| 7 | [滚动进度条](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L182) | 滚动 | 已采用：阅读线绑定内容区的实际 scrollTop 与可滚动长度，和烧炼进度分开。 |
| 8 | [头部阴影渐变](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L220) | 滚动 | 已采用等效边界提示：内容离开顶部时强调工具区分隔线，明确固定区与滚动区。 |
| 9 | [按钮点击波纹](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L262) | 反馈 | 原理是从点击坐标扩张并淡出；本轮采用更短的按压缩放，波纹可用于一次性确认。 |
| 10 | [滑动指示器分段标签页](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L301) | 反馈 | 已采用：创建世界的指示线读取真实按钮边界，以 180ms 平移和变宽；不重建指示器。 |
| 11 | [图片轻微放大](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L356) | 图片 | 在固定裁切区放大图像；适合详情插图，不改变背包物品格的交互尺寸。 |
| 12 | [数字滚动](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L375) | 反馈 | 按渲染时钟插值展示数值；只适合结算统计，生命、魔力和实际任务进度始终显示实时值。 |
| 13 | [光标聚光灯卡片](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L428) | 悬停 | 将局部指针坐标映射到径向光照；可用于少量重点卡片，避免持续重绘整个界面。 |
| 14 | [文字逐行显现](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L554) | 进入 | 用裁切容器显现逐行标题；适合章节标题，避免延迟操作说明的可读时间。 |
| 15 | [暗色模式切换](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L574) | 反馈 | 颜色与图标同步过渡；后续主题切换应在资源就绪后一次提交，避免启动闪白。 |
| 16 | [网格重排动画](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L643) | 布局 | 筛选状态驱动透明度和位置；当前分类有独立 GPU 内容转场，文字搜索已按要求移除。 |
| 17 | [便当网格交错入场](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L693) | 布局 | 按视觉顺序错峰显现不等尺寸卡片；可用于赛制概览，延迟不能阻挡操作。 |
| 18 | [磁吸反馈](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L751) | 反馈 | 按指针距离计算吸附偏移；精确物品操作不采用，可只改变独立装饰层。 |
| 19 | [弹性缓动](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L805) | 反馈 | 保存位置和速度，以弹簧力、阻尼及质量积分；真正的物理弹簧需由 Java 渲染时钟驱动。 |
| 20 | [汉堡菜单变形](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1042) | 反馈 | 图标线条旋转与菜单展开对应；可用于导航开合，必须同步当前展开状态。 |
| 21 | [粘性滚动叙事](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1101) | 滚动 | 固定说明区与可滚动内容分工；适合长规则和图鉴，保留内容区独立滚动。 |
| 22 | [视差滚动](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1317) | 滚动 | 不同装饰层按不同倍率移动；不用于游戏世界或全屏背景，避免影响相机与瞄准。 |
| 23 | [横向滚动作品带](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1400) | 滚动 | 滚动输入映射为横向轨道位移；可用于 RG 路线浏览，不拦截背包的垂直滚动。 |
| 24 | [模糊进入](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1444) | 进入 | 模糊半径与透明度协同变化；不用于首次加载的正文，避免再次出现难辨文字。 |
| 25 | [滚动卡片层叠固化](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1463) | 滚动 | 后续卡片覆盖前卡并调整缩放和明度；适合只读进程回顾，不遮挡可交互槽位。 |
| 26 | [弹性手势阻尼抽屉](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1570) | 反馈 | 拖拽位置、释放速度与阻尼决定抽屉停靠；可用于详情面板，关闭动作不能等待动画。 |
| 27 | [折叠展开浮动操作岛](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1630) | 反馈 | 从一个锚点展开少量快捷动作；适合开发工具，必须保留明确的关闭与键盘路径。 |
| 28 | [灵动岛悬浮胶囊](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1702) | 反馈 | 同一状态容器在多个尺寸和内容之间变形；可用于非战斗通知，战斗数值不能因变形移动。 |
| 29 | [环形刻度进度仪表](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1797) | 反馈 | 用路径描边长度表示比例；可映射真实吟唱或冷却，需要适配 AUI 的 SVG 或 GPU。 |
| 30 | [打字机多词轮播交替](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1866) | 进入 | 按字符逐步加入和删除；不用于必读规则、物品名称和启动文字。 |
| 31 | [饱和度增强](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1942) | 图片 | 通过滤镜在灰阶与彩色之间变化；只用于插图，物品稀有度和可用状态保持可辨。 |
| 32 | [图片跟随预览](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L1962) | 图片 | 已具备固定位置的物品悬停预览；不再增加跟随光标的第二个物品影像。 |
| 33 | [前后对比滑动擦除](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2012) | 图片 | 移动分界手柄改变上层裁切；适合开发者视觉对照，不进入常规战斗 HUD。 |
| 34 | [方向感知卡片划入](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2071) | 悬停 | 根据进入和离开的相对方向选择遮罩位移；适合少量路线卡，保持原点击边界。 |
| 35 | [卡片 3D 倾斜](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2140) | 悬停 | 指针相对位置映射到透视旋转；AUI 不直接运行浏览器 3D 变换，需要单独验证渲染支持。 |
| 36 | [镜面菲涅尔反光 3D 卡片](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2242) | 悬停 | 倾斜同时移动高光层；适合稀有物品展示，限制在卡片内部。 |
| 37 | [拟物鱼眼缩放 Dock](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2296) | 悬停 | 按与指针的距离计算邻近图标的放大；不用于需要精准选位的常驻快捷栏。 |
| 38 | [层叠卡片手势抽卡](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2406) | 布局 | 拖拽位移和速度决定卡片退出，底层补位；路线选择仍需明确确认及服务器回执。 |
| 39 | [页面转场](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2580) | 布局 | 已采用适配方案：UI 历史纹理与新界面在 180ms 内过渡，透明区域继续显示世界。 |
| 40 | [金属光泽扫光文字](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2625) | 进入 | 移动渐变制造标题反光；只适合已完成字形加载的装饰标题，不影响正文清晰度。 |
| 41 | [流光环形边框](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2665) | 进入 | 沿边界移动高光；可用于超位魔典的选中卡，避免为所有格子开启循环动画。 |
| 42 | [流程连线脉冲光波](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2696) | 进入 | 沿曲线路径移动光点；可用于路线节点或工作流程，状态必须来自实际进程。 |
| 43 | [滚动手绘线条勾勒](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2793) | 滚动 | 按阅读比例改变路径描边；适合图鉴关系图，需要补充描边参数的渲染适配。 |
| 44 | [机械滚轮数字翻牌器](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2842) | 反馈 | 各位数字在纵向字带上切换；只用于结算或累计统计，不延迟生命及魔力显示。 |
| 45 | [波形音频动态可视化](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L2958) | 反馈 | 示例通过周期函数模拟波形；实际语音状态应接入真实音频幅度，不能伪装录音。 |
| 46 | [文字波浪反弹悬停](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3011) | 悬停 | 逐字符错峰产生悬停波浪；只适合短装饰标题，不拆分输入内容与普通中文说明。 |
| 47 | [黑客密码解密文本](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3038) | 反馈 | 随机字符再解码回正文；本项目不采用，避免与用户报告的字体乱码混淆。 |
| 48 | [文字 3D 逐字波浪飞入](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3123) | 进入 | 拆字后错峰翻转与清晰化；仅考虑章节演出，字库和 3D 变换支持必须先就绪。 |
| 49 | [磁吸吸附光晕按钮](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3163) | 悬停 | 吸附与光照共同响应指针；可仅应用局部高光，不移动关键操作的命中区域。 |
| 50 | [无缝跑马灯](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3271) | 滚动 | 复制内容并循环平移；不用于规则正文，长物品名称优先换行或在详情中完整显示。 |
| 51 | [动态噪点材质](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3300) | 反馈 | 示例逐像素生成纹理；透明界面不增加全屏噪点，局部材质应静态缓存或使用 GPU。 |
| 52 | [点阵网格近邻感应高亮](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3402) | 悬停 | 按指针距离调整点阵亮度和大小；只作为可选局部装饰，禁止覆盖游戏画面。 |
| 53 | [交互式粒子网格](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3474) | 悬停 | 在 Canvas 中积分点阵运动并回弹；AUI 需改用 Java/GPU，不能直接运行示例脚本。 |
| 54 | [路径形变果冻按钮](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3630) | 反馈 | 在相容的路径控制点间插值；必须先统一路径拓扑，再接入可中断的渲染状态。 |
| 55 | [流体变形有机泡泡](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3719) | 反馈 | 以多段圆角参数形成连续轮廓变化；仅适合加载装饰，交互面板轮廓保持稳定。 |
| 56 | [闪烁微光星空点阵](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3760) | 进入 | 分散的亮度周期与轻微位移形成星芒；可用于局部魔法徽记，默认不添加背景星海。 |
| 57 | [极光渐变弥散流光](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3863) | 进入 | 多层模糊渐变连续移动；按用户透明背景要求，不接入全屏菜单。 |
| 58 | [流星夜空划过特效](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L3919) | 进入 | 尾迹与亮点按轨迹移动；按透明背景要求，不作为菜单背景。 |
| 59 | [鼠标跟随光标](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L4019) | 悬停 | 模拟光标使用追赶插值；本项目保留系统指针，避免新增视觉延迟。 |
| 60 | [物理重力礼花爆炸](https://github.com/holynova/web-motion-showcase/blob/master/data/motions.js#L4138) | 反馈 | 初速度、阻力和重力产生一次性庆祝碎片；可用于胜利通知，走 GPU 且支持关闭动效。 |

## 维护约定

菜单与工作站共享 `motion.css`，原有菜单设置是唯一的动效偏好来源。物品槽不跟随指针漂移，不通过动画修改坐标映射或服务器事务。新效果先明确输入、状态、取消与退场，再接到 UI；新状态到来时允许打断旧动画。

本轮游戏内验证范围、截图和未穷举部分见 [客户端界面说明](archive-ui.md)。
