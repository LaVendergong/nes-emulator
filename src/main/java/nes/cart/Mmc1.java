package nes.cart;

/**
 * Mapper 1（MMC1 / SxROM）。串行写 $8000–$FFFF。
 * ponytail: 不忽略连续周期写。天花板：RMW 碰到银行寄存器。升级：按 CPU cycle 丢弃连写。
 */
final class Mmc1 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private int control = 0x0C;
    private int chr0;
    private int chr1;
    private int prgBank;
    private int shift;
    private int shiftCount;

    Mmc1(byte[] prg, byte[] chr, boolean chrRam) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
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
        if (address >= 0x8000) {
            if ((value & 0x80) != 0) {
                shift = 0;
                shiftCount = 0;
                control |= 0x0C;
                return;
            }
            shift |= (value & 1) << shiftCount;
            shiftCount++;
            if (shiftCount == 5) {
                load(address, shift);
                shift = 0;
                shiftCount = 0;
            }
            return;
        }
        if (address >= 0x6000) {
            prgRam[address - 0x6000] = (byte) value;
        }
    }

    @Override
    public int ppuRead(int address) {
        return chr[chrOffset(address)] & 0xFF;
    }

    @Override
    public void ppuWrite(int address, int value) {
        if (chrRam) {
            chr[chrOffset(address)] = (byte) value;
        }
    }

    @Override
    public int nametableOffset(int address) {
        int off = address & 0x0FFF;
        return switch (control & 3) {
            case 0 -> off & 0x03FF;
            case 1 -> 0x0400 | (off & 0x03FF);
            case 2 -> off & 0x07FF;
            default -> ((off & 0x800) >> 1) | (off & 0x3FF);
        };
    }

    private void load(int address, int data) {
        switch ((address >> 13) & 3) {
            case 0 -> control = data;
            case 1 -> chr0 = data;
            case 2 -> chr1 = data;
            default -> prgBank = data;
        }
    }

    private int prgOffset(int address) {
        int banks = prg.length / 0x4000;
        int last = banks - 1;
        int bank = prgBank & 0x0F;
        if (banks > 16) {
            bank |= (chr0 & 0x10);
        }
        bank &= last;
        int mode = (control >> 2) & 3;
        int slot;
        if (mode <= 1) {
            slot = (bank & ~1) + ((address >> 14) & 1);
        } else if (mode == 2) {
            slot = address < 0xC000 ? 0 : bank;
        } else {
            slot = address < 0xC000 ? bank : last;
        }
        return (slot * 0x4000 + (address & 0x3FFF)) % prg.length;
    }

    private int chrOffset(int address) {
        address &= 0x1FFF;
        int bank;
        if ((control & 0x10) == 0) {
            bank = chr0 & 0x1E;
            return (bank * 0x1000 + address) % chr.length;
        }
        bank = (address < 0x1000 ? chr0 : chr1) & 0x1F;
        return (bank * 0x1000 + (address & 0x0FFF)) % chr.length;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(1);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(control);
        out.writeInt(chr0);
        out.writeInt(chr1);
        out.writeInt(prgBank);
        out.writeInt(shift);
        out.writeInt(shiftCount);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 1) {
            throw new java.io.IOException("存档不是 MMC1");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        control = in.readInt();
        chr0 = in.readInt();
        chr1 = in.readInt();
        prgBank = in.readInt();
        shift = in.readInt();
        shiftCount = in.readInt();
    }
}
