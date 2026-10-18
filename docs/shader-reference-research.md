# Shooting Star 渲染参考研究

日期：2026-10-15。研究对象为用户提供的本地文件；仅用 ZIP 读取和 JDK `javap -p -c -v` 检查资源、字节码及注解，没有加载或执行该模组。

- 文件：[the-shooting-star-demo-1.4.3-neoforge.jar](C:/Users/qeqe1/Downloads/the-shooting-star-demo-1.4.3-neoforge.jar)，13,475,314 bytes。
- SHA-256：`51b99160741a3c77aea50ec6983b6af45e5620d5bedac91aa9b06f0843e758ec`。
- 元数据 `META-INF/neoforge.mods.toml`：版本 `1.4.3-demo`，作者 `rimuru.dev`，Minecraft `[1.21.1,1.21.2)`，NeoForge `[21.1,)`。同一文件还带 `mc1201` 代码和 Forge 风格 `META-INF/mods.toml`，目标 Minecraft `[1.20.1,1.20.2)`。
- 两份元数据均声明 `All Rights Reserved`。本次参考的是渲染原理；TeamLifeBind 需要独立编写 Java、GLSL 和美术资源。未将原 JAR 中的 GLSL、纹理或 class 纳入项目发布资源。

以下 `jar!/…` 路径均指该固定 SHA-256 文件内的准确条目，不是在线推测。字节码留在忽略目录 `.tools/shader-reference/` 供本地复查；本文不附原代码。

## 已确认的渲染链

主控制器为 `jar!/cyou/rimuru/shootingstardemo/mc1211/client/render/d.class`，入口 `a(List<render.e>, render.f, Matrix4f, Matrix4f, float)`。其中 `render.e` 是一个 pass 名称与 uniform 设置回调的记录；`render.g` 负责读取、拼接和编译 shader，并缓存 uniform location。

1. 从主 `RenderTarget` 取得场景颜色、深度及实际尺寸；纹理无效时直接退出，并检查当前为渲染线程。
2. 保存未加特效的场景与深度。独立深度纹理可在原版后处理改写深度前保留世界遮挡信息。
3. 对输入矩阵求逆，形成相机相对的反投影参数；逐个效果向两个离屏颜色目标交替绘制全屏三角形。每次读前一张、写另一张，不同时采样和写入同一纹理。
4. 当 bloom 强度有效时，运行 **5 次降采样**，尺寸依次为效果目标的 1/2 至 1/32，再运行 **4 次升采样**，逐级加回较大层。
5. `composite` 在全尺寸输出上组合原场景、低分辨率效果、bloom、保留帧与曝光输入，最后回到主颜色目标。
6. 使用 `render.d$b` 保存、恢复 GL 状态，异常处理也有恢复路径，避免污染后续原版绘制。

证据：`render/d.class` 的上述入口、私有 `a(int, render.f, Matrix4f, Matrix4f, Matrix4f)`（bloom）、`a(int,int,int,int,int,int)`（目标准备）、`e()`（尺寸更新）；`jar!/assets/shooting_star_demo/shaders/fx/bloom_down.fsh`、`bloom_up.fsh`、`composite.fsh`；`render/g.class` 内的三顶点全屏绘制 shader。

## 世界遮挡和主要参数

证据资源：`jar!/assets/shooting_star_demo/shaders/fx/common.glsl` 的 uniform 声明与反投影函数、`depth_copy.fsh` 的深度写入。

| 参数 | 已观察到的用途 |
| --- | --- |
| `DiffuseSampler` / `DepthSampler` / `AuxSampler` | 前序颜色、世界深度、额外输入（例如未加特效的场景或 bloom 层） |
| `InvProj` / `InvView` | 用屏幕 UV 与深度反投影出相机相对表面位置和观察射线 |
| `ViewProj` | 将相机相对效果点投影回屏幕；识别相机后方的点 |
| `OutSize` | 当前绘制目标尺寸，支持像素足迹、采样偏移等计算 |
| `GTime` / `Time` | 全局时间及效果自己的阶段时间；具体法术分别使用 |
| `Origin` / `Target` / `Caster` / `Facing` | 相机相对起点、瞄准位置、施法者位置和方向 |
| `P0..P3` / `Pts[16]` | 每个效果约定的阶段、强度、半径和有限数量节点；并非通用语义 |

