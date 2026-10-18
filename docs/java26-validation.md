# Java 26 迁移验证

迁移日期：2026-10-12（Asia/Hong_Kong）。Minecraft 保持 **26.3**，游戏内与四端元数据显示版本保持 **0.1.2**，内部构建编号保持 **0.7.0**。此前 Java 25 的完整玩法测试保留在 [原验证记录](gameplay-validation-0.7.0.md)；本页只记录本轮 Java 26 检查。

## 当前配置

`gradle.properties` 集中设置 `java_runtime_version=26`、`java_target_version=26` 与 `common_java_target_version=21`。所有模块工具链、Gradle `JavaExec` 开发任务、Paper 启动脚本与 CI 使用 JDK 26。平台代码目标为 Java 26（class major **70**）；公共模块由 JDK 26 编译，但以 `--release 21` 保留 Java 21 字节码（major **65**）。Fabric 元数据的 `depends.java` 为 `>=26`，Forge/NeoForge 的 `[features.teamlifebind]` 声明 `javaVersion="[26,)"`，将最低运行要求同步到加载器。没有启用 preview 功能。

本机测试 JDK 为 **Eclipse Temurin 26.0.2.1+1**，Windows x64 HotSpot JDK；来自 [Adoptium 官方发布 API](https://api.adoptium.net/v3/assets/latest/26/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse)。下载 ZIP 的 SHA-256：

```text
cf914bf4cc6bf9cedcabec216165bafd10331c5b4d4f2e424b55f6a2e68a1cf5
```

Gradle Wrapper 保持 **9.8.1**。Minecraft、Fabric Loader/API/Loom、Forge/ForgeGradle、NeoForge/ModDevGradle 与 Paper build 175 依赖沿用原固定组合。JDK 切换没有改变配方、材质、赛制或默认配置。

Mixin 配置仍使用库中实际存在的兼容级别：Fabric 为 `JAVA_25`，Forge/NeoForge 为 `JAVA_21`。这些值是 Mixin 的转换兼容级别，平台输出仍为 Java 26 字节码；不填写当前库中不存在的 `JAVA_26` 枚举。实际类检查确认所有平台 Mixin 为 major 70，三端已在 Java 26 下启动并应用必需 Mixin，未出现必需转换失败或 `UnsupportedClassVersionError`。

## 本轮执行结果

所有检查在独立 Gradle 缓存与 `.tools/smoke-26.3/` 测试目录中重新执行，使用本轮 JDK 26，不使用用户原有 `run/` 世界。迁移前的结果保留在历史页，不直接计作本轮通过。

| 检查 | Java 26 本轮结果 |
| --- | --- |
| 四端构建与共享检查 | 通过；四端平台字节码为 major 70，共享模块为 major 65 |
| 共享规则回归 | 通过 **7,256 条断言**，其中包含 **603 条 Campaign 场景**，不重复相加 |
| Forge/NeoForge 元数据及启动器更新后构建 | 通过；两端声明 Java 26 最低要求，开发 `JavaExec` 使用工具链启动器 |
| Paper Campaign / Utility / Gameplay | 通过 **95 / 103 / 168 条断言**；三个启动日志均确认实际使用 Java 26，正常停止、退出码 0 |
| Fabric / Forge / NeoForge 隔离启动 | 通过；启用、命令与规则、正常停止，退出码均为 0 |
| Forge 原生探针 | 最终通过 **138 条断言**；含原有 137 条与 1 条真实 DNS 工厂初始化检查，探针输出 JVM `26.0.2.1`、classfile `70.0` |
| Fabric / Forge / NeoForge 原生维度存档 | 每端各通过 **27/27 个 FULL 区块**检查；每端三个战斗维度各 9/9 |
| 跨平台资源校验 | 通过；**37 份共享文件、103 个 JSON/pack 元数据、14 个模组语言键、433 个公共语言键** |
| Paper 开发启动脚本 | 在独立目录生成成功；`java.exe` 指向本次 Temurin 26.0.2.1+1，使用 Paper 26.3 build 175；生成任务将 Java 运行版本作为缓存输入 |
| 最终发行归档核验 | 通过；**24 个文件**：四端 JAR、插件资源包、README、LICENSE、CHANGELOG 与 16 份文档；共享类、语言、材质与当前构建逐字节一致，显示版本均为 0.1.2 |

共享回归覆盖经验边界、分队/生命周期、语言回退，以及限命额度、同 tick 淘汰、断线 guard 重试、离线团灭、屠龙贡献、静默/侦察、据点与边界。Paper 和 Forge 的具体场景范围沿用 [原玩法验证](gameplay-validation-0.7.0.md)；只有本轮重新执行的通过结果会列入上表。

模组通过 Gradle 开发任务加载当前编译代码；运行进程路径和 Forge 探针输出证明使用本轮 JDK 26。区块检查使用已有隔离存档，检查后移除本轮强制加载记录，不记作重新生成全新地图。Forge 的原生实体探针没有在 Fabric/NeoForge 执行，不将它的 138 条结果归给另外两端。

构建日志保留在 `.tools/build-java26.log` 与 `.tools/java26-metadata-build.log`，资源日志为 `.tools/resources-java26.log`。Paper 日志为 `.tools/paper-campaign-java26.log`、`.tools/paper-utility-java26.log`、`.tools/paper-gameplay-java26.log`。模组日志为 `.tools/fabric-java26.log`、`.tools/forge-java26.log`、`.tools/neoforge-java26.log`，区块检查为对应的 `*-java26-chunks.log`；实际运行进程路径保留在 `*-java26-process.json`，平台与 Mixin 字节码检查保留在 `.tools/native-java26-classes.json`。临时原生探针已移出测试实例，不进入发行包。

Paper 检查加载本轮实际插件 JAR，使用严格玩家/库存接口代理，并执行真实 Bukkit 物品、PDC、配方、方块库存、掉落实体和边界；没有模拟真实 NMS 玩家伤害与网络收发。Forge 使用无真实连接的原生 `ServerPlayer`、实际物品/方块和事件路径，包含实际末影龙部件伤害与必需 Mixin 的贡献计分检查。

本轮完整构建命令（先将 `JAVA_HOME` 与 Gradle JVM 设为本轮 JDK 26）：

```bash
./gradlew :common:check :server:build :fabric:build :forge:build :neoforge:build :server:buildServerResourcePack --console=plain
python scripts/verify-resources.py
```

模组隔离启动分别使用以下任务；测试脚本依次发送 `tlb status`、`tlb help`、`tlb rules`、`tlb start` 与正常停止命令。无真实玩家时，开局正确拒绝不足两人。

```bash
./gradlew -Pdev_run_root=.tools/smoke-26.3 :fabric:runServer --console=plain
./gradlew -Pdev_run_root=.tools/smoke-26.3 :forge:runServer --console=plain
./gradlew -Pdev_run_root=.tools/smoke-26.3 :neoforge:runServer --console=plain
```

三端 Mixin 配置补充 `minVersion: "0.8.7"`，消除首次迁移启动发现的项目配置缺少最低版本声明提示。更新资源后重新构建并进行三端短启动检查，全部正常启动和停止、退出码 0，原提示已消失。该次复测只改资源声明，没有重复计入前述 Forge 137 条原生断言；日志为 `.tools/{fabric,forge,neoforge}-java26-minversion.log`，总结保留在 `.tools/native-java26-minversion-report.json`。

Forge 26.3-66.0.10 的旧 `ForgeMod.hackDNSResolver` 在 Java 26 下反射查找 `Module.implAddExportsOrOpens` 时抛出 `NoSuchMethodException`。进一步在实际 Forge `ServerThread` / 模组线程上下文初始化 `InitialDirContext`，确认 `jdk.naming.dns` 未向 `java.naming` 导出 `com.sun.jndi.dns`，导致 `NoInitialContextException` / `IllegalAccessException`；服务器能启动和旧 137 条断言通过不足以证明 DNS 正常。

项目内 Forge 服务端和客户端开发任务已追加标准 JVM 参数 `--add-exports=jdk.naming.dns/com.sun.jndi.dns=java.naming`。正式 Forge 服务端与所有客户端也必须按 [安装说明](install-0.7.0.md) 添加。该参数只向指定模块导出 DNS provider 包，不开放给所有模块。

修复后实际 Forge `ServerThread` / `TransformingClassLoader[TRANSFORMER]` 线程上下文已成功构造 `InitialDirContext`、读取环境并关闭；最终探针通过 **138 条断言**，包含原有 137 条和本项初始化，正常停止、退出码 0。实际启动进程同时确认带有该导出参数。失败日志保留在 `.tools/forge-java26-dns-probe-before-export.log`，最终成功日志为 `.tools/forge-java26-dns-probe.log`，进程证据为 `.tools/forge-java26-dns-probe-process.json`。这项检查不发送 DNS 查询，远程 DNS/SRV 连接和真实联网对局仍未实测。加载器旧反射补丁的 `NoSuchMethodException` 日志仍会出现，不将其误报为已消除。

## 安装与验证范围

当前平台 JAR 必须使用 Java 26 或更高版本运行；本轮只验证 Java 26。服务端 JVM、客户端启动器以及 IDE Gradle JVM 都应选择 JDK 26。安装步骤见 [安装说明](install-0.7.0.md)。

自动检查不能替代真实客户端和多人对局。客户端模型、网络断线、多人伤害链、连续对局与真实 TPS 数据仍需真人验收；本机执行不记为远程 GitHub Actions 已运行。

发行包通过 `bundleRelease` 生成；最终归档检查逐字节核对四端 JAR、当前 common 类与语言、模组 Java 26 要求、Mixin、生产材质与资源包，并确认文档为本轮版本。ZIP 的 SHA-256 与资源包 SHA-1 另存校验文件，临时探针、测试日志和本机 JDK 不进入发行包。
