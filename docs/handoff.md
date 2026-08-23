# 交接

## 当前状态

**v0.3.0 已发布**（Host / 会话收口）。其后核精度已往前推了一大截，**尚未打新版本号**。发布页仍是：https://github.com/LaVendergong/nes-emulator/releases/tag/v0.3.0

同一份 Java 17 核，Windows 与 Android 两个 Host。Host 只走 `nes.console.Console`。

### 核（v0.3.0 之后已合入工作树）

- 制式：`InesRom.tvSystem` → NTSC / PAL / Dendy。NTSC、Dendy：1 CPU = 3 PPU dots；PAL：5 CPU = 16 dots（3+3+3+3+4）。PAL/Dendy PPU 312 线。APU 采样：NTSC 1789773、PAL 1662607、Dendy 1773447。制式不进 NES1 快照。
- Mapper（`InesRom` 接受）：0–11、13、19、21–26、34、66、69、71、73、75、79、85、87、140、180、206、210。未列出的（如 12）在 cart 边界拒绝。
- 扩声：MMC5 方波/PCM、VRC6 方波+锯齿、FME-7/5B 三方波、Namco 163 波形表、VRC7→`Ym2413`（72 chip clock 出一样；VRC7 只用 6 路 + Konami 音色）。
- PPU：loopy `v` + 背景移位器；精灵每线 8 个；溢出按 n/m 错位；A12 每 dot 交给 cart。
- 6502：游戏会用到的非官方码（含 SHA/SHX/SHY/XAA/LAS）。XAA 用上条指令总线残留 & `$EE`。
- four-screen：`FourScreenCart` 自带 4KB NT，不扩 PPU CIRAM（NES1 v2 长度不变）。MMC5 不包。
- FFE 6/8：`$4501–$4503` CPU 周期 IRQ。

### Host（v0.3.0，此后未改）

- Windows：`jpackage` 便携包 `fc-nes-0.3.0-windows.zip`。选盘/换盘/暂停/重启/存档槽/换绑。键表 `saves/keys.txt`。
- Android：`android/` Gradle；锁横屏；SAF 选盘；存档走应用私有 `saves/`；虚拟键可拖，bit 固定。APK 为调试签名。
- 槽位：`nes.save.SaveStore`，NES1 **v2**（v1 拒读）。两个 Host 共用。
- 测试盘：`roms/nova.nes`（mapper 1 + CHR RAM）。仓库：https://github.com/LaVendergong/nes-emulator

## 下一步唯一动作

等用户一句话点名下一刀（完成定义 + 改哪个模块），再按 `prompts/templates.md` 的 `03-implement` 或 `04-change`。没点名不要加功能、不要重构、不要自己开 mapper/PPU。

核精度这一段（常见 mapper、制式、YM2413 72-slot）已收口。下一刀由用户点名，例如：实机/测试盘对拍、打 v0.4.0、或某张具体盘的 bug。

候选（空着 ≠ 现在要做）：iOS；Nuked 级 YM2413 18-slot 流水线；更偏门 mapper（VRC5、Namco 175 以外的冷门板）；正式 Android 签名。

## 必须先读（最多 5）

1. `AGENTS.md` — 工作流与不可妥协
2. `docs/project-map.md` — 模块、入口、不变量
3. `docs/adr/0002-ntsc-cycle-clock.md` + `docs/adr/0004-pal-ym2413.md` — 周期推进；NTSC/Dendy 1:3，PAL 5:16
4. `docs/adr/0003-android-host.md` — Android 只 blit / 播采样 / 收按钮，不抽 Emulator 接口
5. `src/main/java/nes/cart/InesRom.java` — 接受哪些 mapper、如何判制式

## 已锁定（不要重开）

- 只做一台 FC/NES（NTSC / PAL / Dendy）。Host 只走 `nes.console.Console`。不抽 Emulator 接口。
- 核与 Windows：Java 17 + Maven。Android：Gradle + 系统 View / AudioTrack / SAF。不引入 Flutter / iOS。
- Mapper 只在 `cart`。four-screen 包一层 4KB NT，不改 NES1 CIRAM。`android/` 与 `host/` 禁止解析 iNES。
- 暂停 = Host 不调用 `stepFrame`；换盘/重启 = `new Console(ines)`。切后台也不补帧。
- 即时存档走 `nes.save.SaveStore`（NES1 v2 快照外包 SLOT）。不拆快照。v1 槽拒读。
- Android 只挪虚拟键位置（左右栏内），不改 NES bit，不画键盘。
- 会话不是 ROM 管理器。仓库不收商业 ROM。
- 发布 APK 用调试签名即可；正式签名要另开任务。
- FFE 不做比 `$450x` 更老的变体 IRQ。VRC7 不做完整 YM2413 节奏通道。