天空深度使用远距离射线终点；有实物的像素使用反投影表面终点，让柱、环与体积光按真实世界表面裁切。相机相对坐标减少远离原点时的 float 抖动。`depth_copy` 用采样后写入深度的方式处理深度格式不一致；字节码同时保留可直接 blit 的路径。

`gungnir_mech.fsh`、`star_gun.fsh` 包含有界 SDF/步进循环；不同分支还用解析几何、少量体积采样和纹理 LOD。不能将整个视觉归结为单个巨大 raymarch，更不能简单换成大量粒子。较重的形体和较便宜的环、束、地面投影可以分别处理。

## 值得迁移的视觉组织

这些是原效果实现和注释所表达的时间设计，并非本机播放后的主观评价。

- `fx/star_beam.fsh`：地面定位、闭合符号和扫线先提供预兆；高空亮点逼近后形成白热核心与有色边缘的实心光柱，能量沿柱运动；冲击圈贴合可见地面传播，最后余热衰退。
- `fx/stars_world.fsh`：七个节点依次形成地面阵列、落星与落点冲击，连线按顺序点燃，再以多柱齐放收尾。节点时序比同一时刻铺满所有特效更易读。
- `fx/tess_space.fsh`、`tess_world.fsh`：把地表战斗效果和 Earth/Solar/Galaxy/Bulk 等多尺度镜头表现分开。空间切换、入口和镜头变换需要额外阶段数据，不是普通地面法术必需的成本。
- `fx/bloom_down.fsh`：第一层提取特效新增的发光贡献，并区分场景改色与实体遮盖，避免把原版天空、雪地全部变成光晕；后续层只负责低频扩散。
- `fx/composite.fsh`：有色差、冲击定格、暗角、缩放模糊及颜色分级等参数。适合作为高阶法术的短暂强调，不能让所有普通法术持续改写整个画面。

可迁移的原则是“预兆 → 展开 → 打击 → 余辉”、世界空间定位、深度遮挡、独立发光缓冲与多尺度 bloom；法术图案、形体公式、阶段曲线和实现需重新设计。

## 挂点、缓存与性能边界

证据 class：`mc1211/client/mixin/LevelRendererMixin.class`、`GameRendererMixin.class` 的运行时 `@Inject` 注解，以及 `render/d.class`、`render/b.class`。

- `LevelRenderer.renderLevel`：HEAD 捕获两矩阵；调用 `PostChain.process(float)` 前保留深度；RETURN 执行世界效果。三个挂点共同保证相机参数与本帧世界颜色一致。
- `GameRenderer`：`bobHurt` HEAD 叠加震动，`getFov` RETURN 调整特定镜头 FOV，`renderItemInHand` HEAD 可隐藏手部，`render` RETURN 画 frame mark。这些属于可选镜头/演示功能，不应全部照搬到多人对战。
- 目标按实际尺寸和深度格式检查，变化才重分配存储；shader 编译成功缓存，失败名也缓存，避免每帧反复失败。资源重载入口 `render.d.a()` 清理程序缓存和失败集合。
- `render.d` 有 2,300,000 像素基准预算，效果目标缩放与像素预算平方根相关。动态预算在 25% 至 100% 之间；低分辨率特效在全尺寸 composite 与原图重建合成。
- 每 8 次有效绘制用 `glFinish` 前后计时，目标约 16 ms，再调整预算。这证明确实有自适应质量，但阻塞测量会扰动帧时间；本项目应优先异步 GPU timestamp 或显式质量档位，而非复制此计时方式。
- 可选历史帧路径最多 64 张，闲置超过 600 次调用后释放；普通战斗特效无需支付这种录像内存。
- `render.b` 检查纹理单元、纹理尺寸和浮点目标等能力，也识别软件光栅器。初始化/重载失败应禁用该视觉通路并保留玩法运行，不能每 tick 重试。
- 研究确认历史缓冲有完整删除路径、程序缓存有重置路径；不能仅凭这些方法断言所有 GL 目标都已在关游戏时释放。本项目需要独立覆盖 resize、reload、切世界、断线和 close 的释放与清空。

