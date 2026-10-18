# 0.4.0 新物品材质

共鸣哨与应急绷带的独立外观使用内置 `imagegen` 绘制，取代此前沿用的原版山羊角和纸纹理。设计采用透明背景、清晰像素轮廓与有限色阶，便于在物品栏小尺寸下辨认。

| 物品 | 生产材质 | 外观 |
| --- | --- | --- |
| 共鸣哨 | `resonance_whistle.png` | 铜色哨身、红石红色细节 |
| 应急绷带 | `emergency_bandage.png` | 米白绷带、红色束带 |

## 原稿与游戏资源

生成原稿为 **1254 × 1254 RGBA PNG**，保留在工作区忽略目录 `.tools/item-textures-0.4.0/source/`。完整提示词、内置生成模式及导出记录保存在 `.tools/item-textures-0.4.0/generation.json`；原稿与生成记录不装入模组 JAR 或插件资源包。

发布资源为 **32 × 32 RGBA PNG**，通过 nearest-neighbor 导出，保留透明背景，不进行额外绘画。只有这两份生产 PNG 进入以下资源目录：

- `fabric/src/main/resources/assets/teamlifebind/textures/item/`
- `forge/src/main/resources/assets/teamlifebind/textures/item/`
- `neoforge/src/main/resources/assets/teamlifebind/textures/item/`

三个目录中的同名 PNG 必须字节一致。插件资源包由 Fabric 资源目录构建，使用相同图片。

对应 `models/item/{id}.json` 使用 `minecraft:item/generated`，`textures.layer0` 为 `teamlifebind:item/{id}`；`items/{id}.json` 保留原有模型入口。两件物品的 ID、配方、使用规则和名称保持一致。

## 安装与检查

模组玩家替换对应平台 JAR；插件玩家重新下载本次重建的 `TeamLifeBind-resourcepack-0.4.0.zip`，资源包托管端同时更新 SHA-1。旧 0.4.0 ZIP 仍包含此前的外观，具体配置见[安装说明](install-0.4.0.md)。

资源与发行归档检查已确认模型入口、PNG 格式、透明背景和三端一致，三份模组 JAR 与插件资源包均包含 32px 生产 PNG。物品栏、手持与掉落显示仍需客户端验收。结果记录在[验证记录](gameplay-validation-0.4.0.md)，玩法规则见[玩法说明](gameplay-update-0.4.0.md)。
