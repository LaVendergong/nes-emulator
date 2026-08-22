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

    void saveState(DataOutput out) throws IOException;

    void loadState(DataInput in) throws IOException;
}
