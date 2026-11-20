# Sodium / Iris 兼容

Minecraft **26.3**、Java **26**，TeamLifeBind 显示版本 **0.1.2**。兼容代码随模组提供，Sodium、Iris 和第三方光影包按需单独安装。

## 安装组合

| 加载器 | 用法 | Sodium | Iris |
| --- | --- | --- | --- |
| Fabric 0.19.5 | 性能模组 | 0.9.3-beta.5 | 无需安装 |
| NeoForge 26.3.0.71-beta | 性能模组 | 0.9.3-beta.5 | 无需安装 |
| Fabric 0.19.5 | 性能模组和光影 | 0.9.2 | 1.11.7 |
| NeoForge 26.3.0.71-beta | 性能模组和光影 | 0.9.2 | 1.11.7 |

选择 **26.3** 和对应加载器的原始 JAR，放入客户端 `mods`；光影 ZIP 放入 `shaderpacks`，在 Iris 设置中选择并开启。单人／本地开房安装在自己的客户端。Sodium、Iris 都是客户端组件，独立服务器不需要安装它们。

这两组 Sodium 版本分别测试：Iris 1.11.7 的发布依赖指定 Sodium 0.9.2；Sodium 0.9.3-beta.5 则声明与 Iris 1.12.1 之前的版本不兼容。安装时按同一行选择。Iris 官方支持 Fabric、NeoForge，未提供 Forge 版；Forge 保持无外部渲染模组的运行路径。[Iris 官方说明](https://www.irisshaders.dev/)

发布文件、下载地址及 SHA-512 固定在 [校验清单](../scripts/fixtures/render-compat-lock.json)。文件来自 [Sodium](https://modrinth.com/mod/sodium/versions)、[Iris](https://modrinth.com/mod/iris/versions) 和 [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined/versions) 的发布 API；TeamLifeBind 不重打包或修改这些第三方 JAR。

## 渲染与界面

魔法体积、全分辨率细节和辉光从实际投影矩阵识别深度方向，同时处理正向／反向 Z 及设备裁剪范围。深度标志共用现有 192 字节参数，不依赖“装了 Iris 就强制反转”的假设。

Iris 是否真正启用光影、是否正在渲染阴影，读取它的可选公开 API。主世界效果跳过阴影视角；光影开关变化会重建 TeamLifeBind 自己的渲染资源，并允许之前失败的管线重新初始化。没有安装 Iris 时，也能正常加载和施法。

Sodium 的视频设置与 Iris 的光影设置保留原控制器、布局、滑块、应用与关闭回调。TLB 页头、全屏遮罩替换、原版按钮换肤和文字颜色调整会让出这些设置页，避免内容重叠。

NeoForge 26.3.0.65 之后移除了旧的四参数方块实体提取方法。兼容层保留这个旧描述符并转发到当前接口，使 Sodium 0.9.2 的原生提取功能继续生效。

Iris 1.11.7 的全屏合成使用自有 GL 程序，但占位管线仍声明原版采样器和深度附件。针对这一版本，兼容层改用无采样器、无深度附件的占位管线，保留顶点接口与 Iris 自身程序；渲染校验继续开启。适配使用软目标 Mixin，未安装 Iris 或其他版本时不强制加载该路径。

新版前端校验会检查全部 16 个顶点槽，旧管线记录只列出使用的槽。兼容层补齐未使用槽的空描述，完整描述保持原样；实际顶点格式、缓冲及校验规则不变。

武器仍默认使用 **R** 换弹，键位可修改。Iris 调试重载也有 R 绑定；原生按键映射支持多个绑定，调试重载与武器换弹可以在按键设置中分别改键。

## 验证方法

**2026-11-14 实机验收通过**：四组安装组合共 42 个设置界面场景；六次完整单人／本地联机流程，132 张容器及工作站截图、42 张魔法渲染对照图。Fabric、Forge、NeoForge 未安装外部渲染模组时的原始 GPU 波纹和动态背景回归也通过。

实际客户端在独立测试目录运行，不读取用户存档或改动现有安装：

```text
python scripts/test-archive-ui.py --render-mods=sodium --render-options-only fabric neoforge
python scripts/test-archive-ui.py --render-mods=iris --render-options-only fabric neoforge
python scripts/test-archive-e2e.py --platform fabric --render-mods iris
python scripts/test-archive-e2e.py --platform neoforge --render-mods iris
python scripts/test-archive-e2e.py --platform fabric --render-mods iris --shader-pack complementary
python scripts/test-archive-e2e.py --platform neoforge --render-mods iris --shader-pack complementary
python scripts/test-archive-e2e.py --platform fabric --render-mods sodium
python scripts/test-archive-e2e.py --platform neoforge --render-mods sodium
```

界面测试覆盖三种窗口尺寸和实际 GPU 波纹中间帧；联机测试从 HTML 主菜单进入自己的单人测试存档、连接本地测试服，验证容器、工作站、物品回执，再进行魔法渲染比较。

每种光影包按 **关闭 → 开启 → 关闭** 切换。测试等待真正提交的 GPU 帧，读回魔法 HDR 目标及最终画面，确认效果实际出现；测试服放置的实体墙会完全遮住墙后的效果。光影包括原创合成测试包和未经修改的 **Complementary Reimagined r5.9.3**，使用其默认配置。

Fabric、Complementary 开启时的实际客户端截图：[墙前魔法](images/render-compat-complementary-visible.png)、[移到墙后并被遮挡](images/render-compat-complementary-occluded.png)。两帧使用同一相机与场景，测试魔法分别位于实体墙前后。

生产管线另测普通及正向深度，覆盖法术、枪口、曳光、空投信标、魔力恢复和世界刻纹的前景遮挡：

```text
python scripts/test-magic-renderpearl.py --built-renderer --depth-conventions
python scripts/test-magic-renderpearl.py --built-renderer
python scripts/test-archive-usability.py
```

离线按键检查使用真实 `KeyMapping`，覆盖两种模组注册顺序、共同的 R 和改键后的独立映射。原来的菜单波纹、背景、背包九格快捷栏及三个学习槽保持原流程。

结论针对表中加载器、版本及上述光影配置；其他光影包、渲染后端与额外修改渲染流程的模组需单独验收。
