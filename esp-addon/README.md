# Amethyst Client ESP Addon（Legacy Fabric 1.8.9）

**Description (English):** Add-on for Amethyst Client. Adds an "ESP" category to the client
settings with through-wall overlays for entities, block entities, ores and beds (plus obsidian
around beds), and optionally reveals invisible players.

**Amethyst Client（模组 id：client）的拓展包**：穿透方块显示实体、实体方块、矿石和床，
支持反隐身，并在客户端设置里挂一个「透视 / ESP」分类。适配客户端 **1.0.2 / 1.0.2_24**（两版设置接口一致）。

- 纯客户端，无网络包、无服务端组件
- `depends`：`java >=21`、`fabricloader >=0.14.0`、`minecraft 1.8.9`、`client "*"`（前置不限版本）
- 设置里 6 张卡片、23 行：6 个开关 + 17 个颜色行，文案中文/英文跟随客户端「界面语言」

## 显示内容与默认颜色

**实体透视**（ESP Entities）
| 对象 | 中文行 | 默认颜色 |
| --- | --- | --- |
| 和平生物（猪牛羊鸡兔…） | 和平生物 | 白 `FFFFFFFF` |
| 中立生物（铁傀儡、狼、末影人、僵尸猪人） | 中立生物 | 黄 `FFFFD24B` |
| 敌对生物与玩家 | 敌对生物与玩家 | 红 `FFFF4747` |
| 隐身玩家/生物（开了反隐身时） | 隐身玩家 | 青 `FF00E5FF` |

只画碰撞箱方框，不显示名字/距离；不画自己；只画 `LivingEntity`。

**方块实体透视**（ESP Block Entities）
| 对象 | 中文行 | 默认颜色 |
| --- | --- | --- |
| 箱子、末影箱 | 箱子 | 黄 `FFFFC62E` |
| 陷阱箱 | 陷阱箱 | 橙 `FFFF8C1A` |
| 其它（告示牌、旗帜、熔炉、漏斗、火把、红石、铁轨、头颅…） | 其它方块 | 白 `FFFFFFFF` |

框用方块自己的**选取箱**（和原版高亮同形状）；大箱子原版给的碰撞箱会往邻格偏移，
检测到越界就退回整格，保证框永远落在本方块里。

**资源透视**（ESP Ores）
钻石 `FF3FA0FF` / 绿宝石 `FF2FE06A` / 金 `FFFFD22E` / 铁 `FFE0E0E0` / 煤 `FF333333` /
红石 `FFFF3B30` / 青金石 `FF3D5AFE` / 下界石英 `FFF5F5F5`

**床透视**（ESP Beds，Bedwars 用）
- 只认 `BedBlock.BED_TYPE == HEAD`，渲染时按 `FACING` 反推 FOOT，两格拼成一个高 `0.5625` 的框
- 床颜色 `FFFF5252`
- 「床周黑曜石」卡片打开后，额外画床两格 **半径 2 格**（`dx²+dy²+dz²≤4`）内的黑曜石，默认紫 `FFAA00AA`

**反隐身**（Anti-Invis）
- `LivingEntityRenderer#render` 头尾注入：渲染期间把 `isInvisible()` 翻成 false，开混合、`glBlendFunc(770,771)`
- 不透明度 `invisAlpha=0.5`，改 properties 即可
- `ClientTick` 每 tick 兜底还原，渲染中途异常也不会让人永久显形

实体方块 / 矿石 / 床只扫**已加载区块**，不强制加载；范围按**水平距离**算（高度不限），扫描和渲染一个口径。

## 配置 `config/amethystesp.properties`

```properties
entities=true            # 实体透视
blocks=true              # 方块实体透视
ores=true                # 资源透视
beds=true                # 床透视
showObsidian=true        # 床周黑曜石
antiInvis=true           # 反隐身
colorPeacefulMob=FFFFFFFF
colorNeutralMob=FFFFD24B
colorHostileMob=FFFF4747
colorChest=FFFFC62E
colorTrappedChest=FFFF8C1A
colorOtherBlock=FFFFFFFF
colorDiamondOre=FF3FA0FF
colorEmeraldOre=FF2FE06A
colorGoldOre=FFFFD22E
colorIronOre=FFE0E0E0
colorCoalOre=FF333333
colorRedstoneOre=FFFF3B30
colorLapisOre=FF3D5AFE
colorQuartzOre=FFF5F5F5
colorBed=FFFF5252
colorObsidian=FFAA00AA
colorInvisible=FF00E5FF
entityRange=192          # 8..256（实体不扫区块）
blockRange=96            # 8..128
oreRange=64              # 8..128
bedRange=64              # 8..128
fillAlpha=0.18
invisAlpha=0.5
```

旧键 `players / chests / entityColor / blockColor / oreColor`、旧文件名 `legacyesp.properties` 都会自动兼容。

## 接进客户端设置的方式（1.0.2 / 1.0.2_24）

这一版客户端把设置拆成了两条通路，本模组跟着它走：

1. **界面结构**：`ClientScreen#buildGroups()` 按 `SettingsSpec.ROWS` 拼 `Group/Unit/Row`。
   我们在 TAIL 注入，反射构造一个 `id=216` 的 `Group`（216 在 200–226 里没被占用），
   往里塞 6 个 `Unit`，按英文名插在「ESP」该在的位置（客户端整表 A→Z 排序，不能丢到末尾）。
