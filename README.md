# Amethyst Client 1.0.2 — 源码构建说明

License: GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`)，全文见 `LICENSE`；LGPLv3 引用的 GPLv3 全文见 `COPYING`。

## 环境

- JDK 21（构建产物的字节码固定为 Java 21；游戏端 `fabric.mod.json` 也声明 `java >= 21`）
- Gradle 9.4.0（仓库内已带 wrapper，`./gradlew` 即可，无需本机装 Gradle）
- 需要能访问 `maven.fabricmc.net` / `maven.central` / `libraries.minecraft.net`（首次构建要拉 Minecraft 1.8.9、Yarn、Loader）

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew build
# 产物：build/libs/client-1.0.2.jar
```

## libs/

`build.gradle` 里的 `org.local:jna-win:5.17.0` 不是公共仓库坐标，对应 `libs/jna-win-5.17.0.jar`：

- 来源：JNA 5.17.0（Apache-2.0 / LGPL 双许可，这里按 Apache-2.0 使用）；
- 已裁剪：删除 `META-INF/*.SF|RSA|DSA` 与除 `win32-x86`、`win32-x86-64` 之外的全部原生库（JNA 5.17.0 上游本身也没有 Windows ARM64 产物），`.class` 未改动；
- 仅用于 Windows 输入法修复（IMM32）。

发版时 `libs/` 必须随源码一起提供，否则依赖解析会直接失败。

## 已知行为

- **MJPEG AVI 上限 4 GB**：AVI 1.0 的 RIFF 长度与 `idx1` 偏移都是 32 位，超出就会写出无法完整播放的文件。1.0.2_18 起在逼近上限时主动失败并提示改用 ffmpeg，不再静默产出坏文件。
- **攻击冷却**：`MinecraftClientMixin` 会清空原版点击冷却（1.0.2 起常开）。部分服务器 / 反作弊会把它判定为异常点击速率，联机前请确认目标服务器规则。
- **账号文件**：`config/client-accounts.json` 保存 accessToken，1.0.2_18 起写入后会尝试收紧为 `600`（不支持 POSIX 的文件系统上跳过）。
