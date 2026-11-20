# 魔法攻击特效修复 — 2026-11-03

本批修复 Fabric、Forge、NeoForge 中“有吟唱，却看不到攻击”的绘制问题，覆盖 280 种普通魔法、20 种超位魔法及其全部连段。显示版本仍为 **0.1.2**，发行文件编号 **0.7.0**，运行环境为 Minecraft **26.3 / Java 26**。

## 原因与修改

旧渲染器按距离排序，近身吟唱通常排在攻击前面。当摄像机位于吟唱包围体积内时，这个场景占用完整屏幕裁剪区域。在高分辨率窗口中，单个场景几乎耗尽 1,382,400 像素预算，后面的弹道、射线或命中被整次跳过。

使用实际 Minecraft RenderPearl 管线在 1920×1080 重现：单独的攻击可见，但与吟唱叠加后，攻击新增 RGB 像素总差为 **0**。此前低分辨率、单场景测试没有覆盖这一组合。

现在先筛选有效可见场景，再按总覆盖面积共同分配发光目标分辨率。攻击／命中排在吟唱／消散之前，自己的效果优先；效果增多时立即降采样，减少后等待 20 tick 恢复清晰度。已过期的场景不再占用当前帧预算，调整魔法目标尺寸时保留世界深度纹理。

该修改继续使用世界空间 GLSL、深度遮挡、HDR 发光和辉光合成，无 Minecraft 魔法粒子。伤害、弹道碰撞、魔力消耗、网络协议及 GLSL 图形定义沿用原实现；本次修复的是已有攻击效果没有被提交绘制的问题。

## 验证

真实 Fabric 服务器夹具逐个发动全部 300 种法术，覆盖超位连段在内的 **347 个机制阶段**。每阶段均收到行进或命中事件；300 种法术在测试场景造成伤害时均有对应命中事件。共 **1,608 项断言**通过，记录 **8,676 个场景包、0 个 Minecraft 粒子包**。夹具只操作 `.tools` 中的测试世界和临时玩家。

GPU 回归使用 NVIDIA GeForce RTX 3050 Laptop GPU、各加载器对应的 Minecraft 类和实际 `MagicShaderRenderer.render`。主要场景为 **2560×1440**、相机附近吟唱与前方攻击同时存在；逐项检查全部 347 个机制的行进和命中，共 **694 组组合场景／加载器**。对比在相同发光目标分辨率下去掉攻击的结果，确认攻击确实增加像素；另单独确认吟唱也被保留。

附加检查覆盖 2／4／8 场景预算、模拟低 FPS 分支、画质恢复等待、深度纹理复用和过期场景排除。原有 300 法术可见性、世界深度遮挡、资源重载、远坐标与 42 帧跟随检查也重新运行。

可复用入口（先使用 Java 26 构建三个模组）：

```text
python scripts/test-magic-renderpearl.py --built-renderer --attack-stages --width 2560 --height 1440 --output .tools/magic-attack-release-gpu
python scripts/test-magic-renderpearl.py --built-renderer --output .tools/magic-attack-release-follow
python scripts/test-magic-renderpearl.py --platform fabric --attack-stages --negative-attack-control --output .tools/magic-attack-negative
```

最后一条在隔离副本中关闭共享分辨率，**应当失败**，用于证明回归能够检测一个场景挤掉另一个场景的错误。前两条直接运行 Gradle 构建的生产类，并记录类 SHA-256，便于核对发行 JAR。

这些是实际 GPU 管线和服务器世界测试；GPU 使用合成摄像机与深度场景，未进行完整客户端联机试玩，也未验证第三方光影包兼容性。

## 安装

从本批 `TeamLifeBind-0.7.0-mc26.3.zip` 中选择 `mods/fabric`、`mods/forge` 或 `mods/neoforge` 对应的 JAR，替换客户端旧文件并重启游戏。单人存档／本地开房同样替换自己的模组。已安装 2026-11-02 批次时，本次只需要更新客户端；更旧安装请同时遵循[安装记录](install-0.7.0.md)中的玩法与界面更新步骤。

无需额外光影包或参考模组。版本号继续显示 0.1.2，请以本批发行包的 SHA-256 区分更新。管理员可通过 `/tlb dev magic` 选择具体魔法测试；枪械、玩法模式、魔力生命周期以及上批紧凑菜单均保留。
