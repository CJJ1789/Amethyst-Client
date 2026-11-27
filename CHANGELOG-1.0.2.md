# Amethyst Client 1.0.2

本版把版本号从开发版 `1.0.2_24` 收敛为 `1.0.2`，删除了一套不可达的渲染代码，并修复了一批会在实际使用中出问题的缺陷。

> 对比方法：1.0.1 只发布了编译好的 jar（`Amethyst-Client-1.0.1.jar`），仓库里只有 README，
> 没有源码 tag。所以「相对 1.0.1 的更新」不是靠 git diff，而是把 1.0.1 的 release jar 反编译，
> 与本次构建出的 1.0.2 jar 做「类 / 方法引用 / 字符串常量 / 资源」的结构化对比，
> 再用反编译源码逐条核对具体行为。结论可复核，但不等同于作者视角的完整 changelog。

---

## 一、相对 1.0.1 的更新

### 1. 新增

| 项 | 说明 | 位置 |
|---|---|---|
| 玻璃底独立成类 | 整屏抓屏 + 多点错位取样的毛玻璃实现集中到一个类，设置页与原版页面共用（1.0.1 的 jar 里没有这个类） | `ui/ClientGlass.java`（新增） |
| 全景背景 | 标题页那套 6 面立方体全景抽成公共实现，离屏 1024×1024、叠 3 层，供接管原版页面与设置页复用 | `ui/ClientPanorama.java`（新增） |
| 设置取值层 | 把 `ClientScreen` 里上千行「按下标取/改设置」的 switch 抽成无状态工具类 | `settings/SettingsValues.java`（新增） |
| 版本号单一来源 | User-Agent、回放 meta 里的版本不再写死 `"1.0.1"`，统一读 Fabric 元数据 | `util/ModVersion.java`（新增） |
| 设置项 | 「背景不透明度」`panelOpacity`、「模糊聊天界面」（不再默认排除 `ChatScreen`）、「模糊渐入时长」、「渐变起始色 / 结束色」 | `ClientConfig` / `SettingsSpec` / `Blur` |
| MD3 绘制能力 | 圆角贴图 `imageRound`、四角圆盘 `cornerDisc`、字体 alpha 图集（`beginAlphaText` / `drawRun` / `endAlphaText` / `uploadAlpha`）、动态配色 `buildScheme` / `ease` / `lerp` | `ui/md3/**` |
| 回放健壮性 | `PacketCodec.resolvePacketClass`（只解析不初始化）、AVI 超 4 GB 主动报错 | `replay/**` |
| 元数据与许可 | `license` 由 `LicenseRef-All-Rights-Reserved` 改为 SPDX `LGPL-3.0-only` | `fabric.mod.json` |

### 2. 移除

- **Java 运行时版本检查**：1.0.1 会查 Adoptium API、显示 JDK 版本 / 厂商 / 推荐版本面板；
  1.0.2 删掉了这套逻辑（`EnvCheck.checkJava / fetchLatestPatch / parseMajor / parsePatch /
  prettyVersion / resolveVendor / shortVendor / apiOs / apiArch` 全部消失），只保留 Legacy Fabric 加载器检查。
- **NanoVG 自检**：`Client.probeNanoVg()` 及 `org.lwjgl.nanovg.*` 引用移除。
- **ScreenMixin 的空注入**：`client$paintOverlay` 删除（1.0.1 起已是空实现）。
- **ModListScreen** 的 `isMinecraft` / `javaNote` 删除。
- 多处写死的 `"1.0.1"` 版本字符串。

### 3. 行为 / 缺陷修复（1.0.1 上存在，1.0.2 已修）

| # | 1.0.1 的表现 | 证据 |
|---|---|---|
| 1 | MJPEG AVI 画面上下颠倒 | 1.0.1 `JpegEncoder` 取样用 `srcY = y`；1.0.2 改为 `height-1-y` |
| 2 | MJPEG AVI 整体发绿 | 1.0.1 `encodeBlock` 用 `byte - 128`（≥128 的样本符号扩展成负数）；1.0.2 用 `(v & 0xFF) - 128` |
| 3 | 自定义计分板开关关不掉 | 1.0.1 `ClientConfig.validate()` 末尾无条件 `scoreboardCustom = true; scoreboardShowTitle = true;`；1.0.2 已删 |
| 4 | 皮肤站可用明文 HTTP 传密码 | 1.0.1 只判断前缀 `http` 就放行；1.0.2 拒绝 `http://`，只接受 `https://` |
| 5 | 正版 token 明文落盘且 644 | 1.0.2 写入后尝试收紧为 `600`（非 POSIX 静默跳过） |
| 6 | 打开他人 `.lcr` 可触发任意类 `<clinit>` | 1.0.1 在 `PacketCodec`、`ReplaySession`、`ReplayReader` 三处直接 `Class.forName(className)`；1.0.2 全部改为 `forName(name, false, loader)` |
| 7 | 反射盲调 `Entry` 上所有无参 void 方法 | 1.0.1 `FolderPackRepository.initializeEntry` 遍历 `getDeclaredMethods` 直接 invoke；1.0.2 增加 `isDestructive()` 排除 `close/delete/dispose/clear/shutdown/remove` |
| 8 | AVI 超 4 GB 索引溢出产出坏文件 | 1.0.2 逼近上限时抛错并提示改用 ffmpeg |
| 9 | 单包长度字段可撑爆内存 | 1.0.2 `PacketCodec` 加 8 MiB 单包上限 |
| 10 | 构造器抛异常泄漏回放文件句柄 | 1.0.2 `RenderJob` 失败分支先 `reader.close()` |