## 仍开放

- iOS。
- Nuked 级 YM2413 18-slot 流水线。
- PAL 读档对齐 3+3+3+3+4 相位（需 NES1 v3）。
- 正式 Android 签名；打 v0.4.0（核精度切片发版）。

## 验证

```
mvn -q compile
java -cp target/classes nes.selfcheck.SelfCheck
java -cp target/classes nes.host.Main roms\nova.nes
powershell -File scripts/package-win.ps1
```

Android：Studio **Open** `android/`（不要开仓库根）→ 开 AVD → Run。ROM 自备：

```
adb push roms\nova.nes /sdcard/Download/
```

## 踩过的坑

- `nova.nes` 是 mapper 1、CHR RAM，不是 NROM。16KB PRG 复位向量在文件偏移 `16+0x3FFC`。
- 音频用 write 阻塞限速。`drainTo` 装不下的采样必须留下。Host 队列超过约 100ms 要追上，否则越积越钝。
- CPU 越页寻址禁止 `new int[]`；热路径分配会 GC 破音、越玩越卡。
- MMC1 连续周期写已丢掉。PPU 背景已是 loopy `v` + 移位器。MMC3 A12 须先低 8 个 PPU dot。精灵溢出按 n/m 错位。MMC5 寄存器在 $5xxx，Bus 的 $4020+ 给 cart。扩展声由 Console 注入 APU。
- 超级玛丽死亡音在方波 1（`$4001=$94` 扫频）+ 三角 `$1F` 一次性；包络 `start` 未进 quarter 时按 15 出声，`$400B` 立刻装 linear。
- NES1 v1 存档拒读。XAA 用上条指令总线残留 & $EE。
- Windows 暂停键要挡自动重复。
- 仓库路径含「模拟器」：`android.overridePathCheck=true`。aapt/ndk 若再因中文路径失败，把仓库挪到纯 ASCII 目录。
- AVD 默认写 `%USERPROFILE%\.android\avd`。C 盘不够设 `ANDROID_AVD_HOME`，然后整进程退出 Studio 再建。userdata 大约 12GB。
- Android Studio 必须 Open `android/`。ROM 不打进 APK。`*.jar` 已放行 `android/gradle/wrapper/gradle-wrapper.jar`。
- `assembleRelease` 会卡在签名；出包用 `assembleDebug`，发布页注明调试签名。
- PowerShell 不要用 `&&` 串命令；用 `; if ($LASTEXITCODE -eq 0)`。
- 无声卡时 Host 仍按 60 Hz 墙钟限速，PAL/Dendy 盘会偏快（有声卡则跟采样走，约 50 Hz）。

## 给下一会话的提示词

```
先读 AGENTS.md 和 docs/project-map.md，需要时再读 docs/handoff.md 与 docs/adr/。

NTSC/PAL/Dendy FC/NES。Host 只走 nes.console.Console。Windows 与 Android 两个 Host，核不改平台。

v0.3.0 已发布（Host/会话）。其后核已含常见 mapper、制式、YM2413 72-slot、four-screen；尚未打 v0.4.0。
能玩：mapper 0–11、13、19、21–26、34、66、69、71、73、75、79、85、87、140、180、206、210；MMC5/VRC6/5B/N163/VRC7 扩声；FFE $450x IRQ。
Windows：选盘/换盘/暂停/重启/存档槽/换绑。存档 NES1 v2。
Android：选盘/暂停/重启/存档槽/拖虚拟键位置；锁横屏。工程在 android/。
测试 ROM：roms/nova.nes。https://github.com/LaVendergong/nes-emulator

动手前写四行：意图 / 改哪些不改哪些 / 风险 / 验证。
一次只做一个意图。跨模块只走公开入口。不要顺手重构。
新依赖必须能说出「没有它 60 帧或当前 Host 窗口/音频做不到」。

用户没点名下一刀：不要自己加 iOS/新 Host/新 mapper。问一句完成定义再做。
用户点名了：03-implement 或 04-change；修 bug 用 07-debug（先复现）。

改完更新地图被你碰过的那几行，并跑：
  mvn -q compile
  java -cp target/classes nes.selfcheck.SelfCheck
Android 改动：Studio 打开 android/ 在 AVD 上再跑一遍主路径。
```
