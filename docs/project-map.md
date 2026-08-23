# 项目地图

> 3 分钟能读完。只写现在为真的事。过期的句子直接删，不要加「即将」。

## 一句话

NTSC/PAL/Dendy FC/NES 模拟器：你提供 ROM，Windows 或 Android 把游戏跑到约 60（PAL/Dendy 约 50）帧。

## 不做

用户未列产品级「不做」。下列是冷启动为防摊开加上的工程边界，要推翻先改地图：

- 不做第二机种（GB / SNES / Arcade / FDS）
- 不做 iOS、不做 Flutter / 跨端重写核
- 不做联机、ROM 管理器、ROM 下载或分发
- 仓库不收商业 ROM（版权）

## 本项目宪法（≤10）

1. 只模拟一台 FC/NES（NTSC / PAL / Dendy，由 iNES 头选择）。新机种先写 ADR。
2. Host 只通过 `console` 公开入口说话。
3. CPU 只通过 `bus` 访存，不认识 mapper / PPU / APU 内部。
4. 时间只按 NES 主时钟推进，不以墙钟跳步。
5. 时钟按 CPU cycle 推进，禁止按扫描线估算。NTSC/Dendy：1 CPU = 3 PPU dots。PAL：5 CPU = 16 PPU dots。Dendy 与 PAL 都是 312 线。
6. Mapper 逻辑只在 `cart`。禁止在 `bus` 里写 `if (mapperId == …)`。
7. Host 只 blit 帧缓冲、只播放采样；禁止解释 PPU/APU 寄存器。
8. 新依赖必须能说出「没有它就做不到 60 帧或当前 Host 的窗口/音频」。
9. 核心不变量必须有最小可运行检查；没钉的时序视为可被改丢。
10. 一次一个意图；跨模块只走已有公开入口。

## 模块

| 模块 | 职责 | 公开入口 | 可以依赖 | 禁止依赖 |
|---|---|---|---|---|
| `cart` | 读 iNES、按 mapper 映射 PRG/CHR | 加载 ROM；CPU/PPU 侧 read/write；`nametableOffset`/`nametableRead`；A12；扫描线 IRQ；快照 | 无 | cpu / ppu / apu / host |
| `bus` | CPU 地址译码，接到 RAM / PPU / APU / cart | `read` / `write`；快照；$4020+ 给 cart | cart, ppu 寄存器口, apu 寄存器口 | host, cpu 内部 |
| `cpu` | 6502 | 步进周期；NMI/IRQ/Reset 线；快照 | bus | cart / ppu / apu / host |
| `ppu` | 图像与 NMI | 步进 dot；取帧缓冲；快照 | cart 的 PPU 口（CHR/VRAM、A12、扫描线、精灵窗） | host / cpu / apu |
| `apu` | 方波/三角/噪声/DMC | `tick`、`write`、`read4015`、`drain`、IRQ、`setDmcRead`、`setExpansion`、`takeDmcStall`；快照 | 无（DMC/扩展声走 Console 注入） | host / cpu / ppu / cart |
| `console` | 把上面焊成一台机器，按帧/周期推进 | `new Console(ines)`、`stepFrame`、`stepInstruction`、`framebuffer`、`drainSamples`、`setButtons`、`peekCpu`、`cpuCycles`/`ppuDots`、`saveState`/`loadState` | cpu, ppu, apu, bus, cart | 无（唯一对外门面） |
| `save` | 槽位文件：时间 + 缩略图 + `saveState` 字节 | `write` / `readState` / `meta` / `slotLimit` | 无 | cpu / ppu / apu / bus / cart / host / android / console |
| `host` | Windows 窗口、按键、音频、会话控制 | `Main`：选盘、换盘、暂停、重启、存档槽、换绑 | console, save | cpu / ppu / apu / bus / cart |
| `android` | Android 窗口、触控、音频 | 选盘、暂停、重启、存档槽、拖虚拟键位置；横屏三栏 | console, save | cpu / ppu / apu / bus / cart / host |

