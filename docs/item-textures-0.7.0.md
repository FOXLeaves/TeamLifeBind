# 0.1.2 新物品材质（构建 0.7.0）

静默符与侦察火箭由内置 `imagegen` 单独绘制，采用透明像素轮廓、有限色阶和可辨认的小尺寸形状。

| 物品 | 生产 PNG | 外观 |
| --- | --- | --- |
| 静默符 | `silent_sigil.png` | 暗紫紫水晶符片、青蓝回响碎片核心 |
| 侦察火箭 | `scout_rocket.png` | 红白短火箭、铜色环带、青蓝侦察镜片 |

原稿保留在工作区忽略目录 `.tools/item-textures-0.7.0/source/`，完整提示词与生成模式在 `.tools/item-textures-0.7.0/generation.json`。内置生成保存的原始文件也保留，不删除原稿。

生产图为 **32 × 32 RGBA PNG**，使用 `.tools/export-item-textures-0.7.0.ps1` 机械 nearest-neighbor 采样导出，保留所选像素的透明 alpha，不进行额外重绘。三端下列目录的同名图片字节一致：

- `fabric/src/main/resources/assets/teamlifebind/textures/item/`
- `forge/src/main/resources/assets/teamlifebind/textures/item/`
- `neoforge/src/main/resources/assets/teamlifebind/textures/item/`

模型使用 `minecraft:item/generated`，`textures.layer0` 对应 `teamlifebind:item/{id}`。插件资源包从 Fabric 资源目录构建，包含相同生产 PNG；原稿与生成记录不会装入 JAR/ZIP。

插件玩家需下载构建 0.7.0 的资源包，模组玩家替换对应平台 JAR。制作结果已通过资源校验，发行归档继续核对 PNG 结构、透明背景、模型入口与三端字节一致性；实际物品栏、手持和掉落视觉效果仍需客户端验收。检查结果见[验证记录](gameplay-validation-0.7.0.md)。
