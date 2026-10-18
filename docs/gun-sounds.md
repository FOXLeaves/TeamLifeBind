# 空投枪械原创音效

七把枪各有独立射击音色，并新增换弹开始、换弹完成、逐颗装弹、拉栓、转管启动和转管停止六种机构音效。共 13 个短音效，均由本项目的 Python 程序独立合成，没有读取 TACZ 的音效文件或任何外部录音。音色用于表达枪械类别与机构反馈，没有宣称复现真实枪声。

射击音使用固定随机种子的噪声、下行低频正弦、短促高频冲击、金属共振与延迟尾音组合。手炮有较重的低频尾音；冲锋枪与旋转机枪使用短脉冲，适合连续射击；步枪较紧凑；霰弹枪与 M95 使用较长低频衰减；RPG7 增加独立合成的喷气尾音。换弹和拉栓用多段金属碰撞与摩擦噪声，转管音用独立的上行、下行电机频率包络。

## 资源与事件

所有资源为 `48,000 Hz` 单声道 Ogg Vorbis，最长 `0.500` 秒，全部设置 `stream: false`。每端 OGG 总计 `83,757` 字节。Fabric、Forge、NeoForge 的 `sounds.json` 与 13 个 OGG 逐字节一致；发行时 Paper 资源包携带同一组素材。

声音目录为各端 `assets/teamlifebind/sounds/gun/`。射击事件名为 `teamlifebind:gun.fire.<物品 ID>`，其他事件为 `teamlifebind:gun.<机构名>`。事件中的文件引用为 `teamlifebind:gun/<下表文件名>`，省略 `.ogg` 后缀。

| 文件名 | 时长（秒） | 字节 | 字幕类别 |
| --- | ---: | ---: | --- |
| `fire_battle_pistol.ogg` | 0.250 | 6,088 | fire |
| `fire_tactical_smg.ogg` | 0.115 | 4,972 | fire |
| `fire_assault_rifle.ogg` | 0.190 | 5,539 | fire |
| `fire_breach_shotgun.ogg` | 0.300 | 6,611 | fire |
| `fire_marksman_rifle.ogg` | 0.390 | 7,429 | fire |
| `fire_support_machine_gun.ogg` | 0.095 | 5,008 | fire |
| `fire_siege_launcher.ogg` | 0.500 | 8,522 | fire |
| `reload_start.ogg` | 0.260 | 6,300 | reload |
| `reload_end.ogg` | 0.230 | 6,186 | reload |
| `shell_insert.ogg` | 0.120 | 4,982 | reload |
| `bolt.ogg` | 0.270 | 6,526 | reload |
| `spin_start.ogg` | 0.500 | 8,254 | spin |
| `spin_stop.ogg` | 0.400 | 7,340 | spin |

字幕键分别是 `subtitle.teamlifebind.gun.fire`、`subtitle.teamlifebind.gun.reload`、`subtitle.teamlifebind.gun.spin`，三端均提供中英文文本。资源中的射击衰减距离按枪械配置为 40–80 格，装弹机构为 16 格，转管电机为 24 格；原生模组的声音事件使用对应固定范围，保证服务端广播也采用这些距离。

原生模组在服务端确认射击后播放对应射击声音；换弹进出、SPAS-12 每颗装弹、SPAS-12/M95 拉栓和 Minigun 转管启停由对应状态变化触发。机构声音根据每 tick 的状态判断，不依赖视觉同步心跳。RPG7 命中爆炸继续使用原版爆炸音。Paper 也播放同一组开火声，换弹开始和完成声仅发给射手；重复换弹请求、满弹匣请求和取消装填不会重复播放或误播完成声。Paper 的开火广播采用 Bukkit 默认声音范围，未扩展到模组端的固定范围；需加载发行资源包才能听到自定义音效。

## 制作记录与校验

本地制作目录 `.tools/weapon-sounds/` 保留 `generate_sounds.py`、`verify_sounds.py`、13 个原始 PCM WAV、13 个编码成品、`manifest.json` 与 `verification.json`。这些制作记录不会成为游戏运行依赖。合成种子从 `261013` 开始，每个音效增加 `997`；编码使用 `soundfile 0.13.1` 与 `libsndfile 1.2.2`。生成器规范化每个 Ogg 流的 serial number，并重新计算页 CRC，在相同编码器下重复导出得到逐字节一致的 13 个成品。

已使用 libsndfile 实际解码全部音效，检查采样率、单声道、采样帧数、时长、非零 RMS 和有限样本值；解码峰值为 `0.5692–0.8029`，没有样本削波。另检查全部 41 个 Ogg 页的 CRC、连续序号、同一流 serial、完整 EOS 与末尾采样帧数，并校验 Vorbis identification/comment/setup 三个头。三端资源引用、字幕分类与文件字节一致性全部通过。生产资源校验也包含 Ogg 结构和引用检查，见 [`scripts/verify-resources.py`](../scripts/verify-resources.py)。

`sounds.json` SHA-256：`0a18de61b1fe30bb6ce77179f1afc2074b4688aa2fc950b4198bad31cfe716ee`。

按上表顺序，以 `资源相对路径 UTF-8 + NUL + OGG 字节` 串联计算的总 SHA-256：`caa03789a21b2c26aa3244f49b94c083f7ad0bc151fefa0f30462b61d35fa404`。

| OGG 文件 | SHA-256 |
| --- | --- |
| `fire_battle_pistol.ogg` | `201df427586c30f046c618a7541102ef4a6d6d876e6f95b6f25c498783e271ca` |
| `fire_tactical_smg.ogg` | `4e8933709f0fa97e18fde5e2ea485eadd5b74730a2b50fb650eb9aeec03bbc9a` |
| `fire_assault_rifle.ogg` | `847cde4b84490d2afb0e85de980b1d520cc48a8259bb17567dfb6907f8701fb0` |
| `fire_breach_shotgun.ogg` | `1ba55e0bc642616a9ff536cff7c02d842031ee76e023a28150d11ca2dd2a8ebf` |
| `fire_marksman_rifle.ogg` | `b087bb4b6fa7372cb4664dbf79cd47669105d9782b1926858d121ea165c4bb6a` |
| `fire_support_machine_gun.ogg` | `7f1c826f881bdde2be7a35b69bf1757aacb89ddc61dfb1dbfde91dcc4f4b261c` |
| `fire_siege_launcher.ogg` | `2f0b2994a5bd6189cfdde6b729add435f94044e61ddb019fa73139b8c7ffdb00` |
| `reload_start.ogg` | `af9aa7006a224d8b8177e9615355e91e0be1ef31edda2981a85fc5b2fa4ee9d2` |
| `reload_end.ogg` | `3b0f19fb2f203383211d1a489e9083e81fc17b5b700ed5789370596e99a62d81` |
| `shell_insert.ogg` | `0cd09699ac0d259ba9e173d9c5d01de113de7b77072967ef1d5553d3829fe8f8` |
| `bolt.ogg` | `bd582911407a13e0e6d43f21aeddf57fdc7d7b20ee5803acce812d6ff7abc33a` |
| `spin_start.ogg` | `ebea53e32facd0fdecde50785816182c3f838f3bf2450ad923bd5accad27fdeb` |
| `spin_stop.ogg` | `3ade97b10c46b90635423dc6b4c0c381e53db3295e4e71ada9e5827718b95fa1` |

目前已完成资源结构、实际解码与状态接线检查；尚未通过真人游戏客户端试听，连续射击的叠加响度、距离听感和其他游戏音效的混音仍待客户端验证。
