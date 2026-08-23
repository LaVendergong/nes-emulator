# 交接

## 当前状态

Windows 与 Android 两个 Host，同一份 NTSC 核。会话功能对齐：选盘、暂停、重启、存档槽、换绑。

- 核：mapper 0 / 1（MMC1，连续写丢掉）/ 2（UNROM）；方波/三角/噪声/DMC；游戏常用非官方 6502。
- Windows：选盘/换盘/暂停/重启/存档槽/换绑。存档 NES1 **v2**。便携包 `jpackage`。
- Android：`android/` Gradle；打开 ROM（SAF）、暂停、重启、存档槽、拖虚拟键位置；锁横屏。不扫盘。槽位在应用私有 `saves/`。
- 测试盘：`roms/nova.nes`。仓库：https://github.com/LaVendergong/nes-emulator

## 下一步唯一动作

等用户一句话点名下一刀（完成定义 + 改哪个模块），再按 `prompts/templates.md` 的 `03-implement` 或 `04-change`。没点名不要加功能、不要重构。

候选（空着不等于现在要做）：PPU loopy、其它 mapper、不稳定 6502。

## 必须先读（最多 5）

1. `AGENTS.md`
2. `docs/project-map.md`
3. `docs/adr/0001-java-stdlib-host.md` — Windows：Java 17 + 标准库
4. `docs/adr/0002-ntsc-cycle-clock.md` — 1 CPU = 3 PPU dots，不按墙钟补步
5. `docs/adr/0003-android-host.md` — Android 第二 Host，核仍是这份 Java

## 已锁定（不要重开）

- 只做 NTSC 一台 NES。Host 只走 `nes.console.Console`。不抽 Emulator 接口。
- 核与 Windows：Java 17 + Maven。Android：Gradle + 系统 View / AudioTrack / SAF。不引入 Flutter / iOS。
- Mapper 只在 `cart`：0 / 1 / 2。`android/` 禁止解析 iNES。
- 暂停 = Host 不调用 `stepFrame`；换盘/重启 = `new Console(ines)`。切后台也不补帧。
- 即时存档走 `nes.save.SaveStore`（NES1 v2 快照外包 SLOT）。Windows 写工作目录/`exe` 旁；Android 写应用私有目录。不拆快照。
- Android 只挪虚拟键位置（左右栏内），不改 NES bit，不画键盘。
- 会话不是 ROM 管理器。仓库不收商业 ROM。

## 仍开放

- PPU loopy 移位器、更多 mapper、SHA/SHX/SHY/XAA/LAS。

## 验证

```
mvn -q compile
java -cp target/classes nes.selfcheck.SelfCheck
java -cp target/classes nes.host.Main roms\nova.nes
```

Android：Studio Open `android/` → 开 AVD → Run。把 ROM 放进虚拟机后应用内「打开 ROM」：

```
adb push roms\nova.nes /sdcard/Download/
```

## 踩过的坑

- `nova.nes` 是 mapper 1、CHR RAM，不是 NROM。16KB PRG 复位向量在文件偏移 `16+0x3FFC`。
- 音频用 write 阻塞限速，不要每帧 `new` 采样数组 + 墙钟 60Hz 睡眠。
- MMC1 连续周期写已丢掉。PPU 背景仍用 `t`，不是逐 dot loopy。
- 不稳定非官方 6502 仍抛（带 PC）。NES1 v1 存档拒读。
- Windows 暂停键要挡自动重复。
- 仓库路径含「模拟器」：AGP 默认拒编。已设 `android.overridePathCheck=true`。aapt/ndk 若再因中文路径失败，把仓库挪到纯 ASCII 目录。
- AVD 默认写在 `%USERPROFILE%\.android\avd`。C 盘不够时设用户变量 `ANDROID_AVD_HOME`（本机曾指到 `E:\Android\avd`），然后**整进程退出 Studio** 再建虚拟机。userdata 大约要 12GB。
- Android Studio 必须 Open `android/`，不要 Open 仓库根。ROM 不会打进 APK，要自己推进虚拟机再选。

## 给下一会话的提示词

```
先读 AGENTS.md 和 docs/project-map.md，需要时再读 docs/handoff.md 与 docs/adr/。

NTSC FC/NES。Host 只走 nes.console.Console。Windows 与 Android 两个 Host，核不改平台。

当前能玩：mapper 0/1/2，方波/三角/噪声/DMC，游戏常用非官方 6502。
Windows：选盘/换盘/暂停/重启/存档槽/换绑。存档 NES1 v2。
Android：选盘/暂停/重启/存档槽/拖虚拟键位置；锁横屏。工程在 android/。
测试 ROM：roms/nova.nes。https://github.com/LaVendergong/nes-emulator

动手前写四行：意图 / 改哪些不改哪些 / 风险 / 验证。
一次只做一个意图。跨模块只走公开入口。不要顺手重构。
新依赖必须能说出「没有它 60 帧或当前 Host 窗口/音频做不到」。

用户没点名下一刀：不要自己加 mapper/PPU 重写/Android 存档。问一句完成定义再做。
用户点名了：03-implement 或 04-change；修 bug 用 07-debug（先复现）。

改完更新地图被你碰过的那几行，并跑：
  mvn -q compile
  java -cp target/classes nes.selfcheck.SelfCheck
Android 改动：Studio 打开 android/ 在 AVD 上再跑一遍主路径。
```