## Minecraft 26.3 兼容差异

本地 26.3 API 证据已由工程 JDK `javap` 生成在 `.tools/magic-render-api.txt`、`magic-render-api2.txt`、`magic-render-api3.txt`，对应实际工程依赖。

| 参考 JAR（1.21.1） | 工程（26.3）应采用 |
| --- | --- |
| `RenderTarget.getColorTextureId()/getDepthTextureId()`、全局 raw GL 状态 | `GpuTexture` / `GpuTextureView`，通过 `RenderSystem.getDevice()` 和 RenderPearl 命令接口 |
| 每个参数 `glUniform*`、直接编译 GLSL 150 | `RenderPipeline`、`BindGroupLayout`、`GpuBufferSlice` 与 std140 对齐 uniform block；源码版本以实际 26.3 shader 编译器为准 |
| `renderLevel` 旧签名带 Camera、两矩阵和 LightTexture | 新 `LevelRenderer.render(...)` 与 `CameraRenderState`，`GameRenderer.extract()` / `renderLevel()` / `render()` 分离，必须重新核对 extraction 和 render 的时序 |
| 旧 `PostChain.process(float)` 注入 | 新 requested/applied post-effects、RenderPearl 资源和命令边界；旧 Mixin descriptor 不存在 |
| 临时改 framebuffer attachment | 使用纹理 usage、格式、目标分离及显式 render pass；写入目标不能同时作为该 pass 采样输入 |

不要假定深度清除值、NDC 深度范围、投影朝向与旧 OpenGL 完全相同；26.3 `RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE`、实际投影和后端必须参与验证。不要在游戏产品路径中使用 raw GL 绕过 RenderPearl，否则可能破坏非 OpenGL 后端与原版资源追踪。

## 本地 GPU 验证条件

只检查工具存在性，未启动图形应用或执行参考 JAR。

- `glslangValidator`、`glslc` 未在 PATH；系统有 `C:/Windows/system32/vulkaninfo.exe`。
- 当前命令行 Python 没有 `moderngl`、`glcontext`、`OpenGL`、`glfw` 或 `PIL`，不能直接依赖这些库截图。
- 工程 Gradle 缓存已经包含 LWJGL **3.4.3** 的 `lwjgl`、`lwjgl-sdl`、`lwjgl-opengl`、`lwjgl-shaderc`，各自有 Windows x64 native JAR；位置位于 `.tools/gradle-upgrade-home/caches/modules-2/files-2.1/org.lwjgl/`。
- 可另做 Java harness：SDL 创建带 HIDDEN 标志的 OpenGL 上下文，以离屏 FBO 编译工程自己的 shader、设置 std140 uniform、注入已知颜色/深度纹理，`readPixels` 输出图；不显示窗口。这个途径不需要参考模组或新增下载。
- shaderc 可检查 GLSL/SPIR-V 编译，但不能证明最终画面。离屏 harness 应检查深度遮挡、随相机旋转的投影、输入不被全帧误染、零效果恒等输出、动态分辨率、NaN/Inf 和时间阶段；其结果也不等于完整 Minecraft 客户端、其他光影模组和多人帧率验收。

建议先做少量代表法术的真实 GPU 图片与数值断言，再跑所有参数组合编译/边界验证；单纯 Gradle 编译和服务端探针无法证明 shader 出图正确。
