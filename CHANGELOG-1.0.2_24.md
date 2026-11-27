# Amethyst Client 1.0.2_24

## 1.0.2_24 改动

- 恢复「掺了别的颜色」的静态底色：撤掉 1.0.2_22 加的单色分支，`ClientUi.backdrop` 回到 surface 打底 + 左上主色 / 右下次色两块淡光晕。仍然是静态的，没有旋转全景；世界内依旧只透出场景。

---

# 1.0.2_23

## 1.0.2_23 改动

- 修「三点菜单展开时，底下的卡片跟着高亮」：悬停判定原来直接对整屏坐标做命中测试，菜单没有遮断它。新增 `hoverBlocked`（在 `render()` 里按 `appMenuOpen` 置位）与 `hover()` 封装，卡片、设置行、色块、胶囊这些内容区控件统一走 `hover()`；菜单本身和它的点击命中不受影响。

---

# 1.0.2_22

## 1.0.2_22 改动

- 从主菜单打开客户端设置时，背景不再用原版那套旋转全景，改成一块纯色（面板同色 `colors.surface`，深色主题 `#1C1B1F`），也不再叠主色/次色的光晕。`ClientUi.backdrop()` 新增 `solid` 重载，只有设置页走它；模组列表等其它菜单页保持原样。
- 世界内打开设置仍然不铺任何背景，直接透出场景。

---

# 1.0.2_21

## 1.0.2_21 改动

- 选中的分类胶囊恢复高亮：1.0.2_20 把「背景不透明度」同步到了所有胶囊，包括选中的那个，透明度调到 0 时整排变成一模一样的字，看不出当前分类。现在选中项固定 90% 实（`alpha(primaryContainer, 230)`），未选中项才跟随面板透明度。
- 设置页不再自铺背景：去掉 1.0.2_20 强制铺全景的做法，世界内直接透出场景；只有从标题页打开时才用原版全景（那里没有场景可透，否则是一块空底）。

---

# 1.0.2_20

在 1.0.2_19 基础上继续只动 UI。

## 1.0.2_20 改动

- 顶栏控件跟随「背景不透明度」：搜索框、右上三个圆按钮、分类胶囊（ALL / DISPLAY / …）原来走 `canvasSurface()`，那里为了卡片不糊成一片留了 78% 的透明度保底，于是面板已经全透、顶栏还是几块实心色。新增 `clearRaised()` / 直接 `panelSurface()`，这几个控件现在和面板同透明度（0% 时只剩文字和描边）。详情页的返回 / 关闭按钮同样处理。
- 客户端设置页背景改成原版主菜单的全景：原来只有「没有世界」时才铺 `ClientPanorama`，世界内（Right Shift 打开的设置）露出并模糊玩家眼前的场景。现在 `panoramaLayer(w, h, true)` 强制执行，两个入口的背景一致；同时在世界内绘制全景前关掉深度测试，否则窗口的深度缓冲里残留的世界深度会把天空盒挡掉、背景混进地形。

---

# 1.0.2_19

## 1.0.2_19 改动

- 分类卡片（聊天框 / 名字标签 / 计分板 / UI 选项…）底色：原来在 `surfaceContainer` 上叠 30% 白（深色主题约 `#646267`），和设置页行卡片（叠 10%，约 `#37353C`）差着一档。统一改成 10%，两类卡片现在同色。
- 颜色选择器调色板：原来那组偏灰的 Material 400（`#90A4AE` 蓝灰、`#8D6E63` 土棕、`#5C6BC0` 暗靛）换成 12 个标准色 —— 白 `#FFFFFF`、黑 `#000000`、红 `#F44336`、橙 `#FF9800`、黄 `#FFEB3B`、绿 `#4CAF50`、青 `#00BCD4`、蓝 `#2196F3`、紫 `#9C27B0`、粉 `#E91E63`、灰 `#9E9E9E`、棕 `#795548`。

---

# 1.0.2_18（基线）

基线：1.0.2_17 源码包（196 个 Java 文件）。本版只做修复与一致性收尾，不新增玩法功能。

## 修复（会导致实际故障）