## 主路径

1. Host 读用户给出的 `.nes` 文件，交给 `console`。
2. `cart` 解析 iNES，挂上 mapper。
3. `console` 复位 CPU/PPU/APU。
4. `console` 按 NES 时钟步进：每个 CPU cycle 推进 APU 一次；NTSC/Dendy 再推 3 个 PPU dots，PAL 按 3+3+3+3+4。
5. 一帧结束，Host 把帧缓冲画到窗口，把采样送给声卡。有声卡时用写入阻塞限速；无声卡时才用墙钟 60Hz（慢了不补步）。
6. 按键在每帧开始写入手柄状态。
7. 暂停时 Host 不调用 `stepFrame`，NES 时间冻结；继续时不按墙钟补步。
8. 换盘与重启都是 `new Console(ines)`，不在 Host 里拆 ROM。
9. 即时存档：`console.saveState`/`loadState`（魔数 NES1 **v2**；v1 槽拒读）。槽位文件在 `save`：Windows 写 `saves/<rom名>/`，Android 写应用私有 `saves/<rom名>/`。最多 N 个槽（默认 10，1–30 可调），可覆盖。
10. Windows 手柄键表：弹窗画键盘换绑，写入 `saves/keys.txt`。Android 只挪虚拟键在左右栏的位置（bit 固定），写入 `saves/pad-layout.txt`。

## 不变量

1. NTSC：1 CPU = 3 PPU dots。PAL：312×341 且 5 CPU ≈ 16 PPU。Dendy：312×341 且 1 CPU = 3 PPU。 — 验证：`java -cp target/classes nes.selfcheck.SelfCheck`
2. 暂停后再继续，NES 时间不得跳到墙钟。Host 可以用墙钟 *限速*，不能用墙钟 *补步*。 — 验证：同上（自检里 sleep 后周期不变）
3. `$8000–$FFFF` 的 CPU 读只来自 `cart` 映射，不来自 Host 拆文件。 — 验证：同上（自检读 `$8000==$78`）
4. 采样率 44100：NTSC `1789773`，PAL `1662607`，Dendy `1773447`（误差 ≤1）。`drainTo` 装不下的采样必须留下，不能丢。 — 验证：同上
5. 暂停不推进周期；换盘/重启是新 `Console`，不继承周期。 — 验证：同上（`Session.verify`）
6. 读档后 CPU/PPU 时钟与帧缓冲回到存档点；覆盖槽位换成新快照。 — 验证：同上
7. 默认 Z=A、X=B；换到空键旧键松开；换到已占用键则对调；Host 热键不能绑手柄。 — 验证：同上（`KeyBindings.verify`）
8. 玛丽死亡同款：三角 `$1F` + 方波 `$94` 扫频，每帧 `$4017=$FF` 并重触发，应出非静音采样。 — 验证：同上
9. 帧中途 `$2006` 改 `v`：已画的扫描线仍用旧卷轴，之后跟新 `v` 走。 — 验证：同上（loopy 上半底色、下半实心）
10. CNROM 写 `$8000` 只切 8K CHR，PRG 不动。 — 验证：同上
11. MMC3：R6 切 `$8000`，$C000/$E000 固定；R0 切 2K CHR；latch=0 的第一次 A12 拉 IRQ，$E000 应答；A12 须先低 8 dot。 — 验证：同上
12. 同线第 9 个精灵不画，并置 `$2002` 溢出旗；满 8 个后按 n/m 错位读 OAM。 — 验证：同上
13. XAA 用上条指令数据总线残留（& `$EE`）；SHX/SHY/SHA/LAS 按常见公式。 — 验证：同上
14. MMC5：复位末页、切 8K PRG、乘法器、ExRAM、扫描线 IRQ、PCM、分割左侧 ExRAM；`$5104=1` 时 AT 用 ExRAM 高 2 位铺 8×8。 — 验证：同上
15. AxROM 切 32K；MMC2 读 $0FD8 锁存 CHR；MMC4 切 16K 且 $C000 固定；Color Dreams 同时切 PRG/CHR。 — 验证：同上
16. FFE6 切 16K PRG+8K CHR；FFE8 / GxROM 切 32K PRG+8K CHR；$4503 装载后下一 CPU cycle 溢出应 IRQ。 — 验证：同上
17. VRC1/4 切 PRG/CHR；VRC4/6 周期 IRQ；VRC2a CHR 右移；VRC3 16 位 IRQ；FME-7 命令口与下溢 IRQ；N163 $E000 与 NT→CHR。 — 验证：同上
18. VRC7/YM2413 按键出声、松键衰减；节奏 BD 出声；未满 72 chip clock 保持上一样。Namco 175 不改镜像，340 的 $E000 bit6–7 改镜像。 — 验证：同上

