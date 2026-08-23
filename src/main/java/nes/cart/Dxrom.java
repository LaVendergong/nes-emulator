package nes.cart;

/**
 * Mapper 206（DxROM / MIMIC-1）。MMC3 银行，无 IRQ、无 $A000 镜像。
 */
final class Dxrom implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private final int[] bank = new int[8];
    private int bankSelect;

    Dxrom(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
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
            if ((address & 1) == 0) {
                bankSelect = value;
            } else {
                bank[bankSelect & 7] = value;
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
        if (verticalMirroring) {
            return off & 0x07FF;
        }
        return ((off & 0x800) >> 1) | (off & 0x3FF);
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(206);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(bankSelect);
        for (int b : bank) {
            out.writeInt(b);
        }
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 206) {
            throw new java.io.IOException("存档不是 DxROM");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        bankSelect = in.readInt();
        for (int i = 0; i < bank.length; i++) {
            bank[i] = in.readInt();
        }
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int second = Math.max(0, last - 1);
        int slot;
        if (address >= 0xE000) {
            slot = last;
        } else if (address >= 0xC000) {
            slot = second;
        } else if (address >= 0xA000) {
            slot = bank[7] % banks;
        } else {
            slot = bank[6] % banks;
        }
        return slot * 0x2000 + (address & 0x1FFF);
    }

    private int chrOffset(int address) {
        int a = address & 0x1FFF;
        int r;
        int mask;
        if (a < 0x0800) {
            r = bank[0] & ~1;
            mask = 0x7FF;
        } else if (a < 0x1000) {
            r = bank[1] & ~1;
            mask = 0x7FF;
        } else if (a < 0x1400) {
            r = bank[2];
            mask = 0x3FF;
        } else if (a < 0x1800) {
            r = bank[3];
            mask = 0x3FF;
        } else if (a < 0x1C00) {
            r = bank[4];
            mask = 0x3FF;
        } else {
            r = bank[5];
            mask = 0x3FF;
        }
        return (r * 0x400 + (a & mask)) % chr.length;
    }
}
