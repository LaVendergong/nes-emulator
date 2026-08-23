package nes.cart;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** cart 公开入口：CPU/PPU 总线侧读写与 nametable 镜像。 */
public interface Cartridge {
    int cpuRead(int address);

    void cpuWrite(int address, int value);

    int ppuRead(int address);

    void ppuWrite(int address, int value);

    /** 把 PPU $2000–$2FFF 映到 2KB CIRAM 偏移（0–0x7FF）。 */
    int nametableOffset(int address);

    /** mapper 自带 nametable（ExRAM/fill）时返回 0–255；否则 -1 走 CIRAM。 */
    default int nametableRead(int address) {
        return -1;
    }

    /** 拦截 PPU 写 nametable。true 表示已吃掉。 */
    default boolean nametableWrite(int address, int value) {
        return false;
    }

    void saveState(DataOutput out) throws IOException;

    void loadState(DataInput in) throws IOException;

    /** 每个 CPU cycle。MMC1 用来丢掉 RMW 的第二次写。 */
    default void clockCpu() {}

    /** PPU 地址总线 A12 电平（每 dot 一次，保持上次访存）。MMC3 滤短脉冲。 */
    default void onPpuA12(boolean high) {}

    /** 已滤过的 A12 上升。测试可直接打拍。 */
    default void onPpuA12Rise() {}

    /** MMC3 等 mapper 的 IRQ。默认不拉。 */
    default boolean irqAsserted() {
        return false;
    }

    /** 进入该扫描线（该 tick 的 dot 0）。MMC5 扫描线 IRQ。 */
    default void onPpuScanline(int scanline, boolean rendering) {}

    /** PPU 正在取精灵图案（dots 257–320）。MMC5 CHR 换 $5120 组。 */
    default void setChrSpriteWindow(boolean sprite) {}

    /** 当前背景 coarse X（0–31）。MMC5 分割卷轴。 */
    default void setPpuCoarseX(int coarseX) {}

    /** MMC5 等扩展方波，0–30。默认 0。 */
    default int expansionPulse() {
        return 0;
    }

    /** MMC5 PCM，0–127。默认 0。 */
    default int expansionPcm() {
        return 0;
    }
}