## 关键决策

- [ADR 0001](adr/0001-java-stdlib-host.md) — 语言与 Windows Host：Java 17 + JDK 标准库
- [ADR 0002](adr/0002-ntsc-cycle-clock.md) — 时钟：周期推进，不是扫描线估算
- [ADR 0003](adr/0003-android-host.md) — Android 第二 Host；核仍是这份 Java，不抽 Emulator 接口
- [ADR 0004](adr/0004-pal-ym2413.md) — NTSC/PAL/Dendy；YM2413 72-slot；制式不进 NES1

## 禁止事项（给后来者）

- 不要在 `host` / `android` 里解析 iNES 或实现 mapper
- 不要在 Host 里合成波形；只播放 `console.drainSamples()`
- 不要在 `cpu` 里直接读 PPU/APU 字段
- 不要在 `bus` 里写 mapper 特例
- 不要把商业 ROM 提交进仓库
- 不要为「以后多机种」抽 Emulator 接口
- 不要把会话控制做成 ROM 管理器（不建库、不扫描目录、不下载）
- 不要在 Host 里拆 NES 快照；槽位文件只包时间/缩略图 + `console.saveState()` 字节（走 `save`）

## 如何验证

```
mvn -q compile
java -cp target/classes nes.selfcheck.SelfCheck
java -cp target/classes nes.host.Main 路径\到\rom.nes
powershell -File scripts/package-win.ps1
```

Android：Android Studio **Open** `android/`（不要开仓库根）。Device Manager 开 AVD 后 Run。核源码由 Gradle 编进 APK（排除 `nes.host` / `nes.selfcheck`）。ROM 用 SAF 选；可用 `adb push roms\nova.nes /sdcard/Download/`。
Windows 路径含「模拟器」：`android/gradle.properties` 已开 `overridePathCheck`。AVD 默认在用户目录；C 盘不够设用户环境变量 `ANDROID_AVD_HOME` 到别的盘。

当前发布 **v0.4.0**：https://github.com/LaVendergong/nes-emulator/releases/tag/v0.4.0
Windows：解压 `dist/fc-nes-*-windows.zip`，双击 `FC-NES.exe`。存档写在 exe 旁的 `saves/`。Android APK 为调试签名。

当前切片：mapper 0–11、13、19、21–26、34、66、69、71、73、75、79、85、87、140、180、206、210。NTSC/PAL/Dendy。YM2413 72-slot。four-screen 在 cart 4KB NT。
游戏会用到的非官方 6502（含 SHA/SHX/SHY/XAA/LAS）已实现。XAA 跟总线残留。
Windows 手柄：默认可换绑（Z=A X=B Shift/A=Select Enter=Start 方向键）。菜单「切换按键...」。
Windows 会话：菜单「游戏」或 O=打开/换盘，P/空格=暂停，R=重启，F5=存档槽。
Android 本切片：锁横屏。中栏只画 256×240；虚拟键浮在左右栏，可拖位置，不挡画面。菜单含打开/暂停/重启/存档/调整位置。
