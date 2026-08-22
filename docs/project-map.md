# 项目地图

> 3 分钟能读完。只写现在为真的事。过期的句子直接删，不要加「即将」。

## 一句话

Windows 上的 FC/NES 模拟器：你提供 ROM，本机把游戏跑到约 60 帧。

## 不做

用户未列产品级「不做」。下列是冷启动为防摊开加上的工程边界，要推翻先改地图：

- 不做第二机种（GB / SNES / Arcade / FDS）
- 不做联机、ROM 管理器、ROM 下载或分发
- 仓库不收商业 ROM（版权）

## 本项目宪法（≤10）

1. 只模拟一台 NTSC FC/NES。新机种先写 ADR。
2. Host 只通过 `console` 公开入口说话。
3. CPU 只通过 `bus` 访存，不认识 mapper / PPU / APU 内部。
4. 时间只按 NES 主时钟推进，不以墙钟跳步。
5. NTSC 比例固定：1 CPU cycle = 3 PPU dots。禁止改成按扫描线估算。
6. Mapper 逻辑只在 `cart`。禁止在 `bus` 里写 `if (mapperId == …)`。
7. Host 只 blit 帧缓冲、只播放采样；禁止解释 PPU/APU 寄存器。
8. 新依赖必须能说出「没有它就做不到 60 帧或 Windows 窗口/音频」。
9. 核心不变量必须有最小可运行检查；没钉的时序视为可被改丢。
10. 一次一个意图；跨模块只走已有公开入口。

## 模块

| 模块 | 职责 | 公开入口 | 可以依赖 | 禁止依赖 |
|---|---|---|---|---|
| `cart` | 读 iNES、按 mapper 映射 PRG/CHR | 加载 ROM；CPU/PPU 侧 read/write；`nametableOffset`；快照 | 无 | cpu / ppu / apu / host |
| `bus` | CPU 地址译码，接到 RAM / PPU / APU / cart | `read` / `write`；快照 | cart, ppu 寄存器口, apu 寄存器口 | host, cpu 内部 |
| `cpu` | 6502 | 步进周期；NMI/IRQ/Reset 线；快照 | bus | cart / ppu / apu / host |
| `ppu` | 图像与 NMI | 步进 dot；取帧缓冲；快照 | cart 的 PPU 口（CHR/VRAM 映射） | host / cpu / apu |
| `apu` | 方波/三角/噪声/DMC | `tick`、`write`、`read4015`、`drain`、IRQ、`setDmcRead`、`takeDmcStall`；快照 | 无（DMC 取样走 Console 注入的读回调） | host / cpu / ppu / cart |
| `console` | 把上面焊成一台机器，按帧/周期推进 | `new Console(ines)`、`stepFrame`、`stepInstruction`、`framebuffer`、`drainSamples`、`setButtons`、`peekCpu`、`cpuCycles`/`ppuDots`、`saveState`/`loadState` | cpu, ppu, apu, bus, cart | 无（唯一对外门面） |
| `host` | Windows 窗口、按键、音频、会话控制 | `Main`：选盘、换盘、暂停、重启、存档槽、换绑 | 只依赖 console | cpu / ppu / apu / bus / cart |

## 主路径

1. Host 读用户给出的 `.nes` 文件，交给 `console`。
2. `cart` 解析 iNES，挂上 mapper。
3. `console` 复位 CPU/PPU/APU。
4. `console` 按 NES 时钟步进：1 CPU cycle 同时推进 3 PPU dots 和 1 次 APU。
5. 一帧结束，Host 把帧缓冲画到窗口，把采样送给声卡。有声卡时用写入阻塞限速；无声卡时才用墙钟 60Hz（慢了不补步）。
6. 按键在每帧开始写入手柄状态。
7. 暂停时 Host 不调用 `stepFrame`，NES 时间冻结；继续时不按墙钟补步。
8. 换盘与重启都是 `new Console(ines)`，不在 Host 里拆 ROM。
9. 即时存档：`console.saveState`/`loadState`（魔数 NES1 **v2**；v1 槽拒读）。Host 在 `saves/<rom名>/` 放最多 N 个槽（默认 10，1–30 可调），可覆盖。弹窗点有档槽即读档继续。
10. 手柄键表在 Host：弹窗画键盘，涂色=已绑定；点涂色键再按键或点键盘完成换绑。O/P/空格/R/F5/Esc 留给 Host。写入 `saves/keys.txt`。

## 不变量

1. NTSC：每推进 1 个 CPU cycle，PPU 必须恰好推进 3 个 dots。 — 验证：`java -cp target/classes nes.selfcheck.SelfCheck`
2. 暂停后再继续，NES 时间不得跳到墙钟。Host 可以用墙钟 *限速*，不能用墙钟 *补步*。 — 验证：同上（自检里 sleep 后周期不变）
3. `$8000–$FFFF` 的 CPU 读只来自 `cart` 映射，不来自 Host 拆文件。 — 验证：同上（自检读 `$8000==$78`）
4. 采样率 44100：N 个 CPU cycle 产出约 `N * 44100 / 1789773` 个采样（误差 ≤1）。 — 验证：同上
5. 暂停不推进周期；换盘/重启是新 `Console`，不继承周期。 — 验证：同上（`Session.verify`）
6. 读档后 CPU/PPU 时钟与帧缓冲回到存档点；覆盖槽位换成新快照。 — 验证：同上
7. 默认 Z=A、X=B；换到空键旧键松开；换到已占用键则对调；Host 热键不能绑手柄。 — 验证：同上（`KeyBindings.verify`）

## 关键决策

- [ADR 0001](adr/0001-java-stdlib-host.md) — 语言与 Host：Java 17 + JDK 标准库
- [ADR 0002](adr/0002-ntsc-cycle-clock.md) — 时钟：NTSC，周期推进，不是扫描线估算

## 禁止事项（给后来者）

- 不要在 `host` 里解析 iNES 或实现 mapper
- 不要在 `host` 里合成波形；只播放 `console.drainSamples()`
- 不要在 `cpu` 里直接读 PPU/APU 字段
- 不要在 `bus` 里写 mapper 特例
- 不要把商业 ROM 提交进仓库
- 不要为「以后多机种」抽 Emulator 接口
- 不要把会话控制做成 ROM 管理器（不建库、不扫描目录、不下载）
- 不要在 Host 里拆 NES 快照；槽位文件只包时间/缩略图 + `console.saveState()` 字节

## 如何验证

```
mvn -q compile
java -cp target/classes nes.selfcheck.SelfCheck
java -cp target/classes nes.host.Main 路径\到\rom.nes
```

当前切片：mapper 0 / 1（MMC1，连续周期写丢掉）/ 2（UNROM）；APU 含 DMC。
游戏会用到的非官方 6502（SLO/RLA/SRE/RRA、LAX/SAX、DCP/ISC、`$EB`、ANC/ALR/ARR/AXS、JAM）已实现；不稳定组合（SHA/SHX/SHY/XAA/LAS）仍抛异常（带 PC）。
Host 手柄：默认可换绑（Z=A X=B Shift/A=Select Enter=Start 方向键）。菜单「切换按键...」。
Host 会话：菜单「游戏」或 O=打开/换盘，P/空格=暂停，R=重启，F5=存档槽。