2. **分类名**：`SettingsSpec.GROUP_NAME_ZH/EN` 是 `static final` 数组（下标 = gid - 200），
   字段不能换，但元素可写，直接写第 16 格：中文「透视」、英文「ESP」。
3. **行文案**：`ClientScreen#label(int)` 的 HEAD 注入，我们的 key（130–152）返回自带表。
   客户端 `LABELS` 是定长表且自带 validate 自检，所以一律不动它。
4. **取值/写入**：客户端 1.0.2 起把开关和颜色集中到了
   `net.client.settings.SettingsValues` 的 `isOn / toggle / colorOf / setColor`（静态方法，按 key 分派）。
   我们对这四个方法做 HEAD 注入，透视的行就跟客户端自己的设置完全一样（点开关、开取色器、搜索过滤都正常）。

**key 规划**：开关 130–135（实体 / 方块 / 矿石 / 床 / 床周黑曜石 / 反隐身），
颜色 136–152（17 个，顺序 = `EspConfig.C_*`）。客户端现存 key 最大 116，这一段是空白区。

编译期 `src/xstub/java` 只放 `net.client.ClientScreen`（`buildGroups`、`label`）和
`net.client.settings.SettingsValues` 四个方法的空壳，仅进编译 classpath，不进 jar。

所有注入 `require = 0`：客户端改版改名时只是不生效，不会像 1.0.2_18 那次直接抛
`InvalidInjectionException` 把启动打死。真失效了可以加 `-Damethystesp.probe=true` 启动，
打开一次设置界面，日志会把 `net.client.*` 的真实方法签名打出来（`ClientApiProbe`）。

## 渲染细节

- 整段绘制只用裸 GL（避开 `GlStateManager` 的状态缓存与 OptiFine 脱同步），
  进出用 `glIsEnabled`/`glGetFloat` 记原值再逐一还原：深度测试、深度写入、雾、纹理、剔除、
  AlphaTest、混合、光照、线宽、当前颜色。
- 分帧扫描：每 tick 最多 3 列区块；移动超 24 格或超过 2 秒重扫；只遍历非空 section。
- 床周黑曜石不缓存全图，扫描收尾按每张床做一次 5×5×5 局部查找。

## 本次改动（相对最初 1.0.0 源码）

**新增**：`BedESP`（床 + 床周黑曜石）、`Anti-Invis`（含 `LivingEntityRendererMixin`）、
设置界面 6 卡片 23 行、`SettingsValuesMixin`、中文文案、`ClientApiProbe`（诊断，默认关闭）。

**修掉的问题**
1. `entityColor` 先判 `MobEntity`，狼/末影人/僵尸猪人/铁傀儡全被涂成敌对红（中立分支是死代码）
2. `displayBox` 拿 `getSelectionBox` 的世界绝对坐标去比 0-1，形状框一直没生效
3. 扫描半径写死 8 区块、还有 `>256 → 16` 死代码，配置值形同虚设
4. 6 位 RGB 颜色被解析成 alpha=0（全透明）
5. 拖取色器每帧写磁盘 → 400ms 防抖
6. 实体透视把掉落物、箭、船、经验球都框上 → 只留 `LivingEntity`
7. GL 状态按「标准世界渲染」猜着恢复，换光影出错 → 记录原值还原
8. 每次开界面都重查反射 → 首次解析缓存
9. `gradle.properties` 写死 `/etc/ssl/certs/java/cacerts`，换机器必炸 → 删除
10. `settings.gradle` 的 `rootProject.name` 与产物名不一致 → 改名
11. 旧取法 `ClientScreen#isOn` 等在 1.0.2 已不存在，导致 1.0.2_18 启动崩 → 改注入 `SettingsValues`，并给所有注入加 `require = 0`

## 构建

JDK 21 + Gradle 8.10.2（Loom 1.8.13；8.14 会产出空 jar）：

```
export JAVA_HOME=/path/to/jdk21
./gradlew build
```

产物 `build/libs/Amethyst-Client-ESP-Addon-1.0.1.jar`（Java 21 字节码，去调试信息）。
本次改动在 JDK 21 (Temurin 21.0.13) + Gradle 8.10.2 上完整跑通 `build`，
refmap 里确认生成了 `class_1635;method_5773(Lnet/minecraft/class_1699;DDDFF)V` 等映射。

## 安装

1. 装 Amethyst Client（本包按 **1.0.2_24** 的源码对齐）
2. jar 丢进 `mods/`
3. Client Settings → 分类「透视 / ESP」

## 已知限制

- key 130–152 是客户端 1.0.2_24 的空白区，客户端以后再占用这段 key 会冲突
- 颜色行取的是客户端取色器的 ARGB，本模组渲染只用 RGB，alpha 不参与
- 没有进游戏实测：床框位置、反隐身 0.5 透明度、卡片高度这些运行时表现需要真机验收
- 版本 1.0.1：`mod_version` 已同步，产物文件名跟着变

## 授权

**LGPL-3.0-only**（GNU Lesser General Public License v3.0）。

- 完整条文见 `LICENSE`（LGPLv3）
- 也随 jar 一起打包在 `LICENSE`
- 修改后再分发请保留同样的许可与声明，并按 LGPL 提供对应源码