---

## 二、本版（1.0.2）修复

| # | 文件 | 问题 | 处理 |
|---|---|---|---|
| 1 | `replay/session/ReplayReader.java` | `indexPackets()` 直接把文件里的 `payloadLength` 加到偏移上。负数会让 `pos` 倒退成**死循环**，超大值会让后面 `new byte[payloadLength]` 直接 OOM | 校验 `0 ≤ payloadLength ≤ 8 MiB`、记录不得越界、头部不得残缺，非法直接抛 `IOException` |
| 2 | 同上 | `indexSnapshots()` 的 `count` 未校验，`pos` 同样可能倒退 / 越界 | 校验 `0 ≤ count ≤ 4 万` 且块不越界 |
| 3 | 同上 | 构造函数里 `indexPackets()` 抛异常时两个 `RandomAccessFile` 都没关，fd 泄漏 | 失败分支 `closeQuietly` 两个句柄后再抛出 |
| 4 | 同上 | `readPacketPayload` 按记录里的长度直接分配数组 | 分配前再校验一次上限 |
| 5 | `replay/record/SnapshotSample.java` | `new ArrayList<>(count)`，`count` 从文件读出，构造 `count=Integer.MAX_VALUE` 的文件即 OOM | 读入后先做 `0..40000` 校验 |
| 6 | `util/Accounts.java` | 账号表（含 accessToken）直接 `Files.write` 覆盖，写到一半崩溃就整份丢；`name` 为 null 时 `add()` 直接 NPE | 临时文件 + 原子替换（与 `ClientConfig` 一致）、失败清理 `.tmp`、null 账号过滤、`add()` 空值兜底 |
| 7 | `ui/ClientPanorama.java` | `render()` 抛异常时只置 `failed` 就 return，帧缓冲留在 unbind、视口停在 1024²、深度测试关着 → 画面直接坏掉 | `try/finally` 无条件恢复 FBO、视口与深度测试 |
| 8 | `ui/ClientGlass.java` | 整套 FBO + 可分离高斯 + SDF 面板着色器藏在默认关闭的静态开关后面，等于永不执行的死代码；控件底色用的是未收敛的 `radius`，与取样用的 `r` 不一致 | 删除该管线及其开关（只保留始终可用的取样实现），底色半径统一用 `r` |
| 9 | `replay/render/MjpegAviWriter.java` | `close()` 里任一写索引/回填长度的 IO 异常都会让 `RandomAccessFile` 不关，文件被自己锁住；`movi` LIST 长度在写完 `idx1` 之后才回填，把索引块也算进了 `movi` 尺寸 | `finally` 关闭；先按帧末尾回填 `movi` 尺寸，再写 `idx1` |
| 10 | `replay/render/FfmpegWriter.java` | `waitFor()` 无上限，ffmpeg 卡住时导出线程永远停在 `close()`；启动失败分支不回收进程与管道 | 30 秒超时后强杀并报 stderr；失败路径统一 `destroyQuietly()` |
| 11 | 多处 `toLowerCase()/toUpperCase()` | 未指定 Locale，土耳其语环境把 `ZIP` 变成 `zıp`，`.endsWith(".zip")` 判断失效；PNG 序列名 `frame_%06d` 在阿拉伯/印地语 Locale 下会输出本地数字 | 统一 `Locale.ROOT`（`PackEntry`、`ClientScreen`、`ModListScreen`、`ReplayCommand`、`CurseForgeUpdater`、`ImeBridge`、`PngSequenceWriter`） |
| 12 | `settings/SettingsValues.java` | `setMask(row, col, ...)` 不校验行列，越界时 `setCharAt` 抛异常 | 行列先做 `0..8` 校验 |

---

## 三、本版界面变更（按需求删除）

设置面板里 **HUD 元件相关的三张设置卡片整体删除**（`HUD` 分类本身保留，里面现在只剩 `UI选项` 一张卡）：

| 被删掉的卡片 | 连带删掉的设置项 |
|---|---|
| 名字标签 | 自己名字标签 / 名字背景色 / 名字文字色 |
| 聊天框 | 聊天框显示 / 自定义聊天框背景 / 聊天框颜色 / 输入法修复 |
| 计分板 | 计分板显示 / 隐藏分数 / 自定义计分板背景 / 计分板颜色 / 编辑计分板 |

- 删法：这三组的分组头与数据行从 `SettingsSpec.ROWS` 中移除，因此 `ALL` 页和搜索里也不会再出现；`HUD` 标签、`UI选项`（主题色 / 界面缩放 / 面板不透明度 / 淡入时长 / 渐变起止色 / 模糊聊天 / 现代字体 / 接管原版界面）保持原样。
- `SettingsSpec` 与原始源码的差异**只有**这三组（15 行 + 3 条 `gname`），其余分组顺序、行号、A→Z 校验都不受影响。

`ClientConfig` 里的对应字段与相关渲染逻辑**未删除**，已保存的配置值仍然生效，只是界面上不再提供入口。如需把这些项挪到别的分类或彻底删掉字段，属于另一处改动。

构建：`./gradlew build` 通过，产物 `build/libs/client-1.0.2.jar`（13.6 MB，含裁剪版 JNA 嵌套 jar）。
未做游戏内启动验证（构建环境无显示设备）。
