package nes.cart;

/**
 * Mapper 9（MMC2）/ 10（MMC4）。CHR 靠 PPU 读 $FD/$FE 行锁存。
 */
final class Mmc2 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean prg16k;
    private boolean verticalMirroring;
    private int prgBank;
    private final int[] chrFd = new int[2];
    private final int[] chrFe = new int[2];
    private final int[] latch = {1, 1};

    Mmc2(byte[] prg, byte[] chr, boolean chrRam, boolean prg16k) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.prg16k = prg16k;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            return prg[prgOffset(address)] & 0xFF;
        }
        if (address >= 0x6000) {
            return prgRam[address - 0x6000] & 0xFF;
        }
        return 0;
    }

    @Override
    public void cpuWrite(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;
        if (address >= 0xA000 && address <= 0xAFFF) {
            prgBank = value;
            return;
        }
        if (address >= 0xB000 && address <= 0xBFFF) {
            chrFd[0] = value;
            return;
        }
        if (address >= 0xC000 && address <= 0xCFFF) {
            chrFe[0] = value;
            return;
        }
        if (address >= 0xD000 && address <= 0xDFFF) {
            chrFd[1] = value;
            return;
        }
        if (address >= 0xE000 && address <= 0xEFFF) {
            chrFe[1] = value;
            return;
        }
        if (address >= 0xF000) {
            verticalMirroring = (value & 1) == 0;
            return;
        }
        if (address >= 0x6000 && address < 0x8000) {
            prgRam[address - 0x6000] = (byte) value;
        }
    }

    @Override
    public int ppuRead(int address) {
        address &= 0x1FFF;
        int v = chr[chrOffset(address)] & 0xFF;
        int row = address & 0x1FF8;
        if (row == 0x0FD8) {
            latch[0] = 0;
        } else if (row == 0x0FE8) {
            latch[0] = 1;
        } else if (row == 0x1FD8) {
            latch[1] = 0;
        } else if (row == 0x1FE8) {
            latch[1] = 1;
        }
        return v;
    }

    @Override
    public void ppuWrite(int address, int value) {
        if (chrRam) {
            chr[chrOffset(address & 0x1FFF)] = (byte) value;
        }
    }

    @Override
    public int nametableOffset(int address) {
        int off = address & 0x0FFF;
        if (verticalMirroring) {
            return off & 0x07FF;
        }
        return ((off & 0x800) >> 1) | (off & 0x3FF);
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(prg16k ? 10 : 9);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeBoolean(verticalMirroring);
        out.writeInt(prgBank);
        out.writeInt(chrFd[0]);
        out.writeInt(chrFd[1]);
        out.writeInt(chrFe[0]);
        out.writeInt(chrFe[1]);
        out.writeInt(latch[0]);
        out.writeInt(latch[1]);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        int id = in.readInt();
        if (id != (prg16k ? 10 : 9)) {
            throw new java.io.IOException("存档不是 MMC2/4");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        verticalMirroring = in.readBoolean();
        prgBank = in.readInt();
        chrFd[0] = in.readInt();
        chrFd[1] = in.readInt();
        chrFe[0] = in.readInt();
        chrFe[1] = in.readInt();
        latch[0] = in.readInt();
        latch[1] = in.readInt();
    }

    private int prgOffset(int address) {
        if (prg16k) {
            int banks = Math.max(1, prg.length / 0x4000);
            int last = banks - 1;
            int slot = address >= 0xC000 ? last : prgBank % banks;
            return slot * 0x4000 + (address & 0x3FFF);
        }
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int slot;
        if (address >= 0xA000) {
            slot = last - 2 + ((address - 0xA000) / 0x2000);
            if (slot < 0) {
                slot = last;
            }
        } else {
            slot = prgBank % banks;
        }
        return Math.floorMod(slot, banks) * 0x2000 + (address & 0x1FFF);
    }

    private int chrOffset(int address) {
        int table = address >> 12;
        int bank = latch[table] == 0 ? chrFd[table] : chrFe[table];
        int banks = Math.max(1, chr.length / 0x1000);
        return (bank % banks) * 0x1000 + (address & 0x0FFF);
    }
}
