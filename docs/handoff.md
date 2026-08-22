# 交接

## 当前状态

Windows 上的 NTSC FC/NES 模拟器主路径已通：`nova.nes`（MMC1）能玩、有声、卡顿已按音频时钟修过；用户说暂时没有新现象。

## 下一步唯一动作

等用户用一句话点名下一刀（完成定义 + 改哪个模块），再按 `prompts/templates.md` 的 `03-implement` 或 `04-change` 做。在那之前不要加功能、不要重构。

## 必须先读（最多 5）

1. `AGENTS.md` — 工作纪律。
2. `docs/project-map.md` — 模块边界、不变量、禁止事项。
3. `docs/adr/0001-java-stdlib-host.md` — Java 17 + 标准库，零运行时依赖。
4. `docs/adr/0002-ntsc-cycle-clock.md` — 1 CPU cycle = 3 PPU dots，不用墙钟推 NES。
5. `prompts/templates.md` — 对号入座后再动手。

## 已锁定（不要重开）

- 只做 NTSC 一台 NES。Host 只通过 `nes.console.Console`。
- Java 17 + Maven，运行时零依赖。窗口 AWT/Java2D，音频 `javax.sound.sampled`。
- Mapper 逻辑只在 `cart`。当前：0（NROM）、1（MMC1）。
- 有声卡时 Host 用 `SourceDataLine.write` 阻塞限速；热路径复用采样缓冲。
- ROM 用户自备；仓库不收商业 ROM。测试盘：`roms/nova.nes`。

## 仍开放

- 用户未指定下一功能。地图里空着的不等于现在要做：DMC、PPU loopy 移位器、其它 mapper、未实现的非官方 6502。
- 产品级「明确不做」用户写过「暂无」；工程围栏在地图「不做」。

## 验证

```
mvn -q compile
java -cp target/classes nes.selfcheck.SelfCheck
java -cp target/classes nes.host.Main roms\nova.nes
```

按键：Z=A，X=B，Shift=Select，Enter=Start，方向键。

## 踩过的坑

- `nova.nes` 是 mapper 1、256KB PRG、CHR RAM，不是 NROM。
- 16KB PRG 复位向量在文件偏移 `16+0x3FFC`，不是 `0x7FFC`。
- 每帧 `new` 采样数组 + 墙钟 60.00Hz 睡眠会隔几秒卡一下；已改为复用缓冲 + 音频阻塞。
- MMC1 未忽略连续周期写（RMW 撞银行寄存器可能坏）。PPU 背景用 `t` 算像素，不是逐 dot loopy。
- 未实现的 6502 操作码会抛异常（带 PC）。

## 给下一会话的提示词

```
先读 AGENTS.md 和 docs/project-map.md，需要时再读 docs/handoff.md 与 docs/adr/。

这是 Windows 上的 NTSC FC/NES 模拟器。边界以地图为准。Host 只走 nes.console.Console。

当前能玩：mapper 0/1，方波/三角/噪声，无 DMC。测试 ROM：roms/nova.nes。用户说暂时没有新现象。

动手前写四行：意图 / 改哪些不改哪些 / 风险 / 验证。
一次只做一个意图。跨模块只走公开入口。不要顺手重构，不要为以后预留层。
新依赖必须能说出「没有它 60 帧或窗口/音频做不到」。

用户若没点名下一刀：不要自己加 DMC/mapper/PPU 重写。问一句完成定义再做。
用户若点名了：套 prompts/templates.md 的 03-implement 或 04-change；修 bug 用 07-debug（先复现）。

改完更新地图被你碰过的那几行，并跑：
  mvn -q compile
  java -cp target/classes nes.selfcheck.SelfCheck
```
