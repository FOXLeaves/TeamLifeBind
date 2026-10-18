# 队伍床缺失模型修复

日期：2026-10-13。Minecraft 26.3，Java 26，显示版本仍为 0.1.2，内部构建编号 0.7.0。

队伍床在物品栏中显示紫黑缺失纹理。原物品入口仍使用 `minecraft:special` 的 `minecraft:bed` 渲染器，本地模型还继承 `minecraft:item/white_bed`。真实 Minecraft 26.3 客户端 JAR 已不包含这个专用渲染器，也不存在该物品父模型。原生 `ClientItem.CODEC` 在旧资源上明确报出 `Unknown element id: minecraft:bed`，全部 15 个物品中仅队伍床解码失败。

修复将入口改为与 26.3 原版白床一致的普通床头、床尾组合模型。床头保留 `teamlifebind:item/team_bed` 别名，继承实际存在的 `minecraft:block/white_bed_head`；床尾引用 `minecraft:block/white_bed_foot`，使用原版向后平移一个模型单位的变换。三个模组端的两份 JSON 完全一致，Paper 资源包由同一组资源重新生成。名称、说明、合成和复活规则没有改变。

检查先在旧资源上复现失败，再对修复后的资源复测：

- 原生解码全部 15 个入口通过：8 个基础物品、7 把枪，失败数为 0。
- 原生 `CuboidModel.fromStream` 解析 38 个本地及原版父模型，合并继承的纹理槽并核对 19 张依赖贴图，均存在。
- 队伍床的床头、床尾各解析到 3 个立方体，父模型和纹理链完整。
- 原有 23 个枪械模型、184 个显示变换、92 次左右手方向和 16 个转管阈值检查仍通过。
- 生产资源检查新增床组合、头尾模型引用与床尾变换检查；旧资源会失败，修复后通过。发行校验同时核对三个模组 JAR 与 Paper 资源包内的床资源，防止打入旧文件。

本地原生检查命令为 `python .tools/weapon-models/run_native_model_probe.py`。旧资源失败记录为 `.tools/weapon-models/native-base-item-parser-red.log`，最新成功记录为 `.tools/weapon-models/native-model-parser.log`，均保留在调试目录。通用生产检查为 `python scripts/verify-resources.py`，无需图形窗口或额外 Python 库。

本次仅修改客户端资源和检查说明。重新构建四端 JAR、资源包与发行 ZIP，不重复无法识别该客户端模型问题的专用服务器启动测试。原生解码和依赖验证没有替代真人客户端重载后的画面检查。

模组玩家关闭游戏后替换对应端的单个 TeamLifeBind JAR，再启动客户端。Paper 服务器更换资源包并更新 SHA-1，玩家重新下载资源后使用新模型；当前 SHA-1 见[安装说明](install-0.7.0.md)。
