# 魔法着色器验证

2026-10-20 新增实际收包与移动玩家逐帧比较，验证吟唱、环绕、护盾的跟随，以及引导起点、相机插值、冻结/暂停和固定落点边界。仍使用下面的生产管线入口；新增检查、日志及结果见[跟随修复](magic-follow-fix.md)。下方 927 项是 2026-10-18 批次的历史数量。

2026-10-18 增加实际 Minecraft 渲染器测试。旧版只验证裸 GLSL，曾漏过 Java 层提交零顶点的问题；当前将生产管线测试与独立 GLSL 测试分别记录。

## 三端生产管线

先用 Java 26 构建 `:common:jar :fabric:classes :forge:classes :neoforge:classes`，再运行：

```text
python scripts/test-magic-renderpearl.py
```

脚本使用各加载器实际修补的 Minecraft 26.3 类、生产 `MagicShaderRenderer`、RenderPearl 设备与隐藏 SDL 后端，读回发光目标与最终画面。三端各 **927 项检查**通过，各 **300/300** 法术有像素输出；覆盖真实视锥、深度全遮挡、清理、过期、TTL、资源重载确定性、远坐标与关闭释放。日志和 PNG 在 `.tools/magic-renderpearl-production/{platform}/`。

回退缺陷的最小对照：

```text
python scripts/test-magic-renderpearl.py --platform fabric --negative-control
```

它只在隔离副本恢复两处旧 `draw(0, 3, 1, 0)`，预期以断言失败退出 1；HDR 与最终像素和均为零。正确参数 `draw(3, 1, 0, 0)` 使真实生产路径恢复可见输出。没有替换着色器、混合因子或绕过视锥来制造通过。

## 独立生产 GLSL

```text
python scripts/test-magic-shaders-gpu.py --publish-previews
```

原样编译四份生产资源，使用 192 字节 std140 参数。最终 **3,966 项断言**通过：80 组机制/阶段、12 种法阵、1,820 组跨学派同色比较、2,660 组同学派机制比较、594 组行进/命中/结束图样比较，以及 reversed-Z 两种深度约定、完全/半墙遮挡、辉光边界、相机内外、生命周期和无效 shader 诊断。

GPU 对比固定相机、颜色、时间和图样，使用阈值掩码的归一化对称差；跨学派最低 25.63%，同学派机制最低 14.93%，图样最低 1.55%。它衡量指定测试画面的结构差异，不代表真人辨识率。

可用 `--source`、`--output` 选择隔离测试资源与输出，`--identity-only` 运行辨识检查；只有通过后 `--publish-previews` 才更新文档对照图。生产资源哈希和计时详见[渲染说明](magic-shader-rendering.md)。最终证据位于 `.tools/magic-identity-production-final/gpu-test.log`。

## 范围

这些测试使用真实 GPU、Minecraft 类和生产渲染器，背景/深度仍为可控合成场景，不是玩家联机游戏截图。仍需实际客户端检查手部与半透明实体顺序、外部光影包、其他 GPU 后端、不同画质和多人帧时间。参考 Shooting Star JAR 未被执行，也未加载其资源。