| # | 问题 | 位置 | 处理 |
|---|---|---|---|
| 1 | MJPEG AVI 画面上下颠倒 | `JpegEncoder.encode` | 取行时按 `height-1-y` 翻转，与 PNG / ffmpeg 两条链路一致 |
| 2 | MJPEG AVI 整体发绿、颜色全错 | `JpegEncoder.encodeBlock` | `byte[]` 直接减 128 会把 ≥128 的样本符号扩展（255→-1→-129），改为 `(v & 0xFF) - 128`。纯红/纯白/纯蓝实测已还原 |
| 3 | 自定义计分板开关关不掉 | `ClientConfig.validate()` | 删除无条件 `scoreboardCustom = true` / `scoreboardShowTitle = true` |
| 4 | 皮肤站可用明文 HTTP 传密码 | `Auth.skinSite` | `http://` 直接拒绝，只接受 `https://` |
| 5 | 正版 token 明文落盘且权限 644 | `Accounts.save` | 写入后尝试 `setPosixFilePermissions(600)`；非 POSIX 静默跳过 |
| 6 | 打开他人 `.lcr` 可触发任意类 `<clinit>` | `PacketCodec`、`ReplaySession`、`ReplayReader` | 一律 `Class.forName(..., false, loader)` 只解析不初始化；单包上限 8 MiB |
| 7 | AVI 超过 4 GB 索引溢出 | `MjpegAviWriter.writeFrame` | 逼近 4 GB 时主动抛错并提示改用 ffmpeg / 降分辨率 |
| 8 | 构造器抛异常时泄漏回放文件句柄 | `RenderJob` 构造器 | 失败分支先 `reader.close()` |
| 9 | `.lcr` 里记录的 mod 版本写死 "1.0.1" | `RecordingManager` | 读 `FabricLoader` 的 mod 元数据，统一走新增的 `ModVersion` |
| 10 | 菜单背景两套 mixin 抢同一方法 | `net.client.mixin.ScreenMixin` | 删掉 1.0.2_17 起就已空实现的三段 `renderBackground` 注入 |
| 11 | 反射盲调 `Entry` 上所有无参 void 方法 | `FolderPackRepository.initializeEntry` | 排除 `close/delete/dispose/clear/shutdown/remove` 这类破坏性方法 |

## 修复（性能）

- `ClientGlass`：`DEBUG` 从写死 `true` 改为 `-Dclient.glass.debug=true` 才开（原来每个 GL 调用后都 `glGetError`，是同步点）；`glGetInteger` 的 `allocateDirect(64)` 改为复用静态 `IntBuffer`。
- `EntitySnapshot`：`Boolean.getBoolean` 调试开关提到 `static final`，不再每个实体每次采样查一次系统属性。
- `FrameCapture`：HUD 用的 `Window` 每个导出任务只构造一次。
- `Imm32Backend`：组字缓冲复用 JNA `Memory`，不再每帧分配 native 内存。

## 一致性 / 构建

- `gradle.properties` → `1.0.2_18`；`fabric.mod.json` 描述、`license` 改为合法 SPDX `LicenseRef-All-Rights-Reserved`。
- 三份 mixin 配置 `compatibilityLevel` 由 `JAVA_8` 改为 `JAVA_21`（与 `options.release = 21` 对齐）。
- 补 `libs/jna-win-5.17.0.jar`（裁剪版 JNA，Apache-2.0）与 Gradle Wrapper，源码包现在可以 `./gradlew build` 直接出包。
- `Auth` / `EnvCheck` / `ModrinthUpdater` 的 User-Agent 不再写死版本号。
- `SettingsValues` 里残影持续时间 / 层数的取值范围与 `ClientConfig.validate()` 对齐（200–4000 ms、0–10 层）。
- `NOTICE.txt` 与 `THIRD_PARTY.md` 修正 JNA 嵌套 jar 名（`jna-win-5.17.0.jar`）与 Windows 架构列表。

## 未改动但需要知道

- mod id 仍为 `client`（改名会动到配置目录、资源命名空间和已有存档的回放路径，属于破坏性迁移，未在本版做）。
- 攻击冷却清空（`MinecraftClientMixin`）属于产品行为而非缺陷，保持原样；部分反作弊会判定异常，见 README。
- `ClientScreen`（2350 行）与 `ReplayEditorScreen`（1281 行）未拆分，属于结构性债务。
- 配置系统仍是 90 多个静态字段手写读写链，未改成表驱动。

## 验证

- `./gradlew build` 通过，产物 `build/libs/client-1.0.2_18.jar`（14 MB，含裁剪版 JNA 嵌套 jar）。
- JPEG 编码器用独立 harness 实测：32×32 上红下蓝缓冲 → 解码后上红下蓝、纯色 `(254,0,0)/(255,255,255)/(0,0,254)` 均正确。
- 未做游戏内启动验证（构建环境无显示设备），mixin 应用 / 渲染表现需在实机 `runClient` 复验。
