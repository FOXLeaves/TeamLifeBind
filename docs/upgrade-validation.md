# 0.2.0 升级验证记录

验证日期：2026-10-11。目标为 Minecraft 26.3，Java 25、Gradle 9.8.1；版本来源见 [升级依据](upgrade-research.md)。以下记录区分已经执行的检查与尚待多人试玩的行为。

## 构建与资源检查

Windows PowerShell 下执行：

```powershell
python scripts/verify-resources.py
.\gradlew.bat build :server:buildServerResourcePack --console=plain
.\gradlew.bat bundleRelease --console=plain
git diff --check
```

本机 `JAVA_HOME` 指向 Java 25。为了避开用户正在使用的 Gradle/Loom 缓存锁，本次设置独立 `GRADLE_USER_HOME=.tools/gradle-upgrade-home`；项目不需要这个本机缓存路径。

- 四个平台编译、资源处理和打包成功；`common:check` 执行 4,103 条断言，覆盖经验边界、数值溢出、引擎生命周期、分队快照和语言回退。
- 资源检查通过：21 份跨模组共享文件一致，64 个 JSON/pack 元数据可解析，10 个模组翻译键、283 个公共翻译键两种语言齐全。
- 检查发布 JAR 的版本与 loader 依赖已展开；公共类字节码版本为 Java 21。资源包只包含资源内容，格式精确为 97.1。
- 完整分发包输出到 `build/distributions/TeamLifeBind-0.2.0-mc26.3.zip`，包含四端 JAR、资源 ZIP、README、LICENSE、CHANGELOG 与本目录文档。
- 新增 GitHub Actions 构建流程；本次只在本机执行，未把未运行的远程 CI 计为通过。

## 隔离运行检查

使用 `.tools/smoke-26.3/` 的专用目录和回环地址端口，分别启用四端实现。测试不会复用原有 `run/` 对局世界。

| 平台 | 实际运行版本 | 启动与命令 | 地形生成与存档 | 停服 |
| --- | --- | --- | --- | --- |
| server 插件 | Paper 26.3 build 174，官方 BETA | 插件 0.2.0 正常启用；`tlb status/help/start/stop` 正常响应 | 主世界正常加载；未开局创建插件比赛世界 | 正常，退出码 0 |
| Fabric | Loader 0.19.5、API 0.162.0+26.3 | 模组注册、专用维度及命令正常 | 三个战斗维度各 9 个区块，全 27 个保存为 `minecraft:full` | 正常，退出码 0 |
| Forge | 26.3-66.0.10 | 模组注册、专用维度及命令正常 | 三个战斗维度各 9 个区块，全 27 个保存为 `minecraft:full` | 正常，退出码 0 |
| NeoForge | 26.3.0.70-beta | 模组注册、专用维度及命令正常 | 三个战斗维度各 9 个区块，全 27 个保存为 `minecraft:full` | 正常，退出码 0 |

命令测试没有玩家连接，`tlb start` 正确返回“至少需要两名玩家”。这是命令入口与前置校验测试，不能计为完整对局测试。

模组端执行以下形式的命令，对每个维度强制生成 x/z 为 0..2 的 3×3 区块：

```text
execute in teamlifebind:battle_overworld run forceload add 0 0 32 32
execute in teamlifebind:battle_nether run forceload add 0 0 32 32
execute in teamlifebind:battle_end run forceload add 0 0 32 32
```

随后移除强制加载并正常保存退出，直接解析 Anvil 区域文件内 NBT 的 `Status`；不将“存在区域文件”误当成完整生成。本次未验证每轮换种子后的差异、结构分布或龙战行为。

本机日志保存在 `.tools/paper-smoke-final.log`、`.tools/fabric-smoke.log`、`.tools/forge-smoke.log`、`.tools/neoforge-smoke.log`。这些是忽略的本机验证文件，不随发行包提供。

额外运行了不随发行包提供的 Paper 验证插件，在新建测试世界中调用项目实际的预加载队列。比赛引擎始终处于 idle：同时提交两组共 18 个新区块、确认完整生成；立即取消另一组尚未执行的请求，确认 future 被取消且任务表清空；再次提交 9 个新区块并完成。逐 tick 采样 35 次，异步提交峰值为 4、全局未完成请求峰值为 8，符合默认限额；跨 9 个预算 tick 持续推进，总耗时 1,688 ms。日志输出 `TLB_PROBE PASS`，随后正常停服。这个耗时只用于证明测试未超时，不作为性能对比。同步 Bukkit/Spigot 加载路径与在线玩家开局仍未经过这项运行测试。

Windows OSHI 性能计数器读取出现本机日志错误，未阻止四端启动；Forge 开发日志还有原生 Netty appender 的本机提示。没有发现本项目的注册表、配方、插件启用或地形生成异常。强制并行生成时的短时延迟提示不构成性能基准，当前不宣称提升了具体 TPS 百分比。

## 尚待真实客户端与多人对局验收

- 客户端模型、贴图、音效、物品合成/消费，以及新版普通床、草床、队伍床的交互。
- 两人以上完整开局、10 秒观察、共享血量/经验/饱食/效果、连锁死亡、5 秒重生和禁复活维度。
- 图腾、生命诅咒、牛奶与重复伤害事件；跨维度追踪与旁观者信息权限。
- 屠龙结算、同时死亡、断线重连、两轮连续开局、停止期间的旧异步任务和世界卸载失败处理。
- 4/8/16 人场景的 tick 耗时、预加载队列峰值、网络 UI 更新量，以及 Spigot/Bukkit 实际运行行为。当前运行验证使用 Paper。

玩法平衡、新物品和新赛制另见 [玩法更新路线](gameplay-roadmap.md)，均为建议，不计为本次已经实现的功能。
